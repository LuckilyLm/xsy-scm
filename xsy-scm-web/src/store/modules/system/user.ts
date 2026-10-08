/*
 * 登录用户
 *
 */
import _ from 'lodash';
import {defineStore} from 'pinia';
import localKey from '/@/constants/local-storage-key-const';
import {HOME_PAGE_NAME} from '/@/constants/system/home-const';
import {MENU_TYPE_ENUM} from '/@/constants/system/menu-const';
import {messageApi} from '/@/api/support/message-api';
import {smartSentry} from '/@/lib/smart-sentry';
import {localRead, localSave, localRemove} from '/@/utils/local-util';


export const useUserStore = defineStore({
    id: 'userStore',
    state: () => ({
        token: '',
        //员工id
        employeeId: '',
        // 头像
        avatar: '',
        //登录名
        loginName: '',
        //姓名
        actualName: '',
        //手机号
        phone: '',
        //部门id
        departmentId: '',
        //部门名词
        departmentName: '',
        //是否需要修改密码
        needUpdatePwdFlag: false,
        //是否为超级管理员
        // 初值必须是 false：它是权限放行的总开关（指令、插件、各域 permission 组合式函数
        // 都写成「是超管就直接 true」）。初值给 true 等于「登录信息到达前一律放行」，
        // 页面在登录态未就绪时渲染就会短暂露出本无权限的操作。授权只能由登录响应显式授予。
        administratorFlag: false,
        //上次登录ip
        lastLoginIp: '',
        //上次登录ip地区
        lastLoginIpRegion: '',
        //上次登录 设备
        lastLoginUserAgent: '',
        //上次登录时间
        lastLoginTime: '',
        //左侧菜单树形结构
        menuTree: [],
        //存在页面路由的菜单集合
        menuRouterList: [],
        //是否完成menuRouter初始化
        menuRouterInitFlag: false,
        //父类菜单集合
        menuParentIdListMap: new Map(),
        // 功能点集合
        pointsList: [],
        // 标签页
        tagNav: null,
        // 缓存
        keepAliveIncludes: [],
        // 未读消息数量
        unreadMessageCount: 0,
    }),
    getters: {
        getToken(state) {
            if (state.token) {
                return state.token;
            }
            return localRead(localKey.USER_TOKEN);
        },
        getNeedUpdatePwdFlag(state) {
            return state.needUpdatePwdFlag;
        },
        //是否初始化了 路由
        getMenuRouterInitFlag(state) {
            return state.menuRouterInitFlag;
        },
        //菜单树
        getMenuTree(state) {
            return state.menuTree;
        },
        //菜单的路由
        getMenuRouterList(state) {
            return state.menuRouterList;
        },
        //菜单的父级id
        getMenuParentIdListMap(state) {
            return state.menuParentIdListMap;
        },
        //功能点
        getPointList(state) {
            if (_.isEmpty(state.pointsList)) {
                let localUserPoints = localRead(localKey.USER_POINTS) || '';
                state.pointsList = localUserPoints ? JSON.parse(localUserPoints) : [];
            }
            return state.pointsList;
        },
        //标签页
        getTagNav(state) {
            if (_.isNull(state.tagNav)) {
                let localTagNav = localRead(localKey.USER_TAG_NAV) || '';
                state.tagNav = localTagNav ? JSON.parse(localTagNav) : [];
            }
            let tagNavList = _.cloneDeep(state.tagNav) || [];
            tagNavList.unshift({
                menuName: HOME_PAGE_NAME,
                menuTitle: '首页',
            });
            return tagNavList;
        },
    },

    actions: {
        /**
         * 登出：把「身份」与「权限」两类状态一并清干净。
         *
         * <p>只清 token 是不够的：这是一份会被下一个登录者复用的内存状态，
         * 而多个权限判断直接读 `administratorFlag` / `pointsList`（不经过 localStorage）。
         * 若残留上一个人的超管标记或功能点，换人登录后、登录响应到达前的那一小段时间里，
         * 页面会按上一个人的权限渲染 —— 真正的越权入口，而不是显示问题。
         *
         * <p>因此这里按 state 的字段逐个复位到初始值，而不是只挑几个「看起来重要」的。
         * 清空后再删 localStorage：顺序反了会留下一个「内存已清、下次刷新又读回来」的窗口。
         * 每个字段的初值以 state() 的定义为准，两边必须同步维护。
         */
        logout() {
            // —— 身份 ——
            this.token = '';
            this.employeeId = '';
            this.avatar = '';
            this.loginName = '';
            this.actualName = '';
            this.phone = '';
            this.departmentId = '';
            this.departmentName = '';
            this.needUpdatePwdFlag = false;
            this.administratorFlag = false;
            this.lastLoginIp = '';
            this.lastLoginIpRegion = '';
            this.lastLoginUserAgent = '';
            this.lastLoginTime = '';
            // —— 权限与菜单 ——
            this.menuTree = [];
            this.menuRouterList = [];
            this.menuRouterInitFlag = false;
            this.menuParentIdListMap = new Map();
            this.pointsList = [];
            // —— 界面状态 ——
            // 置 null 而不是 []：getTagNav 用 _.isNull 判断「还没从本地读过」，
            // 给 [] 会让它跳过本地读取，行为与初始值不一致。
            this.tagNav = null;
            this.keepAliveIncludes = [];
            this.unreadMessageCount = 0;

            localRemove(localKey.USER_TOKEN);
            localRemove(localKey.USER_POINTS);
            localRemove(localKey.USER_TAG_NAV);
            localRemove(localKey.APP_CONFIG);
            localRemove(localKey.HOME_QUICK_ENTRY);
            localRemove(localKey.NOTICE_READ);
        },
        // 查询未读消息数量
        async queryUnreadMessageCount() {
            try {
                let result = await messageApi.queryUnreadCount();
                this.unreadMessageCount = result.data;
            } catch (e) {
                smartSentry.captureError(e);
            }
        },
        //设置登录信息
        setUserLoginInfo(data) {
            // 用户基本信息
            this.token = data.token;
            this.employeeId = data.employeeId;
            this.avatar = data.avatar;
            this.loginName = data.loginName;
            this.actualName = data.actualName;
            this.phone = data.phone;
            this.departmentId = data.departmentId;
            this.departmentName = data.departmentName;
            this.needUpdatePwdFlag = data.needUpdatePwdFlag;
            this.administratorFlag = data.administratorFlag;
            this.lastLoginIp = data.lastLoginIp;
            this.lastLoginIpRegion = data.lastLoginIpRegion;
            this.lastLoginUserAgent = data.lastLoginUserAgent;
            this.lastLoginTime = data.lastLoginTime;

            //菜单权限
            this.menuTree = buildMenuTree(data.menuList);

            //拥有路由的菜单
            this.menuRouterList = data.menuList.filter((e) => e.path || e.frameUrl);

            //父级菜单集合
            this.menuParentIdListMap = buildMenuParentIdListMap(this.menuTree);

            //功能点
            this.pointsList = data.menuList.filter((menu) => menu.menuType === MENU_TYPE_ENUM.POINTS.value && menu.visibleFlag && !menu.disabledFlag);

            // 获取用户未读消息
            this.queryUnreadMessageCount();
        },

        setToken(token) {
            this.token = token;
        },

        //设置标签页
        setTagNav(route, from) {
            if (_.isNull(this.tagNav)) {
                let localTagNav = localRead(localKey.USER_TAG_NAV) || '';
                this.tagNav = localTagNav ? JSON.parse(localTagNav) : [];
            }
            // name唯一标识
            let name = route.name;
            if (!name || name === HOME_PAGE_NAME || name === '403' || name === '404') {
                return;
            }
            let findTag = (this.tagNav || []).find((e) => e.menuName === name);
            if (findTag) {
                // @ts-ignore
                findTag.fromMenuName = from.name;
                findTag.fromMenuQuery = from.query;
                findTag.menuQuery = route.query;
            } else {
                // @ts-ignore
                this.tagNav.push({
                    // @ts-ignore
                    menuName: name,
                    // @ts-ignore
                    menuTitle: route.meta.title,
                    menuQuery: route.query,
                    menuIcon: route.meta.icon,
                    // @ts-ignore
                    fromMenuName: from.name,
                    fromMenuQuery: from.query,
                });
            }
            localSave(localKey.USER_TAG_NAV, JSON.stringify(this.tagNav));
        },
        //关闭标签页
        closeTagNav(menuName, closeAll) {
            if (_.isEmpty(this.getTagNav)) return;
            if (closeAll && !menuName) {
                this.tagNav = [];
                this.clearKeepAliveIncludes();
            } else {
                let findIndex = (this.tagNav || []).findIndex((e) => e.menuName === menuName);
                if (closeAll) {
                    if (findIndex === -1) {
                        this.tagNav = [];
                        this.clearKeepAliveIncludes();
                    } else {
                        let tagNavElement = (this.tagNav || [])[findIndex];
                        this.tagNav = [tagNavElement];
                        this.clearKeepAliveIncludes(tagNavElement.menuName);
                    }
                } else {
                    (this.tagNav || []).splice(findIndex, 1);
                    this.deleteKeepAliveIncludes(menuName);
                }
            }
            localSave(localKey.USER_TAG_NAV, JSON.stringify(this.tagNav));
        },
        //关闭页面
        closePage(route, router, path) {
            if (!this.getTagNav || _.isEmpty(this.getTagNav)) return;
            if (path) {
                router.push({path});
            } else {
                // 寻找tagNav
                let index = this.getTagNav.findIndex((e) => e.menuName === route.name);
                if (index === -1) {
                    router.push({name: HOME_PAGE_NAME});
                } else {
                    let tagNav = this.getTagNav[index];
                    if (tagNav.fromMenuName && this.getTagNav.some((e) => e.menuName === tagNav.fromMenuName)) {
                        router.push({name: tagNav.fromMenuName, query: tagNav.fromMenuQuery});
                    } else {
                        // 查询左侧tag
                        let leftTagNav = this.getTagNav[index - 1];
                        router.push({name: leftTagNav.menuName, query: leftTagNav.menuQuery});
                    }
                }
            }
            this.closeTagNav(route.name, false);
        },
        // 加入缓存
        pushKeepAliveIncludes(val) {
            if (!val) {
                return;
            }
            if (!this.keepAliveIncludes) {
                this.keepAliveIncludes = [];
            }
            if (this.keepAliveIncludes.length < 30) {
                let number = this.keepAliveIncludes.findIndex((e) => e === val);
                if (number === -1) {
                    this.keepAliveIncludes.push(val);
                }
            }
        },
        // 删除缓存
        deleteKeepAliveIncludes(val) {
            if (!this.keepAliveIncludes || !val) {
                return;
            }
            let number = this.keepAliveIncludes.findIndex((e) => e === val);
            if (number !== -1) {
                this.keepAliveIncludes.splice(number, 1);
            }
        },
        // 清空缓存
        clearKeepAliveIncludes(val) {
            if (!val || !this.keepAliveIncludes.includes(val)) {
                this.keepAliveIncludes = [];
                return;
            }
            this.keepAliveIncludes = [val];
        },
    },
});

