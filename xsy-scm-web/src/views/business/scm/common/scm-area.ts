/* 省市区选择与六列编码、名称之间的转换。名称只用于展示，随选择一起写入。 */

import type {AreaColumns, AreaNode} from '/@/types/business/scm/area';

const LEVELS = ['province', 'city', 'district'] as const;

/** 六列的联合键。 */
type ColumnKey = keyof AreaColumns;
type Draft = Record<ColumnKey, AreaColumns[ColumnKey]>;

function draft(columns: AreaColumns): Draft {
  return columns as Draft;
}

function codeKey(level: (typeof LEVELS)[number]): ColumnKey {
  return `${level}Code` as ColumnKey;
}

function nameKey(level: (typeof LEVELS)[number]): ColumnKey {
  return `${level}Name` as ColumnKey;
}

function emptyAreaColumns(): AreaColumns {
  const columns = {} as AreaColumns;
  const target = draft(columns);
  for (const level of LEVELS) {
    target[codeKey(level)] = null;
    target[nameKey(level)] = null;
  }
  return columns;
}

/** 级联选中路径拆成 6 列；未选或清空时整组置空，不残留上一次的层级。 */
export function areaColumnsOf(nodes?: readonly AreaNode[] | null): AreaColumns {
  const columns = emptyAreaColumns();
  if (!nodes?.length) {
    return columns;
  }
  const target = draft(columns);
  LEVELS.forEach((level, index) => {
    const node = nodes[index];
    if (node) {
      target[codeKey(level)] = node.value;
      target[nameKey(level)] = node.label;
    }
  });
  return columns;
}

/** 把已保存的六列还原成级联显示值；遇到缺失层级就停止。 */
export function areaNodesOf(columns?: Partial<AreaColumns> | null): AreaNode[] {
  const nodes: AreaNode[] = [];
  if (!columns) {
    return nodes;
  }
  for (const level of LEVELS) {
    const code = columns[codeKey(level)];
    const name = columns[nameKey(level)];
    if (code == null || !name) {
      break;
    }
    nodes.push({value: Number(code), label: String(name)});
  }
  return nodes;
}
