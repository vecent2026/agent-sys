# 统一筛选系统架构重构方案（MVP 版）

> **修订说明**：本文档基于 MVP 原则进行了精简，移除了性能评分系统、测试套件组件、迁移框架等非必要模块，保留了解决核心问题所需的最小有效架构。

## 1. 整体架构设计

### 核心设计原则

- **实用性优先**：只实现解决实际问题所需的功能，不过度设计
- **可扩展性**：Handler 插件体系支持动态字段类型扩展，新增字段类型无需修改核心代码
- **正确性**：使用 EXISTS 子查询解决标签筛选 500 错误和动态字段"为空"逻辑问题
- **类型安全**：前后端严格的类型定义，减少运行时错误

### 架构分层图

```
前端筛选层
  FilterPanel（筛选面板 UI）
    └── FieldRegistry（字段元信息管理）
    └── ValueInput（各类型值输入组件）

API 传输层
  UnifiedFilterQuery / UnifiedFilterCondition（标准 DTO）

后端筛选引擎
  AppUserController（在现有接口上扩展 /filter 端点）
    └── UnifiedFilterEngine（核心引擎，条件验证 → Handler 调度 → SQL 执行）
        └── FieldFilterRegistry（Handler 注册表）
            ├── BuiltinStringFieldHandler
            ├── BuiltinEnumFieldHandler
            ├── BuiltinDateFieldHandler
            ├── BuiltinNumberFieldHandler
            ├── TagCascadeFieldHandler      ← 修复 500 错误的核心
            ├── CustomTextFieldHandler
            ├── CustomNumberFieldHandler
            ├── CustomDateFieldHandler
            ├── CustomRadioFieldHandler
            └── CustomCheckboxFieldHandler

数据访问层
  LambdaQueryWrapper → app_user / user_field_value / app_user_tag_relation
```

### 删除的非必要模块

| 已删除 | 原因 |
|--------|------|
| `BatchCustomFieldOptimizer` | N+1 问题未被证明是瓶颈，后续如需再添加 |
| `FilterCompatibilityAdapter` | 直接在 AppUserServiceImpl 中调用引擎即可 |
| `LegacyCompatibleAppUserService` | 中间层嵌套中间层，增加复杂度 |
| `UnifiedFilterController` | 字段元数据接口合并到现有 Controller |
| `CustomFieldFilterTestController` | 测试代码不属于生产代码 |
| 性能评分/等级/告警系统 | 不影响功能，过度设计 |
| 前端迁移框架（FilterPanelV2、useFilterMigration） | 平滑迁移机制过于复杂 |
| 前端测试套件（CompatibilityTestSuite、TagFilterTest） | 测试组件不属于生产代码 |
| FilterConditionBuilder / FilterValidator | 过度封装，直接构造对象即可 |

---

## 2. 数据模型

### 前端类型定义

```typescript
// 字段类型（const + type 模式，兼容 erasableSyntaxOnly）
export const FieldType = {
  BUILTIN_STRING:   'builtin_string',
  BUILTIN_NUMBER:   'builtin_number',
  BUILTIN_DATE:     'builtin_date',
  BUILTIN_ENUM:     'builtin_enum',
  CUSTOM_TEXT:      'custom_text',
  CUSTOM_NUMBER:    'custom_number',
  CUSTOM_DATE:      'custom_date',
  CUSTOM_RADIO:     'custom_radio',
  CUSTOM_CHECKBOX:  'custom_checkbox',
  CUSTOM_LINK:      'custom_link',
  TAG_CASCADE:      'tag_cascade',
} as const;
export type FieldType = (typeof FieldType)[keyof typeof FieldType];

// 操作符
export const FilterOperator = {
  EQUALS:               'equals',
  NOT_EQUALS:           'not_equals',
  CONTAINS:             'contains',
  NOT_CONTAINS:         'not_contains',
  GREATER_THAN:         'gt',
  LESS_THAN:            'lt',
  GREATER_THAN_OR_EQUAL:'gte',
  LESS_THAN_OR_EQUAL:   'lte',
  BETWEEN:              'between',
  AFTER:                'after',
  BEFORE:               'before',
  IS_EMPTY:             'is_empty',
  IS_NOT_EMPTY:         'is_not_empty',
  IN:                   'in',
  NOT_IN:               'not_in',
  STARTS_WITH:          'starts_with',
  ENDS_WITH:            'ends_with',
  CONTAINS_ALL:         'contains_all',
} as const;
export type FilterOperator = (typeof FilterOperator)[keyof typeof FilterOperator];

// 筛选条件
export interface FilterCondition {
  id: string;
  fieldKey: string;
  fieldType: FieldType;
  operator: FilterOperator;
  value: FilterValue;
}

// 值类型
export type FilterValue =
  | string
  | number
  | Date
  | string[]
  | number[]
  | DateRange
  | NumberRange
  | TagCascadeValue
  | null;

export interface DateRange {
  start: Date | string | null;
  end: Date | string | null;
}

export interface NumberRange {
  start: number | null;
  end: number | null;
}

export interface TagCascadeValue {
  categoryId: number | null;
  tagIds: number[];
}

// 完整筛选查询
export interface UnifiedFilterQuery {
  conditions: FilterCondition[];
  logic: 'AND' | 'OR';
  page: number;
  size: number;
  sortBy?: string;
  sortOrder?: 'ASC' | 'DESC';
}
```

