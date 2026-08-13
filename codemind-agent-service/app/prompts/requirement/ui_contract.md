# UI_CONTRACT A9.10.14.1 Agent-First Quality Contract

生成 UI_CONTRACT 时必须遵守：

1. 当前资产只描述一个 PAGE，不得输出 pages/routes/module 等旧结构。
2. PAGE UI_CONTRACT 是 Requirement 原型、Mock Runtime、Development Agent 的同源蓝图，不是截图、HTML 或 Vue 代码。
3. LLM 必须服从系统生成计划：allowedFeatureKeys、allowedNavigationTargetIds、pagePattern、requiredStateKeys、forbiddenTerms 是硬边界。
4. navigationTargets 是唯一的页面跳转声明位置；NAVIGATE interaction 只能引用 targetPageId，禁止输出 targetPageKey / targetPageName 到 interaction。
5. 每个可见字段必须进入 fields[]；每个按钮必须进入 actionIds + interactions；每个表格列必须进入 columns[]；不得把字段、按钮或列塞进 description。
6. 每个可见组件必须有稳定 componentId，每个字段/按钮/表格列/Tab 必须有稳定 fieldKey/actionId/columnKey/tabKey，便于画布选择器和自然语言修改。
7. LIST_PAGE/TABLE_LIST 必须是企业后台列表模式：页面头、查询筛选区、全宽表格区、表格工具栏、行操作、空状态、mockData。
8. FORM_PAGE/创建/新增/编辑/录入必须是企业后台主从表单模式：页面头、主操作栏、4 列基础信息表单、全宽明细表格、校验提示、底部操作栏；不得生成查询筛选区。
9. DETAIL/DASHBOARD/WORKFLOW/SETTINGS/UPLOAD/AUTH 必须按页面语义生成对应结构，禁止套普通 CRUD 模板。
10. 用户可见文案必须是产品文案，禁止出现 UI_CONTRACT、Mock、component、featureCoverage、schema、开发说明等技术词。
11. 输出必须能够通过 A9.10.14.1 Agent UI Quality Gate；质量门不通过时不得保存为 PAGE 当前原型，也不得进入 MODULE_PROTOTYPE_SNAPSHOT。
