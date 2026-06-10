# 电子元器件装配质量评估系统部署文档（x86 服务器版）

> 如果后端也使用 Docker，请优先看新版文档：`部署文档-Docker后端-Nginx前端-x86服务器.md`。本文档保留了 `jar + systemd` 的传统后端部署方式，适合作为备用方案。

本文档面向纯新手，按“后端 Spring Boot + 前端 Vue + Nginx”的前后端分离方式部署到 x86 服务器。

这里的 x86 服务器通常指：

- `x86_64`
- `amd64`
- Intel Xeon / Core 系列 CPU
- AMD EPYC / Ryzen 系列 CPU

如果你的服务器是国产 ARM、鲲鹏、飞腾、树莓派、Apple Silicon，那就不是本文档默认的 x86 环境，Node.js、JDK、Nginx 的安装包可能不同。

项目实际情况：

- 后端：Spring Boot，默认端口 `8080`
- 前端：Vue 3 + Vite，打包后由 Nginx 提供静态页面
- 前端接口前缀：`/api`
- WebSocket 地址：`/ws/quality-stream`
- 数据库：PostgreSQL
- 图数据库：Neo4j
- 后端不会自动建业务表：`spring.jpa.hibernate.ddl-auto=none`

推荐部署结构：

```text
用户浏览器
  |
  |  http://服务器IP/
  v
Nginx，监听 80 端口
  |
  |-- 前端静态文件：/usr/share/nginx/html/quality-assessment
  |
  |-- /api/ 反向代理到 http://127.0.0.1:8080/api/
  |
  |-- /ws/ 反向代理到 http://127.0.0.1:8080/ws/
              |
              v
         Spring Boot 后端，监听 8080
              |
              |-- PostgreSQL
              |-- Neo4j
```

## 0. 确认服务器是不是 x86

登录服务器后执行：

```bash
uname -m
```

如果输出是下面之一，说明是 x86 64 位服务器：

```text
x86_64
amd64
```

再看系统信息：

```bash
cat /etc/os-release
```

如果是 Ubuntu，会看到类似：

```text
NAME="Ubuntu"
VERSION="22.04.x LTS"
```

本文档后面的命令默认按：

```text
Ubuntu 22.04/24.04 + x86_64/amd64
```

来写。

## 1. 准备信息

部署前先准备这些信息。不要跳过，后面配置要用。

| 名称 | 示例 | 说明 |
|---|---|---|
| 服务器 IP | `192.168.1.100` | 浏览器访问地址 |
| 后端端口 | `8080` | 项目默认端口 |
| 前端访问端口 | `80` | Nginx 默认 HTTP 端口 |
| PostgreSQL 地址 | `127.0.0.1:5432` | 可以是本机，也可以是远程数据库 |
| PostgreSQL 数据库名 | `CCTV` | 以实际库名为准 |
| PostgreSQL 用户名 | `postgres` | 以实际账号为准 |
| PostgreSQL 密码 | `<你的密码>` | 不要写进公开文档或代码仓库 |
| Neo4j 地址 | `bolt://127.0.0.1:7687` 或 `bolt+s://xxx:7687` | 以实际环境为准 |
| Neo4j 用户名 | `neo4j` | 以实际账号为准 |
| Neo4j 密码 | `<你的密码>` | 以实际账号为准 |

下文用这些占位符表示你需要替换的内容：

```text
<服务器IP>
<数据库IP>
<数据库名>
<数据库用户名>
<数据库密码>
<Neo4j地址>
<Neo4j用户名>
<Neo4j密码>
<JWT密钥>
```

## 2. 服务器目录规划

建议把文件放到这些目录，后续命令也按这个目录写。

```text
/opt/quality-assessment/backend      后端 jar 文件和配置
/opt/quality-assessment/logs         后端日志
/usr/share/nginx/html/quality-assessment   前端 dist 文件
/etc/nginx/conf.d/quality-assessment.conf  Nginx 配置
```

创建目录：

