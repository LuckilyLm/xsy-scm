/*
 * 省 / 市 / 区地理归属的共享类型
 *
 * 三张主档（customer / supplier / warehouse）都存同一组 6 列：三级编码 + 三级名称快照。
 * 编码是 GB/T 2260 六位整数，取值与 `area-cascader` 的数据源同源同值。
 */

/** `a-cascader` 选中路径上的一个节点。 */
export interface AreaNode {
    value: number;
    label: string;
}

/** 一行主档的地理归属列；未选择时整组为 `null`。 */
export interface AreaColumns {
    provinceCode: number | null;
    provinceName: string | null;
    cityCode: number | null;
    cityName: string | null;
    districtCode: number | null;
    districtName: string | null;
}
