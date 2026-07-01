# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目定位

面向 AI 编码 agent 的**内部插件市场平台**(企业内网,非公开)。v1 只支持 Claude Code 插件的托管、版本化与受控分发,架构为多 agent 预留扩展点但不提前实现。设计全貌见 `docs/superpowers/specs/2026-06-29-agent-plugin-hub-design.md`(注:`docs/superpowers/` 被 gitignore,仅本地存在)。

**核心协议假设**(整套方案命脉):Claude Code 只能通过 `source:npm` 安装插件。后端因此**自实现一个最小只读 npm registry**(packument + tarball 两端点)+ 动态生成 `marketplace.json`。这两个是**全平台仅有的对 CC 暴露的端点**,其余均为内部 API。

## 构建与测试

**必须先设 JAVA_HOME**:本机 PATH 默认 `java` 是 JDK 11,直接 `mvn` 会编译失败。项目需 JDK 17+,团队约定用 JDK 21 构建。JDK 与 Maven 都在 `~/devsoft`:

```bash
export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home
MVN=~/devsoft/apache-maven-3.9.11/bin/mvn
```

- **`$MVN test`** — 只跑单元测试(surefire)。
- **`$MVN verify`** — 单元 + 集成测试(failsafe,`*IT` 结尾的类)。**回归务必用 `verify`**;集成测试用 Testcontainers 起 MySQL + MinIO,**需 Docker 运行中**。
- 单个单元测试:`$MVN test -Dtest=ValidatorTest`;单个集成测试:`$MVN verify -Dit.test=DistTagControllerIT`。
- 本地起依赖:`docker compose up -d`(MySQL `:3306`,MinIO `:9000`/控制台 `:9001`)。
- 跑应用(dev profile,会自动种子化演示插件):
  ```bash
  $MVN spring-boot:run -Dspring-boot.run.profiles=dev
  ```

里程碑冒烟手册:`docs/m0|m1|m2-smoke-runbook.md`(真机 CC `marketplace add` + `install` 验收步骤)。

## 技术栈

Spring Boot 3.5.10 / Java 17 · MySQL + MyBatis Plus 3.5.16(实体用 Lombok)+ Flyway · 对象存储 S3/MinIO(AWS SDK v2)· Spring Security(OIDC + 自实现 registry token)· 测试 JUnit5 + AssertJ + Mockito + Testcontainers。

## 架构与代码结构

按功能分包于 `com.agentpluginhub.*`,每包一个限界职责:

| 包 | 职责 |
|----|------|
| `registry` | **CC 接缝①** 只读 npm registry:`GET /registry/{*path}` 单入口分发 packument 与 `…/-/<file>.tgz` |
| `marketplace` | **CC 接缝②** `GET /marketplace.json`,渲染 `source:npm` 指向本平台 registry;version 字段跟随 `stable` 渠道 |
| `catalog` | `PluginCatalog` 读路径:从 DB 聚合插件/版本/dist-tag,供 registry 与 marketplace 消费 |
| `publish` | 作者上传 `POST /api/plugins`(multipart,ROLE_AUTHOR);`Validator` 校验结构/`plugin.json`/保留名,`DependencyInspector` 查依赖 |
| `review` | 审核状态机:`/api/submissions` 列表 + `{id}/review|approve|reject`(均 ROLE_ADMIN) |
| `versions` | 版本治理读 API `GET /api/plugin-versions/{*pkg}` |
| `disttag` | dist-tag 提升/回滚 `PUT /api/dist-tags/{tag}/plugins/{*pkg}`(ROLE_ADMIN,仅 `latest`/`stable`) |
| `storage` | `ArtifactStore` 抽象:`S3ArtifactStore`(默认)/ `LocalArtifactStore`;blob 不入库,DB 只存 key + sha512 |
| `security` | OIDC 登录 + 角色 + registry token 签发 |
| `domain` | MyBatis Plus 实体(`@TableName/@TableId/@TableField` + Lombok):`plugin` / `plugin_version` / `dist_tag` / `submission` / `app_user`+`user_role` / `registry_token` |
| `mapper` | 持久层入口:每实体一个 `BaseMapper`;`MapperQueries` 封装 `one/exists/count` 等 `LambdaQueryWrapper` 语义(`one` 底层 `selectOne`,多行匹配会抛异常) |
| `common` | `ApiExceptionHandler` 统一异常→HTTP 映射、`IntegrityUtil`(sha512) |
| `config` | `AppProperties` / `S3Properties` / `DevDataSeeder`(仅 dev) |

