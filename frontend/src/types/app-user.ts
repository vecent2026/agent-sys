export interface AppUser {
  id: number;
  nickname: string;
  avatar: string;
  mobile: string;
  email: string;
  gender: number;
  birthday: string;
  registerSource: string;
  status: number;
  registerTime: string;
  lastLoginTime: string;
  lastLoginIp: string;
  tags: AppUserTag[];
  fieldValues?: FieldValueInfo[];
}

export interface FieldValueInfo {
  fieldId: number;
  fieldKey: string;
  fieldName: string;
  fieldType: string;
  fieldValue?: string | string[];
  fieldValueLabel?: string;
}

export interface AppUserTag {
  id: number;
  name: string;
  color: string;
}

export interface AppUserQuery {
  page: number;
  size: number;
  keyword?: string;
  registerSource?: string;
  status?: number;
  tagIds?: string;
  registerStartTime?: string;
  registerEndTime?: string;
  filters?: string; // JSON string of FilterCondition[]
  filterLogic?: 'AND' | 'OR';
}

export interface AppUserForm {
  nickname: string;
  avatar?: string;
  mobile?: string;
  email?: string;
  gender?: number;
  birthday?: string;
}

export interface BatchTagForm {
  userIds: number[];
  tagIds: number[];
}

export interface UserStatusForm {
  status: number;
}

export interface AppUserTagCategory {
  id: number;
  name: string;
  color: string;
  description: string;
  sort: number;
  tagCount: number;
  createTime: string;
}

export interface AppUserTagCategoryForm {
  name: string;
  color?: string;
  description?: string;
  sort?: number;
}

export interface AppUserTagDetail {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  color: string;
  description: string;
  status: number;
  userCount: number;
  createTime: string;
  updateTime: string;
}

export interface AppUserTagForm {
  id?: number;
  categoryId: number;
  name: string;
  color: string;
  description?: string;
  status?: number;
}

export interface AppUserTagQuery {
  page: number;
  size: number;
  name?: string;
  categoryId?: number;
  status?: number;
}

export interface AppUserField {
  id: number;
  fieldName: string;
  fieldKey: string;
  fieldType: 'RADIO' | 'CHECKBOX' | 'TEXT' | 'LINK';
  config: FieldConfig;
  isRequired: number;
  isDefault: number;
  status: number;
  sort: number;
  createTime: string;
}

export interface FieldConfig {
  options?: FieldOption[];
  maxLength?: number;
  pattern?: string;
  patternMessage?: string;
  linkType?: string;
  placeholder?: string;
}

export interface FieldOption {
  label: string;
  value: string;
}

export interface AppUserFieldForm {
  id?: number;
  fieldName: string;
  fieldKey: string;
  fieldType: string;
  config?: FieldConfig;
  isRequired?: number;
  status?: number;
  sort?: number;
}

export interface AppUserFieldQuery {
  page: number;
  size: number;
  name?: string;
  type?: string;
  status?: number;
}

export interface UserFieldValue {
  fieldId: number;
  fieldName: string;
  fieldKey: string;
  fieldType: string;
  fieldValue: string | string[];
  fieldValueLabel?: string;
}

export interface UserFieldValuesForm {
  fieldValues: {
    fieldId: number;
    fieldValue: string | string[];
  }[];
}

export const REGISTER_SOURCES = [
  { label: 'APP', value: 'APP' },
  { label: 'H5', value: 'H5' },
  { label: '小程序', value: 'MINIAPP' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: 'QQ', value: 'QQ' },
  { label: '微博', value: 'WEIBO' },
];

export const USER_STATUS = [
  { label: '正常', value: 1 },
  { label: '禁用', value: 0 },
  { label: '注销', value: 2 },
];

export const TAG_COLORS = [
  { label: '蓝色', value: 'blue' },
  { label: '绿色', value: 'green' },
  { label: '橙色', value: 'orange' },
  { label: '红色', value: 'red' },
  { label: '紫色', value: 'purple' },
  { label: '青色', value: 'cyan' },
  { label: '洋红', value: 'magenta' },
  { label: '火山红', value: 'volcano' },
  { label: '金色', value: 'gold' },
  { label: '青柠', value: 'lime' },
  { label: '极客蓝', value: 'geekblue' },
  { label: '灰色', value: 'default' },
];

export const FIELD_TYPES = [
  { label: '单选', value: 'RADIO' },
  { label: '多选', value: 'CHECKBOX' },
  { label: '文本', value: 'TEXT' },
  { label: '链接', value: 'LINK' },
  { label: '日期', value: 'DATE' },
];
