---
version: alpha
name: 「项目必填」产品名
description: 「项目必填」一句话描述 — 内部管理系统通用设计基座(默认可直接通过 lint)
colors:
  primary: "#4f46e5"
  primary-hover: "#4338ca"
  primary-bg: "#eef2ff"
  on-primary: "#ffffff"
  text-1: "#17181c"
  text-2: "#55585f"
  text-3: "#9297a1"
  bg: "#f6f7f9"
  surface: "#ffffff"
  border: "#e6e8ec"
  success: "#15803d"
  success-bg: "#f0fdf4"
  warning: "#b45309"
  warning-bg: "#fffbeb"
  error: "#b91c1c"
  error-bg: "#fef2f2"
  info: "#2563eb"
  info-bg: "#eff6ff"
  code-bg: "#16181d"
  code-text: "#e6e8ec"
typography:
  h1:
    fontFamily: -apple-system, BlinkMacSystemFont, PingFang SC, Segoe UI, sans-serif
    fontSize: 22px
    fontWeight: 700
  card-title:
    fontFamily: -apple-system, BlinkMacSystemFont, PingFang SC, Segoe UI, sans-serif
    fontSize: 15px
    fontWeight: 600
  body-md:
    fontFamily: -apple-system, BlinkMacSystemFont, PingFang SC, Segoe UI, sans-serif
    fontSize: 14px
    lineHeight: 1.6
  body-sm:
    fontFamily: -apple-system, BlinkMacSystemFont, PingFang SC, Segoe UI, sans-serif
    fontSize: 13px
  code:
    fontFamily: ui-monospace, SF Mono, JetBrains Mono, Menlo, Consolas, monospace
    fontSize: 12.5px
rounded:
  sm: 6px
  md: 8px
  pill: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.sm}"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.on-primary}"
  button-default:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-1}"
    rounded: "{rounded.sm}"
  nav-active:
    backgroundColor: "{colors.primary-bg}"
    textColor: "{colors.primary}"
    rounded: "{rounded.sm}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-1}"
    rounded: "{rounded.md}"
  page:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.text-2}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-1}"
    rounded: "{rounded.sm}"
  divider:
    backgroundColor: "{colors.border}"
  meta-text:
    textColor: "{colors.text-3}"
  tag-success:
    backgroundColor: "{colors.success-bg}"
    textColor: "{colors.success}"
    rounded: "{rounded.sm}"
  tag-info:
    backgroundColor: "{colors.info-bg}"
    textColor: "{colors.info}"
    rounded: "{rounded.sm}"
  tag-warning:
    backgroundColor: "{colors.warning-bg}"
    textColor: "{colors.warning}"
    rounded: "{rounded.sm}"
  tag-error:
    backgroundColor: "{colors.error-bg}"
    textColor: "{colors.error}"
    rounded: "{rounded.sm}"
  code-box:
    backgroundColor: "{colors.code-bg}"
    textColor: "{colors.code-text}"
    rounded: "{rounded.md}"
---

## Overview

> ✎ **项目必填**:用 2-3 句话写清楚本产品是什么、给谁用、页面以什么形态为主
> (表格密集/表单密集/看板)。删掉本引用块。

视觉基调是**轻量内部工具**(参考 Linear / Stripe 后台):白色容器浮在浅灰版面上,
克制的单一主色,信息密度适中,不做营销页式装饰。

**技术落地约定**:本项目前端用 React 18 + Ant Design 5,**不引入第二套样式体系**。
token 通过 AntD `ConfigProvider` theme token 注入,映射关系:

| DESIGN.md token | AntD ConfigProvider token |
|---|---|
| `colors.primary` | `colorPrimary` |
| `colors.text-1` / `text-2` / `text-3` | `colorText` / `colorTextSecondary` / `colorTextTertiary` |
| `colors.bg` / `surface` | `colorBgLayout` / `colorBgContainer` |
| `colors.border` | `colorBorderSecondary` |
| `colors.success` / `warning` / `error` / `info` | `colorSuccess` / `colorWarning` / `colorError` / `colorInfo` |
| `rounded.md` / `rounded.sm` | `borderRadius` / `borderRadiusSM` |
| `typography.body-md.fontSize` | `fontSize` |

> ✎ **技术栈不同时改这里**:Vue 3 + Element Plus 项目把映射表换成
> `--el-color-primary` 等 CSS 变量;其余正文规则不变。AntD 覆盖不到的样式用
> 与 token 同名的 CSS 变量实现。

