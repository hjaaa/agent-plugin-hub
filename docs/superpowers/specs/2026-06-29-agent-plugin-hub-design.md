# agent-plugin-hub 设计文档

> 状态:设计稿(待评审) · 日期:2026-06-29 · 作者:huangjian

## 1. 背景与目标

构建一个**面向 AI 编码 agent 的内部插件市场平台**。第一阶段(v1)只支持 **Claude Code** 插件的发现、托管、版本化与受控分发;架构上为未来扩展其他 agent(如 Codex)预留扩展点,但 **v1 不提前实现多 agent 抽象**(YAGNI)。

- **目标用户**:公司内部多个团队(企业/团队内部平台,非公开社区、非对外 SaaS)。
- **平台定位**:重量级托管平台/注册表——平台**自身托管/镜像插件产物**,当唯一 source of truth,作者上传、版本化存储与分发。
- **技术栈**:前后端分离。后端 Spring Boot(复用现有基建:MySQL;制品走对象存储/MinIO);前端独立 SPA(React/Vue)。
- **独立仓库**:与 `mcp-platform` 物理隔离,理由见 §3.2。

## 2. 范围

### v1 必须(基线骨架 + 两项治理)

- **基线骨架**:OIDC 登录、发现/搜索界面、插件详情、产物托管与版本化、动态生成 `marketplace.json` 供 Claude Code 安装、一键拿安装命令。
- **上传 + 上架审核工作流**:作者上传 → 校验 → 管理员审批 → 上架。
- **版本管理 + 发布渠道**:多版本并存、`latest`/`stable` 双渠道、回滚、不可变发布。

### v2+ 延后(不在 v1 实现)

- 团队级可见性 / 安装权限控制。
- 使用统计(安装量/活跃度)、星标评分、问题反馈。
- 多 agent 适配器(Codex/Cursor/Gemini CLI 等)。
- 公共库抽取(仅当真出现跨仓共享需求时再做)。

## 3. 关键决策与取舍

### 3.1 产物如何"被 Claude Code 装上":选 npm registry 路线(方案 A)

**约束事实**(已核实官方文档):Claude Code 客户端能消费的插件 source 只有 `github / git url / git-subdir / npm / 本地路径`,**没有"HTTP 下载 tarball"这种 source**;且**不存在中心注册表协议**,市场本质是"含 `.claude-plugin/marketplace.json` 的 git 仓库或一个远程 URL 指向的 `marketplace.json`"。

因此"平台托管产物 + 被 CC 原生安装"的干净交点是 **npm**:

- 作者把插件目录 `npm pack` 成 tarball 上传;平台版本化存储。
- 后端**自身实现一个最小 npm registry 只读接口**(packument + tarball 两个端点),**无需部署 Verdaccio**。
- 生成的 `marketplace.json` 每个插件条目用 `{"source":"npm","package":"@org/foo","version":"~1.2.0","registry":"https://<平台>/registry"}`。
- CC 安装时内部跑 `npm install` 命中该 registry,**版本解析、semver、不可变发布、发布渠道(dist-tags)全部复用 npm 协议**。

**被否方案**:
- 方案 B(复用内部 GitLab,git tag 当版本):source of truth 落到 GitLab,与"平台托管产物"拧;平台需到处建仓/push,治理边界糊。
- 方案 C(平台内部 mono-repo + git-subdir):多插件各自版本化别扭(整仓一条 tag 线),不满足 v1 的版本管理/发布渠道硬需求,仅适合 demo。

### 3.2 独立仓库,而非并入 mcp-platform

- 两者是不同限界上下文:`mcp-platform` 是把内部系统包装成 `@Tool` 暴露给 AI 的**运行时**(产物 fat-jar MCP server);本平台是**分发/治理 Web 平台**(产物 web 后端 + 前端 + 对象存储 + npm 端点)。
- 本平台**不暴露任何 MCP 工具**,套不进 mcp-platform 的 BOM/starter/`services/<name>` 模块架构。
- mcp-platform 近期刻意清理了前端/TS 残留并收紧镜像;引入 React/Vue 工具链会污染该仓。
- 安全爆炸半径:不应把"持 DB/Redis 凭据的工具运行时"与"对外内容分发平台"关进同一仓/进程。
- 复用很弱:`mcp-platform-core`(数据源路由、`@RequireWrite`、SQL 安全校验)本平台几乎用不上。
- 二者真实关系是产品级消费(本平台未来可把 mcp-platform 的 MCP server 当一个插件条目分发),单向引用,不需共享构建。

