import { create } from 'zustand';
import { userViewApi } from '@/api/user-view';

// Types
export interface FilterCondition {
  id: string;
  field: string;
  operator: string;
  value: any;
  type: 'string' | 'enum' | 'date';
}

export interface ViewConfig {
  id: string;
  name: string;
  filters: FilterCondition[];
  hiddenFields: string[];
  filterLogic: 'AND' | 'OR';
  isDefault?: boolean;
  orderNo?: number;
  viewConfig?: {
    columnOrder?: string[];
    columnWidths?: Record<string, number>;
  };
}

export interface FieldDefinition {
  fieldKey: string;
  fieldName: string;
  fieldType: 'TEXT' | 'RADIO' | 'CHECKBOX' | 'DATE' | 'NUMBER' | 'LINK';
  isDefault: boolean;
  config?: any;
}

interface UserManagementState {
  // View State
  views: ViewConfig[];
  currentViewId: string | null;
  viewLoading: boolean; // Added loading state
  columnOrder: string[];
  setCurrentView: (viewId: string) => void;
  fetchViews: () => Promise<void>;
  createView: (name: string, payload?: {
    filters?: FilterCondition[];
    hiddenFields?: string[];
    filterLogic?: 'AND' | 'OR';
    viewConfig?: ViewConfig['viewConfig'];
  }) => Promise<void>;
  updateView: (id: string, updates: Partial<ViewConfig>) => Promise<void>;
  deleteView: (id: string) => Promise<void>;
  reorderViews: (nextViews: ViewConfig[]) => Promise<void>;
  
  // Filter State
  filterLogic: 'AND' | 'OR';
  filters: FilterCondition[];
  setFilterLogic: (logic: 'AND' | 'OR') => void;
  addFilter: (condition: FilterCondition) => void;
  removeFilter: (conditionId: string) => void;
  updateFilter: (id: string, updates: Partial<FilterCondition>) => void;
  clearFilters: () => void;
  
  // Field Visibility State
  fieldDefinitions: FieldDefinition[];
  hiddenFields: string[];
  setFieldDefinitions: (fields: FieldDefinition[]) => void;
  toggleField: (fieldKey: string) => void;
  setHiddenFields: (fieldKeys: string[]) => void;
  setColumnOrder: (keys: string[]) => void;
  
  // Layout State
  batchBarVisible: boolean;
  setBatchBarVisible: (visible: boolean) => void;
}

