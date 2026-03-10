import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import {
  Table,
  Button,
  Space,
  message,
  Drawer,
  Tag,
  Tooltip,
  Popconfirm,
  Pagination,
  Checkbox,
  Modal,
} from 'antd';
import type { ColumnsType, ColumnType } from 'antd/es/table';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getAppUserList,
  updateAppUserStatus,
  batchAddAppUserTags,
  batchRemoveAppUserTags,
  exportAppUsers,
  getEnabledAppFields,
} from '@/api/app-user';
import { USER_STATUS, REGISTER_SOURCES } from '@/types/app-user';
import type { AppUserQuery, AppUser, AppUserField } from '@/types/app-user';
import dayjs from 'dayjs';
import { TagsOutlined, DeleteOutlined } from '@ant-design/icons';
import { DndContext, PointerSensor, useSensor, useSensors, DragOverlay, type DragEndEvent, type DragOverEvent } from '@dnd-kit/core';
import { SortableContext, horizontalListSortingStrategy, useSortable, arrayMove } from '@dnd-kit/sortable';
import UserDetail from './components/UserDetail';
import BatchTagModal from './components/BatchTagModal';
import ImportModal from './components/ImportModal';
import ViewBar from './components/ViewBar';
import ActionBar from './components/ActionBar';
import { useUserManagementStore } from '@/store/userManagementStore';
import { mapFiltersToQuery } from './utils/filter-utils';
import styles from './index.module.css';

const REGISTER_SOURCE_MAP: Record<string, { text: string; color: string }> = {
  APP: { text: 'APP', color: 'blue' },
  H5: { text: 'H5', color: 'cyan' },
  MINIAPP: { text: '小程序', color: 'green' },
  WECHAT: { text: '微信', color: 'success' },
  ALIPAY: { text: '支付宝', color: 'blue' },
  QQ: { text: 'QQ', color: 'purple' },
  WEIBO: { text: '微博', color: 'orange' },
};

const STATUS_MAP: Record<number, { text: string; color: string }> = {
  1: { text: '正常', color: 'success' },
  0: { text: '禁用', color: 'error' },
  2: { text: '注销', color: 'default' },
};

const GENDER_MAP: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };

// 目前仅固定右侧“操作”列；左侧固定列由“当前第一列”动态决定
const PINNED_RIGHT_KEYS = ['action'];

type InsertIndicator = {
  overId: string | null;
  activeId: string | null;
  position: 'left' | 'right' | null;
  x: number | null;
};

const DraggableHeaderCell: React.FC<any> = (props) => {
  const { children, id, indicator, draggable, ...restProps } = props;
  const { attributes, listeners, setNodeRef, isDragging } = useSortable({
    id,
    disabled: !draggable,
  });

  // 不再对表头 th 做位移动画，只稍微调整透明度，真正的“移动”交由 DragOverlay 实现
  const style: React.CSSProperties = {
    ...restProps.style,
    opacity: isDragging ? 0.9 : 1,
  };

  const showIndicator = draggable && indicator?.overId === id && indicator?.activeId && indicator.activeId !== id;
  const indicatorPositionStyle: React.CSSProperties =
    indicator?.position === 'right'
      ? { right: 0, left: 'auto' }
      : { left: 0 };

  return (
    <th
      ref={setNodeRef}
      {...restProps}
      style={style}
      className={[restProps.className, styles.draggableTh].filter(Boolean).join(' ')}
    >
      {showIndicator && <div className={styles.dropIndicator} style={indicatorPositionStyle} />}
      <div className={draggable ? styles.headerCellDraggable : styles.headerCell}>
        <div className={styles.headerTitle}>{children}</div>
        {draggable && (
          <div className={styles.dragHandle} {...attributes} {...listeners} onClick={(e) => e.stopPropagation()}>
            ⋮⋮
          </div>
        )}
      </div>
    </th>
  );
};

/** 列表邮箱脱敏，如 0****@examp... */
function maskEmail(email: string | undefined): string {
  if (!email || typeof email !== 'string') return '-';
  const at = email.indexOf('@');
  if (at <= 0) return email;
  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  const maskedLocal = local.length > 0 ? local[0] + '****' : '****';
  const maskedDomain = domain.length > 5 ? domain.slice(0, 5) + '...' : domain;
  return `${maskedLocal}@${maskedDomain}`;
}

