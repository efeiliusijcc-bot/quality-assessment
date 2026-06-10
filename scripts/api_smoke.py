#!/usr/bin/env python3
"""Authenticated API smoke checks for the current demo app.

Environment variables:
  API_BASE      Backend base URL, default http://127.0.0.1:8080
  API_USER      Login username, default admin
  API_PASSWORD  Login password, default 123456
"""

from __future__ import annotations

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


BASE = os.getenv("API_BASE", "http://127.0.0.1:8080").rstrip("/")
USERNAME = os.getenv("API_USER", "admin")
PASSWORD = os.getenv("API_PASSWORD", "123456")
TIMEOUT_SECONDS = float(os.getenv("API_TIMEOUT", "8"))


@dataclass
class CheckResult:
    name: str
    ok: bool
    detail: str


class SmokeClient:
    def __init__(self) -> None:
        self.token = ""

    def request(
        self,
        method: str,
        path: str,
        body: Any | None = None,
        *,
        expect_api_response: bool = True,
    ) -> tuple[int, dict[str, str], bytes, Any | None]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
                raw = resp.read()
                status = resp.status
                response_headers = dict(resp.headers.items())
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            status = exc.code
            response_headers = dict(exc.headers.items())
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Cannot reach {BASE}: {exc.reason}") from exc

        parsed = None
        content_type = response_headers.get("Content-Type", "")
        if raw and "json" in content_type:
            parsed = json.loads(raw.decode("utf-8"))
            if expect_api_response and parsed.get("code") != 200:
                raise AssertionError(f"{method} {path} returned api code {parsed.get('code')}: {parsed.get('msg')}")
        return status, response_headers, raw, parsed

    def login(self, username: str = USERNAME, password: str = PASSWORD) -> None:
        _, _, _, captcha = self.request("GET", "/api/user/captcha")
        data = captcha["data"]
        image = data["captchaImage"]
        svg = base64.b64decode(image.split(",", 1)[1]).decode("utf-8")
        match = re.search(r">([^<]+)</text>", svg)
        if not match:
            raise AssertionError("Captcha SVG text was not found")

        payload = {
            "username": username,
            "password": password,
            "captchaId": data["captchaId"],
            "captchaCode": match.group(1),
        }
        _, _, _, login = self.request("POST", "/api/user/login", payload)
        self.token = login["data"]["token"]


def ok(name: str, detail: str = "200") -> CheckResult:
    return CheckResult(name, True, detail)


def fail(name: str, exc: BaseException) -> CheckResult:
    return CheckResult(name, False, str(exc))


def api_get(client: SmokeClient, name: str, path: str) -> CheckResult:
    try:
        status, _, _, parsed = client.request("GET", path)
        payload = parsed.get("data") if parsed else None
        if isinstance(payload, list):
            return ok(name, f"{status}, {len(payload)} rows")
        if isinstance(payload, dict) and "total" in payload:
            return ok(name, f"{status}, total={payload['total']}")
        return ok(name, str(status))
    except BaseException as exc:
        return fail(name, exc)


def api_post(client: SmokeClient, name: str, path: str, body: Any | None = None) -> CheckResult:
    try:
        status, _, _, _ = client.request("POST", path, body)
        return ok(name, str(status))
    except BaseException as exc:
        return fail(name, exc)


def download(client: SmokeClient, name: str, path: str, body: Any | None, magic: bytes) -> CheckResult:
    try:
        status, headers, raw, _ = client.request("POST", path, body, expect_api_response=False)
        if status != 200:
            raise AssertionError(f"HTTP {status}")
        if not raw.startswith(magic):
            raise AssertionError(f"Unexpected file signature: {raw[:8]!r}")
        return ok(name, f"{headers.get('Content-Type', '')}, {len(raw)} bytes")
    except BaseException as exc:
        return fail(name, exc)


def forbidden(client: SmokeClient, name: str, method: str, path: str) -> CheckResult:
    try:
        status, _, _, parsed = client.request(method, path, expect_api_response=False)
        api_code = parsed.get("code") if isinstance(parsed, dict) else None
        if status == 403 or api_code == 403:
            return ok(name, f"HTTP {status}, code={api_code}")
        raise AssertionError(f"Expected forbidden, got HTTP {status}, code={api_code}")
    except BaseException as exc:
        return fail(name, exc)


