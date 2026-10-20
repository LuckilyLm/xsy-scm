import type {Job,Item} from './types';

/** 导航只分组可见任务，不创建新的业务范围或累计。 */
export interface TaskRow {job:Job;item:Item}
export type SortingMode='products'|'customers';
export interface SortingGroup {key:string;label:string;detail:string;rows:TaskRow[];relatedCount:number;identityKnown:boolean}

/** 以字符串身份分组；缺少身份时按原任务隔离，不能根据名称猜测。
 * @param row 当前员工可见的非空作业和明细，不接受空值。
 * @param mode products以SKU分组，customers以客户分组。
 * @returns 仅用于导航的稳定键，不是授权凭据或业务记录号。
 */
export function groupKey(row:TaskRow,mode:SortingMode):string{
  const id=mode==='products'?row.item.skuId:row.item.customerId;
  return id?.trim()?JSON.stringify(['id',id]):JSON.stringify(['task',row.job.jobId,row.item.taskItemId]);
}

/** 返回商品或客户索引；不合计数量，避免跨单位混算。
 * @param rows 当前可见任务数组，无任务传空数组，不接受null。
 * @param mode 已选的商品或客户分组方式。
 * @returns 保留原任务对象的分组数组，空输入返回空数组。
 */
export function sortingGroups(rows:TaskRow[],mode:SortingMode):SortingGroup[]{
  const groups=new Map<string,SortingGroup>();
  for(const row of rows){
    const key=groupKey(row,mode),item=row.item;
    let group=groups.get(key);
    if(!group){
      const id=mode==='products'?item.skuId:item.customerId;
      group={key,label:mode==='products'?item.productName:item.customerName||'客户资料未提供',
        detail:mode==='products'?item.specName||'默认规格':id?`客户编号 ${id}`:`${item.orderNo||row.job.taskNo} · 客户身份待补充`,
        rows:[],relatedCount:0,identityKnown:!!id?.trim()};groups.set(key,group);
    }
    group.rows.push(row);
  }
  for(const group of groups.values())group.relatedCount=new Set(group.rows.map(row=>groupKey(row,mode==='products'?'customers':'products'))).size;
  return [...groups.values()];
}