const UserList: React.FC = () => {
  const queryClient = useQueryClient();
  const { setFieldDefinitions, hiddenFields, filters, filterLogic, columnOrder, currentViewId, views, updateView, setColumnOrder } = useUserManagementStore();
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [batchTagVisible, setBatchTagVisible] = useState(false);
  const [batchTagMode, setBatchTagMode] = useState<'add' | 'remove'>('add');
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [importVisible, setImportVisible] = useState(false);

  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [dragIndicator, setDragIndicator] = useState<InsertIndicator>({ overId: null, activeId: null, position: null, x: null });
  const [activeDragId, setActiveDragId] = useState<string | null>(null);
  const tableWrapperRef = useRef<HTMLDivElement | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 6 },
    }),
  );
  const searchParams: AppUserQuery = useMemo(() => {
    const filterParams = mapFiltersToQuery(filters, filterLogic);
    return {
      page: currentPage,
      size: pageSize,
      ...filterParams,
    };
  }, [filters, filterLogic, currentPage, pageSize]);

  const { data: userData } = useQuery({
    queryKey: ['app-users', searchParams],
    queryFn: () => getAppUserList(searchParams),
  });

  const { data: fieldData } = useQuery({
    queryKey: ['app-fields-enabled'],
    queryFn: () => getEnabledAppFields(),
  });

  useEffect(() => {
    if (fieldData) {
      // Convert AppUserField to FieldDefinition and inject default options
      const convertedFields = fieldData.map((field: AppUserField) => {
        let configObj = field.config || {};
        if (typeof configObj === 'string') {
          try {
            configObj = JSON.parse(configObj);
          } catch (e) {
            console.error('Failed to parse field config:', e);
            configObj = {};
          }
        }

        // Backend default fields logic to patch enum options into their config
        if (field.fieldKey === 'status') {
          configObj = { ...configObj, options: USER_STATUS.map(s => ({ label: s.label, value: String(s.value) })) };
        } else if (field.fieldKey === 'gender') {
          configObj = {
            ...configObj,
            options: [
              { label: '未知', value: '0' },
              { label: '男', value: '1' },
              { label: '女', value: '2' },
            ],
          };
        } else if (field.fieldKey === 'register_source' || field.fieldKey === 'source') {
          configObj = { ...configObj, options: REGISTER_SOURCES };
        }

        return {
          ...field,
          config: configObj,
          isDefault: field.isDefault === 1,
        };
      });
      setFieldDefinitions(convertedFields);
    }
  }, [fieldData, setFieldDefinitions]);

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: number }) =>
      updateAppUserStatus(id, status),
    onSuccess: () => {
      message.success('状态更新成功');
      queryClient.invalidateQueries({ queryKey: ['app-users'] });
    },
  });

  const batchTagMutation = useMutation({
    mutationFn: ({
      userIds,
      tagIds,
      mode,
    }: {
      userIds: number[];
      tagIds: number[];
      mode: 'add' | 'remove';
    }) =>
      mode === 'add'
        ? batchAddAppUserTags(userIds, tagIds)
        : batchRemoveAppUserTags(userIds, tagIds),
    onSuccess: () => {
      message.success(batchTagMode === 'add' ? '批量打标签成功' : '批量移除标签成功');
      setBatchTagVisible(false);
      setSelectedRowKeys([]);
      queryClient.invalidateQueries({ queryKey: ['app-users'] });
    },
  });

  const handleExport = useCallback(() => {
    Modal.confirm({
      title: '确认导出',
      content: '确定导出当前筛选条件下的用户列表吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: () => {
        exportAppUsers(searchParams).then((blob) => {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `用户列表_${dayjs().format('YYYYMMDDHHmmss')}.xlsx`;
          a.click();
          window.URL.revokeObjectURL(url);
          message.success('导出成功');
        });
      },
    });
  }, [searchParams]);

  const handleStatusChange = useCallback((id: number, status: number) => {
    statusMutation.mutate({ id, status });
  }, [statusMutation]);

  const handleViewDetail = useCallback((id: number) => {
    setCurrentUserId(id);
    setDetailVisible(true);
  }, []);

  const handleBatchTag = useCallback((mode: 'add' | 'remove') => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择用户');
      return;
    }
    setBatchTagMode(mode);
    setBatchTagVisible(true);
  }, [selectedRowKeys]);

  const handleBatchTagSubmit = useCallback((tagIds: number[]) => {
    batchTagMutation.mutate({
      userIds: selectedRowKeys,
      tagIds,
      mode: batchTagMode,
    });
  }, [batchTagMutation, selectedRowKeys, batchTagMode]);



  const handleSelectAll = useCallback((checked: boolean) => {
    if (checked) {
      // Select all users in current page
      const allIds = userData?.records.map((user: AppUser) => user.id) || [];
      setSelectedRowKeys(allIds);
    } else {
      setSelectedRowKeys([]);
    }
  }, [userData]);

  // 仅对“中间业务列”应用顺序；固定列在调用处单独拼接
  const applyColumnOrder = useCallback((centerCols: ColumnType<AppUser>[], order: string[]) => {
    if (!order || order.length === 0) return centerCols;
    const map = new Map<string, ColumnType<AppUser>>();
    centerCols.forEach((c) => {
      const key = String((c as any).key ?? (c as any).dataIndex ?? '');
      if (key) map.set(key, c);
    });
    const ordered = order.map((k) => map.get(k)).filter(Boolean) as ColumnType<AppUser>[];
    const rest = centerCols.filter((c) => {
      const key = String((c as any).key ?? (c as any).dataIndex ?? '');
      return !key || !order.includes(key);
    });
    return [...ordered, ...rest];
  }, []);

  const columns = useMemo(() => {
    const userColumn = {
      title: '用户',
      key: 'user',
      width: 140,
      render: (_: unknown, record: AppUser) => {
        const vipTag = record.tags?.find((t) => t.name && t.name.toUpperCase().includes('VIP'));
        return (
          <Space size={4} wrap>
            <span onClick={() => handleViewDetail(record.id)} style={{ fontWeight: 500, cursor: 'pointer' }}>
              {record.nickname || '-'}
            </span>
            {vipTag && <Tag color={vipTag.color || 'default'}>{vipTag.name}</Tag>}
          </Space>
        );
      },
    };

    const fieldColumns = (fieldData || [])
      .filter((field: AppUserField) => 
        field.fieldKey !== 'nickname' && 
        field.fieldKey !== 'avatar' &&
        !hiddenFields.includes(field.fieldKey)
      )
      .sort((a: AppUserField, b: AppUserField) => a.sort - b.sort)
      .map((field: AppUserField) => {
        if (field.isDefault === 1) {
          switch (field.fieldKey) {
            case 'mobile':
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => record.mobile || '-',
              };
            case 'email':
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => maskEmail(record.email),
              };
            case 'gender':
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => GENDER_MAP[record.gender] || '-',
              };
            case 'birthday':
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => record.birthday || '-',
              };
            case 'register_source':
              return {
                title: '注册来源',
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => {
                  const { text } = REGISTER_SOURCE_MAP[record.registerSource] || { text: record.registerSource };
                  return <Tag>{text}</Tag>;
                },
              };
            case 'register_time':
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => record.registerTime ? dayjs(record.registerTime).format('YYYY-MM-DD HH:mm') : '-',
              };
            case 'last_login_time':
              return {
                title: '最后登录',
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => record.lastLoginTime ? dayjs(record.lastLoginTime).format('YYYY-MM-DD HH:mm') : '-',
              };
            case 'last_login_ip':
              return {
                title: '最后登录IP',
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => record.lastLoginIp || '-',
              };
            case 'status':
              return {
                title: '状态',
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => {
                  const { text, color } = STATUS_MAP[record.status] || { text: '未知', color: 'default' };
                  return <Tag color={color}>{text}</Tag>;
                },
              };
            default:
              return {
                title: field.fieldName,
                key: `field:${field.fieldKey}`,
                width: 130,
                ellipsis: true,
                render: (_: unknown, record: AppUser) => {
                  const fieldValue = record.fieldValues?.find((fv) => fv.fieldKey === field.fieldKey);
                  if (!fieldValue) return '-';
                  return fieldValue.fieldValue || '-';
                },
              };
          }
        }
        
        return {
          title: field.fieldName,
          key: `field:${field.fieldKey}`,
          width: 130,
          ellipsis: true,
          render: (_: unknown, record: AppUser) => {
            const fieldValue = record.fieldValues?.find((fv) => fv.fieldKey === field.fieldKey);
            if (!fieldValue) return '-';
            
            switch (field.fieldType) {
              case 'CHECKBOX':
                return Array.isArray(fieldValue.fieldValue) 
                  ? fieldValue.fieldValueLabel || fieldValue.fieldValue.join(',')
                  : fieldValue.fieldValue || '-';
              case 'RADIO':
                return fieldValue.fieldValueLabel || fieldValue.fieldValue || '-';
              case 'LINK':
                return fieldValue.fieldValue ? (
                  <a href={fieldValue.fieldValue as string} target="_blank" rel="noopener noreferrer">
                    查看
                  </a>
                ) : '-';
              default:
                return fieldValue.fieldValue || '-';
            }
          },
        };
      });

    const tagColumn = {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 130,
      ellipsis: true,
      render: (_: unknown, record: AppUser) => {
        const tagList = record.tags || [];
        const displayTags = tagList.slice(0, 2);
        const remainingCount = tagList.length - 2;

        return (
          <Space wrap size={4}>
            {displayTags.map((tag) => (
              <Tag key={tag.id} color={tag.color} style={{ margin: 0 }}>
                {tag.name}
              </Tag>
            ))}
            {remainingCount > 0 && (
              <Tooltip
                title={
                  <Space direction="vertical" size={4}>
                    {tagList.slice(2).map((tag) => (
                      <Tag key={tag.id} color={tag.color} style={{ margin: 0 }}>
                        {tag.name}
                      </Tag>
                    ))}
                  </Space>
                }
              >
                <Tag style={{ margin: 0, cursor: 'pointer' }}>+{remainingCount}</Tag>
              </Tooltip>
            )}
            {tagList.length === 0 && <span style={{ color: '#999' }}>-</span>}
          </Space>
        );
      },
    };

    const actionColumn = {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: unknown, record: AppUser) => (
        <Space size={4}>
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => handleViewDetail(record.id)}>
            详情
          </Button>
          <Popconfirm
            title={record.status === 1 ? '确定禁用该用户吗？' : '确定启用该用户吗？'}
            onConfirm={() => handleStatusChange(record.id, record.status === 1 ? 0 : 1)}
          >
            <Button type="link" size="small" danger style={{ padding: 0 }}>
              {record.status === 1 ? '禁用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    };

    const allCols = [userColumn, ...fieldColumns, tagColumn, actionColumn] as ColumnType<AppUser>[];

    const pinnedRight = allCols.filter((c) =>
      PINNED_RIGHT_KEYS.includes(String((c as any).key ?? '')),
    );
    const nonRight = allCols.filter((c) => {
      const key = String((c as any).key ?? '');
      return !PINNED_RIGHT_KEYS.includes(key);
    });

    const orderedNonRight = applyColumnOrder(nonRight, columnOrder);

    // 动态固定“当前第一列”为左侧固定列
    const finalCols: ColumnType<AppUser>[] = orderedNonRight.map((col, index) =>
      index === 0
        ? ({ ...col, fixed: 'left' } as ColumnType<AppUser>)
        : col,
    );

    // 右侧固定列保持 fixed:'right'
    return [...finalCols, ...pinnedRight];
  }, [applyColumnOrder, columnOrder, fieldData, hiddenFields, handleStatusChange, handleViewDetail]);

  const filteredUsers = userData?.records || [];

  const draggableColumnIds = useMemo(() => {
    const cols = (columns as ColumnType<AppUser>[]);
    if (cols.length === 0) return [];
    // 仅排除右侧 pinned 列，其它业务列（包括当前第一列）都可以拖拽；
    // 拖拽后由 columns useMemo 决定新的“第一列固定左侧”
    return cols
      .map((c) => String((c as any).key ?? ''))
      .filter((colKey) => !!colKey && !PINNED_RIGHT_KEYS.includes(colKey));
  }, [columns]);

  const enhancedColumns: ColumnsType<AppUser> = useMemo(() => {
    return (columns as ColumnType<AppUser>[]).map((col) => {
      const key = String((col as any).key ?? '');
      const draggable = !!key && draggableColumnIds.includes(key);
      return {
        ...col,
        onHeaderCell: () => ({
          id: key,
          draggable,
          indicator: dragIndicator,
        }),
        onCell: () => ({
          className: key === activeDragId ? styles.draggingColumn : undefined,
        }),
      } as any;
    });
  }, [activeDragId, columns, dragIndicator, draggableColumnIds]);

  const activeDragTitle = useMemo(() => {
    if (!activeDragId) return null;
    const col = (columns as ColumnType<AppUser>[]).find((c) => String((c as any).key ?? '') === activeDragId);
    return col?.title ?? null;
  }, [activeDragId, columns]);

  const persistColumnOrder = useCallback(async (nextOrder: string[]) => {
    setColumnOrder(nextOrder);
    if (!currentViewId) return;
    const current = views.find(v => v.id === currentViewId);
    const prevCfg = current?.viewConfig || {};
    try {
      await updateView(currentViewId, { viewConfig: { ...prevCfg, columnOrder: nextOrder } } as any);
    } catch (e) {
      // 失败时不阻断 UI；下次 fetchViews 可回滚
    }
  }, [currentViewId, setColumnOrder, updateView, views]);

  const handleDragOver = useCallback((event: DragOverEvent) => {
    const overId = event.over?.id ? String(event.over.id) : null;
    const activeId = event.active?.id ? String(event.active.id) : null;
    if (!overId || !activeId) {
      setDragIndicator({ overId: null, activeId: null, position: null, x: null });
      return;
    }

    // 使用宽高/left 来判断插入方向，但为了兼容 dnd-kit 的类型定义，这里做一次宽松断言
    const overRect = (event.over?.rect as any) as { left: number; width: number } | null;
    const activeRectRaw = (event.active.rect.current as any) as {
      left?: number;
      width?: number;
    } | {
      translated?: { left: number; width: number };
    } | null;

    const activeRectTranslated = (activeRectRaw && (activeRectRaw as any).translated)
      ? (activeRectRaw as any).translated as { left: number; width: number }
      : (activeRectRaw as any as { left: number; width: number } | null);

    let position: 'left' | 'right' = 'left';
    let lineX: number | null = null;
    const wrapperRect = tableWrapperRef.current?.getBoundingClientRect();
    if (wrapperRect && overRect && activeRectTranslated && typeof overRect.left === 'number' && typeof overRect.width === 'number') {
      const centerX = overRect.left + overRect.width / 2;
      const pointerX = activeRectTranslated.left + (activeRectTranslated.width || 0) / 2;
      position = pointerX > centerX ? 'right' : 'left';
      const rawX = position === 'right' ? overRect.left + overRect.width : overRect.left;
      // 转换为相对于表格容器左侧的坐标，避免蓝线跑到容器外
      lineX = rawX - wrapperRect.left;
      // 边界保护：限制在 [0, wrapperRect.width]
      if (lineX < 0) lineX = 0;
      if (lineX > wrapperRect.width) lineX = wrapperRect.width;
    }

    setDragIndicator({ overId, activeId, position, x: lineX });
  }, []);

  const handleDragEnd = useCallback(async (event: DragEndEvent) => {
    const { active, over } = event;
    setDragIndicator({ overId: null, activeId: null, position: null, x: null });
    setActiveDragId(null);
    if (!over) return;
    const activeId = String(active.id);
    const overId = String(over.id);
    if (activeId === overId) return;
    const oldIndex = draggableColumnIds.indexOf(activeId);
    const overIndex = draggableColumnIds.indexOf(overId);
    if (oldIndex < 0 || overIndex < 0) return;

    let newIndex = overIndex;
    if (dragIndicator.position === 'right') {
      // 插入到目标列之后，注意左移/右移时索引的差异
      newIndex = overIndex > oldIndex ? overIndex : overIndex + 1;
    }

    const next = arrayMove(draggableColumnIds, oldIndex, newIndex);
    await persistColumnOrder(next);
  }, [dragIndicator.position, draggableColumnIds, persistColumnOrder]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden", background: "#fff" }}>
      {/* ── View Bar ── */}
      <ViewBar />

      {/* ── Operation Bar ── */}
      <ActionBar
        onImportClick={() => setImportVisible(true)}
        onExportClick={handleExport}
      />

      {/* ── Table ── */}
      <div className={styles.tableWrapper} ref={tableWrapperRef}>
        <DndContext
          sensors={sensors}
          onDragStart={(e) => setActiveDragId(String(e.active.id))}
          onDragOver={handleDragOver}
          onDragCancel={() => { setDragIndicator({ overId: null, activeId: null, position: null, x: null }); setActiveDragId(null); }}
          onDragEnd={handleDragEnd}
        >
          <SortableContext items={draggableColumnIds} strategy={horizontalListSortingStrategy}>
            {dragIndicator.x != null && (
              <div
                className={styles.globalDropLine}
                style={{ left: dragIndicator.x }}
              />
            )}
            <Table
              dataSource={filteredUsers}
              columns={enhancedColumns}
              rowKey="id"
              size="small"
              // scroll.y 只限制 tbody 的 max-height；表格总高 = 表头 + scroll.y，须 ≤ tableWrapper（100vh - 196）
              // 故 scroll.y ≤ 100vh - 196 - 48(表头) = 100vh - 244
              scroll={{ x: 'max-content', y: 'calc(100vh - 244px)' }}
              sticky
              components={{
                header: {
                  cell: DraggableHeaderCell as any,
                },
              }}
              rowSelection={{
                type: "checkbox",
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys as number[]),
                fixed: true,
              }}
              pagination={false}
            />
          </SortableContext>
          <DragOverlay>
            {activeDragTitle ? (
              <div className={styles.dragOverlay}>{String(activeDragTitle)}</div>
            ) : null}
          </DragOverlay>
        </DndContext>
      </div>

      {/* ── Bottom Bar ── */}
      <div style={{ height: 48, borderTop: "1px solid #f0f0f0", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 16px", flexShrink: 0, background: "#fff" }}>
        <Space>
          <Checkbox
            checked={selectedRowKeys.length === filteredUsers.length && filteredUsers.length > 0}
            indeterminate={selectedRowKeys.length > 0 && selectedRowKeys.length < filteredUsers.length}
            onChange={(e) => handleSelectAll(e.target.checked)}
          />
          <Button size="small" disabled={selectedRowKeys.length === 0} icon={<TagsOutlined />} onClick={() => handleBatchTag('add')}>
            打标签
          </Button>
          <Button size="small" disabled={selectedRowKeys.length === 0} icon={<DeleteOutlined />} onClick={() => handleBatchTag('remove')}>
            移除标签
          </Button>
          {selectedRowKeys.length > 0 && (
            <span style={{ color: "#666", fontSize: 12 }}>已选 {selectedRowKeys.length} 条</span>
          )}
        </Space>

        <div style={{ marginLeft: "auto" }}>
          <Pagination
            current={currentPage}
            pageSize={pageSize}
            total={userData?.total}
            size="small"
            showTotal={(total) => `共 ${total} 条`}
            showSizeChanger
            pageSizeOptions={[20, 50, 100]}
            onChange={(page, size) => {
              // 切换页码或每页数量时，立即更新本地分页状态，
              // 并在修改 pageSize 时重置到第 1 页，确保查询生效。
              if (size && size !== pageSize) {
                setPageSize(size);
                setCurrentPage(1);
              } else {
                setCurrentPage(page);
              }
            }}
          />
        </div>
      </div>

      <Drawer
        title="用户详情"
        open={detailVisible}
        onClose={() => setDetailVisible(false)}
        width={600}
        destroyOnClose
      >
        {currentUserId && <UserDetail userId={currentUserId} />}
      </Drawer>

      <BatchTagModal
        visible={batchTagVisible}
        mode={batchTagMode}
        onConfirm={handleBatchTagSubmit}
        onCancel={() => setBatchTagVisible(false)}
        loading={batchTagMutation.isPending}
      />

      <ImportModal
        visible={importVisible}
        onCancel={() => setImportVisible(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['app-users'] });
        }}
      />
    </div>
  );
};

export default UserList;