```bash
sudo mkdir -p /opt/quality-assessment/backend
sudo mkdir -p /opt/quality-assessment/logs
sudo mkdir -p /usr/share/nginx/html/quality-assessment
```

## 3. 安装基础软件

以下命令以 Ubuntu / Debian 服务器为例。如果你用 CentOS、Rocky Linux、Windows Server，命令会不同，但思路一样。

本节命令适用于 x86_64 / amd64 Ubuntu 服务器。

### 3.1 更新系统软件包

```bash
sudo apt update
sudo apt upgrade -y
```

### 3.2 安装 Java 17

后端项目 `pom.xml` 中配置的是 Java 17。

```bash
sudo apt install -y openjdk-17-jdk
```

检查是否安装成功：

```bash
java -version
```

看到类似内容即可：

```text
openjdk version "17.x.x"
```

确认 Java 是 x86_64 版本：

```bash
java -XshowSettings:properties -version 2>&1 | grep os.arch
```

正常会看到：

```text
os.arch = amd64
```

### 3.3 安装 Maven

项目自带 `mvnw`，理论上可以不用全局 Maven。但新手部署时装 Maven 更直观。

```bash
sudo apt install -y maven
mvn -version
```

### 3.4 安装 Node.js 和 npm

前端需要 Node.js。建议安装 Node.js 20 或 22。

Ubuntu 默认源里的 Node 可能较旧，建议使用 NodeSource：

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

检查版本：

```bash
node -v
npm -v
```

确认 Node 是 x64：

```bash
node -p "process.arch"
```

正常输出：

```text
x64
```

### 3.5 安装 Nginx

```bash
sudo apt install -y nginx
nginx -v
```

启动 Nginx：

```bash
sudo systemctl enable nginx
sudo systemctl start nginx
sudo systemctl status nginx
```

浏览器访问：

```text
http://<服务器IP>/
```

如果能看到 Nginx 欢迎页，说明 Nginx 正常。

## 4. 数据库准备

### 4.1 PostgreSQL 准备

后端配置中：

```properties
spring.jpa.hibernate.ddl-auto=none
```

这表示程序启动时不会自动创建业务表。你必须提前准备 PostgreSQL 数据库和表结构。

最少需要确认这些 schema 存在：

```sql
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS qc;
CREATE SCHEMA IF NOT EXISTS eval;
CREATE SCHEMA IF NOT EXISTS etl;
CREATE SCHEMA IF NOT EXISTS prod;
CREATE SCHEMA IF NOT EXISTS kg;
```

注意：

- 上面只创建 schema，不创建具体业务表。
- 项目正式运行还需要完整表结构。
- 如果老师或数据库文档已经给了建表 SQL，应先在 PostgreSQL 里执行完整建表 SQL。
- 如果使用已有远程数据库，需要确认服务器能连接到远程数据库的 `5432` 端口。

在服务器上测试 PostgreSQL 端口是否能连：

```bash
nc -vz <数据库IP> 5432
```

如果没有 `nc`：

```bash
sudo apt install -y netcat-openbsd
```

### 4.2 Neo4j 准备

项目会连接 Neo4j，用于知识图谱相关功能。

测试服务器是否能连 Neo4j：

```bash
nc -vz <Neo4j服务器IP> 7687
```

如果你使用的是云端 Neo4j，并且地址是 `bolt+s://...`，确保：

- 服务器能访问该域名。
- Neo4j 用户名和密码正确。
- 防火墙允许访问 `7687`。

## 5. 后端部署

### 5.1 在本机或服务器打包后端

进入项目根目录：

```bash
cd /path/to/demo3
```

如果在 Windows 本机，可以在 PowerShell 中进入项目目录：

```powershell

```cd E:\dainzi\demo3

#### Linux / macOS 打包

```bash
./mvnw clean package -DskipTests
```

或者：

```bash
mvn clean package -DskipTests
```

#### Windows PowerShell 打包

```powershell
.\mvnw.cmd clean package -DskipTests
```

