export interface XsyEnumWrapper<T> {
  [key: string]: XsyEnum<T>;
}

export interface XsyEnum<T> {
  [key: string]: XsyEnumItem<T>;
}

export interface XsyEnumItem<T> {
  value: T;
  desc: string;
}

export interface XsyEnumPlugin {
  getDescByValue(constantName: string, value: unknown): string;

  getValueDescList(constantName: string): XsyEnumItem<unknown>[];

  getValueDesc(constantName: string): {[key: string]: string};
}