### 发布 → 安装数据流

作者 `npm pack` → 上传 tarball → 校验通过 + 存**待审 pending blob** + 建 `submission(SUBMITTED)` → 管理员审批 → 版本登记 `plugin_version(PUBLISHED)` + 设 dist-tag + 清理 pending blob → `marketplace.json` 重建 → CC `marketplace add` 拉清单、`install` 命中 registry `npm install`。

- **不可变发布**:同一 `(package, version)` 一旦 PUBLISHED 不可覆盖;改动要发新版本号。
- **发布渠道/回滚**:`latest`/`stable` 是两个**可移动 dist-tag 指针**;回滚=移指针,产物不动。首发审批时自动把 `stable` 和 `latest` 都指向首版本。
- **审核状态机**:`SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED`;非法跃迁拒绝(幂等),并发审批用乐观锁防双批。

### 安全模型(两个平面)

- **Web 平面**:OIDC 登录 + session,角色 `AUTHOR`/`ADMIN`,方法级 `@PreAuthorize` 把守。新 OIDC 用户默认 `AUTHOR`(`LocalUserService` upsert);初始 admin 由 `BOOTSTRAP_ADMIN_SUBJECT` 引导。`/api/**` 豁免 CSRF(CLI/脚本调用)。
- **机器平面**:`/registry/**` stateless bearer token,**仅当 `app.registry.auth.enabled=true` 才启用**(`registryFilterChain` @Order(1));默认关闭时由 `webFilterChain` permitAll 放行。

## 易踩坑的约定

- **scoped 包名带 `/`**:路径变量用 catch-all `{*path}`/`{*pkg}`,URL 中 `/` 编码为 `%2F`(如 `@demo%2Fhello-plugin`)。`SecurityConfig` 已显式放宽 `StrictHttpFirewall` 允许编码斜杠。
- **反代头**:`server.forward-headers-strategy=framework`。可信反代必须**覆盖**(而非透传客户端的)`X-Forwarded-Host`/`X-Forwarded-Proto`,且应用不得直接暴露公网——否则 packument 中生成的 tarball 下载绝对 URL 可被伪造头投毒。
- **Flyway**:schema 全权由 `db/migration/V*.sql` 管理(MyBatis Plus 不做 DDL 自动同步)。**改表结构必须加新 migration**,勿改已发布的 V*.sql。
- **Testcontainers**:`AbstractIntegrationTest` 用单例容器(static 块启动、JVM 存活期保活),**不用** `@Container`/`@Testcontainers`(会在首个测试类后停容器导致后续超时)。
- **`marketplace.json` 容错铁律**:必须永远吐合法 JSON——坏插件跳过 + 记日志,绝不让单个插件拖垮整份清单。
- **S3 建桶**:生产部署设 `S3_AUTO_CREATE_BUCKET=false`(bucket 由 IaC 预建 + least-privilege)。
- **异常→HTTP**:统一走 `ApiExceptionHandler`(404 未知包/版本、422 校验失败、409 重复发布/非法跃迁/乐观锁冲突)。

## 协作约定补充

里程碑顺序为「先消除协议层不确定性 → 业务闭环 → 体验层」:M0(最小 npm registry 闭环)/ M1(发布审核 + OIDC)/ M2(版本+渠道)均已合并 main,M3 = 前端 UI。鉴权用 Spring Security(不引入 Sa-Token)。
