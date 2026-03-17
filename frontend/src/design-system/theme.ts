import type { ThemeConfig } from 'antd';

// 供组件内 useToken 或直接引用时的颜色常量（与 token 对应，禁止在业务代码中写死新颜色）
export const designTokens = {
  colorBgContainer: '#FFFFFF',
  colorBorder: '#F0F0F0',
  colorFillTertiary: '#FAFAFA',
  colorFillQuaternary: '#F5F5F5',
  colorTextTertiary: '#8C8C8C',
  colorTextQuaternary: '#BFBFBF',
  colorPrimaryBg: '#E6F4FF',
  colorError: '#DC2626',
  colorSuccess: '#16A34A',
  colorWarning: '#EA580C',
  // Tag 颜色映射（Ant Design 颜色名 -> 规范色板）
  tagColorMap: {
    blue: '#2563EB',
    green: '#16A34A',
    orange: '#EA580C',
    red: '#DC2626',
    purple: '#7C3AED',
    cyan: '#0891B2',
    magenta: '#DB2777',
    volcano: '#EA580C',
    gold: '#EAB308',
    lime: '#65A30D',
    geekblue: '#2563EB',
    default: '#D9D9D9',
  } as Record<string, string>,
  // 权限节点类型配置（DIR/MENU/BTN）
  permissionTypeMap: {
    DIR: { color: 'blue', label: '目录' },
    MENU: { color: 'green', label: '菜单' },
    BTN: { color: 'orange', label: '按钮' },
  } as Record<string, { color: string; label: string }>,
  // 操作日志状态配置（SUCCESS/FAIL）
  logStatusMap: {
    SUCCESS: { color: 'success', label: '成功' },
    FAIL: { color: 'error', label: '失败' },
  } as Record<string, { color: string; label: string }>,
};

// 全局 Ant Design 主题配置
// 以后如需调整配色/圆角/字号，优先改这里
export const appTheme: ThemeConfig = {
  token: {
    // 背景与容器（基于 ui-ux-pro-max MASTER 配色）
    colorBgBase: '#F8FAFC',
    colorBgContainer: '#FFFFFF',

    // 边框与填充
    colorBorder: designTokens.colorBorder,
    colorFillTertiary: designTokens.colorFillTertiary,
    colorTextTertiary: designTokens.colorTextTertiary,
    colorPrimaryBg: designTokens.colorPrimaryBg,

    // 品牌与状态色（更鲜活的科技蓝）
    colorPrimary: '#2563EB', // 更高饱和度的蓝色
    colorSuccess: designTokens.colorSuccess,
    colorWarning: '#EAB308',
    colorError: designTokens.colorError,

    // 文本
    colorText: '#020617',
    colorTextSecondary: '#334155',

    // 形状与阴影 / 圆角
    borderRadius: 8,
    boxShadowSecondary: '0 4px 6px rgba(15, 23, 42, 0.12)',

    // 排版
    fontFamily:
      "'Plus Jakarta Sans', system-ui, -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif",
  },
  components: {
    Layout: {
      headerHeight: 64,
    },
    Card: {
      borderRadius: 15,
      padding: 16,
    },
    Button: {
      controlHeight: 32,
      borderRadius: 8,
    },
    Table: {
      cellPaddingBlock: 8,
      cellPaddingInline: 12,
    },
  },
};

