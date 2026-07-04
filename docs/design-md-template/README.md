# DESIGN.md 内部管理系统设计基座(推广包)

给内部后端管理系统前端用的设计规范模板,基于 Google [design.md](https://github.com/google-labs-code/design.md)
格式(alpha):YAML frontmatter 存设计 token(机器可校验),Markdown 正文写使用规则
(给 AI 编码 agent 与人看)。首个落地项目:Agent Plugin Hub M3(见本仓库 `frontend/DESIGN.md`)。

模板自带一套**开箱即绿**的默认值(靛蓝主色 + 700 色阶语义色,全部组合已过 WCAG AA),
新项目复制后即可通过 lint,再按需替换。

## 接入步骤(约 15 分钟)

1. **复制文件**
   - `DESIGN.template.md` → 目标仓库 `frontend/DESIGN.md`(或前端工程根目录)
   - `design-lint.yml` → 目标仓库 `.github/workflows/design-lint.yml`,改最后一行的文件路径
2. **填空**:搜索正文中的 `✎ 项目必填`,共 4 处:
   - Overview 的产品叙述;技术栈非 React + AntD 5 时替换映射表
   - 品牌主色(如需):改 `primary` / `primary-hover` / `primary-bg` 三个值
   - **领域语义色映射**:把产品的状态词汇(订单状态/环境标签/工单优先级……)逐一映射到
     success / info / warning / error 四个语义对
   - Components 末尾追加产品特有组件规则
3. **本地 lint**(需 Node 18+):
   ```bash
   npx -y @google/design.md@0.3.0 lint frontend/DESIGN.md
   ```
   目标:`errors: 0, warnings: 0`。
4. **让 agent 生效**:目标仓库 `CLAUDE.md`(或 `AGENTS.md`)加一条:
   > **前端 UI 铁律**:任何涉及前端界面的开发或修改,动手前必须先读 `frontend/DESIGN.md`
   > 并遵循其中的 token 与规则,禁止硬编码色值/字号/间距;需要新的视觉样式时,
   > 先在 DESIGN.md 补 token 与说明再实现。

## 改动模板时的守则

- **对比度是硬约束**:改任何颜色后必须重跑 lint。文字色统一取 700 色阶、底色取 50 色阶,
  这个公式天然过 WCAG AA(4.5:1),改浅前先想清楚。
- **删 token 要成对删**:组件引用了的 color 不能单删(broken-ref 是 error 级会挂 CI);
  只定义不引用会报 orphaned-tokens warning。例如删 code-box 组件要连同
  `colors.code-bg` / `code-text` / `typography.code` 一起删。
- **正文章节顺序固定**:Overview → Colors → Typography → Layout → Elevation & Depth
  → Shapes → Components → Do's and Don'ts,乱序会报 section-order warning。

## 已知边界(实测,CLI 0.3.0)

- 组件合法子 token 只有:`backgroundColor` / `textColor` / `typography` / `rounded` /
  `padding` / `size` / `height` / `width`。**没有 `borderColor`**——边框色用 `divider`
  组件的 `backgroundColor` 表达(模板已如此处理)。
- lint 只在 error 时非零退出;warning 不挂 CI,但建议保持零 warning 基线。
- Windows 下命令别名用 `designmd`:`npx -p @google/design.md designmd lint …`。
- 格式处于 **alpha**,字段可能变:每个仓库把 CLI 钉死到具体版本(模板钉 `0.3.0`),
  升级统一走 PR,先本地跑通再改 workflow。

## 设计决策速览(为什么长这样)

- **版面公式**:浅灰版面(`#f6f7f9`)+ 白卡片 + 1px 边框弱阴影,参考 Linear / Stripe 后台。
- **主色克制**:主色只给可交互元素,一页至多一个主按钮;默认靛蓝 `#4f46e5` 是中性选择,
  可换品牌色。
- **四级字号、5 档间距、两级圆角**:刻度少才守得住,agent 生成的页面才不会跑偏。
- **高密度变体**:重表格控制台在 Layout 一节按说明整体收紧一档,选定后全站统一。
