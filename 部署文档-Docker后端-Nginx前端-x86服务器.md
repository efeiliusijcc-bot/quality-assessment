# 电子元器件装配质量评估系统部署文档（Docker 后端 + Nginx 前端）

本文档适用于 x86_64 / amd64 Linux 服务器。推荐环境：

```text
Ubuntu 22.04/24.04 x86_64
Docker + Docker Compose
Nginx
PostgreSQL
Neo4j
```

部署方式：

```text
浏览器
  |
  v
Nginx :80
  |-- 前端静态文件 dist
  |-- /api/ 代理到 127.0.0.1:8080
  |-- /ws/ 代理到 127.0.0.1:8080
        |
        v
Docker 容器：Spring Boot 后端
        |
        |-- PostgreSQL
        |-- Neo4j
```

这个方案比 `jar + systemd` 更适合你现在的情况：后端环境固定在 Docker 镜像里，服务器只需要安装 Docker，不需要在系统里直接管理 Java 进程。

## 1. 本项目已新增的 Docker 文件

项目根目录已新增：

```text
Dockerfile.backend
docker-compose.backend.yml
.dockerignore
deploy/backend.env.example
```

作用说明：

| 文件 | 作用 |
|---|---|
| `Dockerfile.backend` | 构建后端 Spring Boot Docker 镜像 |
| `docker-compose.backend.yml` | 启动后端容器 |
| `.dockerignore` | 构建镜像时排除无关目录，减少镜像上下文 |
| `deploy/backend.env.example` | 后端环境变量模板 |

## 2. 确认服务器架构

登录服务器：

```bash
ssh <服务器用户名>@<服务器IP>
```

检查 CPU 架构：

```bash
uname -m
```

如果输出是：

```text
x86_64
```

说明是 x86 服务器。

查看系统：

```bash
cat /etc/os-release
```

## 3. 安装 Docker

### 3.1 卸载旧版本

```bash
sudo apt remove -y docker docker-engine docker.io containerd runc || true
```

### 3.2 安装依赖

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg
```

### 3.3 添加 Docker 官方源

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
```

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

### 3.4 安装 Docker 和 Compose

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

检查：

```bash
docker version
docker compose version
```

设置开机启动：

```bash
sudo systemctl enable docker
sudo systemctl start docker
```

如果你不想每次都写 `sudo docker`，可以把当前用户加入 docker 组：

```bash
sudo usermod -aG docker $USER
```

然后退出 SSH，重新登录服务器。

## 4. 安装 Nginx

前端仍然用 Nginx 部署，这是生产环境更标准的方式。

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
sudo systemctl status nginx
```

## 5. 准备部署目录

服务器上创建目录：

```bash
sudo mkdir -p /opt/quality-assessment
sudo mkdir -p /opt/quality-assessment/deploy
sudo mkdir -p /opt/quality-assessment/logs
sudo mkdir -p /usr/share/nginx/html/quality-assessment
sudo chown -R $USER:$USER /opt/quality-assessment
```

推荐最终结构：

```text
/opt/quality-assessment
  ├─ Dockerfile.backend
  ├─ docker-compose.backend.yml
  ├─ pom.xml
  ├─ mvnw
  ├─ .mvn/
  ├─ src/
  ├─ deploy/
  │   └─ backend.env
  └─ logs/

/usr/share/nginx/html/quality-assessment
  ├─ index.html
  └─ assets/
```

## 6. 上传项目到服务器

你本机项目目录是：

```text
E:\dainzi\demo3
```

### 6.1 推荐方式：上传整个项目

用 Git 最方便：

```bash
cd /opt/quality-assessment
git clone <你的仓库地址> .
```

如果没有 Git 仓库，可以用 WinSCP、FinalShell、Xftp 上传这些内容：

```text
Dockerfile.backend
docker-compose.backend.yml
.dockerignore
pom.xml
mvnw
.mvn/
src/
frontend/
deploy/backend.env.example
```

### 6.2 Windows PowerShell scp 示例

如果服务器允许 SSH，可以在 Windows PowerShell 中执行：

```powershell
scp E:\dainzi\demo3\Dockerfile.backend <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp E:\dainzi\demo3\docker-compose.backend.yml <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp E:\dainzi\demo3\.dockerignore <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp E:\dainzi\demo3\pom.xml <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp E:\dainzi\demo3\mvnw <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp -r E:\dainzi\demo3\.mvn <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp -r E:\dainzi\demo3\src <服务器用户名>@<服务器IP>:/opt/quality-assessment/
scp -r E:\dainzi\demo3\deploy <服务器用户名>@<服务器IP>:/opt/quality-assessment/
```

如果嫌命令多，用 WinSCP 图形界面拖过去更简单。

## 7. 配置后端环境变量

进入服务器目录：

```bash
cd /opt/quality-assessment
```

复制模板：

```bash
cp deploy/backend.env.example deploy/backend.env
```

编辑：

```bash
nano deploy/backend.env
```

示例：

```bash
SERVER_PORT=8080

