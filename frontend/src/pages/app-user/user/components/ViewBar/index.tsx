import React, { useState, useEffect, useRef } from 'react';
import { Button, Dropdown, message, Tooltip, Modal } from 'antd';
import { LeftOutlined, RightOutlined, DownOutlined, PlusOutlined, MoreOutlined, DeleteOutlined, AppstoreOutlined } from '@ant-design/icons';
import { useUserManagementStore } from '@/store/userManagementStore';


const ViewBar: React.FC = () => {
  const { 
    views, 
    currentViewId, 
    viewLoading,
    setCurrentView, 
    fetchViews, 
    createView, 
    updateView, 
    deleteView,
    reorderViews,
  } = useUserManagementStore();
  
  const [editingViewId, setEditingViewId] = useState<string | null>(null);
  const [editingViewName, setEditingViewName] = useState('');
  const [showAllViewsDropdown, setShowAllViewsDropdown] = useState(false);
  const viewScrollRef = useRef<HTMLDivElement>(null);
  const [canScroll, setCanScroll] = useState(false);
  const [draggingId, setDraggingId] = useState<string | null>(null);

  useEffect(() => {
    fetchViews();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Detect view overflow
  useEffect(() => {
    const el = viewScrollRef.current;
    if (!el) return;
    const check = () => setCanScroll(el.scrollWidth > el.clientWidth);
    check();
    const ro = new ResizeObserver(check);
    ro.observe(el);
    return () => ro.disconnect();
  }, [views]);

  const scrollViews = (dir: 'left' | 'right') => {
    viewScrollRef.current?.scrollBy({ left: dir === 'left' ? -120 : 120, behavior: 'smooth' });
  };

  const MAX_VIEWS = 20;
  const isAtLimit = views.length >= MAX_VIEWS;

  const addView = async () => {
    if (isAtLimit) return;
    const newName = `视图${views.length + 1}`;
    try {
      // 创建一个“原始”视图：不继承当前视图的筛选/列配置
      await createView(newName, {
        filters: [],
        hiddenFields: [],
        filterLogic: 'AND',
        viewConfig: {
          columnOrder: [],
        },
      });
      // 不再在此处调用 fetchViews()，避免覆盖 createView 已写入 store 的新视图
      message.success('视图创建成功');
    } catch (error) {
      message.error('创建视图失败');
    }
  };

  const deleteViewHandler = async (id: string) => {
    try {
      await deleteView(id);
      message.success('视图删除成功');
    } catch (error) {
      message.error('删除视图失败');
    }
  };

  const confirmDeleteView = (view: (typeof views)[number]) => {
    if (view.isDefault) {
      message.warning('默认视图不可删除');
      return;
    }
    Modal.confirm({
      title: '删除视图',
      content: `确定要删除视图「${view.name || '未命名视图'}」吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => deleteViewHandler(view.id),
    });
  };

  const duplicateViewHandler = async (viewId: string) => {
    if (isAtLimit) {
      message.warning(`视图数量已达上限（${MAX_VIEWS}个）`);
      return;
    }
    const source = views.find((v) => v.id === viewId);
    if (!source) {
      message.error('当前视图不存在，无法复制');
      return;
    }
    const baseName = source.name || '新视图';
    const newName = `${baseName}_副本`;
    try {
      await createView(newName, {
        filters: source.filters || [],
        hiddenFields: source.hiddenFields || [],
        filterLogic: source.filterLogic || 'AND',
        viewConfig: source.viewConfig,
      });
      message.success('视图复制成功');
    } catch (error) {
      message.error('复制视图失败');
    }
  };

  const saveViewName = async (id: string) => {
    if (!editingViewName.trim()) {
      message.warning('视图名称不能为空');
      return;
    }
    
    const trimmed = editingViewName.trim().slice(0, 20) || '新视图';
    try {
      await updateView(id, { name: trimmed });
      setEditingViewId(null);
      message.success('视图名称更新成功');
    } catch (error) {
      message.error('更新视图名称失败');
    }
  };

  const handleDropOnView = (targetId: string) => {
    if (!draggingId || draggingId === targetId) return;
    const currentIndex = views.findIndex(v => v.id === draggingId);
    const targetIndex = views.findIndex(v => v.id === targetId);
    if (currentIndex === -1 || targetIndex === -1) return;
    const next = [...views];
    const [moved] = next.splice(currentIndex, 1);
    next.splice(targetIndex, 0, moved);
    reorderViews(next);
  };

  return (
    <div style={{ height: 40, borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', padding: '0 4px', background: '#f5f7fa', flexShrink: 0 }}>
      {canScroll && (
        <Button type="text" size="small" icon={<LeftOutlined />} onClick={() => scrollViews('left')} style={{ padding: '0 6px' }} />
      )}

      <div ref={viewScrollRef} style={{ display: 'flex', flex: 1, overflow: 'hidden', scrollbarWidth: 'none' }}>
        {/* Default View */}
        <div
          onClick={() => !viewLoading && setCurrentView('')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            padding: '0 12px',
            height: 40,
            cursor: 'pointer',
            flexShrink: 0,
            userSelect: 'none',
            fontSize: 13,
            position: 'relative',
            background: currentViewId === null ? '#ffffff' : 'transparent',
            color: currentViewId === null ? '#1677ff' : '#666',
            fontWeight: currentViewId === null ? 600 : 400,
            borderRight: '1px solid #e8e8e8',
            borderBottom: currentViewId === null ? '2px solid #1677ff' : '2px solid transparent',
            boxShadow: currentViewId === null ? 'inset 0 1px 0 0 #e8e8e8, inset 1px 0 0 0 #e8e8e8' : 'none',
            transition: 'color 0.15s, background 0.15s',
          }}
        >
          <AppstoreOutlined style={{ fontSize: 12, color: currentViewId === null ? '#1677ff' : '#bbb' }} />
          <span>默认视图</span>
        </div>

        {views.map((view) => {
          const isActive = currentViewId === view.id;
          const isDefaultView = !!view.isDefault;
          const displayName = (view.name ?? '未命名').length > 10 ? (view.name ?? '未命名').slice(0, 10) + '…' : (view.name ?? '未命名');

          const moreMenu = {
            items: [
              {
                key: 'duplicate',
                label: '复制视图',
                icon: <AppstoreOutlined />,
                onClick: () => {
                  duplicateViewHandler(view.id);
                },
              },
              {
                key: 'delete',
                label: isDefaultView ? '默认视图不可删除' : '删除视图',
                icon: <DeleteOutlined />,
                danger: !isDefaultView,
                disabled: isDefaultView,
                onClick: () => {
                  if (!isDefaultView) {
                    confirmDeleteView(view);
                  }
                },
              },
            ],
          };

          return (
            <div
              key={view.id}
              onClick={() => !viewLoading && setCurrentView(view.id)}
              draggable
              onDragStart={(e) => {
                setDraggingId(view.id);
                e.dataTransfer.effectAllowed = 'move';
              }}
              onDragOver={(e) => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
              }}
              onDrop={(e) => {
                e.preventDefault();
                handleDropOnView(view.id);
                setDraggingId(null);
              }}
              onDragEnd={() => setDraggingId(null)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '0 12px',
                height: 40,
                cursor: 'pointer',
                flexShrink: 0,
                userSelect: 'none',
                fontSize: 13,
                position: 'relative',
                background: isActive ? '#ffffff' : 'transparent',
                color: isActive ? '#1677ff' : '#666',
                fontWeight: isActive ? 600 : 400,
                borderRight: '1px solid #e8e8e8',
                borderBottom: isActive ? '2px solid #1677ff' : '2px solid transparent',
                boxShadow: isActive ? 'inset 0 1px 0 0 #e8e8e8, inset 1px 0 0 0 #e8e8e8' : 'none',
                transition: 'color 0.15s, background 0.15s',
              }}
            >
              <AppstoreOutlined style={{ fontSize: 12, color: isActive ? '#1677ff' : '#bbb' }} />
              {editingViewId === view.id ? (
                <input
                  autoFocus
                  maxLength={20}
                  value={editingViewName}
                  style={{ border: 'none', borderBottom: '1px solid #1677ff', outline: 'none', width: 80, fontSize: 13, background: 'transparent' }}
                  onChange={(e) => setEditingViewName(e.target.value)}
                  onBlur={() => saveViewName(view.id)}
                  onKeyDown={(e) => { if (e.key === 'Enter') saveViewName(view.id); }}
                  onClick={(e) => e.stopPropagation()}
                />
              ) : (
                <span onDoubleClick={(e) => {
                  e.stopPropagation();
                  // 默认视图名称不可编辑
                  if (isDefaultView) return;
                  setEditingViewId(view.id);
                  setEditingViewName(view.name ?? '');
                }}>
                  {displayName}
                </span>
              )}
              {isActive && (
                <Dropdown menu={moreMenu} trigger={['click']}>
                  <Button
                    type="text"
                    size="small"
                    icon={<MoreOutlined />}
                    style={{ padding: '0 2px', height: 20 }}
                    onClick={(e) => e.stopPropagation()}
                  />
                </Dropdown>
              )}
            </div>
          );
        })}
      </div>

      {canScroll && (
        <Button type="text" size="small" icon={<RightOutlined />} onClick={() => scrollViews('right')} style={{ padding: '0 6px' }} />
      )}

      <Dropdown
        open={showAllViewsDropdown}
        onOpenChange={setShowAllViewsDropdown}
        menu={{
          items: [
            { key: 'default', label: '默认视图', onClick: () => setCurrentView('') },
            ...views.map(v => ({
              key: v.id,
              label: v.name,
              style: currentViewId === v.id ? { color: '#1677ff' } : {},
              onClick: () => { setCurrentView(v.id); setShowAllViewsDropdown(false); },
            }))
          ],
        }}
        trigger={['click']}
      >
        <Button type="text" size="small" style={{ borderLeft: '1px solid #f0f0f0', borderRadius: 0, height: 40, padding: '0 10px' }}>
          全部视图 <DownOutlined style={{ fontSize: 10 }} />
        </Button>
      </Dropdown>

      <Tooltip title={isAtLimit ? `视图数量已达上限（${MAX_VIEWS}个）` : '新增视图'}>
        <Button
          type="text"
          size="small"
          icon={<PlusOutlined />}
          onClick={isAtLimit ? undefined : addView}
          disabled={isAtLimit}
          style={{ borderLeft: '1px solid #f0f0f0', borderRadius: 0, height: 40, width: 36 }}
        />
      </Tooltip>
    </div>
  );
};

export default ViewBar;
