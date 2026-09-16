/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/order/order-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 */
import {request,getRequest,postRequest} from '/@/lib/axios';
import type {ScmResponse,ScmPage} from '/@/types/business/scm/customer';
import type {Order,Query,Id} from '/@/views/business/scm/order/order-types';
import type {ResolveResult} from '/@/types/business/scm/pricing';
// A failed retry retains its UUID; a changed payload or successful request starts a new command.
const keys=new Map<string,string>();
export async function orderCommand<T>(path:string,data:unknown):Promise<ScmResponse<T>> {const signature=path+JSON.stringify(data);let key=keys.get(signature);if(!key){key=crypto.randomUUID();keys.set(signature,key);}const result=await request({url:path,method:'post',data,headers:{'Idempotency-Key':key}}) as unknown as ScmResponse<T>;keys.delete(signature);return result;}
export const orderApi={
 query:(data:Query)=>postRequest('/scm/order/query',data) as unknown as Promise<ScmResponse<ScmPage<Order>>>,
 detail:(id:Id)=>getRequest('/scm/order/detail/'+id,{}) as unknown as Promise<ScmResponse<Order>>,
 create:(data:Order)=>orderCommand<Order>('/scm/order/create',data),
 update:(data:Order)=>postRequest('/scm/order/update',data) as unknown as Promise<ScmResponse<Order>>,
 submit:(data:unknown)=>orderCommand<Order>('/scm/order/submit',data),confirm:(data:unknown)=>orderCommand<Order>('/scm/order/confirm',data),cancel:(data:unknown)=>orderCommand<Order>('/scm/order/cancel',data),actual:(data:unknown)=>orderCommand<Order>('/scm/order/item/actual-quantity',data),
 delete:(data:unknown)=>postRequest('/scm/order/delete',data),batchDelete:(orders:unknown[])=>postRequest('/scm/order/batch-delete',{orders}),
 preview:(data:{customerId:Id;skuIds:Id[]})=>postRequest('/scm/order/price/preview',data) as unknown as Promise<ScmResponse<ResolveResult>>,
};
