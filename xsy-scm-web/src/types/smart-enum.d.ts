/*
 * @LastEditTime: 2021-08-26
 * @LastEditors:
 * @Description:
 * @FilePath: /smart-admin/@/types/base.d.ts
 */
export interface SmartEnumWrapper<T> {
  [key: string]: SmartEnum<T>;
}

export interface SmartEnum<T> {
  [key: string]: SmartEnumItem<T>;
}

export interface SmartEnumItem<T> {
  value: T;
  desc: string;
}

export interface SmartEnumPlugin {
  getDescByValue(constantName: string, value: unknown): string;

  getValueDescList(constantName: string): SmartEnumItem<unknown>[];

  getValueDesc(constantName: string): {[key: string]: string};
}
