# PC / Mobile 双端实现规范（三版统一）

Django、Spring Boot 单体版、Gin 单体版遵循同一套 PC / Mobile 页面规则，保证行为一致、可交叉验证。Spring Boot 与 Gin 的 REST API 已拆到同级独立项目目录，不包含 Thymeleaf / Gin 页面模板；API 契约见[数据与 API 文档](../data/api-contract.md)，共享 Vue 源码位于 `versions/vue/pms-vue/`。

## 1. 视图模式（view mode）

三种模式：`auto` / `pc` / `mobile`

- **auto**：服务端按 `User-Agent` 判断。命中移动特征 → `mobile`，否则 `pc`。
  移动特征正则（大小写不敏感）：
  `Android|iPhone|iPad|iPod|Mobile|Windows Phone|BlackBerry|Opera Mini|IEMobile`
- 用户手动选择写入 cookie **`pms_view`**，值为 `pc` / `mobile` / `auto`，`Path=/`，有效期 1 年。
- 无 cookie 时按 `auto` 处理。
- 最终生效模式（effective mode）只能是 `pc` 或 `mobile`；`auto` 只是偏好值。

## 2. 切换端点

| 方法 | 路径 | 行为 |
|------|------|------|
| GET | `/view/pc/` | 写 `pms_view=pc`，302 回到来源页 |
| GET | `/view/mobile/` | 写 `pms_view=mobile`，302 回到来源页 |
| GET | `/view/auto/` | 写 `pms_view=auto`，302 回到来源页 |

- 回跳目标优先取 query 参数 `next`（**只接受以 `/` 开头的站内相对路径**，防止开放重定向）；
  其次取 `Referer` 的 path；最后回落到 `/`。
- 切换端点：Django 需登录；Gin/Spring 走各自页面鉴权；均应豁免 CSRF（GET 幂等）。
- 三版都在公共上下文注入两个变量供模板使用：
  - `view_mode`：当前生效模式（`pc` 或 `mobile`）
  - `view_pref`：用户偏好（`auto` / `pc` / `mobile`）

## 3. 模板组织

- **PC 模板**：保持现有路径不动（如 `templates/dashboard.html`、`templates/daily/daily_list.html`）。
- **Mobile 模板**：放在 `m/` 子目录下、同名相对路径：
  - `templates/m/base.html`
  - `templates/m/dashboard.html`
  - `templates/m/daily/list.html` / `form.html` /（Gin 另有 `detail.html`）
  - `templates/m/weekly/…`、`templates/m/monthly/…`
  - `templates/m/login.html`（Django 为 `templates/m/registration/login.html`）
- 渲染选择：`effective_mode == mobile` → 模板名加 `m/` 前缀；PC 保持原名。
  - Django：`render(request, tpl, ctx)`，其中 `tpl` 经 helper 加前缀。
  - Gin：`s.render(c, page, title, data)`，`page` 经 helper 在 `templates` map 里选 `m/<page>` 或 `<page>`。
  - Spring：controller 返回视图名，经 helper 加 `m/` 前缀（用 `RequestContextHolder` 读 cookie/UA）。
- 各版新增一个薄封装 helper（如 `page_view(request, name)` / `s.page(c, name)` / `Views.page(name)`），
  集中处理模式判定与前缀，避免每个视图重复判断。

## 4. 移动端 UI 规范（不是简单缩放）

- `<meta name="viewport" content="width=device-width, initial-scale=1">`。
- **顶部**：精简 header —— 品牌 + 当前页标题 + 「PC 版」切换链接。
- **底部固定 Tab 栏**：工作台 `/`、每日 `/daily/`、每周 `/weekly/`、每月 `/monthly/`；
  当前项高亮；点击区 ≥ 44px；`position: fixed; bottom: 0`。
- **内容**：卡片流，不用宽表格。每张清单/每条记录一张卡片，标题突出、关键字段摘要、操作按钮成排。
- **表单**：纵向单列，每个分区一张卡片；明细项（时间盒/领域/任务流/健康项）用可重复的卡片块；
  提交按钮 sticky 底部、大按钮。
- **配色**：沿用 v2 设计变量（靛蓝主色、柔和徽章），另建 `static/css/mobile.css` 或在现有 CSS 追加 `@media (max-width: 767.98px)` 的 `m-` 前缀样式。
- **切换入口**：
  - PC 顶栏加「📱 手机版」链接 → `/view/mobile/?next=<当前路径>`
  - Mobile 顶栏加「🖥 PC 版」链接 → `/view/pc/?next=<当前路径>`；底部再放一个「自动」链接 → `/view/auto/`
- 移动端仍需完整 CRUD：新建/编辑/删除/勾选时间盒，一条都不能少。

## 5. 不变的部分

- 本规范只涉及单体页面版；独立 API 项目的 REST 契约（`/api/**`）、Token 格式、BCrypt、用户隔离和数据库表结构保持不变。
- 三版表单字段名与 POST 目标必须与 PC 端一致（移动端只是另一套呈现，提交到相同路由）。
- 登录页也要有移动版，登录后跳回工作台。

## 6. 验收标准

1. 三版各自：桌面 UA 看到 PC 模板、移动 UA 看到 Mobile 模板；手动切换后 cookie 生效并在刷新后保持。
2. 移动端能完成：登录 → 每日清单新建/编辑/勾选/删除 → 周/月清单新建/编辑/删除 → 切回 PC。
3. `next` 参数传入 `//evil.com` 或 `http://evil.com` 时不外跳（回落 `/`）。
4. PC 与 Mobile 的表单提交到同一 URL、字段同名，数据互通。
5. 三版编译/测试通过；模板全部可渲染。
