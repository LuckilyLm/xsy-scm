/** 判断单位是否表示500克的市斤。
 * @param unit 设备协议或任务单位；空值/未知值返回false。
 * @returns sj、斤、市斤均为true，不把公斤或磅归入市斤。
 */
export function isJin(unit:string|null|undefined):boolean {return unit==='sj'||unit==='斤'||unit==='市斤';}

/** 比较同物理单位别名，不转换或舍入数值。
 * @param raw 设备原始单位；空值不匹配。
 * @param business 任务业务单位；空值不匹配。
 * @returns 同编码或同为市斤别名时true。
 */
export function unitsMatch(raw:string|undefined,business:string|undefined):boolean {
  return !!raw&&!!business&&(raw===business||isJin(raw)&&isJin(business));
}

/** 展示单位，保留公斤历史的物理含义。
 * @param unit 原始或冻结单位；空值显示破折号。
 * @returns 市斤统一显示斤，其他单位原样返回。
 */
export function unitLabel(unit:string|null|undefined):string {return isJin(unit)?'斤':unit||'—';}

/** 展示录入来源，旧数据无字段时明确标记来源未记录。
 * @param source 冻结来源DEVICE/MANUAL；可空且不根据当前模式补造。
 * @returns 中文来源标签。
 */
export function sourceLabel(source:string|null|undefined):string {
  return source==='MANUAL'?'手工录入':source==='DEVICE'?'电子秤称重':'历史记录';
}
