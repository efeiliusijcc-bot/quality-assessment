#!/usr/bin/env node
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

const API_BASE = (process.env.API_BASE || 'http://127.0.0.1:8080').replace(/\/$/, '');
const FRONTEND_BASE = (process.env.FRONTEND_BASE || 'http://127.0.0.1:5173').replace(/\/$/, '');
const USERNAME = process.env.API_USER || 'admin';
const PASSWORD = process.env.API_PASSWORD || '123456';
const EXPLICIT_CHROME_PATH = process.env.CHROME_PATH || '';
const CHROME_PATH = EXPLICIT_CHROME_PATH || findChromePath();
const DEBUG_PORT = Number(process.env.CHROME_DEBUG_PORT || 9223);
const CHROME_REMOTE_URL = (process.env.CHROME_REMOTE_URL || '').replace(/\/$/, '');
const SHOULD_LAUNCH_CHROME = process.env.UI_SMOKE_LAUNCH === '1' || Boolean(EXPLICIT_CHROME_PATH);
const CDP_HTTP_BASE = CHROME_REMOTE_URL || `http://127.0.0.1:${DEBUG_PORT}`;

const routes = [
  ['/upload', '多模态资源上传'],
  ['/cleaning-rules', '数据清洗规则'],
  ['/import-history', '导入历史记录'],
  ['/graph-sync', '知识图谱同步'],
  ['/knowledge-graph', '知识图谱可视化'],
  ['/export', '结果筛选与资源导出'],
  ['/assessment/qualified', '产品质量合格评估'],
  ['/assessment/judgment', '工艺参数研判评估'],
  ['/assessment/prediction', '工艺参数预测评估'],
  ['/defect-detection', '缺陷识别大屏'],
];

const forbiddenText = [
  'Production API',
  'STREAM STATUS',
  'CURRENT PAYLOAD',
  'SUBMISSION LOG',
  'OPTIMIZATION SUMMARY',
  'Top 5',
  'coming soon',
  'reserved',
  '未实现',
  '占位',
];

if (!CHROME_REMOTE_URL && !SHOULD_LAUNCH_CHROME) {
  console.log('[SKIP] UI smoke requires CHROME_REMOTE_URL or UI_SMOKE_LAUNCH=1.');
  console.log('Example: set CHROME_REMOTE_URL=http://127.0.0.1:9222 after starting Chrome with --remote-debugging-port=9222.');
  process.exit(0);
}

if (SHOULD_LAUNCH_CHROME && !CHROME_PATH) {
  console.error('Chrome was not found. Set CHROME_PATH to chrome.exe or msedge.exe.');
  process.exit(1);
}

const results = [];
const browserErrors = [];
let chrome;
let userDataDir;
let chromeStderr = '';

