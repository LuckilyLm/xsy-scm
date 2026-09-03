import { Badge, Input } from 'antd';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import styles from './AdminLayout.module.css';
import { primaryNavigation, secondaryNavigation } from './navigation';

export function AdminLayout() {
  const location = useLocation();
  const activePrimary = primaryNavigation.find((item) => item.path !== '/' && location.pathname.startsWith(item.path)) ?? primaryNavigation[0];
  const section = activePrimary.path === '/' ? 'products' : activePrimary.path.slice(1);
  const secondary = secondaryNavigation[section] ?? [];
  return (
    <div className={styles.shell}>
      <aside className={styles.primarySidebar}>
        <nav aria-label="一级导航" className={styles.primaryNav}>
          {primaryNavigation.map((item) => (
            <NavLink
              className={item.path === activePrimary.path ? styles.primaryItemActive : styles.primaryItem}
              key={item.label}
              to={item.path}
              end={item.path === '/'}
            >
              <span className={styles.primaryIcon}>{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>

      <aside className={styles.secondarySidebar}>
        <nav aria-label={`${activePrimary.label}二级导航`} className={styles.secondaryNav}>
          {secondary.map((item) => (
            <NavLink
              className={({ isActive }) => isActive ? styles.secondaryItemActive : styles.secondaryItem}
              key={item.path}
              to={item.path}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className={styles.collapseHint}>收起</div>
      </aside>

      <header className={styles.header}>
        <div className={styles.brand}>鲜蔬源智慧供应链</div>
        <Input.Search
          aria-label="功能搜索"
          className={styles.globalSearch}
          placeholder="搜功能、搜应用"
        />
        <div className={styles.headerActions}>
          <Badge dot>
            <span className={styles.headerLink}>新功能</span>
          </Badge>
          <span className={styles.headerLink}>帮助中心</span>
          <span className={styles.userName}>管理员</span>
        </div>
      </header>

      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}
