/*
 * 数据大屏静态路由
 *
 */
import type {RouteRecordRaw} from 'vue-router';

export const screenRouters: Array<RouteRecordRaw> = [
    {
        path: '/screen',
        name: 'Screen',
        component: () => import('/@/views/business/scm/screen/index.vue'),
        meta: {
            title: '数据大屏',
            hideInMenu: true,
        },
    },
];