export const useUserManagementStore = create<UserManagementState>((set, get) => ({
  // View State
  views: [],
  currentViewId: null,
  viewLoading: false,
  columnOrder: [],
  setCurrentView: (viewId) => {
    const state = get();
    const view = state.views.find(v => v.id === viewId);
    if (view) {
      set({
        currentViewId: viewId,
        filters: view.filters || [],
        hiddenFields: view.hiddenFields || [],
        filterLogic: view.filterLogic || 'AND',
        columnOrder: view.viewConfig?.columnOrder || [],
      });
    } else {
      // If switching to "Default View" (null or empty id), reset
      set({
        currentViewId: null,
        filters: [],
        hiddenFields: [],
        filterLogic: 'AND',
        columnOrder: [],
      });
    }
  },
  fetchViews: async () => {
    try {
      const res = await userViewApi.getViews();
      // 兼容：拦截器可能返回 data 或直接返回数组；部分网关可能返回 { data: [] }
      let list: ViewConfig[] = [];
      if (Array.isArray(res)) {
        list = res;
      } else if (res && typeof res === 'object' && Array.isArray((res as any).data)) {
        list = (res as any).data;
      } else if (res && typeof res === 'object' && Array.isArray((res as any).list)) {
        list = (res as any).list;
      }
      // 规范化并按序号排序（后端返回 orderNo）
      const normalized = list
        .map((v) => ({
          ...v,
          orderNo: typeof (v as any).orderNo === 'number' ? (v as any).orderNo : undefined,
        }))
        .sort((a, b) => {
          const ao = a.orderNo ?? Number.MAX_SAFE_INTEGER;
          const bo = b.orderNo ?? Number.MAX_SAFE_INTEGER;
          return ao - bo;
        });

      // 合并为一次原子更新，减少 store 订阅者重渲染次数
      set({ views: normalized, viewLoading: false });
    } catch (error) {
      console.error('Failed to fetch views:', error);
      set({ views: [], viewLoading: false });
    }
  },
  createView: async (name, payload) => {
    const state = get();
    if (state.views.length >= 20) {
      throw new Error('视图数量已达上限，最多创建 20 个视图');
    }
    set({ viewLoading: true });
    try {
      const effectiveFilters = payload?.filters ?? state.filters;
      const effectiveHidden = payload?.hiddenFields ?? state.hiddenFields;
      const effectiveLogic = payload?.filterLogic ?? state.filterLogic;
      const effectiveViewConfig = payload?.viewConfig ?? { columnOrder: state.columnOrder };
      const newView = await userViewApi.createView({
        name,
        filters: effectiveFilters,
        hiddenFields: effectiveHidden,
        filterLogic: effectiveLogic,
        viewConfig: effectiveViewConfig,
      });
      // 归一化：可能为 res.data 或直接为视图对象；保证 id/name 为字符串
      const raw = (newView && typeof newView === 'object' && 'id' in newView)
        ? newView
        : (newView as any)?.data ?? (newView as any)?.view;
      if (!raw) {
        console.warn('createView: unexpected response shape', newView);
        return;
      }
      const currentViews = state.views;
      const maxOrder =
        currentViews.length > 0
          ? Math.max(...currentViews.map((v) => v.orderNo || 0))
          : 0;
      const viewData: ViewConfig = {
        id: String(raw.id ?? ''),
        name: String(raw.name ?? name ?? '新视图'),
        filters: Array.isArray(raw.filters) ? raw.filters : [],
        hiddenFields: Array.isArray(raw.hiddenFields) ? raw.hiddenFields : [],
        filterLogic: raw.filterLogic === 'OR' ? 'OR' : 'AND',
        isDefault: raw.isDefault ?? false,
        orderNo: typeof raw.orderNo === 'number' ? raw.orderNo : maxOrder + 1,
        viewConfig: (raw as any).viewConfig,
      };
      if (viewData.id) {
        set((s) => ({
          views: [...s.views, viewData].sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0)),
          currentViewId: viewData.id,
        }));
      }
    } catch (error) {
      console.error('Failed to create view:', error);
      throw error;
    } finally {
      set({ viewLoading: false });
    }
  },
  updateView: async (id, updates) => {
    set({ viewLoading: true });
    try {
      await userViewApi.updateView(id, updates);
      set((state) => ({
        views: state.views.map(v => v.id === id ? { ...v, ...updates } : v),
      }));
    } catch (error) {
      console.error('Failed to update view:', error);
      throw error;
    } finally {
      set({ viewLoading: false });
    }
  },
  deleteView: async (id) => {
    set({ viewLoading: true });
    try {
      await userViewApi.deleteView(id);
      set((state) => {
        const newViews = state.views
          .filter(v => v.id !== id)
          .map((v, index) => ({ ...v, orderNo: index + 1 }));

        // If deleted current view, switch to default (null)
        const newCurrentId = state.currentViewId === id ? null : state.currentViewId;
        
        // If switching to default, reset filters/fields
        if (newCurrentId === null && state.currentViewId === id) {
           return {
             views: newViews,
             currentViewId: null,
             filters: [],
             hiddenFields: [],
             filterLogic: 'AND',
           };
        }
        
        return {
          views: newViews,
          currentViewId: newCurrentId,
        };
      });
    } catch (error) {
      console.error('Failed to delete view:', error);
      throw error;
    } finally {
      set({ viewLoading: false });
    }
  },
  reorderViews: async (nextViews) => {
    const withOrder: ViewConfig[] = nextViews.map((v, index) => ({
      ...v,
      orderNo: index + 1,
    }));
    set({ views: withOrder });
    try {
      await Promise.all(
        withOrder.map((v) =>
          userViewApi.updateView(v.id, { orderNo: v.orderNo } as any),
        ),
      );
    } catch (error) {
      console.error('Failed to reorder views:', error);
    }
  },
  
  // Filter State
  filterLogic: 'AND',
  filters: [],
  setFilterLogic: (logic) => set({ filterLogic: logic }),
  addFilter: (condition) => set((state) => ({ 
    filters: [...state.filters, condition] 
  })),
  removeFilter: (id) => set((state) => ({ 
    filters: state.filters.filter(f => f.id !== id) 
  })),
  updateFilter: (id, updates) => set((state) => ({ 
    filters: state.filters.map(f => 
      f.id === id ? { ...f, ...updates } : f 
    ) 
  })),
  clearFilters: () => set({ filters: [], filterLogic: 'AND' }),
  
  // Field Visibility State
  fieldDefinitions: [],
  hiddenFields: [],
  setFieldDefinitions: (fields) => set({ fieldDefinitions: fields }),
  toggleField: (fieldKey) => set((state) => ({
    hiddenFields: state.hiddenFields.includes(fieldKey)
      ? state.hiddenFields.filter(k => k !== fieldKey)
      : [...state.hiddenFields, fieldKey]
  })),
  setHiddenFields: (fieldKeys) => set({ hiddenFields: fieldKeys }),
  setColumnOrder: (keys) => set({ columnOrder: keys }),
  
  // Layout State
  batchBarVisible: false,
  setBatchBarVisible: (visible) => set({ batchBarVisible: visible }),
}));