DB_URL=jdbc:postgresql://<数据库IP>:5432/<数据库名>?reWriteBatchedInserts=true&tcpKeepAlive=true
DB_USERNAME=<数据库用户名>
DB_PASSWORD=<数据库密码>

NEO4J_URI=<Neo4j地址>
NEO4J_USERNAME=<Neo4j用户名>
NEO4J_PASSWORD=<Neo4j密码>
NEO4J_DATABASE=neo4j

JWT_SECRET=<至少32位JWT密钥>
JWT_EXPIRATION_MINUTES=480

CORS_ALLOWED_ORIGINS=http://<服务器IP>,http://<服务器IP>:80
QUALITY_STREAM_INTERVAL_MS=1000

JAVA_OPTS=-Xms256m -Xmx1024m
```

注意：

- `DB_URL` 不能写错。
- 如果 PostgreSQL 也在同一台服务器但不是 Docker，数据库 IP 可以用 `172.17.0.1` 或服务器内网 IP，具体取决于数据库监听地址。
- 如果 PostgreSQL 是远程服务器，直接写远程 IP。
- 如果 PostgreSQL 以后也放进 Docker Compose，才可以用服务名连接。
- `JWT_SECRET` 至少 32 位，不能太短。

保护环境变量文件：

```bash
chmod 600 deploy/backend.env
```

## 8. 构建后端 Docker 镜像

在服务器执行：

```bash
cd /opt/quality-assessment
docker build -f Dockerfile.backend -t quality-assessment-backend:latest .
```

构建过程会：

1. 使用 Maven 镜像下载依赖。
2. 执行 `./mvnw clean package -DskipTests`。
3. 把生成的 jar 放到 Java 17 JRE 运行镜像中。

查看镜像：

```bash
docker images | grep quality-assessment-backend
```

如果构建失败，常见原因：

- 服务器无法访问 Maven 仓库。
- `mvnw` 没有执行权限。
- 项目源码没有上传完整。
- 服务器磁盘空间不足。

给 `mvnw` 加执行权限：

```bash
chmod +x mvnw
```

## 9. 启动后端容器

使用 Compose 启动：

```bash
cd /opt/quality-assessment
docker compose -f docker-compose.backend.yml up -d
```

查看容器：

```bash
docker ps
```

查看日志：

```bash
docker logs -f quality-assessment-backend
```

测试后端：

```bash
curl http://127.0.0.1:8080/api/user/captcha
```

如果返回 JSON，说明后端容器启动成功。

### 为什么 compose 里端口写 `127.0.0.1:8080:8080`

`docker-compose.backend.yml` 中是：

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

意思是：

- 容器内部后端端口：`8080`
- 宿主机本地端口：`127.0.0.1:8080`
- 只有服务器本机能访问后端端口
- 外部用户不能直接访问 `http://服务器IP:8080`
- 外部用户只能通过 Nginx 的 `80` 访问

这比直接暴露 `0.0.0.0:8080:8080` 更安全。

## 10. 前端打包

前端可以在本机打包，也可以在服务器打包。

### 10.1 服务器打包

安装 Node.js 22：

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
node -v
npm -v
```

进入前端目录：

```bash
cd /opt/quality-assessment/frontend
```

安装依赖：

```bash
npm ci
```

如果失败：

```bash
npm install
```

打包：

```bash
npm run build
```

复制 dist 到 Nginx 目录：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r dist/* /usr/share/nginx/html/quality-assessment/
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment
sudo chmod -R 755 /usr/share/nginx/html/quality-assessment
```

### 10.2 Windows 本机打包后上传

在 Windows PowerShell：

```powershell
cd E:\dainzi\demo3\frontend
npm ci
npm run build
```

上传：

```powershell
scp -r E:\dainzi\demo3\frontend\dist\* <服务器用户名>@<服务器IP>:/tmp/quality-assessment-dist/
```

服务器上执行：

```bash
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r /tmp/quality-assessment-dist/* /usr/share/nginx/html/quality-assessment/
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment
```

## 11. 配置 Nginx

创建配置：