### 3.3 鉴权用复用公司 OIDC,而非自建账号

- 内部企业平台,公司已有 OIDC/OAuth2 IdP(Keycloak/Okta/企业微信/飞书/钉钉等)。
- 复用 IdP:不自建凭据库(无注册/改密/找回,无密码表脱库风险)、零 onboarding、团队信息可从 IdP 取、离职即失权。
- **身份不在里程碑 0 关键路径上**:里程碑 0 不需要登录;OIDC 接入落在里程碑 1(审核)/ 里程碑 3(前端)。

### 3.4 中性命名 + 多 agent 扩展缝(不提前实现)

- 仓库名 `agent-plugin-hub`,不绑定 Claude Code。
- 架构中 ②发布、③审核、⑤制品存储、⑥npm registry、①发现 **均 agent 无关**;唯一与 Claude Code 绑定的是 ⑦——本质是"把同一份已发布插件渲染成某 agent 认的清单格式"的**适配器**。
- 未来支持 Codex 等 = 新增一个 ⑦-类适配器,底层全复用。**v1 仅实现 Claude Code 适配器**。

## 4. 架构总览

前后端分离。后端按"单一职责、接口清晰、可独立测试"拆 7 个单元;**对外暴露给 CC 的只有两类 HTTP 端点**(manifest + registry),其余为内部服务。

```
┌─────────────── 前端 SPA (React/Vue) ───────────────┐
│  发现/搜索/详情 · 一键安装命令 · 作者上传 · 审核控制台   │
└───────────────────────┬─────────────────────────────┘
                        │ REST (web session, OIDC)
┌───────────────────────▼──────── Spring Boot 后端 ───────────────────────┐
│  ① 发现服务 DiscoveryService     ② 发布服务 PublishingService            │
│  ③ 审核工作流 ReviewWorkflow      ④ 校验器 Validator                      │
│  ⑤ 制品存储 ArtifactStore         ⑥ npm 只读端点 RegistryController ◀── CC │
│  ⑦ Manifest 生成器 MarketplaceController(agent 适配器扩展点)◀──────── CC │
└──────────────────────────────────────────────────────────────────────────┘
        │MySQL(元数据/版本/审核)   │对象存储(tarball)   │OIDC IdP
```

| # | 单元 | 职责 | agent 相关? |
|---|------|------|-------------|
| ① | DiscoveryService | list/search/detail/versions(读路径) | 无关 |
| ② | PublishingService | 接收 tarball、登记待审 submission | 无关 |
| ③ | ReviewWorkflow | 上架审核状态机 | 无关 |
| ④ | Validator | 结构/`plugin.json`/保留名 校验 | 无关 |
| ⑤ | ArtifactStore | 版本化 blob + integrity hash(指针外置) | 无关 |
| ⑥ | RegistryController | npm 只读端点:packument + tarball | 无关(npm 协议) |
| ⑦ | MarketplaceController | 渲染 `marketplace.json`(`source:npm` 指向 ⑥) | **Claude Code 专属适配器**(扩展点) |

**两个对 CC 的接缝(全平台仅此两处对外)**:
- ⑦ `GET /marketplace.json` → 开发者 `/plugin marketplace add https://<平台>/marketplace.json`
- ⑥ `GET /registry/:pkg` + `.tgz` → CC 解析 `source:npm` 后内部 `npm install` 命中

### 4.1 校验器实现取舍

- (a) 后端 shell 调用 `claude plugin validate`:最准,但给后端引入 claude CLI 运行时依赖。
- (b) 自行按 JSON Schema 校验 `plugin.json`/marketplace 条目 + 保留名:无外部依赖,需跟官方字段演进。
- **v1 决策**:用 (b) 做基础结构 + 保留名校验;(a) 作为可选增强,不在 v1 引入。

## 5. 数据流(发布 → 安装)

```
作者侧                        平台                                    开发者侧(CC)
  │ npm pack 插件目录            │                                         │
  │ ── 上传 tarball ──────────▶ ② 存待审 blob + ④ 校验 → 建 submission       │
管理员 ── 审核控制台 ─────────▶ ③ APPROVE                                  │
  │                             │  → 版本登记 plugin_version(PUBLISHED)   │
  │                             │  → 更新 dist-tag(latest/stable)        │
  │                             │  → marketplace.json 失效重建            │
  │                             │  ⑦ GET /marketplace.json ◀── marketplace add
  │                             │  ⑥ GET /registry/:pkg ◀──── install → npm install
  │                             │     GET ….tgz ◀──────────── 下载校验 integrity
```

