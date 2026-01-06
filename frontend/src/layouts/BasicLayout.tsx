import React, { useState, useMemo } from 'react';
import { Layout, Menu, theme, Dropdown, Avatar, Breadcrumb } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DashboardOutlined,
} from '@ant-design/icons';
import { useUserStore } from '@/store/userStore';
import type { Permission } from '@/types/permission';
import type { MenuProps } from 'antd';
import * as Icons from '@ant-design/icons';

const { Header, Sider, Content } = Layout;

// Helper to dynamically load icon
const getIcon = (iconName?: string) => {
  if (!iconName) return null;
  const IconComponent = (Icons as any)[iconName];
  return IconComponent ? <IconComponent /> : null;
};

const BasicLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, logout, menus } = useUserStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userMenu = [
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      onClick: handleLogout,
    },
  ];

  // Transform permissions to AntD menu items
  const menuItems = useMemo(() => {
    const transformMenu = (items: Permission[]): MenuProps['items'] => {
      return items.map((item) => {
        // Only show DIR and MENU types in sidebar
        if (item.type === 'BTN') return null;

        const menuItem: any = {
          key: item.path || item.id.toString(), // Use path as key if available, else ID
          icon: getIcon(item.icon),
          label: item.name,
        };

        if (item.children && item.children.length > 0) {
          const children = transformMenu(item.children);
          if (children && children.length > 0) {
            menuItem.children = children;
          }
        }

        // If it's a leaf node (MENU), add click handler
        if (item.type === 'MENU' && item.path) {
          menuItem.onClick = () => navigate(item.path!);
        }

        return menuItem;
      }).filter(Boolean);
    };

    // Always add Dashboard as the first item
    const dashboardItem = {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '首页',
      onClick: () => navigate('/dashboard'),
    };

    return [dashboardItem, ...(transformMenu(menus) || [])];
  }, [menus, navigate]);

  // Generate breadcrumb items from current path
  const generateBreadcrumbItems = () => {
    const pathname = location.pathname;
    const pathSegments = pathname.split('/').filter(Boolean);
    
    const items = [{
      title: <span onClick={() => navigate('/dashboard')} style={{ cursor: 'pointer' }}>首页</span>,
      key: 'home'
    }];
    
    // If we're already at dashboard, don't add extra items
    if (pathname === '/dashboard' || pathname === '/') {
      return items;
    }
    
    let currentPath = '';
    
    pathSegments.forEach((segment) => {
      currentPath += `/${segment}`;
      
      // Skip adding 'dashboard' again since we already have '首页' as the first item
      if (segment === 'dashboard') {
        return;
      }
      
      // Find menu item with this path to get the display name
      const menuItem = findMenuItem(menuItems, currentPath);
      if (menuItem) {
        items.push({
          title: typeof menuItem.label === 'string' ? <span>{menuItem.label}</span> : <>{menuItem.label}</>,
          key: currentPath
        });
      } else {
        // Fallback to segment if no menu item found
        items.push({
          title: <span>{segment}</span>,
          key: currentPath
        });
      }
    });
    
    return items;
  };
  
  // Helper to find menu item by path
  const findMenuItem = (items: MenuProps['items'], path: string): any => {
    for (const item of items || []) {
      if (!item) continue;
      if (item.key === path) {
        return item;
      }
      // Check if item has children property (SubMenuType)
      if ('children' in item && item.children) {
        const found = findMenuItem(item.children, path);
        if (found) return found;
      }
    }
    return null;
  };

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden', display: 'flex', width: '100%' }}>
      <Sider 
        trigger={null} 
        collapsible 
        collapsed={collapsed} 
        style={{ overflowY: 'auto' }}
      >
        <div className="demo-logo-vertical" style={{ height: 32, margin: 16, background: 'rgba(255, 255, 255, 0.2)' }} />
        <Menu
          theme="dark"
          mode="inline"
          defaultSelectedKeys={[location.pathname]}
          // Open all submenus by default or manage openKeys state
          defaultOpenKeys={['/system']} 
          items={menuItems}
        />
      </Sider>
      <Layout style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <Header style={{ 
          padding: '0', 
          background: colorBgContainer, 
          display: 'flex', 
          alignItems: 'center',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
          minHeight: '64px',
          flexShrink: 0,
          zIndex: 1,
        }}>
          {/* 左侧：折叠按钮 */}
          <div style={{ 
            display: 'flex', 
            alignItems: 'center',
            padding: '0 16px',
          }}>
            {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
              className: 'trigger',
              onClick: () => setCollapsed(!collapsed),
              style: { 
                fontSize: '18px',
                cursor: 'pointer',
                padding: '8px',
                borderRadius: '4px',
                transition: 'all 0.3s'
              },
            } as any)}
          </div>
          
          {/* 中间：面包屑 - 自适应宽度 */}
          <div style={{ 
            display: 'flex', 
            alignItems: 'center',
            flex: 1,
            overflow: 'hidden',
          }}>
            <Breadcrumb 
              items={generateBreadcrumbItems()} 
              style={{ 
                width: '100%',
                overflow: 'hidden',
                whiteSpace: 'nowrap',
                textOverflow: 'ellipsis'
              }}
            />
          </div>
          
          {/* 右侧：用户信息 */}
          <div style={{ 
            display: 'flex', 
            alignItems: 'center',
            padding: '0 16px',
          }}>
            <Dropdown menu={{ items: userMenu }}>
              <div style={{ 
                cursor: 'pointer', 
                display: 'flex', 
                alignItems: 'center', 
                gap: '8px',
                padding: '8px 12px',
                borderRadius: '6px',
                transition: 'all 0.3s'
              }}>
                <Avatar 
                  icon={<UserOutlined />} 
                  src={userInfo?.avatar} 
                  size="default"
                />
                <span style={{ 
                  fontSize: '14px',
                  fontWeight: 500,
                  maxWidth: '100px',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap'
                }}>
                  {userInfo?.nickname || userInfo?.username || 'Admin'}
                </span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content
          style={{
            padding: '24px 16px',
            overflowY: 'auto',
            flex: 1,
          }}
        >
          <div
            style={{
              padding: 24,
              minHeight: '100%',
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
            }}
          >
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
