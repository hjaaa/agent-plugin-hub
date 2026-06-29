# 里程碑 0 真机冒烟手册(Claude Code)

目的:在**真实 Claude Code** 里验证「自建 npm registry + 动态 marketplace.json」能被原生 `marketplace add` + `install` 装上。这是 M0 的唯一验收证据。

## M0 范围限制:仅自包含插件

M0 的 registry 只服务自家已登记的包,不代理上游 npm。因此**仅支持自包含插件**:插件 `package.json` 不应声明需从 registry 解析的外部 `dependencies`(依赖须 bundle 进 tarball,或无外部依赖)。带外部依赖的插件会在 `marketplace.json` 渲染时被**跳过(不广告)**,以免"列出来却装不上"。上游代理/镜像与发布期校验留待 M1。

## 前置

- 已构建通过:`export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home && mvn test` 全绿。
- 已种子化制品:在仓库根执行 `./scripts/seed-artifacts.sh`,确认 `./artifacts/` 下有 `demo-hello-plugin-1.0.0.tgz` 与 `index.json`。

## 启动服务

```bash
export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home
mvn spring-boot:run
# 另开终端自检(应返回 marketplace JSON,registry 字段为 http://localhost:8080/registry):
curl -s http://localhost:8080/marketplace.json
# 自检 packument(scoped 包名用 %2F):
curl -s http://localhost:8080/registry/@demo%2Fhello-plugin
```

## 路线 (i):动态 HTTP marketplace(默认先试)

在真实 Claude Code 会话里:

```
/plugin marketplace add http://localhost:8080/marketplace.json
/plugin install hello-plugin@agent-plugin-hub
```

验证:

```
/hello-plugin:hello
```

预期:输出 `hello from agent-plugin-hub M0 plugin ✅`。

- 若全部成功 → 里程碑 0 **GO**(路线 i)。在下方「验收记录」勾选并记录 CC 版本。
- 若 `marketplace add` 因鉴权/协议被拒,或 `install` 无法命中 registry → 记录报错原文,转路线 (ii)。

## 路线 (ii):私有 git 仓 marketplace(兜底,spec §8)

当路线 (i) 不通时:

1. 建一个内网私有 git 仓(如 GitLab),仓内放 `.claude-plugin/marketplace.json`,内容等于 `curl http://localhost:8080/marketplace.json` 的输出(`registry` 指向本服务的 `/registry`,需为 CC 所在机可达的地址而非 localhost)。
2. 配置 CC 的 git 凭据(`GITLAB_TOKEN` 等)。
3. 在 CC 里:
   ```
   /plugin marketplace add <私有仓 git url>
   /plugin install hello-plugin@agent-plugin-hub
   /hello-plugin:hello
   ```
4. 若成功 → 里程碑 0 **GO**(路线 ii)。registry 仍由本服务动态提供,仅 manifest 改为 git 分发。

## 验收记录(执行后填写)

- [x] 路线 (i) 通过 —— CC 版本:本机 Claude Code CLI,日期:2026-06-29
  - `marketplace add http://localhost:8080/marketplace.json` → "Successfully added marketplace: agent-plugin-hub"
  - `install hello-plugin@agent-plugin-hub` → "✓ Installed hello-plugin";`/reload-plugins` 插件数 9 → 10
  - `/hello-plugin:hello` 正常输出 "hello from agent-plugin-hub M0 plugin ✅"
  - 旁证:`curl` 验证 marketplace.json/packument/tarball 均正确;真实 `npm install --registry=http://localhost:8080/registry` 直连服务安装成功
- [ ] 路线 (ii) 通过 —— CC 版本:____,日期:____,转用原因:____
- [ ] 均不通过 —— 报错原文与结论:____