打包成功后，jar 通常在：

```text
target/demo3-0.0.1-SNAPSHOT.jar
```

说明：

- Java 的 jar 包本身不绑定 CPU 架构。
- 你可以在 Windows x86 电脑上打包，再上传到 Linux x86 服务器运行。
- 只要服务器安装了 Java 17，通常就能运行。
- 如果代码里有本地 native 库，才需要特别关注架构；本项目主要是 Java 依赖，不需要额外处理。

如果文件名不同，查看 `target` 目录：

```bash
ls target
```

### 5.2 上传 jar 到服务器

如果你在自己电脑打包，需要把 jar 上传到服务器。

你的本机项目目录是：

```text
E:\dainzi\demo3
```

Windows PowerShell 打包后 jar 路径通常是：

```text
E:\dainzi\demo3\target\demo3-0.0.1-SNAPSHOT.jar
```

Linux / macOS 示例：

```bash
scp target/demo3-0.0.1-SNAPSHOT.jar root@<服务器IP>:/opt/quality-assessment/backend/app.jar
```

Windows 可以用 Xftp、FinalShell、MobaXterm、WinSCP 上传。

如果用 Windows PowerShell 的 `scp`，示例：

```powershell
scp E:\dainzi\demo3\target\demo3-0.0.1-SNAPSHOT.jar root@<服务器IP>:/opt/quality-assessment/backend/app.jar
```

如果服务器禁止 root 登录，改成你的服务器用户名：

```powershell
scp E:\dainzi\demo3\target\demo3-0.0.1-SNAPSHOT.jar <服务器用户名>@<服务器IP>:/tmp/app.jar
```

然后登录服务器：

```bash
sudo mv /tmp/app.jar /opt/quality-assessment/backend/app.jar
```

最终服务器上应有：

```text
/opt/quality-assessment/backend/app.jar
```

### 5.3 编写后端环境变量文件

不要直接改 jar，也不要把数据库密码写进代码。推荐写一个环境变量文件：

```bash
sudo nano /opt/quality-assessment/backend/app.env
```

写入下面内容，按实际情况替换：

```bash
SERVER_PORT=8080

DB_URL=jdbc:postgresql://<数据库IP>:5432/<数据库名>?reWriteBatchedInserts=true&tcpKeepAlive=true
DB_USERNAME=<数据库用户名>
DB_PASSWORD=<数据库密码>

NEO4J_URI=<Neo4j地址>
NEO4J_USERNAME=<Neo4j用户名>
NEO4J_PASSWORD=<Neo4j密码>
NEO4J_DATABASE=neo4j

JWT_SECRET=<JWT密钥>
JWT_EXPIRATION_MINUTES=480

CORS_ALLOWED_ORIGINS=http://<服务器IP>,http://<服务器IP>:80
QUALITY_STREAM_INTERVAL_MS=1000
```

JWT 密钥说明：

- `JWT_SECRET` 是用于生成登录令牌的密钥。
- 不要使用太短的字符串。
- 建议至少 32 个字符。
- 示例：`my-quality-assessment-jwt-secret-2026-change-me`

修改文件权限，避免普通用户直接看到密码：

```bash
sudo chmod 600 /opt/quality-assessment/backend/app.env
```

### 5.4 手动启动后端测试

先手动启动一次，确认没有数据库连接错误：

```bash
cd /opt/quality-assessment/backend
set -a
source ./app.env
set +a
java -jar app.jar
```

如果启动成功，会看到 Spring Boot 启动日志，并监听 `8080`。

新开一个终端测试接口：

```bash
curl http://127.0.0.1:8080/api/user/captcha
```

如果返回 JSON，说明后端基本可用。

停止手动启动：

```bash
Ctrl + C
```

### 5.5 配置 systemd 后台运行

systemd 的作用：让后端作为系统服务运行，开机自动启动，崩溃后方便重启。

创建服务文件：

```bash
sudo nano /etc/systemd/system/quality-assessment-backend.service
```

写入：