- **不可变发布**:同一 `(package, version)` 一旦 PUBLISHED 不可覆盖(防供应链偷换);改动要发新版本号。
- **发布渠道/回滚**:`latest`/`stable` 是两个**可移动的 dist-tag 指针**指向某已发布版本;回滚=移指针,产物不动。

## 6. 数据模型(MySQL,v1 核心 5 表)

| 表 | 关键字段 | 职责 |
|----|---------|------|
| `plugin` | id, package(`@org/foo` 唯一), display_name, description, owner_team, category, homepage, repo_url | 跨版本不变的插件元信息 |
| `plugin_version` | id, plugin_id, version, tarball_ref(对象存储 key), integrity(sha512), size, status(PUBLISHED), uploaded_by, published_at | 每版本一行,产物指针 + 完整性哈希 |
| `dist_tag` | plugin_id, tag(`latest`/`stable`), version | 可移动指针;发布渠道与回滚靠它 |
| `submission` | id, plugin_version_id, state(SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED), submitter, reviewer, review_notes, 时间戳 | 上架审核状态机 |
| `app_user` / `team` | 来自 OIDC,v1 极简(id, account, team, role) | 鉴权/归属 |

- blob 不进 MySQL,`plugin_version` 与对象存储是**指针关系**(存 key + hash),使 ⑤ 可独立替换(本地盘/MinIO/S3)。
- v2+ 再加 `install_stat`、`rating`,v1 不建。

## 7. 错误处理

- **发布/上传(②④)**:tarball 损坏/非法 → 拒绝并回明确原因;`plugin.json` 缺失/字段非法/撞保留名 → 校验失败、actionable 反馈;重复版本(已 PUBLISHED)→ `409`;超大包 → 上限拦截。
- **registry(⑥)**:未知 package/version → `404`(npm 协议格式);token 无效 → `401`;`integrity` 不匹配 → 拒绝下回。
- **manifest 生成(⑦)**:**必须永远吐合法 JSON**——坏插件**跳过 + 记日志**,绝不让单个插件拖垮整份 `marketplace.json`;生成的 marketplace `name` 避开保留字。
- **审核状态机(③)**:非法跃迁拒绝(幂等);并发审批用乐观锁防双批。

## 8. 安全 / 鉴权

### 三个平面

1. **Web 平面(前端↔后端)**:OIDC 登录 + web session;角色 `author`/`admin`;团队信息尽量从 IdP 取。上传需登录,审批需 admin。
2. **CC 安装平面(两道关,各自 token)**:
   - manifest 拉取:`/plugin marketplace add https://<平台>/marketplace.json`。
   - registry 下载:`source:npm` → `npm install` → 读 `.npmrc` 的 `//<平台>/registry/:_authToken=xxx`;平台按用户/团队签发**只读 registry token**,经 `managed-settings.json`/env 下发,可吊销可轮换。
3. **全信任模型应对**:CC 插件以用户权限跑任意代码、官方无签名。内部信任边界 + 上架审核 gate(④ + ③)为主控;不可变发布防过审版本被偷换;registry token 只读。v1 校验器可选扫 `bin/`、`.mcp.json`、`hooks` 高危命令——**只提示不阻断**。

### manifest 鉴权双路兜底(待实测决定)

**待验证**:CC 对"远程 URL 形式 `marketplace.json`"支持哪种鉴权(Bearer/basic/git 凭据),官方文档未明确。

- (i) **动态 HTTP 端点** + 网络层兜底(内网/VPN/IP 白名单);应用层鉴权待实测。
- (ii) **每次发布后把 `marketplace.json` push 到内网私有 git 仓**,CC 用原生 git 凭据(`GITLAB_TOKEN` 等)鉴权——官方明确支持私有 git 市场,**最稳**;代价是 manifest 多一步"渲染→push"。registry 仍走动态 npm 端点。
- **v1 决策**:用 (ii) 兜底;里程碑 0 实测 (i),能走通则切回更简单的 (i)。

## 9. 测试策略

遵循既有规范(JUnit5 + AssertJ + Mockito,外部依赖 mock)。

