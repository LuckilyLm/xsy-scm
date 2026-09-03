import {
  AppstoreOutlined,
  BarChartOutlined,
  DollarOutlined,
  HomeOutlined,
  InboxOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Badge, Input } from 'antd';
import { NavLink, Outlet } from 'react-router-dom';
import styles from './AdminLayout.module.css';

const primaryNavigation = [
  { label: '首页', icon: <HomeOutlined /> },
  { label: '商品', icon: <ShopOutlined />, active: true },
  { label: '订单', icon: <InboxOutlined /> },
  { label: '采购', icon: <ShoppingCartOutlined /> },
  { label: '库房', icon: <AppstoreOutlined /> },
  { label: '客户', icon: <TeamOutlined /> },
  { label: '财务', icon: <DollarOutlined /> },
  { label: '报表', icon: <BarChartOutlined /> },
];

const productNavigation = ['商品档案', '商品分类', '辅助资料', '商品图片', '售卖情况'];

export function AdminLayout() {
  return (
    <div className={styles.shell}>
      <aside className={styles.primarySidebar}>
        <nav aria-label="一级导航" className={styles.primaryNav}>
          {primaryNavigation.map((item) => (
            <button
              className={item.active ? styles.primaryItemActive : styles.primaryItem}
              key={item.label}
              type="button"
            >
              <span className={styles.primaryIcon}>{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <aside className={styles.secondarySidebar}>
        <nav aria-label="商品二级导航" className={styles.secondaryNav}>
          {productNavigation.map((item, index) =>
            index === 0 ? (
              <NavLink className={styles.secondaryItemActive} key={item} to="/products">
                {item}
              </NavLink>
            ) : (
              <span className={styles.secondaryItem} key={item}>
                {item}
              </span>
            ),
          )}
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
