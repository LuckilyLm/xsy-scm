import {DownOutlined, LockOutlined, LogoutOutlined} from '@ant-design/icons';
import {Dropdown, Modal} from 'antd';
import {useState} from 'react';
import {NavLink, Outlet, useLocation} from 'react-router-dom';
import {useAuth} from '../../auth/AuthProvider';
import {ChangePasswordModal} from '../../pages/auth/ChangePasswordModal';
import styles from './AdminLayout.module.css';
import {resolveActiveGroup, useNavigation} from './navigation';

export function AdminLayout() {
    const location = useLocation();
    const {user, logout} = useAuth();
    const navigation = useNavigation();
    const [changePasswordOpen, setChangePasswordOpen] = useState(false);
    const [collapsed, setCollapsed] = useState(false);

    const activePrimary = resolveActiveGroup(navigation, location.pathname);
    const secondary = activePrimary?.children ?? [];

    async function handleLogout() {
        await logout();
    }

    return (
        <div className={styles.shell}>
            <aside className={styles.primarySidebar}>
                <nav aria-label="一级导航" className={styles.primaryNav}>
                    {navigation.map((item) => (
                        <NavLink
                            className={item.key === activePrimary?.key ? styles.primaryItemActive : styles.primaryItem}
                            key={item.key}
                            to={item.path}
                        >
                            <span className={styles.primaryIcon}>{item.icon}</span>
                            <span>{item.label}</span>
                        </NavLink>
                    ))}
                </nav>
            </aside>

            <aside
                className={
                    collapsed ? `${styles.secondarySidebar} ${styles.secondaryCollapsed}` : styles.secondarySidebar
                }
            >
                {secondary.length > 0 ? (
                    <nav aria-label={`${activePrimary?.label ?? ''}二级导航`} className={styles.secondaryNav}>
                        {secondary.map((item) => (
                            <NavLink
                                className={({isActive}) => (isActive ? styles.secondaryItemActive : styles.secondaryItem)}
                                key={item.path}
                                to={item.path}
                            >
                                {item.label}
                            </NavLink>
                        ))}
                    </nav>
                ) : (
                    <div className={styles.emptyNav}>暂无可访问功能</div>
                )}
                <button
                    aria-expanded={!collapsed}
                    className={styles.collapseButton}
                    onClick={() => setCollapsed((previous) => !previous)}
                    type="button"
                >
                    {collapsed ? '展开' : '收起'}
                </button>
            </aside>

            <header className={styles.header}>
                <div className={styles.brand}>鲜蔬源智慧供应链</div>
                <div className={styles.headerActions}>
                    {user ? (
                        <Dropdown
                            menu={{
                                items: [
                                    {key: 'password', icon: <LockOutlined/>, label: '修改密码'},
                                    {type: 'divider'},
                                    {key: 'logout', icon: <LogoutOutlined/>, label: '退出登录'},
                                ],
                                onClick: ({key}) => {
                                    if (key === 'password') {
                                        setChangePasswordOpen(true);
                                        return;
                                    }
                                    if (key === 'logout') {
                                        void Modal.confirm({
                                            title: '退出登录',
                                            content: '退出后需要重新登录才能继续操作。',
                                            okText: '退出',
                                            cancelText: '取消',
                                            onOk: () => handleLogout(),
                                        });
                                    }
                                },
                            }}
                        >
              <span className={styles.userName}>
                {user.displayName}
                  {user.administrator ? '（超管）' : ''}
                  <DownOutlined style={{marginLeft: 6, fontSize: 10}}/>
              </span>
                        </Dropdown>
                    ) : null}
                </div>
            </header>

            <main className={collapsed ? `${styles.content} ${styles.contentCollapsed}` : styles.content}>
                <Outlet/>
            </main>

            <ChangePasswordModal
                forced={user?.mustChangePassword === true}
                open={changePasswordOpen || user?.mustChangePassword === true}
                onClose={() => setChangePasswordOpen(false)}
            />
        </div>
    );
}