- **单元**:④Validator(合法/非法 `plugin.json`、保留名、边界);⑦Manifest(**golden 文件比对** + 坏插件跳过);⑥Registry(packument 序列化 golden、dist-tag 解析、404/401);③状态机(合法/非法跃迁、幂等、乐观锁);不可变发布(重复发版被拒)。
- **集成**:发布→审批→manifest 含该插件→registry 可下载 端到端(对象存储用 testcontainer MinIO 或 mock);用**真实 `npm install`** 打测试实例 registry,验证 packument/tarball 真能被 npm 解析装上。
- **🔑 CC 真机冒烟(最重要)**:真实 Claude Code 里 `/plugin marketplace add` + `/plugin install` 跑通——验证 "npm-source + 自实现 registry" 协议假设的**唯一证明**,必须最先且持续跑。

## 10. MVP 里程碑(执行顺序)

铁律:**先消除协议层最大不确定性,再做业务闭环,最后做体验层**。

- **🟥 里程碑 0 — 打通最小闭环(头等大事,先做)**:无前端/无审核/无登录。手工塞一个 `npm pack` 好的 tarball 进存储 → 后端实现 ⑥registry 两端点 + 一份 `marketplace.json`(走 §8 的 (ii):push 内网 git 仓,或临时本地文件)→ **真实 CC** 里 `marketplace add` + `install` 装上能用。跑不通则方案推倒,故必须最先。
- **🟧 里程碑 1 — 发布与审核闭环**:②上传 + ④校验 + ③审核状态机 + 审批后自动重建 manifest;OIDC 接入(`author`/`admin`)。
- **🟨 里程碑 2 — 版本管理 + 发布渠道**:多版本、`dist_tag`(latest/stable)、回滚、不可变发布。
- **🟩 里程碑 3 — 前端 + 体验**:发现/搜索/详情/一键安装命令 + author 上传 UI + admin 审核控制台。前后端分离并行,放最后(不在关键风险路径)。
- *(v2+:团队可见性/安装权限、使用统计、评分反馈、多 agent 适配器)*

## 11. 待验证项与风险

| 项 | 风险 | 处置 |
|----|------|------|
| CC 对 `source:npm` 自建 registry 的真实兼容性 | **高**——整套方案命脉 | 里程碑 0 真机冒烟最先验证 |
| 远程 URL `marketplace.json` 的鉴权方式 | 中 | §8 双路兜底,默认 (ii) 私有 git |
| `npm pack`/npm 版本解析与 CC 内部 npm 行为的细节差异 | 中 | 集成测试用真实 `npm install` 覆盖 |
| 保留 marketplace 名冲突 | 低 | ⑦ 生成时校验避开保留字 |
| 校验器跟随官方字段演进 | 低 | (b) 自校验 + 预留 (a) 增强 |

## 12. 多 agent 扩展点(未来,非 v1)

- ⑦ 抽象为 `AgentManifestAdapter`:输入"已发布插件集合",输出某 agent 认的清单。v1 仅 `ClaudeCodeAdapter`(渲染 `marketplace.json`)。
- 未来 Codex 等:新增对应 Adapter,复用 ①②③⑤⑥;可能直接对接开源 Agent Skills 标准(`agentskills/agentskills`)+ `npx skills` 安装器。
- **本节为方向说明,不在 v1 实现,避免过度设计。**

## 附:Claude Code 插件市场机制参考(已核实官方文档)

- 插件:`.claude-plugin/plugin.json`(name/description/version/author…);组件 `skills/`、`agents/`、`hooks/hooks.json`、`.mcp.json` 等放插件根目录。
- 市场:`.claude-plugin/marketplace.json`(name/owner/plugins[]);插件 source 支持 `github`/`url(git)`/`git-subdir`/`npm`/相对路径;npm source 支持自定义 `registry`。
- 安装:`/plugin marketplace add <owner/repo | git url | 远程 marketplace.json url | 本地路径>`;`/plugin install <plugin>@<marketplace>`。
- 去中心化,无中心注册表;市场即 git 仓库或远程 URL。
- 保留名:`claude-code-marketplace`、`anthropic-*`、`claude-plugins-*` 等及仿冒变体不可用。
- 企业:`settings.json` 的 `extraKnownMarketplaces`、`managed-settings.json` 的 `strictKnownMarketplaces`/`blockedMarketplaces`;私有 git 用 `GITHUB_TOKEN`/`GITLAB_TOKEN` 等。
- 无官方签名/校验机制;全信任模型(插件以用户权限执行任意代码)。