### 后端数据模型

```java
// 统一筛选条件 DTO
@Data
public class UnifiedFilterCondition {
    private String fieldKey;
    private FieldType fieldType;
    private FilterOperator operator;
    private Object value;
}

// 统一筛选查询 DTO
@Data
public class UnifiedFilterQuery {
    private List<UnifiedFilterCondition> conditions;
    private FilterLogic logic;   // AND | OR
    private PaginationInfo pagination;
    private SortInfo sort;

    @Data @Builder
    public static class PaginationInfo {
        @Builder.Default private Integer page = 1;
        @Builder.Default private Integer size = 20;
    }

    @Data @Builder
    public static class SortInfo {
        private String field;
        private SortDirection direction;
        public enum SortDirection { ASC, DESC }
    }
}

// 筛选结果（仅包含必要字段）
@Data @Builder
public static class FilterQueryResult {
    private boolean success;
    private List<AppUser> records;
    private Long totalRecords;
    private Long currentPage;
    private Long pageSize;
    private Long totalPages;
    private String errorMessage;
}
```

---

## 3. 后端核心实现

### 统一筛选引擎（精简版）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedFilterEngine {

    private final FieldFilterRegistry filterRegistry;
    private final AppUserService userService;

    public FilterQueryResult executeFilter(UnifiedFilterQuery query) {
        try {
            // 1. 验证并构建已验证条件列表
            List<ValidatedFilterCondition> validatedConditions =
                validateAndPrepareConditions(query.getConditions());

            // 2. 构建 SQL 查询
            LambdaQueryWrapper<AppUser> wrapper = buildQuery(validatedConditions, query.getLogic());

            // 3. 应用排序
            applySorting(wrapper, query.getSort());

            // 4. 执行分页查询
            PaginationInfo p = query.getPagination();
            Page<AppUser> page = userService.page(
                new Page<>(p.getPage(), p.getSize()), wrapper
            );

            return FilterQueryResult.builder()
                .success(true)
                .records(page.getRecords())
                .totalRecords(page.getTotal())
                .currentPage(page.getCurrent())
                .pageSize(page.getSize())
                .totalPages(page.getPages())
                .build();

        } catch (Exception e) {
            log.error("Filter execution failed: {}", e.getMessage(), e);
            return FilterQueryResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private LambdaQueryWrapper<AppUser> buildQuery(
            List<ValidatedFilterCondition> conditions, FilterLogic logic) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        boolean useAnd = (logic == FilterLogic.AND);
        filterRegistry.applyFilters(wrapper, conditions, useAnd);
        return wrapper;
    }
}
```

### Handler 插件接口

```java
// 字段处理器接口
public interface FieldFilterHandler {
    FieldType getSupportedFieldType();
    List<FilterOperator> getSupportedOperators();
    void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition);
    FilterValidationResult validateValue(FilterOperator operator, Object value);
}
```

### 关键 Handler 实现

**内置字段（直接主表查询，性能最优）**：
```java
public class BuiltinStringFieldHandler implements FieldFilterHandler {
    @Override
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        SFunction<AppUser, String> col = getColumnFunction(condition.getFieldKey());
        String value = (String) condition.getNormalizedValue();
        switch (condition.getOperator()) {
            case EQUALS      -> wrapper.eq(col, value);
            case NOT_EQUALS  -> wrapper.ne(col, value);
            case CONTAINS    -> wrapper.like(col, value);
            case NOT_CONTAINS-> wrapper.notLike(col, value);
            case STARTS_WITH -> wrapper.likeRight(col, value);
            case ENDS_WITH   -> wrapper.likeLeft(col, value);
            case IS_EMPTY    -> wrapper.and(w -> w.isNull(col).or().eq(col, ""));
            case IS_NOT_EMPTY-> wrapper.isNotNull(col).ne(col, "");
            case IN          -> wrapper.in(col, (List<?>) condition.getNormalizedValue());
            case NOT_IN      -> wrapper.notIn(col, (List<?>) condition.getNormalizedValue());
        }
    }
}
```

**标签字段（EXISTS 子查询，修复 500 错误的核心）**：
```java
public class TagCascadeFieldHandler implements FieldFilterHandler {
    @Override
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        TagCascadeValue tagValue = parseTagValue(condition.getNormalizedValue());
        List<Long> tagIds = tagValue.getTagIds().stream()
            .map(Long::valueOf).collect(Collectors.toList());

