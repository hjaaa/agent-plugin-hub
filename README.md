# Agent Plugin Hub

[![CI](https://github.com/hjaaa/agent-plugin-hub/actions/workflows/ci.yml/badge.svg)](https://github.com/hjaaa/agent-plugin-hub/actions/workflows/ci.yml)
[![CodeQL](https://github.com/hjaaa/agent-plugin-hub/actions/workflows/codeql.yml/badge.svg)](https://github.com/hjaaa/agent-plugin-hub/actions/workflows/codeql.yml)

面向 AI 编码 agent 的**内部插件市场平台**:托管、版本化并受控分发 Claude Code 插件。作者上传插件包,管理员审核发布,使用者在 Claude Code 里一条命令安装。

## 核心思路

Claude Code 原生只支持通过 `source: npm` 安装 marketplace 插件。本平台因此**自实现一个最小只读 npm registry**(packument + tarball 两个端点),并动态生成 `marketplace.json` 指向它——无需依赖公网 npm,插件产物完全私有可控:

```
Claude Code ── GET /marketplace.json ──► 插件清单(source: npm,指向本平台 registry)
Claude Code ── GET /registry/{pkg}   ──► packument 元数据 / tarball 字节(npm install 协议)
```

这两个是平台**仅有的**对 Claude Code 暴露的端点,其余均为需要登录的内部管理 API。

## 发布 → 安装流程

```
作者 npm pack ──► POST /api/plugins 上传 tarball
      │  结构校验(plugin.json / 保留名 / 包名与 semver 合法性 / 依赖自包含检查)
      ▼
submission(SUBMITTED) ──► 管理员 review / approve / reject
      │  审批通过:版本登记为 PUBLISHED + 移动 dist-tag + 产物落对象存储
      ▼
marketplace.json 自动更新 ──► Claude Code 端 marketplace add + install 即可用
```

发布治理规则:

- **不可变发布**:同一 `(package, version)` 一旦 PUBLISHED 不可覆盖,改动必须发新版本号。
- **双渠道指针**:`latest` / `stable` 是两个可移动的 dist-tag;`marketplace.json` 的版本跟随 `stable`。回滚 = 移动指针,产物不动。首发审批时两个指针同时指向首版本。
- **审核状态机**:`SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED`,非法跃迁拒绝,并发审批由乐观锁兜底。

## 快速开始

前置:JDK 17+、Docker(本地依赖与集成测试都需要)。

```bash
# 1. 启动 MySQL(:3306)与 MinIO(:9000,控制台 :9001)
docker compose up -d

# 2. 以 dev profile 启动(自动种子化演示插件,无需真实 OIDC IdP)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

然后在 Claude Code 中验证:

```
/plugin marketplace add http://localhost:8080/marketplace.json
/plugin install hello-plugin@agent-plugin-hub
```

示例插件源码见 `examples/hello-plugin`,逐里程碑的真机验收步骤见 `docs/m0|m1|m2-smoke-runbook.md`。

## API 一览

**机器平面(对 Claude Code / npm)**,可选 bearer token 鉴权(`REGISTRY_AUTH_ENABLED`,默认关闭):

| 端点 | 说明 |
|------|------|
| `GET /marketplace.json` | 插件市场清单,版本跟随 `stable` 渠道 |
| `GET /registry/{*path}` | 只读 npm registry:packument 与 `…/-/<file>.tgz` tarball |

**Web 平面(内部管理 API)**,OIDC 登录 + 角色控制(`AUTHOR` / `ADMIN`):

| 端点 | 角色 | 说明 |
|------|------|------|
| `POST /api/plugins`(multipart `file`) | AUTHOR | 上传 `npm pack` 产物,创建待审提交 |
| `GET /api/submissions` | ADMIN | 待审列表 |
| `POST /api/submissions/{id}/review\|approve\|reject` | ADMIN | 审核状态流转 |
| `GET /api/plugin-versions/{*pkg}` | 登录用户 | 版本治理视图(发布时间降序) |
| `PUT /api/dist-tags/{tag}/plugins/{*pkg}` | ADMIN | 提升 / 回滚 `latest` / `stable` 指针 |
| `GET\|POST\|DELETE /api/registry-tokens` | ADMIN | registry 只读 token 签发与吊销 |
| `GET /healthz` | 公开 | 健康检查 |

注:scoped 包名中的 `/` 在 URL 里编码为 `%2F`,如 `@demo%2Fhello-plugin`。

## 构建与测试

```bash
mvn test     # 单元测试
mvn verify   # 单元 + 集成测试(Testcontainers 起 MySQL + MinIO,需 Docker 运行中)
```

回归请以 `mvn verify` 为准;CI 在 Java 17 与 21 两个矩阵上执行同样的命令。

## 配置

关键环境变量(完整默认值见 `src/main/resources/application.yml`):

| 变量 | 说明 |
|------|------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL 连接 |
| `STORAGE_TYPE` | `s3`(默认)或 `local` |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_REGION` | 对象存储(MinIO / S3) |
| `S3_AUTO_CREATE_BUCKET` | 启动时自动建桶;生产环境建议 `false`,由 IaC 预建 |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` / `OIDC_AUTH_URI` 等 | OIDC 登录端点(显式配置,不依赖 issuer discovery) |
| `BOOTSTRAP_ADMIN_SUBJECT` | 初始管理员的 OIDC subject(新用户默认 `AUTHOR` 角色) |
| `REGISTRY_AUTH_ENABLED` | registry 端点 bearer token 鉴权开关,默认 `false` |

部署提示:应用配置了 `server.forward-headers-strategy=framework`,反向代理必须**覆盖**(而非透传)`X-Forwarded-Host` / `X-Forwarded-Proto`,且应用不应直接暴露公网,否则 packument 中的 tarball 下载 URL 可被伪造头投毒。

## 技术栈

Spring Boot 3.5 / Java 17 · MySQL + MyBatis Plus + Flyway · 对象存储 S3 / MinIO(AWS SDK v2)· Spring Security(OIDC + 自实现 registry token)· JUnit 5 + Testcontainers

## 项目结构

按功能分包于 `com.agentpluginhub.*`:

| 包 | 职责 |
|----|------|
| `registry` / `marketplace` | 对 Claude Code 的两个协议端点 |
| `catalog` | 从 DB 聚合插件 / 版本 / dist-tag 的只读视图 |
| `publish` | 上传入口与结构校验、依赖自包含检查 |
| `review` | 审核状态机 |
| `versions` / `disttag` | 版本查询与渠道指针管理 |
| `storage` | `ArtifactStore` 抽象(S3 / 本地文件) |
| `security` | OIDC 登录、角色、registry token |
| `domain` / `mapper` | MyBatis Plus 实体与持久层 |

## 里程碑

- **M0** ✅ 最小闭环:自建 registry + marketplace.json,真机 Claude Code 安装验收通过
- **M1** ✅ 发布审核闭环 + OIDC 登录与角色
- **M2** ✅ 版本治理:stable 渠道、dist-tag 提升 / 回滚、版本读 API
- **M3** 🚧 前端管理 UI
