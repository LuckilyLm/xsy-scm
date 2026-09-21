/*
 * 省 / 市 / 区选择的「路径 ↔ 编码 + 名称快照」双向转换（纯函数，可被 `node --test` 直接单测）
 *
 * 三张主档（customer / supplier / warehouse）各自存 6 列：三级编码 + 三级名称快照。
 * 名称只服务展示与导出，不参与任何关联，因此必须由**同一次选择**写入，不能事后按编码反查——
 * 区划会调整，快照要忠于当时。转换集中在这里，避免三个表单各写一遍下标拆解。
 *
 * 注意：`customer / supplier` 的 `empty*()` 初值各自显式列出六列，没有借用这里的函数——
 * 那两个模块要能被 `node --test` 直接加载，而本仓库纪律是 node 加载的模块不得有相对值导入。
 */

import type { AreaColumns, AreaNode } from '/@/types/business/scm/area';

const LEVELS = ['province', 'city', 'district'] as const;

/** 6 列的联合键；直接写 `columns[codeKey(..)]` 会被 TS 收窄成 `null`，故统一经 `Draft` 赋值。 */
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

/**
 * 已存的 6 列还原成级联显示值，供编辑表单回填。
 *
 * 遇到第一个缺失的层级即停止：`a-cascader` 的取值必须是一条连续路径，
 * 拼成「省 + 空 + 区」会让组件显示异常。存量行只解析到省时返回单元素数组。
 */
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
    nodes.push({ value: Number(code), label: String(name) });
  }
  return nodes;
}
