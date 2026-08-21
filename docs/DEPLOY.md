# MusicParty 生产部署指南

MusicParty 是基于**统一认证中心（auth-center）**的多频道在线听歌平台。本指南覆盖开发环境与生产环境部署、安全配置、运维与上线回归清单。

## 架构

```
[浏览器]
   │ HTTPS
   ▼
[Nginx / Caddy 反代]  ←—— 唯一公网入口（X-Forwarded-For 覆写 / WS 升级 / 限流）
   │
   ▼
[MusicParty Spring Boot :8080]
   ├── PostgreSQL 15（业务数据，独立部署/内网）
   ├── auth-center :8000（登录/账号/角色，生产必须 HTTPS）
   ├── ncm-api 容器 :3000（网易云接口，仅内网）
   └── qq-api 容器 :3200（可选，仅内网）
```

- 后端端口（8080）**不得直连公网**：应用启用 `server.forward-headers-strategy: framework` 后信任反代转发的 `X-Forwarded-*` 头，若后端暴露公网，客户端可伪造 XFF 绕过限流。
- 第三方音乐 API 容器（3000/3200）**生产不要映射宿主端口**（删掉 compose 的 `ports`，仅保留内部网络），避免免费代理面。

## 一、前置依赖

1. **JDK 21**、**Node 18+**、**Docker**（音源容器）
2. **PostgreSQL 15+**（独立部署；生产使用最小权限专用账号，见 3.1）
3. **认证中心（auth-center）** 已部署，且：
   - 与本站共享完全一致的 `JWT_SECRET`（`openssl rand -hex 32`）
   - `.env` 中 `CORS_ORIGINS` 包含本站生产域名（登录回跳白名单）
   - 登录页实现**新协议**：登录成功后跨域 `POST <本站>/api/auth/sso`（请求头 `Origin: <auth-center origin>` + `X-Requested-With: AuthCenter`，body `{token}`），收到 200 后跳回 `redirect`；**绝不把 token 放进 URL**。auth-center 参考实现见其仓库 `demo-app/`
   - 生产自身走 HTTPS（Caddy/Nginx），`SMTP_SECURE=true`（邮件 TLS）

## 二、开发环境部署

```bash
# 1. 依赖容器（PostgreSQL + 网易云 API；如已自行部署 DB 可只起音源）
docker compose up -d

# 2. 配置
cp .env.example .env
#    修改：JWT_SECRET（与认证中心一致）、DB_*、NETEASE_API_URL=http://127.0.0.1:3000
#    CORS_ALLOWED_ORIGINS=http://localhost:8848,http://localhost:8080
#    AUTH_CENTER_URL=http://localhost:8000

# 3. 后端
JAVA_HOME=<jdk21路径> ./mvnw -DskipTests spring-boot:run   # 默认 :8080

# 4. 前端（二选一）
cd music-party-web
npm install
npm run dev        # 开发模式 :5173
# 或生产构建（由后端托管）：
npm run build && cp -r dist/* ../src/main/resources/static/
```

## 三、生产部署

### 3.1 生产 `.env` 模板

```bash
# --- 基础 ---
SERVER_PORT=8080
BASE_URL=https://musicparty.example.com

# --- 数据库（独立 PostgreSQL，最小权限专用账号）---
DB_HOST=db.internal
DB_PORT=5432
DB_NAME=musicparty
DB_USER=musicparty_app
DB_PASSWORD=<32字节随机>

# --- 认证中心（生产必须 https）---
AUTH_CENTER_URL=https://auth.example.com
JWT_SECRET=<openssl rand -hex 32>          # 与 auth-center .env 完全一致
COOKIE_ENCRYPTION_KEY=<openssl rand -hex 32>  # 独立于 JWT_SECRET，勿复用
SUPER_ADMIN_AUTH_UIDS=<管理员authUid逗号分隔>  # 如 1,2（auth-center users 表 id）

# --- CORS（生产域名；auth-center origin 会自动并入）---
CORS_ALLOWED_ORIGINS=https://musicparty.example.com

# --- 音乐源（仅内网可达）---
NETEASE_API_URL=http://netease-api:3000
QQ_API_URL=http://qq-api:3200
NETEASE_ENABLED=true
QQ_ENABLED=true
KUGOU_ENABLED=true
BILIBILI_ENABLED=true
NETEASE_COOKIE=
NETEASE_QUALITY=exhigh
QQ_COOKIE=
QQ_QUALITY=320
KUGOU_COOKIE=
BILIBILI_SESSDATA=

# --- 队列/聊天 ---
QUEUE_MAX_SIZE=1000
QUEUE_HISTORY_SIZE=50
QUEUE_MAX_USER_SONGS=100
PLAYLIST_IMPORT_LIMIT=100
CHAT_HISTORY_LIMIT=1000
CHAT_MIN_INTERVAL=1000
CHAT_MAX_LENGTH=200
CACHE_MAX_SIZE=1GB
```

