# 里程碑 1 真机冒烟手册(Claude Code)

验证「发布→审核→审批后自动上架」闭环,并保留 M0 的 install 真机验证。

## 前置
- Docker 运行中。
- 起依赖:`docker compose up -d`(MySQL:3306,MinIO:9000/9001)。
- 构建:`export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home && mvn test` 全绿(需 Docker)。

## 启动(dev profile,自动种子演示插件)
```bash
export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 自检:
curl -s http://localhost:8080/marketplace.json          # 应含 hello-plugin / @demo/hello-plugin
curl -s http://localhost:8080/registry/@demo%2Fhello-plugin
```

## 路线 (i):真机 CC install(沿用 M0)
```
/plugin marketplace add http://localhost:8080/marketplace.json
/plugin install hello-plugin@agent-plugin-hub
/hello-plugin:hello
```
预期输出 `hello from agent-plugin-hub M0 plugin ✅`。

## 发布→审核闭环验证(API)
> 需先在 IdP 配好 OIDC client 并以浏览器登录拿到 web session;无 IdP 时本段可用集成测试 `PublishApproveEndToEndIT` 充当自动化证明。
1. 作者上传:`POST /api/plugins`(multipart `file=@your-plugin.tgz`,需 ROLE_AUTHOR 会话)→ `201` submissionId。
2. 管理员审核台:`GET /api/submissions?state=SUBMITTED`(ROLE_ADMIN)。
3. 审批:`POST /api/submissions/{id}/approve`(ROLE_ADMIN)→ 该插件即出现在 `/marketplace.json`,可被 install。

## registry token(非 localhost 部署)
开启 `app.registry.auth.enabled=true` 后:
1. admin `POST /api/registry-tokens {"label":"team-x"}` → 拿明文 token(仅一次)。
2. 下发到 CC 侧 `.npmrc`:`//<平台host>/registry/:_authToken=<token>`。
3. 无 token / 无效 token 访问 `/registry/**` → 401。

## 验收记录(执行后填写)
- [ ] 发布→审批→marketplace 含该插件(集成测试 `PublishApproveEndToEndIT` 绿即可勾选)
- [ ] dev profile + 真机 CC install hello-plugin 成功;`/hello-plugin:hello` 正常
- [ ] registry token 开关:有效 200 / 无效 401 / 吊销 401