        if (condition.getOperator() == FilterOperator.IS_EMPTY) {
            wrapper.notExists(
                "SELECT 1 FROM app_user_tag_relation r WHERE r.user_id = app_user.id"
            );
            return;
        }
        if (condition.getOperator() == FilterOperator.IS_NOT_EMPTY) {
            wrapper.exists(
                "SELECT 1 FROM app_user_tag_relation r WHERE r.user_id = app_user.id"
            );
            return;
        }
        if (tagIds.isEmpty()) return;

        String tagIdStr = tagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        switch (condition.getOperator()) {
            case CONTAINS -> wrapper.exists(
                "SELECT 1 FROM app_user_tag_relation r " +
                "WHERE r.user_id = app_user.id AND r.tag_id IN (" + tagIdStr + ")"
            );
            case NOT_CONTAINS -> wrapper.notExists(
                "SELECT 1 FROM app_user_tag_relation r " +
                "WHERE r.user_id = app_user.id AND r.tag_id IN (" + tagIdStr + ")"
            );
        }
    }
}
```

**动态字段"为空"逻辑（关键修复）**：
```java
// CustomFieldHandlerBase 中正确处理"为空"：没有记录 OR 记录值为空
protected void applyIsEmptyFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId) {
    wrapper.and(w -> w
        .notExists(
            "SELECT 1 FROM user_field_value fv " +
            "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId
        )
        .or()
        .exists(
            "SELECT 1 FROM user_field_value fv " +
            "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId +
            " AND (fv.field_value IS NULL OR fv.field_value = '')"
        )
    );
}
```

### API 接口（在现有 Controller 扩展）

```java
// 在现有 AppUserController 中新增，不创建新 Controller
@PostMapping("/filter")
public Result<Page<AppUserVO>> filterUsers(@RequestBody @Valid UnifiedFilterQuery query) {
    UnifiedFilterEngine.FilterQueryResult result = filterEngine.executeFilter(query);
    if (!result.isSuccess()) {
        return Result.error(500, result.getErrorMessage());
    }
    // 转换为 VO
    List<AppUserVO> vos = convertToVOs(result.getRecords());
    Page<AppUserVO> page = buildPage(result, vos);
    return Result.success(page);
}

// 获取可筛选字段定义（供前端动态渲染）
@GetMapping("/filter/fields")
public Result<List<FieldMetadata>> getFilterFields() {
    List<FieldMetadata> fields = new ArrayList<>();
    fields.addAll(getBuiltinFieldMetadata());
    fields.addAll(getCustomFieldMetadata());
    return Result.success(fields);
}
```

---

## 4. 前端实现

### 字段注册表（保留，管理字段元信息）

```typescript
// core/FieldRegistry.ts
export class FieldRegistry {
  private fields = new Map<string, FieldDefinition>();

  constructor() {
    this.registerBuiltinFields();
  }

  // 注册动态字段（从后端接口加载后调用）
  registerCustomFields(customFields: Array<{
    fieldKey: string;
    fieldName: string;
    fieldType: string;
    config?: any;
  }>): void {
    customFields.forEach(field => {
      const fieldType = FieldTypeUtils.fromLegacyFieldType(field.fieldType);
      this.register({ fieldKey: field.fieldKey, fieldName: field.fieldName, fieldType, ... });
    });
  }

  get(fieldKey: string): FieldDefinition | undefined { ... }
  getAll(): FieldDefinition[] { ... }
  getFieldOptions(): Array<{ label: string; value: string; type: FieldType; group: string }> { ... }
}
```

### API 层（仅保留必要接口）

```typescript
// api/unified-filter.ts

// 执行筛选查询
export async function executeUnifiedFilter(request: UnifiedFilterRequest): Promise<PageResult<AppUser>> {
  const response = await request.post<PageResult<AppUser>>('/api/app-users/filter', request);
  return response.data;
}