启动校验（必须满足，否则拒绝启动）：
- `JWT_SECRET` 缺失/占位符 → 拒绝启动
- `COOKIE_ENCRYPTION_KEY` 缺失 → 启动告警（回退 JWT_SECRET 派生，仅限本地；生产必须配齐）

### 3.2 Docker Compose 部署（推荐）

仓库根 `docker-compose.yml` 编排三个服务：`netease-api`（网易云音源）、`qq-api`（QQ 音源，可选 profile）、`music-party`（主应用）。安全变量（`CORS_ALLOWED_ORIGINS` / `COOKIE_ENCRYPTION_KEY` / `SUPER_ADMIN_AUTH_UIDS`）已透传，随 `.env` 生效。

```bash
# 1. 构建镜像（Dockerfile 三阶段：前端构建 → jar 打包 → 运行时含 ffmpeg）
#    compose 引用 thornex/music-party:latest；自建镜像仓库请同步改 docker-compose.yml 的 image
docker build -t thornex/music-party:latest .

# 2. 配置环境变量（compose 自动读取；安全变量模板见 3.1）
cp .env.example .env && vim .env

# 3. 启动
docker compose up -d               # 主应用 + 网易云音源
docker compose --profile qq up -d  # 追加 QQ 音源（qq-api:3200，需配 QQ_COOKIE）

# 4. 验证
curl http://<主机>:8848/           # 前端可访问（容器 8080 → 主机 8848）
docker compose logs music-party    # 无启动告警（COOKIE_ENCRYPTION_KEY 等）

# 5. 更新部署
git pull && docker compose build && docker compose up -d
```

> 说明：`postgresql` 与 `auth-center` 不在 compose 内（独立部署，见 3.1/3.3）；`db/`、`cached_media/` 数据卷挂载于 `./music_party/` 下。

### 3.3 Nginx 反代（HTTPS + WS + 长连接 + 限流）

```nginx
# 80 → 443（HSTS 由应用响应头下发，仅 https 生效）
server {
    listen 80;
    server_name musicparty.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name musicparty.example.com;
    ssl_certificate     /etc/letsencrypt/live/musicparty.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/musicparty.example.com/privkey.pem;

    # 关键：X-Forwarded-For 必须覆写（不能 $proxy_add_x_forwarded_for 追加客户端伪造值）
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header Host $host;

    # 纵深限流（应用层限流依赖 XFF 覆写生效）
    limit_req_zone $binary_remote_addr zone=auth:10m rate=30r/m;
    location /api/auth/ { limit_req zone=auth burst=10 nodelay; proxy_pass http://127.0.0.1:8080; }
    location /api/public/album-songs/ { limit_req zone=auth burst=5 nodelay; proxy_pass http://127.0.0.1:8080; }

    # WebSocket（STOMP）
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # 直播流长连接
    location /radio/stream {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_read_timeout 3600s;
        proxy_buffering off;
    }

    location / { proxy_pass http://127.0.0.1:8080; proxy_read_timeout 120s; }

    # access log 脱敏：query string 不落日志
    access_log /var/log/nginx/musicparty.access.log main_nq;
    log_format main_nq '$remote_addr - $remote_user [$time_local] "$request_method $uri $server_protocol" $status $body_bytes_sent "$http_referer"';
}
```

### 3.4 auth-center 生产配合（复核清单）

- [ ] `CORS_ORIGINS=https://musicparty.example.com`（回跳白名单；其 CSP `connect-src` 随配置自动同步）
- [ ] auth-center 自身 HTTPS（Caddy），`AUTH_CENTER_URL` 全链路 https
- [ ] `SMTP_HOST/SMTP_SECURE=true`（邮件 TLS）
- [ ] 登录页新协议已生效（无 `?token=` 回跳；`/api/auth/sso` 带 `X-Requested-With: AuthCenter`）

## 四、安全配置说明（对应审查修复项）

| 项 | 说明 |
|---|---|
| SSO 来源校验 | `/api/auth/sso` 校验 Origin/Referer == `AUTH_CENTER_URL` origin + `X-Requested-With: AuthCenter`；合法请求种 60s 一次性 `music_sso_token` cookie（SameSite=Lax，HTTPS 下自动 Secure） |
| 角色来源 | 本地角色取自已验签 token 的 `role` claim（auth-center 已签入）；`SUPER_ADMIN_AUTH_UIDS` 非空时仅白名单内 uid 可成为超管 |
| Cookie 加密 | 全链路 AES-256-GCM（`COOKIE_ENCRYPTION_KEY`），密文 `enc:v1:`；审核完成后凭证置 NULL；日志掩码 |
| 媒体缓存 | `/media/**` 仅接受 HMAC 签名 URL（30 分钟，密钥派生自 JWT_SECRET） |
| 限流 | 登录 30/分/IP、搜索代理 20/分/IP、直播并发 10/IP、密码 join 10 次/60s、Cookie 提交 5 次/时（反代必须覆写 XFF，否则退化为全站单桶） |
| 公开主页 | `/u/<authUid>` 纯数字路由，用户名不可用（可改名、ID 永不变） |
| 安全头 | CSP（connect-src 含 wss: 与 auth-center origin）、HSTS、X-Frame-Options DENY、nosniff |

