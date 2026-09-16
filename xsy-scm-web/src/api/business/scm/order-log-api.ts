/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/order/order-log-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 */
import {postRequest} from '/@/lib/axios';
import type {ScmResponse,ScmPage} from '/@/types/business/scm/customer';
import type {LogRow,Query} from '/@/views/business/scm/order/order-types';
export const orderLogApi={
 query:(data:Query)=>postRequest('/scm/order/log/query',data) as unknown as Promise<ScmResponse<ScmPage<LogRow>>>,
};
