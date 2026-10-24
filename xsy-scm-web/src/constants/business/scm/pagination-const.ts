/**
 * SCM 列表每页条数。
 *
 * 后台列表一次看 20 条足够，下拉里不放 50/100，避免越翻越慢、也避免同事习惯性拉满；
 * 报表、分拣、客户详情这类「逐行核对」的明细列表放宽到 50。默认仍从 20 条开始。
 */
export const SCM_PAGE_SIZE_OPTIONS = ['10', '20'];
export const SCM_PAGE_SIZE_OPTIONS_WIDE = ['20', '50'];
