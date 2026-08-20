# MusicParty 部署指南

MusicParty 是一个基于 **统一认证中心（C:\Project\Auth）** 的多频道在线听歌平台。
登录使用自研统一登录平台的账号体系，频道内用户名与认证中心一致。

## 架构

```
[浏览器] → MusicParty (Spring Boot :8848)
              ├── PostgreSQL（独立部署，业务数据）
              ├── 认证中心 (:8000) — 登录/账号/角色（admin → 总管理员）
              ├── netease-api 容器 (:3000) — 网易云接口
              └── qq-api 容器 (:3200，可选) — QQ音乐接口
```

## 一、前置依赖

1. **PostgreSQL**（独立部署，建议 15+）
2. **统一认证中心** 已部署运行（见 C:\Project\Auth\README.md）
   - 其 `.env` 中 `CORS_ORIGINS` 必须包含本站域名（回跳白名单），例如：
     `CORS_ORIGINS=http://localhost:8001,http://localhost:8848`
3. **Docker**（用于 netease-api / qq-api 音源容器）

## 二、配置

复制 `.env.example` 为 `.env` 并修改：

| 变量 | 说明 |
|------|------|
| `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` | PostgreSQL 连接（库名建议 musicparty） |
| `AUTH_CENTER_URL` | 认证中心地址（http://localhost:8000 或 https://auth.你的域名） |
| `JWT_SECRET` | **必须与认证中心 .env 的 JWT_SECRET 完全一致**（共享密钥，用于本地验签） |
| `NETEASE_API_URL` | 网易云 API 容器地址（容器网络内 `http://netease-api:3000`，本地调试 `http://127.0.0.1:3000`） |
| `QQ_API_URL` | QQ API 容器地址（可选） |
| `NETEASE_COOKIE` 等 | 平台 Cookie（无 Cookie 时搜索/试听可用，VIP/高音质受限） |

## 三、数据库初始化

全新部署：无需手动建表，首次启动自动创建（`ddl-auto: update`）。

已有旧库升级：执行 `db/migration.sql`（含 password_hash 可空等关键迁移）。

## 四、启动

```bash
# 1. 启动音源容器（netease 必需；qq 可选）
docker compose up -d netease-api
# docker compose --profile qq up -d qq-api   # 需要 QQ 音源时

# 2. 构建并启动主应用
docker compose up -d --build music-party

# 或本地开发：
mvnw clean package -DskipTests
java -jar target/MusicParty-0.0.1-SNAPSHOT.jar
```

## 五、使用流程

1. 访问 `http://localhost:8848` → 自动跳转认证中心登录页
2. 使用统一账号登录（认证中心角色 `admin` 自动成为本站总管理员）
3. 登录后进入首页：查看频道卡片 → 点击加入频道 → 进入播放页
4. 频道管理员可在 `/channel/{id}/settings` 配置：加入权限（公开/密码/邀请制/仅成员可见）、
   成员、管理员、各平台 Cookie、音源开关

## 六、公开 API（iframe 嵌入论坛）

| 端点 | 说明 |
|------|------|
| `GET /api/public/config` | 站点标题/作者/信息页 |
| `GET /api/public/stats` | 频道数/在线人数/累计播放 |
| `GET /api/public/channels` | 频道列表（含在线人数/正在播放） |
| `GET /api/public/channels/{id}` | 单频道详情 + 最近播放 |
| `GET /api/public/users/{username}/likes` | 该用户收到的赞总数 |
| `GET /api/public/users/{username}/playlists` | 公开歌单列表 |
| `GET /embed/channel.html?id={id}` | **iframe 嵌入卡片页**（纯 HTML+JS，10s 轮询） |

示例（论坛嵌入）：
```html
<iframe src="http://你的域名/embed/channel.html?id=1" width="360" height="130" frameborder="0"></iframe>
```

## 七、管理后台（总管理员）

- 用户管理（角色调整/删除）
- 频道管理（创建/删除）
- 站点品牌（标题/作者/背景词/信息页 HTML）
- 数据管理（手动清理 + **定时清理**：间隔/保留天数/目标可配置）
- 平台 Cookie 更新（在线生效，无需重启）

## 八、常见问题

| 问题 | 解决 |
|------|------|
| 登录后 401 / 无法访问 API | 检查 `JWT_SECRET` 是否与认证中心一致 |
| 跳转认证中心后回跳失败 | 认证中心 `CORS_ORIGINS` 未包含本站域名 |
| 网易云搜索正常但播放失败 | 配置 `NETEASE_COOKIE`（频道设置页或环境变量） |
| 频道列表为空 | 需要先创建频道（总管理员在 /admin 或普通用户在首页无创建入口，由管理员创建） |
| 数据库启动报错 | 执行 `db/migration.sql` 完成旧库迁移 |
