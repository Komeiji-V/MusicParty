# Music Party (new-MusicParty)

> 一个高颜值、多频道、多人实时在线听歌 Web 应用。
>
> 基于 [EveElseIf/MusicParty](https://github.com/EveElseIf/MusicParty) 深度改造：统一登录（SSO）、多频道房间、个人主页展示、称号系统、Cookie 池，并完成三轮安全审查加固。

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.16-green) ![Vue](https://img.shields.io/badge/Vue.js-3-4FC08D) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue) ![Docker](https://img.shields.io/badge/Docker-Ready-blue)

---

## ⚡ 核心特性

- **统一登录（SSO 新协议）**：接入自研认证中心（auth-center），账号全局通用；登录回跳经跨域 POST `/api/auth/sso` + 60 秒一次性搬运 cookie 完成，**token 全程不进 URL**
- **多频道系统**：类似 TeamSpeak 的频道概念，顶部下拉快速切换；每频道独立队列/播放器/聊天，互不干扰
- **频道权限四模式**：公开 / 密码 / 邀请制（成员白名单）/ 仅成员可见（HIDDEN）
- **多音源聚合**：网易云 / B站 / QQ音乐 / 酷狗，一处搜索全网点播（B 站走本地缓存流式）
- **实时同步**：STOMP WebSocket 同步播放器/队列/聊天/点赞/投票切歌/直播流
- **个人主页展示**：专辑大图 + 喜爱歌曲模块墙（1x1 / 1x2）+ 1x3 歌词卡片（点击展开完整歌词）；公开主页按**不可变 authUid** 路由——用户名可改，链接永不变
- **个人歌单**：分类管理、一键收藏、公开/私有、导出 TXT/JSON
- **称号系统**：管理后台授予彩色称号，频道内与公开主页展示
- **Cookie 池**：用户提交各平台 Cookie → 管理员审核 → 加密入池轮换使用，失败自动切换
- **PostgreSQL 持久化**：队列/聊天/历史/缓存/点赞全部落库，重启可恢复
- **可配置定时清理**：聊天/历史/队列/缓存/已驳回 Cookie 提交，防数据库膨胀

## 🔒 安全特性（经三轮安全审查加固，上线前实测通过）

- **WS 身份模型**：身份键服务端派生（`u:{userId}` / `g:{UUID}`），客户端不可伪造冒用，广播不含客户端可控凭证
- **JWT 密钥治理**：无默认密钥（缺失/占位符直接拒绝启动）、无逃生门；默认过期 1 天
- **Cookie 全链路加密**：Cookie 池 / 频道配置 / 用户提交审核表均 AES-256-GCM（`COOKIE_ENCRYPTION_KEY` 独立密钥，密文 `enc:v1:` 带版本号）；审核完成后凭证置空；日志全程掩码；第三方 API 调用走请求头不落 URL
- **角色信任链**：本地角色取自已验签 token 的 claim（不信任明文 userinfo）；`SUPER_ADMIN_AUTH_UIDS` 白名单 + 角色变更审计日志
- **SSO 来源校验**：`/api/auth/sso` 校验 Origin/Referer + `X-Requested-With: AuthCenter`，跨站伪造 403
- **默认拒绝**：`anyRequest().denyAll()` 显式白名单；`/media/**` 缓存音频 HMAC 签名 URL（30 分钟，无签名/过期/篡改一律 403）
- **CORS 白名单**：仅显式配置域名 + auth-center origin，凭据不回显任意源
- **IP 限流**：登录交换 30/分、第三方搜索代理 20/分、直播流每 IP 10 并发、频道密码 join 10 次/60s、Cookie 提交 5 次/时
- **XSS 收敛**：共享白名单消毒器（含控制字符 scheme 绕过修复），HomePage/InfoModal 统一接入；CSP / HSTS / X-Frame-Options 响应头
- **隐藏频道元数据**：HIDDEN 频道非成员返回 404，不暴露存在性
- **越权防护**：频道/歌单/Cookie 池所有管理操作 403；队列置顶仅本人或管理员
- 依赖保持 **Spring Boot 3.5.x** 最新维护分支（覆盖 Spring Framework / Tomcat / pgjdbc 系列已知 CVE）

## 🚀 快速开始（开发环境）

前置：JDK 21、Node 18+、Docker（音源容器）、PostgreSQL 15+、认证中心（auth-center）。

```bash
# 1. 启动音源容器（网易云/QQ API；PostgreSQL 需自行部署或复用已有实例）
docker compose up -d

# 2. 配置环境变量（JWT_SECRET 必须与认证中心完全一致）
cp .env.example .env        # 修改 JWT_SECRET / DB_* / NETEASE_API_URL

# 3. 启动后端（默认 :8080）
JAVA_HOME=<jdk21路径> ./mvnw -DskipTests spring-boot:run

# 4. 前端（开发模式 :5173；或 npm run build 后由后端托管静态资源）
cd music-party-web && npm install && npm run dev
```

> 认证中心（auth-center）为独立项目，需先部署并保证：① 与本站共享完全相同的 `JWT_SECRET`（`openssl rand -hex 32`）；② `CORS_ORIGINS` 包含本站域名；③ 登录页实现新协议（跨域 POST 本站 `/api/auth/sso`，带 `Origin` + `X-Requested-With: AuthCenter`）。对接细节见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 📦 生产部署

**上线前必读 [docs/DEPLOY.md](docs/DEPLOY.md)**，包含：生产 `.env` 模板（密钥/白名单/域名）、Nginx HTTPS 反代配置（X-Forwarded-* 覆写、WebSocket/直播流长连接、限流）、auth-center 生产配合项、上线回归清单、备份与密钥轮换、已知边界。

## 🏗 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5.16 / Java 21 / Spring Security / WebFlux WebClient / STOMP WebSocket |
| 前端 | Vue 3 / Pinia / Vue Router / Tailwind CSS |
| 存储 | PostgreSQL 15（JPA） |
| 音源 | NeteaseCloudMusicApi / Bilibili（本地缓存）/ QQ / Kugou |

## 📄 开源说明

本项目参考自 [EveElseIf/MusicParty](https://github.com/EveElseIf/MusicParty)，在功能与安全层面做了大量改造。VIP 歌曲/高音质等会员内容需要具有相应资格账号的 Cookie（提交后经管理员审核加密入库），项目本身不提供破解。