## 五、运维

### 5.1 备份

- PostgreSQL 常规备份（业务数据）
- **密钥单独保管**：`JWT_SECRET` / `COOKIE_ENCRYPTION_KEY` / 认证中心密钥，**不得与数据库备份同处存放**——Cookie 密文 + 无密钥备份 = 数据不可恢复（这是加密的预期效果）
- 恢复流程：恢复 DB → 注入密钥 → 启动（`JWT_SECRET` 变更会导致存量 token 全部失效，需重新登录）

### 5.2 密钥轮换

- `JWT_SECRET`：替换后所有已登录用户需重新登录（旧 token 验签失败）
- `COOKIE_ENCRYPTION_KEY`：替换后**存量加密 Cookie 全部无法解密**（条目被跳过入池，需重新提交/配置）。`enc:v1:` 前缀预留了版本位，轮换时建议：旧密钥只解、新密钥只加密（双密钥迁移），或安排维护窗口直接重配 Cookie 池

### 5.3 定时清理

管理后台 → 站点配置 → 定时清理：目标可选 `chat` / `history` / `queue` / `cache` / **`rejected_cookies`**（超期已驳回的 Cookie 提交，建议显式开启）。

### 5.4 日志

- 反代 access log 已按 3.2 模板脱敏（不记 query string）
- 应用日志不打印 token/cookie 明文（审核通过后凭证即置空）

## 六、已知边界（上线后按需处理）

- **likes 计数按用户名**：用户改名后历史点赞数归零（历史数据不追溯）；后续可迁移为按 authUid 计数
- **会话即时吊销未做**（E1）：auth-center JWT 已携带 `ver` claim，可扩展"改密后旧 token 立即失效"；当前旧 token 最长 1 天有效
- **token 存 localStorage**：依赖 CSP `script-src 'self'` + 消毒器收敛 XSS 面；长期可迁移 httpOnly cookie + refresh 模型
- **跨注册域边界**：SSO 搬运 cookie 依赖 SameSite=Lax；auth-center 与本站同注册域（如 *.example.com）无问题；若未来跨注册域，Safari ITP 可能拦截第三方 cookie，需评估一次性票据模式
- **媒体签名密钥复用 JWT_SECRET**：与密钥治理同生命周期，密钥分离时一并处理

## 七、上线回归清单

| # | 项 | 期望 |
|---|---|---|
| 1 | 登录链路 | auth-center 登录 → POST `/api/auth/sso`（Origin + X-Requested-With）→ 200 + `Set-Cookie: music_sso_token; Secure; SameSite=Lax` → 前端收 token 入 localStorage → cookie 清除 → 刷新不重复收；evil Origin / 缺自定义头 → 403 |
| 2 | Cookie 提交/审核 | submit 落库 `enc:v1:` 密文；approve/reject 后凭证置 NULL；管理端列表掩码；第 6 次提交 → 429 |
| 3 | 公开页 | `/u/<authUid>` 200、非数字 404；featured/likes/playlists/titles 正常 |
| 4 | 频道/WS | `/ws` 经反代 Upgrade 正常；越权订阅被拒；在线列表身份键 `u:{id}/g:{uuid}`；聊天限流/长度生效 |
| 5 | 限流 | sso 30/分（第 31 次 429）、album-songs 20/分（第 21 次 429）、直播 10 并发、join 密码 10 次/60s、submit 5 次/时（**反代后按真实 IP 计数**） |
| 6 | 媒体 | 无签名 403、签名 URL 30 分钟内 200、过期/篡改 403 |
| 7 | CORS | auth-center origin preflight 200；evil origin 无 ACAO；同源页面无报错 |
| 8 | 安全头 | CSP（connect-src 含 wss: 与 auth-center origin）、HSTS（https）、X-Frame-Options DENY |
| 9 | 启动校验 | JWT_SECRET 缺失 → 拒启动；COOKIE_ENCRYPTION_KEY 缺失 → 告警；AUTH_CENTER_URL=http → 确认 |
| 10 | 清理 | 管理后台开启 `rejected_cookies` → 超期 REJECTED 行被删除 |
