/** Format fixed decimal strings without floating point conversion. */
export function formatAmount(value:string|null|undefined):string {
 if(value==null) return '未定价';
 const [whole,fraction='']=value.split('.');
 return `¥ ${whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')}.${fraction.padEnd(4,'0')}`;
}
export function formatAmountOrDash(value:string|null|undefined):string {return value==null?'—':formatAmount(value);}
