import dayjs from 'dayjs';
import type { AppUserQuery } from '@/types/app-user';
import type { FilterCondition } from '@/store/userManagementStore';

export type FilterOperator = 'equals' | 'not_equals' | 'contains' | 'not_contains' | 'gt' | 'lt' | 'empty' | 'not_empty';

export interface FilterConfig {
  operators: { label: string; value: FilterOperator }[];
  valueType: 'string' | 'enum' | 'date' | 'number';
}

export const OPERATOR_LABELS: Record<FilterOperator, string> = {
  equals: '等于',
  not_equals: '不等于',
  contains: '包含',
  not_contains: '不包含',
  gt: '晚于', // For dates
  lt: '早于', // For dates
  empty: '为空',
  not_empty: '不为空',
};

export const FIELD_FILTER_CONFIG: Record<string, FilterConfig> = {
  TEXT: {
    operators: [
      { label: '等于', value: 'equals' },
      { label: '不等于', value: 'not_equals' },
      { label: '包含', value: 'contains' },
      { label: '不包含', value: 'not_contains' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'string',
  },
  RADIO: {
    operators: [
      { label: '等于', value: 'equals' },
      { label: '不等于', value: 'not_equals' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'enum',
  },
  CHECKBOX: {
    operators: [
      { label: '包含', value: 'contains' },
      { label: '不包含', value: 'not_contains' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'enum',
  },
  DATE: {
    operators: [
      { label: '等于', value: 'equals' },
      { label: '晚于', value: 'gt' },
      { label: '早于', value: 'lt' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'date',
  },
  NUMBER: {
    operators: [
      { label: '等于', value: 'equals' },
      { label: '不等于', value: 'not_equals' },
      { label: '大于', value: 'gt' },
      { label: '小于', value: 'lt' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'number',
  },
  LINK: {
    operators: [
      { label: '包含', value: 'contains' },
      { label: '不包含', value: 'not_contains' },
      { label: '为空', value: 'empty' },
      { label: '不为空', value: 'not_empty' },
    ],
    valueType: 'string',
  },
};

export const DATE_PRESETS = {
  指定时间: { label: '指定时间', type: 'specific' },
  今天: { label: '今天', getValue: () => dayjs().startOf('day'), type: 'preset' },
  昨天: { label: '昨天', getValue: () => dayjs().subtract(1, 'day').startOf('day'), type: 'preset' },
  本周: { label: '本周', getValue: () => dayjs().startOf('week'), type: 'preset' },
  上周: { label: '上周', getValue: () => dayjs().subtract(1, 'week').startOf('week'), type: 'preset' },
  过去7天: { label: '过去 7 天', getValue: () => dayjs().subtract(7, 'day'), type: 'preset' },
  本月: { label: '本月', getValue: () => dayjs().startOf('month'), type: 'preset' },
  上月: { label: '上月', getValue: () => dayjs().subtract(1, 'month').startOf('month'), type: 'preset' },
  过去30天: { label: '过去 30 天', getValue: () => dayjs().subtract(30, 'day'), type: 'preset' },
  本季度: { label: '本季度', getValue: () => dayjs().startOf('month'), type: 'preset' },
  上季度: { label: '上季度', getValue: () => dayjs().subtract(3, 'month').startOf('month'), type: 'preset' },
  今年: { label: '今年', getValue: () => dayjs().startOf('year'), type: 'preset' },
};

export const mapFiltersToQuery = (filters: FilterCondition[], filterLogic: 'AND' | 'OR' = 'AND'): Partial<AppUserQuery> => {
  const query: Partial<AppUserQuery> = {};

  // Serialize all filters to JSON string for backend to handle generic filtering
  // For dates coming from ValueInput, 'value' might be an object { presetType, value }
  const processedFilters = filters.map(f => {
    if (f.type === 'date' && f.value && typeof f.value === 'object' && 'presetType' in f.value) {
      return { ...f, type: f.value.presetType, value: f.value.value };
    }
    return f;
  });

  // Filter out invalid filters (empty value unless operator is empty/not_empty)
  const validFilters = processedFilters.filter(f => {
    if (f.operator === 'empty' || f.operator === 'not_empty' || f.operator === '为空' || f.operator === '不为空') return true;
    if (f.field === 'tags' && f.value && typeof f.value === 'object' && Array.isArray(f.value.tagIds)) return f.value.tagIds.length > 0;
    if (Array.isArray(f.value)) return f.value.length > 0;
    return f.value !== '' && f.value !== null && f.value !== undefined;
  });

  if (validFilters.length > 0) {
    query.filters = JSON.stringify(validFilters);
    query.filterLogic = filterLogic;
  }

  // Keep legacy mapping for backward compatibility (only if logic is AND, to avoid conflict)
  // If logic is OR, we rely solely on the generic `filters` param (backend must support it)
  if (filterLogic === 'AND') {
    const STATUS_TEXT_TO_CODE: Record<string, number> = {
      正常: 1,
      禁用: 0,
      注销: 2,
    };

    const DATE_PRESET_KEYS = Object.keys(DATE_PRESETS);

    processedFilters.forEach(filter => {
      const field = filter.field;
      const op = filter.operator;
      const val = filter.value;

      // 关键词：客户姓名/昵称 包含 或 等于
      if ((field === 'name' || field === 'nickname' || field === 'mobile') && (op === '包含' || op === '等于') && val) {
        query.keyword = typeof val === 'string' ? val : (Array.isArray(val) ? val[0] : String(val));
      }

      // 状态：等于 正常/禁用/注销 -> 1/0/2
      if (field === 'status' && op === '等于' && val != null) {
        const code = STATUS_TEXT_TO_CODE[String(val)];
        if (code !== undefined) query.status = code;
        else if (typeof val === 'number') query.status = val;
      }

      // 注册来源
      if ((field === 'source' || field === 'register_source') && op === '等于' && val) {
        query.registerSource = typeof val === 'string' ? val : (Array.isArray(val) ? val[0] : String(val));
      }

      // 注册时间：支持 registerDate / register_time，中文操作符 等于/晚于/早于
      if (field === 'registerDate' || field === 'register_time') {
        let dateValue: dayjs.Dayjs | null = null;
        const typeStr = filter.type as string;
        if (typeStr && DATE_PRESET_KEYS.includes(typeStr) && (DATE_PRESETS as any)[typeStr]?.getValue) {
          dateValue = (DATE_PRESETS as any)[typeStr].getValue();
        } else if (val) {
          const parsed = dayjs(val);
          if (parsed.isValid()) dateValue = parsed;
        }
        if (dateValue) {
          const fmt = 'YYYY-MM-DD HH:mm:ss';
          if (op === '晚于') {
            query.registerStartTime = dateValue.format(fmt);
          } else if (op === '早于') {
            query.registerEndTime = dateValue.format(fmt);
          } else if (op === '等于') {
            query.registerStartTime = dateValue.startOf('day').format(fmt);
            query.registerEndTime = dateValue.endOf('day').format(fmt);
          }
        }
      }
    });
  }

  return query;
};