```bash
sudo nano /etc/nginx/conf.d/quality-assessment.conf
```

写入：

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
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml image/svg+xml;

    location / {
        try_files $uri $uri/ /index.html;
    }

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

    location /assets/ {
        try_files $uri =404;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

检查配置：

```bash
sudo nginx -t
```

重载：

```bash
sudo systemctl reload nginx
```

## 12. 防火墙和安全组

只需要对外开放：

```text
80
443，如果配置 HTTPS
```

不建议对公网开放：

```text
8080
5432
7687
```

Ubuntu UFW：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

云服务器还要在控制台安全组里放行 `80`。

## 13. 验证部署

后端容器：

```bash
docker ps
docker logs --tail=100 quality-assessment-backend
```

后端接口：

```bash
curl http://127.0.0.1:8080/api/user/captcha
```

Nginx 代理：

```bash
curl http://127.0.0.1/api/user/captcha
```

浏览器访问：

```text
http://<服务器IP>/
```

浏览器检查：

- 登录页能打开。
- 验证码能显示。
- 登录接口不是 404/502。
- 刷新页面不 404。
- Network 里 `/ws/quality-stream` 能连接。

## 14. 更新后端

代码改了以后：

```bash
cd /opt/quality-assessment
docker build -f Dockerfile.backend -t quality-assessment-backend:latest .
docker compose -f docker-compose.backend.yml up -d
```

如果容器没有使用新镜像，可以强制重建：

```bash
docker compose -f docker-compose.backend.yml up -d --force-recreate
```

查看日志：

```bash
docker logs -f quality-assessment-backend
```

## 15. 更新前端

```bash
cd /opt/quality-assessment/frontend
npm run build
sudo rm -rf /usr/share/nginx/html/quality-assessment/*
sudo cp -r dist/* /usr/share/nginx/html/quality-assessment/
sudo chown -R www-data:www-data /usr/share/nginx/html/quality-assessment
sudo systemctl reload nginx
```

浏览器按：

```text
Ctrl + F5
```

## 16. 常见问题

### 16.1 Nginx 返回 502

说明 Nginx 连不上后端。

检查：

```bash
docker ps
docker logs --tail=100 quality-assessment-backend
curl http://127.0.0.1:8080/api/user/captcha
```

如果 `curl 127.0.0.1:8080` 不通，问题在后端容器。

### 16.2 后端容器一直重启

查看日志：

```bash
docker logs -f quality-assessment-backend
```

常见原因：

- `deploy/backend.env` 数据库地址错。
- PostgreSQL 密码错。
- Neo4j 密码错。
- 数据库表不存在。
- `JWT_SECRET` 太短。

### 16.3 容器里访问不到本机 PostgreSQL

如果 PostgreSQL 安装在同一台服务器的宿主机上，容器里不能用 `127.0.0.1` 访问宿主机数据库。

可选做法：

1. 用服务器内网 IP。
2. 用 Docker 默认网关，一般是 `172.17.0.1`。
3. 把 PostgreSQL 也放进 Docker Compose。

先查 Docker 网关：

```bash
ip addr show docker0
```

如果看到：

```text
inet 172.17.0.1/16
```

则 `DB_URL` 可以尝试：

```text
jdbc:postgresql://172.17.0.1:5432/<数据库名>?reWriteBatchedInserts=true&tcpKeepAlive=true
```

前提是 PostgreSQL 监听地址允许 Docker 网关访问。

### 16.4 前端页面打开，但接口 404

检查前端生产配置：

```text
frontend/.env.production
```

应该是：

```env
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8080
VITE_USE_MOCK=false
```

然后重新打包前端。

### 16.5 WebSocket 失败

确认 Nginx 有：

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

浏览器 Network 里看 `/ws/quality-stream`：

- `101`：正常
- `404`：路径代理错
- `502`：后端容器不可用

## 17. 最终检查清单

- [ ] `uname -m` 是 `x86_64`
- [ ] `docker version` 正常
- [ ] `docker compose version` 正常
- [ ] `docker images` 里有 `quality-assessment-backend`
- [ ] `docker ps` 里后端容器是 `Up`
- [ ] `curl http://127.0.0.1:8080/api/user/captcha` 正常
- [ ] `curl http://127.0.0.1/api/user/captcha` 正常
- [ ] `/usr/share/nginx/html/quality-assessment/index.html` 存在
- [ ] `sudo nginx -t` 成功
- [ ] 浏览器能打开 `http://<服务器IP>/`
- [ ] 登录页验证码正常
- [ ] WebSocket `/ws/quality-stream` 正常

