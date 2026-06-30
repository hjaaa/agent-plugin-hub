# 里程碑 2 真机冒烟手册(Claude Code)

验证「stable 渠道首发自动设 / dist-tag 手动提升回滚 / 版本读 API」闭环。M1 的发布→审批→marketplace 闭环在本里程碑不回退,仍由 `PublishApproveEndToEndIT` 守护。

## 前置

- Docker 运行中。
- 启依赖:`docker compose up -d`(MySQL:3306,MinIO:9000/9001)。
- 构建:`export JAVA_HOME=<本机 JDK 21 路径> && mvn clean verify` 全绿(需 Docker)。
- 若需真实 OIDC 登录(发布/审批/dist-tag 接口),通过环境变量 `OIDC_AUTH_URI`/`OIDC_TOKEN_URI`/`OIDC_JWK_URI`/`OIDC_USERINFO_URI` 配置真实 IdP 端点。

## 启动(dev profile)

> **说明**:`DevDataSeeder` 在 dev 空库时自动走真实发布→审批流程种植 `@demo/hello-plugin@1.0.0`,审批后系统自动将 `stable` 和 `latest` 均指向该版本。

```bash
# 请将 <JAVA_HOME> 替换为本机 JDK 21 的实际路径,例如:
# export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export JAVA_HOME=<JAVA_HOME>
~/devsoft/apache-maven-3.9.11/bin/mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动日志中应出现:

```
dev seed: published @demo/hello-plugin@1.0.0
```

## 路线 (a):marketplace 广告 stable 版本

```bash
# marketplace 应含 @demo/hello-plugin,且 version 字段为 stable 渠道值
curl -s http://localhost:8080/marketplace.json | jq '.plugins[] | select(.source.package == "@demo/hello-plugin") | .source.version'
# 预期:"1.0.0"(首发 stable = 1.0.0)
```

## 路线 (b):发布第二版 → 审批 → 版本列表读 API

> 需已登录 IdP 拿到 web session(ROLE_AUTHOR 发布 / ROLE_ADMIN 审批);无 IdP 时,`DistTagControllerIT` / `VersionEndpointIT` 充当自动化证明。

### b1. 作者上传第二版(待真机验证)

```bash
# POST multipart,需 ROLE_AUTHOR session cookie
curl -s -b cookies.txt \
  -F "file=@your-hello-plugin-1.1.0.tgz" \
  http://localhost:8080/api/plugins
# 预期:201 {"submissionId": <N>}
```

### b2. 管理员审批(待真机验证)

```bash
# POST 需 ROLE_ADMIN session cookie
curl -s -b admin-cookies.txt -X POST \
  http://localhost:8080/api/submissions/<submissionId>/approve
# 预期:200;1.1.0 进入 PUBLISHED 状态
```

### b3. 查版本列表 — latest 前进,stable 不动

```bash
# scoped 包名含 "/" → URL 中用 %2F
curl -s -b cookies.txt \
  "http://localhost:8080/api/plugin-versions/@demo%2Fhello-plugin" | jq .
```

预期响应结构(待真机验证):

```json
{
  "package": "@demo/hello-plugin",
  "distTags": {
    "latest": "1.1.0",
    "stable": "1.0.0"
  },
  "versions": [
    {"version": "1.1.0", "status": "PUBLISHED", ...},
    {"version": "1.0.0", "status": "PUBLISHED", ...}
  ]
}
```

## 路线 (c):dist-tag 提升 stable → marketplace 跟随;再回滚

> 需 ROLE_ADMIN session cookie。无 IdP 时 `DistTagControllerIT.promote_then_rollback_stable_reflects_in_marketplace_and_packument` 充当自动化证明。

### c1. 提升 stable → 1.1.0(待真机验证)

```bash
curl -s -b admin-cookies.txt -X PUT \
  -H "Content-Type: application/json" \
  -d '{"version":"1.1.0"}' \
  "http://localhost:8080/api/dist-tags/stable/plugins/@demo/hello-plugin"
# 预期:200 {"stable":"1.1.0","latest":"1.1.0"}
```

### c2. 验证 marketplace 改广告 1.1.0(待真机验证)

```bash
curl -s http://localhost:8080/marketplace.json | jq '.plugins[] | select(.source.package == "@demo/hello-plugin") | .source.version'
# 预期:"1.1.0"
```

### c3. 回滚 stable → 1.0.0(待真机验证)

```bash
curl -s -b admin-cookies.txt -X PUT \
  -H "Content-Type: application/json" \
  -d '{"version":"1.0.0"}' \
  "http://localhost:8080/api/dist-tags/stable/plugins/@demo/hello-plugin"
# 预期:200 {"stable":"1.0.0","latest":"1.1.0"}

# 再次检查 marketplace
curl -s http://localhost:8080/marketplace.json | jq '.plugins[] | select(.source.package == "@demo/hello-plugin") | .source.version'
# 预期:"1.0.0"
```

## 错误场景(自动化 IT 已覆盖,列出作备查)

| 场景 | 端点 | 预期 HTTP |
|------|------|-----------|
| PUT 非 `latest`/`stable` tag | `PUT /api/dist-tags/beta/plugins/@demo/hello-plugin` | 422 |
| PUT 版本未发布 | body `{"version":"9.9.9"}` | 404 |
| 非 ADMIN 调 dist-tag | ROLE_AUTHOR session | 403 |
| 查不存在包的版本列表 | `GET /api/plugin-versions/@x/none` | 404 |

## 生产部署注意事项

### S3/MinIO — 禁止自动建桶

```yaml
# docker-compose / Kubernetes env
S3_AUTO_CREATE_BUCKET: "false"
```

生产环境 bucket 应由 IaC(Terraform/CDK)提前创建并附 least-privilege IAM 策略。启动时 `S3_AUTO_CREATE_BUCKET=false` 可避免误在生产账号自动建桶。

### 反代 X-Forwarded-* 注意事项

`application.yml` 已配 `server.forward-headers-strategy: framework`。反代(nginx/ALB/Kong)须透传 `X-Forwarded-For`、`X-Forwarded-Proto`、`X-Forwarded-Host`(以及可选的 `X-Forwarded-Port`),否则 registry packument 中生成的 tarball 下载绝对 URL 会携带错误的协议或主机名,导致 `claude code install` 失败。

nginx 示例配置:

```nginx
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
proxy_set_header X-Forwarded-Port  $server_port;
```

## 验收记录(执行后填写)

- [ ] dev profile 启动,日志含 `dev seed: published @demo/hello-plugin@1.0.0`
- [ ] `GET /marketplace.json` → version 为 `1.0.0`(stable 值)
- [ ] 发布 1.1.0 + 审批 → `GET /api/plugin-versions/@demo%2Fhello-plugin` 含两版本、latest=1.1.0、stable=1.0.0
- [ ] `PUT /api/dist-tags/stable/...` 提升 → marketplace version 变 1.1.0
- [ ] 回滚 stable → 1.0.0 → marketplace version 变回 1.0.0
- [ ] `S3_AUTO_CREATE_BUCKET=false` 生产模式启动无报错(已有 bucket)
- [ ] 集成测试:`DistTagControllerIT` 4 个、`VersionEndpointIT` 2 个全绿(自动化证明,等同真机)