```ini
[Unit]
Description=Quality Assessment Backend
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/quality-assessment/backend
EnvironmentFile=/opt/quality-assessment/backend/app.env
ExecStart=/usr/bin/java -jar /opt/quality-assessment/backend/app.jar
Restart=always
RestartSec=5
StandardOutput=append:/opt/quality-assessment/logs/backend.log
StandardError=append:/opt/quality-assessment/logs/backend-error.log

[Install]
WantedBy=multi-user.target
```

重新加载 systemd：

```bash
sudo systemctl daemon-reload
```

设置开机启动：

```bash
sudo systemctl enable quality-assessment-backend
```

启动后端：

```bash
sudo systemctl start quality-assessment-backend
```

查看状态：

```bash
sudo systemctl status quality-assessment-backend
```

查看日志：

```bash
tail -f /opt/quality-assessment/logs/backend.log
```

如果启动失败，看错误日志：

```bash
tail -f /opt/quality-assessment/logs/backend-error.log
```

常用命令：

```bash
sudo systemctl restart quality-assessment-backend
sudo systemctl stop quality-assessment-backend
sudo systemctl status quality-assessment-backend
```

## 6. 前端部署

### 6.1 确认前端生产环境配置

项目已有：

```text
frontend/.env.production
```

内容是：

```env
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_USE_MOCK=false
```

生产部署时推荐保持：

```env
VITE_API_BASE_URL=/api
```

原因：

- 浏览器访问前端：`http://<服务器IP>/`
- 前端请求接口：`http://<服务器IP>/api/...`
- Nginx 再把 `/api/...` 转发给后端：`http://127.0.0.1:8080/api/...`
- 浏览器不直接暴露后端端口，部署更清晰。

### 6.2 安装前端依赖

进入前端目录：

```bash
cd /path/to/demo3/frontend
```

如果使用项目的 lock 文件，推荐：

```bash
npm ci
```

如果 `npm ci` 报错，再用：

```bash
npm install
```

说明：

- 前端打包后的 `dist` 是静态 HTML、CSS、JS 文件。
- 静态文件不绑定 CPU 架构。
- 你可以在 Windows x86 电脑上打包前端，再上传到 Linux x86 服务器。
- 也可以直接在 Linux x86 服务器上安装 Node.js 后打包。

### 6.3 打包前端

```bash
npm run build
```

成功后会生成：

```text
frontend/dist
```

### 6.4 上传前端 dist 到服务器

如果你在本机打包，上传 `frontend/dist` 目录里的所有内容到服务器：

```text
/usr/share/nginx/html/quality-assessment
```

Linux / macOS 示例：

```bash
scp -r frontend/dist/* root@<服务器IP>:/usr/share/nginx/html/quality-assessment/
```

Windows PowerShell 示例：

```powershell
scp -r E:\dainzi\demo3\frontend\dist\* root@<服务器IP>:/usr/share/nginx/html/quality-assessment/
```

如果服务器禁止 root 登录：

```powershell
scp -r E:\dainzi\demo3\frontend\dist\* <服务器用户名>@<服务器IP>:/tmp/quality-assessment-dist/
```