## Colors

- `primary` 只用于**可交互元素**:主按钮、激活态导航、链接、聚焦环。不做大面积装饰底色。
  > ✎ **项目必填**:如需替换品牌主色,改 `primary`/`primary-hover`/`primary-bg`
  > 三个值后必须重跑 lint 验对比度。
- 语义色成对使用:文字用 `success`/`warning`/`error`/`info`,底色用对应 `*-bg`,禁止交叉混配。
- 所有"文字色 on 底色"组合必须满足 WCAG AA(≥4.5:1);success/warning/error 文字色
  特意统一取 700 色阶(`#15803d` / `#b45309` / `#b91c1c`),就是为了在各自浅色底上达标,
  不要改浅。`meta-text`(text-3)只用于时间戳、辅助说明等非关键信息,不用于正文。
- **领域语义色(项目必填)**:把本产品的领域状态词汇逐一映射到四个语义对,全站必须一致。
  > ✎ 示例(替换为你的领域):订单已完成=success、待支付=info、超时预警=warning、
  > 已取消=error;环境标签 prod=error、staging=warning、dev=info。
  > 元规则:同一领域状态在所有页面颜色一致,禁止一处绿一处蓝。

## Typography

- 界面文字用系统字体栈(含 PingFang SC),层级只有四级:h1(22/700)、card-title(15/600)、
  body-md(14)、body-sm(13)。不要引入更多字号。
- ID、单号、代码、哈希类字段用 `code`(等宽)字体;若产品无此类字段,可删除
  `typography.code` 与 `components.code-box`(连同 `colors.code-bg`/`code-text` 一起删,
  避免 broken-ref / 孤儿 token)。

## Layout

- 内容区最大宽度 1180px 居中,页面内边距 32px;顶栏高 56px,sticky。
- 间距只用 4/8/16/24/32 刻度(对应 spacing token),卡片间距 16px,区块间距 24px。
- 列表型信息用表格;发现/浏览型信息用卡片栅格(`minmax(340px, 1fr)` 自适应列)。
- **高密度变体**:重表格的运维/风控类控制台可整体收紧一档——AntD `compact` 算法 +
  body-md 降为 13px + 间距刻度整体下移(xs/sm/md = 4/8/12)。选定后写死在本节,全站统一。

## Elevation & Depth

- 卡片默认阴影极轻(`0 1px 2px rgba(23,24,28,.04), 0 1px 3px rgba(23,24,28,.06)`),
  靠 1px 边框区分层级,不靠重阴影。
- 仅两种浮层深度:卡片(轻阴影)与弹层/抽屉(`0 6px 24px rgba(23,24,28,.12)`)。
- 可点击卡片 hover 时轻微上浮(translateY(-1px))+ 边框变主色调。

## Shapes

- 卡片、弹层用 `rounded.md`(8px);按钮、输入框、标签用 `rounded.sm`(6px);
  徽标计数、状态 pill 用 `rounded.pill`。
- 不使用直角和超大圆角(>12px)。

## Components

- **按钮**:一个页面至多一个主按钮(实心主色);次级操作用描边默认钮;危险操作
  用红色描边钮,不用实心红。
- **标签(Tag)**:状态一律用「浅底 + 深字 + 1px 同系边框」的标签,不用实心标签。
- **表格**:表头 12px 加粗灰字、浅灰底;行 hover 高亮;操作列按钮用小号。
- **抽屉**:复杂操作(审批、多字段编辑)用右侧抽屉(宽 560px),不用弹窗堆叠表单。
- > ✎ **项目必填**:在此追加本产品特有组件的规则(如命令框、时间线、看板卡),
  > 新组件的颜色引用一律走 `{colors.*}` token。

## Do's and Don'ts

- ✅ 所有颜色、圆角、间距从 token 取值(组件库 theme token 或同名 CSS 变量)。
- ✅ 新增语义场景先回到本文件补 token 与说明,再写实现。
- ❌ 禁止在组件里硬编码色值/字号/间距(包括组件库组件的 inline style)。
- ❌ 禁止同一领域状态在不同页面用不同颜色。
- ❌ 禁止引入第二套组件库或与选型冲突的样式体系。
- ❌ 空状态不留白板:给一句说明文字,不放照片类素材。