try {
  await assertReachable(API_BASE + '/api/user/captcha', 'backend');
  await assertReachable(FRONTEND_BASE, 'frontend');

  const login = await loginViaApi();
  pass('auth.login', `${USERNAME}@${API_BASE}`);

  if (SHOULD_LAUNCH_CHROME) {
    userDataDir = mkdtempSync(join(tmpdir(), 'demo3-ui-smoke-'));
    chrome = spawn(CHROME_PATH, [
      '--headless=new',
      '--disable-gpu',
      '--disable-extensions',
      '--no-first-run',
      '--no-default-browser-check',
      '--remote-debugging-address=127.0.0.1',
      `--remote-debugging-port=${DEBUG_PORT}`,
      `--user-data-dir=${userDataDir}`,
      'about:blank',
    ], { stdio: ['ignore', 'ignore', 'pipe'] });
    chrome.stderr?.on('data', (chunk) => {
      chromeStderr += chunk.toString();
    });
  }

  await waitForChrome();
  const target = await createTarget();
  const cdp = await connectCdp(target.webSocketDebuggerUrl);

  cdp.on('Runtime.exceptionThrown', (event) => {
    browserErrors.push(`exception: ${event.exceptionDetails?.text || 'unknown'}`);
  });
  cdp.on('Runtime.consoleAPICalled', (event) => {
    if (event.type === 'error') {
      const text = (event.args || []).map((arg) => arg.value || arg.description || '').join(' ');
      browserErrors.push(`console.error: ${text}`);
    }
  });

  await cdp.send('Runtime.enable');
  await cdp.send('Page.enable');
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', {
    source: `
      try {
        localStorage.setItem('qa_access_token', ${JSON.stringify(login.token)});
        localStorage.setItem('qa_user_profile', ${JSON.stringify(JSON.stringify({
          ...login.user,
          role: String(login.user.role || '').toLowerCase(),
        }))});
      } catch (error) {
        console.error('auth seed failed', error);
      }
    `,
  });

  for (const [path, marker] of routes) {
    try {
      await checkRoute(cdp, path, marker);
      pass(`route${path}`, marker);
    } catch (error) {
      fail(`route${path}`, error);
    }
  }

  const severeErrors = browserErrors.filter((item) =>
    !/ResizeObserver loop|favicon|net::ERR_ABORTED/i.test(item),
  );
  if (severeErrors.length) {
    fail('browser.console', new Error(severeErrors.slice(0, 5).join(' | ')));
  } else {
    pass('browser.console', 'no console errors');
  }
} catch (error) {
  fail('setup', error);
} finally {
  if (chrome && !chrome.killed) {
    chrome.kill();
    await new Promise((resolve) => chrome.once('exit', resolve));
  }
  if (userDataDir) {
    try {
      rmSync(userDataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    } catch (error) {
      console.warn(`[WARN] temp cleanup skipped: ${error.message}`);
    }
  }
}

printResults();

async function checkRoute(cdp, path, marker) {
  await cdp.navigate(FRONTEND_BASE + path);
  await waitFor(async () => {
    const text = await cdp.text();
    return text.includes(marker);
  }, 20000, `${path} marker ${marker}`);

  const location = await cdp.eval('location.pathname');
  if (location !== path) {
    throw new Error(`expected ${path}, got ${location}`);
  }

  const text = await cdp.text();
  const stale = forbiddenText.find((item) => text.includes(item));
  if (stale) {
    throw new Error(`found stale UI text: ${stale}`);
  }
}

async function loginViaApi() {
  const captcha = await getJson(API_BASE + '/api/user/captcha');
  const data = captcha.data;
  const svg = Buffer.from(data.captchaImage.split(',', 2)[1], 'base64').toString('utf8');
  const code = svg.match(/>([^<]+)<\/text>/)?.[1];
  if (!code) {
    throw new Error('captcha code was not found in SVG');
  }

  const login = await postJson(API_BASE + '/api/user/login', {
    username: USERNAME,
    password: PASSWORD,
    captchaId: data.captchaId,
    captchaCode: code,
  });
  if (login.code !== 200) {
    throw new Error(`login failed: ${login.msg || login.code}`);
  }
  return login.data;
}

async function createTarget() {
  const response = await fetch(`${CDP_HTTP_BASE}/json/new?about:blank`, { method: 'PUT' });
  if (!response.ok) {
    throw new Error(`create chrome target failed: ${response.status}`);
  }
  return response.json();
}

async function connectCdp(url) {
  const ws = new WebSocket(url);
  let nextId = 1;
  const pending = new Map();
  const listeners = new Map();

  await new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve, { once: true });
    ws.addEventListener('error', reject, { once: true });
  });

  ws.addEventListener('message', (message) => {
    const payload = JSON.parse(message.data);
    if (payload.id && pending.has(payload.id)) {
      const { resolve, reject } = pending.get(payload.id);
      pending.delete(payload.id);
      if (payload.error) {
        reject(new Error(payload.error.message || JSON.stringify(payload.error)));
      } else {
        resolve(payload.result || {});
      }
      return;
    }
    if (payload.method && listeners.has(payload.method)) {
      for (const listener of listeners.get(payload.method)) {
        listener(payload.params || {});
      }
    }
  });

  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });

  return {
    send,
    on(method, listener) {
      if (!listeners.has(method)) listeners.set(method, []);
      listeners.get(method).push(listener);
    },
    async navigate(url) {
      await send('Page.navigate', { url });
      await waitFor(async () => {
        const state = await this.eval('document.readyState');
        return state === 'complete' || state === 'interactive';
      }, 15000, `page ready ${url}`);
      await sleep(1000);
    },
    async eval(expression) {
      const result = await send('Runtime.evaluate', {
        expression,
        returnByValue: true,
        awaitPromise: true,
      });
      if (result.exceptionDetails) {
        throw new Error(result.exceptionDetails.text || 'Runtime.evaluate failed');
      }
      return result.result?.value;
    },
    async text() {
      return this.eval('document.body ? document.body.innerText : ""');
    },
  };
}

async function waitForChrome() {
  await waitFor(async () => {
    if (chrome?.exitCode !== null) {
      throw new Error(`Chrome exited with code ${chrome.exitCode}: ${chromeStderr.trim()}`);
    }
    try {
      const response = await fetch(`${CDP_HTTP_BASE}/json/version`);
      return response.ok;
    } catch {
      return false;
    }
  }, 15000, 'Chrome DevTools endpoint');
}

async function assertReachable(url, name) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${name} not reachable: HTTP ${response.status}`);
  }
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET ${url} failed: ${response.status}`);
  return response.json();
}

async function postJson(url, body) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`POST ${url} failed: ${response.status}`);
  return response.json();
}

async function waitFor(predicate, timeoutMs, label) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await predicate()) return;
    await sleep(250);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function findChromePath() {
  const candidates = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  ];
  return candidates.find((candidate) => existsSync(candidate));
}

function pass(name, detail) {
  results.push({ name, ok: true, detail });
  console.log(`[PASS] ${name}: ${detail}`);
}

function fail(name, error) {
  results.push({ name, ok: false, detail: error?.message || String(error) });
  console.log(`[FAIL] ${name}: ${error?.message || String(error)}`);
}

function printResults() {
  const passed = results.filter((result) => result.ok).length;
  const failed = results.length - passed;
  console.log(`\nSummary: ${passed} passed, ${failed} failed`);
  process.exitCode = failed ? 1 : 0;
}