/**
 * 构建菜单父级集合
 */
function buildMenuParentIdListMap(menuTree) {
    let menuParentIdListMap = new Map();
    recursiveBuildMenuParentIdListMap(menuTree, [], menuParentIdListMap);
    return menuParentIdListMap;
}

function recursiveBuildMenuParentIdListMap(menuList, parentMenuList, menuParentIdListMap) {
    for (const e of menuList) {
        // 顶级parentMenuList清空
        if (e.parentId === 0) {
            parentMenuList = [];
        }
        let menuIdStr = e.menuId.toString();
        let cloneParentMenuList = _.cloneDeep(parentMenuList);
        if (!_.isEmpty(e.children) && e.menuName) {
            // 递归
            cloneParentMenuList.push({name: menuIdStr, title: e.menuName});
            recursiveBuildMenuParentIdListMap(e.children, cloneParentMenuList, menuParentIdListMap);
        } else {
            menuParentIdListMap.set(menuIdStr, cloneParentMenuList);
        }
    }
}

/**
 * 构建菜单树
 *
 * @param  menuList
 * @returns
 */
function buildMenuTree(menuList) {
    //1 获取所有 有效的 目录和菜单
    let catalogAndMenuList = menuList.filter((menu) => menu.menuType !== MENU_TYPE_ENUM.POINTS.value && menu.visibleFlag && !menu.disabledFlag);

    //2 获取顶级目录
    let topCatalogList = catalogAndMenuList.filter((menu) => menu.parentId === 0);
    for (const topCatalog of topCatalogList) {
        buildMenuChildren(topCatalog, catalogAndMenuList);
    }
    return topCatalogList;
}

function buildMenuChildren(menu, allMenuList) {
    let children = allMenuList.filter((e) => e.parentId === menu.menuId);
    if (children.length === 0) {
        return;
    }
    menu.children = children;
    for (const item of children) {
        buildMenuChildren(item, allMenuList);
    }
}
