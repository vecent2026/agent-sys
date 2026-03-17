# 自定义筛选组件 Bug 分析与实现方案

## 1. 现状与 Bug 根因分析

系统当前包含了一套非常完善的筛选配置体系（在 `filter-utils.ts` 和 `FilterConditionRow.tsx` 中定义了极度细致的字段、操作符、值的映射机制）。但是，用户视图组件 `FilterPanel/index.tsx` 的编写者**完全忽视了这些设计，采用了硬编码的临时逻辑**：

- **条件等式未匹配**：`index.tsx` 中写死了全局固定的 `STRING_OPERATORS` 和 `ENUM_OPERATORS`。不论是什么字段（如文本、数字、链接等），只要不是 Date，就被当做普通的文本，永远只展示“包含、不包含”等几个简单的操作符。
- **值组件类型失效**：当类型明明是枚举（如 `gender`, `status`）时，界面的编辑框却没有变成固定的选项 Select，而是普通的 Input 或者是格式错误的 Select。因为原逻辑没有深层次调用表单配置中的 `config.options` 数据。
- **日期处理存在前后端不兼容**：因为旧逻辑在日期筛选上通过 `valueType` 强行切换，且使用原生的 `Date` 及 `dayjs` 后转出的是含 `Z` 结尾也就是 `ISOString` 格式，在传输给后端的 Java `LocalDateTime.parse` 时会直接发生反序列化崩溃（HTTP 500）。

## 2. 详细实现方案设计

解决这一问题的核心思路是：**摒弃 `FilterPanel/index.tsx` 的手写屎山逻辑，全面启用基于 `filter-utils.ts` 配置驱动的 `FilterConditionRow.tsx` + `ValueInput.tsx` 组件。**

### 2.1 重写 `FilterPanel/index.tsx`
- 移除硬编码的操作符枚举。
- 当渲染每一行条件时，直接渲染 `<FilterConditionRow />` 并向其传递更新与删除的回调。
- 只有这样，每行独立的条件才能精确读取自身的字段类型（`TEXT`, `RADIO`, `NUMBER`, `DATE` 等）从而正确的切换。

### 2.2 完善 `FilterConditionRow.tsx`
- 挂载当`field`字段变动时，默认清空旧值，并自动选中属于新字段适合的首个`operator`（例如字段从文本切为数值，操作符自动切为“等于”）。

### 2.3 修复 `ValueInput.tsx` 的控件渲染
- **字符串与枚举**：让 `ValueInput` 负责从字段的附加属性 `fieldConfig.options` 读取下拉选项。
- **日期处理 (核心修复)**：不再采用单纯的受控 `value` 作为基础，而是拦截预设范围选择（本周、上个月等）。并且在抛出给外层时，严格将其转换为 `YYYY-MM-DD HH:mm:ss` 的标准时间格式（移除ISO的尾部`Z`和毫秒），确保能被后端的 `AppUserServiceImpl.java` 用 `LocalDateTime.parse` 正确接盘。

### 2.4 数据序列化修正 `filter-utils.ts`
- 在向后台发出网关请求之前，通过 `mapFiltersToQuery` 扫描所有的 `ValidFilters`。发现如果是具有复合结构的日期对象 `{presetType, value}`，则在此处拍平提取其真实的 date string 交由通用查询引擎执行。