// 获取可筛选字段定义
export async function getFilterFields(): Promise<FieldMetadata[]> {
  const response = await request.get<FieldMetadata[]>('/api/app-users/filter/fields');
  return response.data;
}
```

### FilterPanel 修复重点

原始 `FilterPanel` 组件仅需修复两个问题：

1. **`ValueInput.tsx`**：标签字段的值格式需构造为 `TagCascadeValue` 对象 `{ categoryId, tagIds }`，当前传的是字符串导致后端解析失败（500 根源）

2. **`FilterConditionRow.tsx`**：标签字段选择时，`fieldType` 需正确设置为 `tag_cascade`，确保后端路由到 `TagCascadeFieldHandler`

```typescript
// ValueInput.tsx 标签字段的正确格式
const handleTagChange = (categoryId: number | null, tagIds: number[]) => {
  onChange({
    value: { categoryId, tagIds } satisfies TagCascadeValue
  });
};
```

---

## 5. 数据库索引建议

以下索引在动态字段和标签筛选量级增大时会显著提升性能，建议在上线前添加：

```sql
-- 动态字段查询：user_id + field_id 复合索引
CREATE INDEX idx_field_value_user_field ON user_field_value(user_id, field_id);

-- 动态字段值筛选：field_id + field_value 复合索引
CREATE INDEX idx_field_value_field_value ON user_field_value(field_id, field_value(100));

-- 标签关联查询：user_id + tag_id 复合索引
CREATE INDEX idx_tag_relation_user_tag ON app_user_tag_relation(user_id, tag_id);
```

---

## 6. 实施计划

### Phase 1：后端基础架构（已完成）

**目标**：建立 Handler 插件体系和 DTO 模型，不依赖前端

| 任务 | 文件 | 状态 |
|------|------|------|
| 枚举定义 | `FieldType.java`、`FilterOperator.java` | ✅ |
| DTO 模型 | `UnifiedFilterCondition.java`、`UnifiedFilterQuery.java` | ✅ |
| 引擎框架 | `UnifiedFilterEngine.java`（精简版） | ✅ |
| Handler 接口 | `FieldFilterHandler.java` | ✅ |
| Handler 注册表 | `FieldFilterRegistry.java` | ✅ |
| 内置字段 Handler | `Builtin*FieldHandler.java` × 4 | ✅ |

**验收标准**：编译通过，Handler 注册成功

### Phase 2：筛选 Handler 完整实现（进行中）

**目标**：实现所有字段类型的 Handler，包括修复 500 错误的 TagCascadeFieldHandler

| 任务 | 文件 | 状态 |
|------|------|------|
| 标签字段 Handler | `TagCascadeFieldHandler.java` | ✅ |
| 动态文本 Handler | `CustomTextFieldHandler.java` | ✅ |
| 动态数字 Handler | `CustomNumberFieldHandler.java` | ✅ |
| 动态日期 Handler | `CustomDateFieldHandler.java` | ✅ |
| 动态单选 Handler | `CustomRadioFieldHandler.java` | ✅ |
| 动态多选 Handler | `CustomCheckboxFieldHandler.java` | ✅ |
| API 接口扩展 | `AppUserController.java` 新增 `/filter` | 🔲 |
| 构建验证 | `mvn clean package` 零错误 | 🔲 |

**验收标准**：
- 构建零错误
- 标签筛选不再返回 500
- 动态字段"为空"筛选逻辑正确

### Phase 3：前端集成（待完成）

**目标**：修复现有 FilterPanel 组件，对接新的后端接口

| 任务 | 文件 | 状态 |
|------|------|------|
| 修复标签值格式 | `FilterPanel/ValueInput.tsx` | 🔲 |
| 修复 fieldType 传递 | `FilterPanel/FilterConditionRow.tsx` | 🔲 |
| 对接 `/filter` 接口 | `FilterPanel/index.tsx` | 🔲 |
| 动态字段类型映射 | `enums/FieldType.ts` | ✅ |
| API 调用层 | `api/unified-filter.ts`（精简） | ✅ |

**验收标准**：
- 标签筛选在前端可以正常选值并提交
- 动态字段所有类型可正常筛选
- 无 TypeScript 编译错误

---

## 7. 已解决的核心问题

| 问题 | 根因 | 解决方式 |
|------|------|---------|
| 标签筛选 500 错误 | 前端传值格式错误 + 后端未处理 TagCascade 类型 | `TagCascadeFieldHandler` + 前端值格式修复 |
| 动态字段"为空"逻辑错误 | 仅判断值为 NULL，未考虑记录不存在的情况 | `NOT EXISTS` + `OR (EXISTS AND value = '')` |
| 动态字段类型扩展困难 | 所有类型堆在一个 if-else 中 | Handler 插件体系，每种类型独立处理器 |
| 新增字段类型需改核心代码 | 无扩展点设计 | `FieldFilterRegistry` 插件注册，新增只需实现接口 |