然后登录服务器：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r /tmp/quality-assessment-dist/* /usr/share/nginx/html/quality-assessment/
```

如果你直接在服务器打包，可以执行：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r dist/* /usr/share/nginx/html/quality-assessment/
```

设置权限：

```bash
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment
sudo chmod -R 755 /usr/share/nginx/html/quality-assessment
```

## 7. Nginx 配置

### 7.1 创建 Nginx 配置文件

```bash
sudo nano /etc/nginx/conf.d/quality-assessment.conf
```

写入以下完整配置：

```nginx
server {
    listen 80;
    server_name <服务器IP>;

    root /usr/share/nginx/html/quality-assessment;
    index index.html;

    client_max_body_size 50m;

    gzip on;
    gzip_comp_level 5;
    gzip_min_length 1024;
    gzip_types
        text/plain
        text/css
        application/json
        application/javascript
        text/xml
        application/xml
        application/xml+rss
        image/svg+xml;

    # 前端 Vue Router history 模式支持。
    # 用户刷新 /login、/assessment 等页面时，仍返回 index.html。
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 REST API 代理。
    # 前端请求 /api/user/login，Nginx 转发到 http://127.0.0.1:8080/api/user/login。
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 60s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }

    # WebSocket 代理。
    # 前端连接 /ws/quality-stream，Nginx 转发到后端 WebSocket。
    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 静态资源缓存。
    location /assets/ {
        try_files $uri =404;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

如果你有域名，例如 `quality.example.com`，把：

```nginx
server_name <服务器IP>;
```

改成：

```nginx
server_name quality.example.com;
```

### 7.2 检查 Nginx 配置

```bash
sudo nginx -t
```

如果看到：

```text
syntax is ok
test is successful
```

说明配置语法正确。

### 7.3 重启 Nginx

```bash
sudo systemctl reload nginx
```

如果 reload 失败，用：

```bash
sudo systemctl restart nginx
sudo systemctl status nginx
```

## 8. 防火墙放行端口

如果服务器开启了防火墙，需要放行 `80`。

Ubuntu UFW：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

如果你暂时需要从外部直接访问后端 `8080` 调试，也可以临时放行：

```bash
sudo ufw allow 8080/tcp
```

正式部署建议不要暴露 `8080`，让外部只访问 Nginx 的 `80/443`。

云服务器还要在云厂商控制台放行安全组：

- HTTP：`80`
- HTTPS：`443`
- 不建议公开：`5432`、`7687`、`8080`

## 9. 访问验证

### 9.1 检查后端服务

在服务器上执行：

```bash
curl http://127.0.0.1:8080/api/user/captcha
```

如果返回 JSON，说明后端接口正常。

### 9.2 检查 Nginx API 代理

在服务器上执行：

```bash
curl http://127.0.0.1/api/user/captcha
```

如果也返回 JSON，说明 Nginx `/api` 代理正常。

### 9.3 检查前端页面

浏览器访问：

```text
http://<服务器IP>/
```

检查：

- 页面是否能打开。
- 登录页是否正常显示。
- 验证码是否能加载。
- 登录请求是否能发送。
- 登录后页面刷新是否不会 404。

### 9.4 检查 WebSocket

打开浏览器开发者工具：

1. 按 `F12`
2. 进入 `Network`
3. 找到 `WS`
4. 查看是否有 `/ws/quality-stream`

正常情况：

- 状态码是 `101 Switching Protocols`
- 页面显示 WebSocket 已连接或持续重连后成功

如果 WebSocket 失败，优先检查 Nginx 的 `/ws/` 配置是否包含：

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

## 10. 使用 HTTPS

如果只是课程演示，HTTP 可以先用。如果部署到公网，建议配置 HTTPS。

假设你有域名 `quality.example.com`，并且域名已经解析到服务器。

安装 Certbot：

```bash
sudo apt install -y certbot python3-certbot-nginx
```

申请证书：

```bash
sudo certbot --nginx -d quality.example.com
```

按提示选择自动跳转 HTTPS。

完成后访问：

```text
https://quality.example.com/
```

注意：

- HTTPS 下 WebSocket 会自动使用 `wss://`。
- 本项目前端代码根据当前页面协议自动生成 WebSocket 地址，所以不需要额外改前端代码。

## 11. 更新部署流程

以后代码改了，按下面流程更新。

### 11.1 更新后端

本机或服务器重新打包：

```bash
mvn clean package -DskipTests
```

上传或复制 jar：

```bash
sudo cp target/demo3-0.0.1-SNAPSHOT.jar /opt/quality-assessment/backend/app.jar
```

重启后端：

```bash
sudo systemctl restart quality-assessment-backend
sudo systemctl status quality-assessment-backend
```

看日志：

```bash
tail -f /opt/quality-assessment/logs/backend.log
```

### 11.2 更新前端

重新打包：

```bash
cd frontend
npm run build
```

替换 Nginx 静态文件：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r dist/* /usr/share/nginx/html/quality-assessment/
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment
sudo systemctl reload nginx
```

浏览器强制刷新：

```text
Ctrl + F5
```

## 12. 常见问题排查

### 12.1 页面打开是 Nginx 欢迎页

原因：

- Nginx 没有加载你的配置。
- 默认站点配置优先显示。

检查：

```bash
sudo nginx -T | grep quality-assessment -n
```

确认配置文件存在：

```bash
ls /etc/nginx/conf.d/
```

也可以删除默认站点：

```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

### 12.2 页面刷新后 404

原因：

- Vue Router 页面刷新时，Nginx 没有回退到 `index.html`。

确认 Nginx 有：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### 12.3 前端能打开，但接口 404

检查浏览器开发者工具里请求地址。

正确请求应类似：

```text
http://<服务器IP>/api/user/captcha
```

如果请求变成：

```text
http://<服务器IP>/user/captcha
```

说明前端接口前缀错了，要检查：

```text
frontend/.env.production
```

应为：

```env
VITE_API_BASE_URL=/api
```

然后重新：

```bash
npm run build
```

### 12.4 前端接口 502 Bad Gateway

原因通常是 Nginx 找不到后端。

检查后端是否启动：

```bash
sudo systemctl status quality-assessment-backend
```

检查后端端口：

```bash
ss -lntp | grep 8080
```

测试后端：

```bash
curl http://127.0.0.1:8080/api/user/captcha
```

如果本地 `8080` 都不通，先修后端。

### 12.5 后端启动失败，日志提示数据库连接失败

检查：

```bash
cat /opt/quality-assessment/backend/app.env
```

重点看：

```bash
DB_URL
DB_USERNAME
DB_PASSWORD
```

测试数据库端口：

```bash
nc -vz <数据库IP> 5432
```

常见原因：

- 数据库 IP 写错。
- 数据库密码写错。
- 数据库没有放行服务器 IP。
- PostgreSQL 没有开启远程连接。
- 数据库名不存在。

### 12.6 后端启动失败，提示表不存在

原因：

- 项目不会自动创建业务表。
- 数据库还没有执行完整建表 SQL。

解决：

- 找到项目对应的 PostgreSQL 建表脚本。
- 在目标数据库执行建表脚本。
- 确认 schema 和表名与代码一致。

### 12.7 登录验证码接口能访问，但登录失败

检查：

- 数据库里是否有用户数据。
- 用户密码是否和系统加密方式匹配。
- 浏览器请求头是否带了正确数据。
- 后端日志是否有认证失败原因。

项目自带接口冒烟测试脚本：

```bash
API_BASE=http://127.0.0.1:8080 API_USER=admin API_PASSWORD=123456 ./test_all_apis.sh
```

Windows PowerShell：

```powershell
$env:API_BASE="http://127.0.0.1:8080"
$env:API_USER="admin"
$env:API_PASSWORD="123456"
.\test_all_apis.ps1
```

如果你的管理员账号不是 `admin / 123456`，替换成真实账号。

### 12.8 WebSocket 连接失败

检查 Nginx 配置：

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

检查后端是否允许 `/ws/**`：

项目代码中 `SecurityConfig` 已允许：

```java
.requestMatchers("/", "/error", "/images/**", "/ws/**").permitAll()
```

检查浏览器 Network 的 WS 请求：

- 如果是 `404`，多半是 Nginx 路径代理错。
- 如果是 `502`，多半是后端没启动或端口不通。
- 如果是 `403`，检查安全配置和跨域。

### 12.9 上传 Excel 或文件失败

项目后端配置：

```properties
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

Nginx 也需要允许 50MB：

```nginx
client_max_body_size 50m;
```

如果上传超过 50MB，需要两边都改。

### 12.10 改了前端但浏览器还是旧页面

处理方式：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r frontend/dist/* /usr/share/nginx/html/quality-assessment/
sudo systemctl reload nginx
```

浏览器按：

```text
Ctrl + F5
```

或者清除浏览器缓存。

## 13. 一键部署命令示例

下面是一个简化流程，适合代码已经在服务器上的情况。

在项目根目录执行：

```bash
# 1. 打包后端
mvn clean package -DskipTests

# 2. 替换后端 jar
sudo cp target/demo3-0.0.1-SNAPSHOT.jar /opt/quality-assessment/backend/app.jar
sudo systemctl restart quality-assessment-backend

# 3. 打包前端
cd frontend
npm ci
npm run build

# 4. 替换前端文件
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r dist/* /usr/share/nginx/html/quality-assessment/
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment

# 5. 重载 Nginx
sudo nginx -t
sudo systemctl reload nginx
```

## 14. 部署完成检查清单

部署完成后逐项确认：

- [ ] `java -version` 是 Java 17
- [ ] `node -v` 正常
- [ ] `nginx -v` 正常
- [ ] `/opt/quality-assessment/backend/app.jar` 存在
- [ ] `/opt/quality-assessment/backend/app.env` 已配置真实数据库信息
- [ ] `sudo systemctl status quality-assessment-backend` 显示 running
- [ ] `curl http://127.0.0.1:8080/api/user/captcha` 有返回
- [ ] `/usr/share/nginx/html/quality-assessment/index.html` 存在
- [ ] `sudo nginx -t` 成功
- [ ] `curl http://127.0.0.1/api/user/captcha` 有返回
- [ ] 浏览器能打开 `http://<服务器IP>/`
- [ ] 登录页验证码能显示
- [ ] 登录后页面能正常跳转
- [ ] 刷新页面不 404
- [ ] WebSocket `/ws/quality-stream` 能连接

## 15. 生产环境安全建议

课程演示可以先跑通功能。正式部署建议做这些事：

- 不要使用默认数据库密码。
- 不要把 `app.env` 上传到公开仓库。
- 不要对公网开放 PostgreSQL `5432`。
- 不要对公网开放 Neo4j `7687`。
- 不要对公网开放后端 `8080`，统一走 Nginx。
- 使用 HTTPS。
- 定期备份 PostgreSQL。
- 定期备份 Neo4j。
- 后端日志不要记录明文密码、Token、数据库密码。
- `JWT_SECRET` 使用足够长的随机字符串。

## 16. 最小可用部署顺序

如果你不知道从哪里开始，就按这个顺序做：

1. 准备 PostgreSQL 和 Neo4j，确认账号密码可用。
2. 服务器安装 Java 17、Node.js、Nginx。
3. 后端打包成 jar。
4. 配置 `/opt/quality-assessment/backend/app.env`。
5. 手动 `java -jar app.jar` 跑通后端。
6. 配置 systemd，让后端后台运行。
7. 前端 `npm run build`。
8. 把 `dist` 放到 Nginx 静态目录。
9. 配置 Nginx 的 `/api/` 和 `/ws/` 代理。
10. 浏览器访问系统并测试登录、接口、WebSocket。

## 17. x86 服务器专用检查清单

如果你明确是部署到 x86 服务器，额外检查这些：

- [ ] `uname -m` 输出 `x86_64`
- [ ] `java -XshowSettings:properties -version 2>&1 | grep os.arch` 输出 `amd64`
- [ ] `node -p "process.arch"` 输出 `x64`
- [ ] Nginx 是系统包安装，不是 ARM 版本手动包
- [ ] 后端 jar 文件已经上传到 `/opt/quality-assessment/backend/app.jar`
- [ ] 前端 dist 文件已经上传到 `/usr/share/nginx/html/quality-assessment`
- [ ] 防火墙或安全组开放 `80`，不直接开放 `5432`、`7687`、`8080`

对于本项目，x86 服务器不需要改代码。后端 jar 和前端 dist 都可以直接部署，关键是服务器上的 Java、Node、Nginx、数据库连接配置正确。