def first_batch(client: SmokeClient) -> str | None:
    _, _, _, parsed = client.request("GET", "/api/assessment/batches")
    data = parsed.get("data") or []
    return str(data[0]) if data else None


def main() -> int:
    client = SmokeClient()
    results: list[CheckResult] = []

    try:
        client.login()
        append_result(results, ok("auth.login", f"{USERNAME}@{BASE}"))
    except BaseException as exc:
        append_result(results, fail("auth.login", exc))
        return print_results(results)

    checks = [
        ("upload.statistics", "/api/upload/statistics"),
        ("etl.importJobs", "/api/etl/import-jobs"),
        ("etl.cleaningRules", "/api/etl/cleaning-rules"),
        ("etl.cleaningLogs", "/api/etl/cleaning-logs"),
        ("defect.samples", "/api/defect/samples"),
        ("defect.statistics", "/api/defect/statistics"),
        ("assessment.batches", "/api/assessment/batches"),
        ("assessment.stations", "/api/assessment/stations"),
        ("assessment.qualified", "/api/assessment/qualified"),
        ("assessment.judgment", "/api/assessment/judgment"),
        ("assessment.prediction", "/api/assessment/prediction"),
        ("assessment.history", "/api/assessment/history?page=1&size=5"),
        ("assessment.judgmentStream", "/api/assessment/judgment/stream"),
        ("assessment.predictionSimulation", "/api/assessment/prediction/simulation"),
        ("graph.syncTasks", "/api/graph/sync/tasks"),
        ("graph.versions", "/api/graph/versions"),
        ("graph.entities", "/api/graph/entities"),
        ("graph.relations", "/api/graph/relations"),
        ("graph.gatTasks", "/api/graph/gat-tasks"),
        ("graph.stats", "/api/graph/stats"),
        ("export.records", "/api/export/records?page=1&pageSize=5"),
    ]
    for name, path in checks:
        append_result(results, api_get(client, name, path))

    batch_no = None
    try:
        batch_no = first_batch(client)
    except BaseException as exc:
        append_result(results, fail("assessment.firstBatch", exc))
    if batch_no:
        encoded = urllib.parse.quote(batch_no, safe="")
        for result in [
            api_get(client, "graph.visualization", f"/api/graph/visualization/{encoded}?full=true"),
            api_get(client, "graph.analysis", f"/api/graph/analysis/{encoded}"),
            api_get(client, "graph.reasoning", f"/api/graph/reasoning/{encoded}"),
        ]:
            append_result(results, result)

    export_body = {"batchId": None, "station": None, "status": None, "dateRange": None, "page": 1, "pageSize": 5}
    for result in [
        api_post(client, "export.excelRecord", "/api/export/excel", export_body),
        api_post(client, "export.pdfRecord", "/api/export/pdf", export_body),
        download(client, "export.excelDownload", "/api/export/excel/download", export_body, b"PK"),
        download(client, "export.pdfDownload", "/api/export/pdf/download", export_body, b"%PDF"),
    ]:
        append_result(results, result)

    operator = SmokeClient()
    try:
        operator.login("operator", "123456")
        append_result(results, ok("auth.login.operator", f"operator@{BASE}"))
        append_result(results, forbidden(operator, "authz.operatorCleaningRulesDenied", "GET", "/api/etl/cleaning-rules"))
    except BaseException as exc:
        append_result(results, fail("auth.login.operator", exc))

    return print_results(results)


def append_result(results: list[CheckResult], result: CheckResult) -> None:
    results.append(result)
    mark = "PASS" if result.ok else "FAIL"
    print(f"[{mark}] {result.name}: {result.detail}", flush=True)


def print_results(results: list[CheckResult]) -> int:
    passed = sum(1 for item in results if item.ok)
    failed = len(results) - passed
    print(f"\nSummary: {passed} passed, {failed} failed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
