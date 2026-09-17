/**
 * SCM 域的**审计快照差异**工具（跨模块共享）。
 *
 * ## 为什么需要它
 *
 * 所有 SCM 操作日志（采购单 / 订单 / 价格）都用 `beforeData` / `afterData` 两个
 * JSONB 全量快照记录变更。直接 `JSON.stringify(..., null, 2)` 堆进 `<pre>` 有三个问题：
 *
 * 1. **看不出改了什么** —— 全量快照里绝大部分字段没变，用户要自己逐行比对；
 * 2. **长** —— 采购单快照带 `items` + 每条 item 的 `allocations`，几百行 JSON，
 *    抽屉里要滚动很久，且真正的变更点被淹没；
 * 3. **难读** —— `{"status":"DRAFT"}` 这样的原始键值对不如「状态：草稿 → 已提交」。
 *
 * 因此这里把快照整理成**字段级差异表**：一行一个字段，只标注是否变更。
 * 这与 W3 `price-history-list.vue` 已有的「字段 / 变更前 / 变更后」三列表格是同一形态，
 * 只是补上了「是否变更」的判定与数组支持。
 *
 * ## 刻意的取舍
 *
 * - **不翻译值**。快照里存的是原始值（`status` 是 `'DRAFT'`，`supplierId` 是 `311`），
 *   本模块**不做**枚举文案映射，也不拿 id 去查名称。原因是日志是**审计证据**：
 *   把 `311` 渲染成「某某供应商」会引入一层可能与当时不一致的实时查询，
 *   而把 `DRAFT` 猜成某个中文词则可能在枚举演进后失真。
 *   展示层拿 `fields` 里的字段名（如 `status`）自行交给枚举去翻，比在这里硬编码更稳。
 * - **不做值猜测性解析**（同 `scm-display.ts` 的纪律）：`null` / `undefined` 一律渲染成
 *   `—`，其余值走 `JSON.stringify`，不尝试把字符串数字变回数字。
 * - **数组单独处理**：`items` 这类数组元素整体在变，逐元素铺平会炸行数。
 *   因此数组按**行**比较：能配上的配对比较，配不上的标为新增/移除，
 *   再把每一行的**明细字段**摊到嵌套表里。
 */

/** 差异表的一行：一个标量字段，或一个数组字段。 */
export interface DiffField {
  /** 快照里的原始键名（不翻译，保留可追溯性）。 */
  key: string;
  /** 变更前的展示值。 */
  before: string;
  /** 变更后的展示值。 */
  after: string;
  /** 是否发生变化（数组字段只要内容不同即为 `true`）。 */
  changed: boolean;
}

/** 数组字段展开后的一行。 */
export interface DiffChild {
  /** 行标识：优先取业务键（`skuId` / `allocationId` …），取不到则退回下标。 */
  identity: string;
  /** 该行在变更前是否存在。 */
  beforeExists: boolean;
  /** 该行在变更后是否存在。 */
  afterExists: boolean;
  /** 该行内的字段变化。 */
  fields: DiffField[];
}

/** 数组字段的展开结果。 */
export interface DiffArray {
  key: string;
  /** 行集合。新增行 `beforeExists=false`，移除行 `afterExists=false`。 */
  rows: DiffChild[];
}

/** 一次快照比较的完整结果。 */
export interface DiffResult {
  /** 标量字段（含对象字段，拍平成 JSON 串比较）。 */
  fields: DiffField[];
  /** 数组字段的逐行展开。 */
  arrays: DiffArray[];
  /** 发生变化的标量字段数（不含数组内部行数变化）。 */
  changedCount: number;
}

/** 值的展示形式：`null` / `undefined` / 空串 → `—`。 */
function show(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  if (typeof value === 'object') {
    // 对象/嵌套数组落到这里说明它没被上层按数组处理（如 `Map` 里塞了个对象）。
    // 不递归展开，直接给紧凑 JSON，避免展示层出现 `[object Object]`。
    return JSON.stringify(value);
  }
  return String(value);
}

/** 值是否变化。走 `JSON.stringify` 而非 `!==`，以覆盖对象与数组的内容比较。 */
function differs(a: unknown, b: unknown): boolean {
  if (a === b) {
    return false;
  }
  if (a === null || a === undefined || b === null || b === undefined) {
    // 到这里说明两者不相等，且至少有一个是空值 → 视为变化。
    // 但 `null` 与 `undefined` 之间不算变化（都是「无值」）。
    return (a ?? null) !== (b ?? null);
  }
  if (typeof a !== typeof b) {
    return true;
  }
  return JSON.stringify(a) !== JSON.stringify(b);
}

/** 行标识的候选键，按优先级排列。 */
const IDENTITY_KEYS = ['skuId', 'allocationId', 'demandId', 'id', 'receiptItemId', 'returnId'];

/** 给数组元素找一个稳定的行标识，用于跨快照配对。 */
function identityOf(row: unknown, index: number): string {
  if (row && typeof row === 'object') {
    const record = row as Record<string, unknown>;
    for (const key of IDENTITY_KEYS) {
      const value = record[key];
      if (value !== null && value !== undefined && value !== '') {
        return `${key}=${String(value)}`;
      }
    }
  }
  return `#${index}`;
}

/** 把一行对象拍平成 `DiffField[]`（忽略其中的嵌套数组，嵌套数组不再递归展开）。 */
function fieldsOf(before: unknown, after: unknown): DiffField[] {
  const b = (before && typeof before === 'object' ? before : {}) as Record<string, unknown>;
  const a = (after && typeof after === 'object' ? after : {}) as Record<string, unknown>;
  const keys = [...new Set([...Object.keys(b), ...Object.keys(a)])];
  return keys
    .filter((key) => !Array.isArray(a[key]) && !Array.isArray(b[key]))
    .map((key) => ({
      key,
      before: show(b[key]),
      after: show(a[key]),
      changed: differs(b[key], a[key]),
    }));
}

/**
 * 行内字段：把「行标识」本身去掉。
 *
 * 行标识已经作为分组标题显示（如 `skuId=520`、`id=104`），
 * 再在明细里重复一行 `id 104 → 104` 只是噪音。
 */
function rowFields(before: unknown, after: unknown): DiffField[] {
  const identityKey = identityKeyOf(before) ?? identityKeyOf(after);
  return fieldsOf(before, after).filter((f) => f.key !== identityKey);
}

/** 找出这一行会用作标识的键名（与 {@link identityOf} 同一优先级）。 */
function identityKeyOf(row: unknown): string | undefined {
  if (!row || typeof row !== 'object') {
    return undefined;
  }
  const record = row as Record<string, unknown>;
  return IDENTITY_KEYS.find((key) => {
    const value = record[key];
    return value !== null && value !== undefined && value !== '';
  });
}

/** 数组的逐行比较：按行标识配对，两侧都出现过的做字段级比较。 */
function arrayOf(key: string, before: unknown, after: unknown): DiffArray {
  const b = Array.isArray(before) ? before : [];
  const a = Array.isArray(after) ? after : [];
  const bSeen = new Set<number>();
  const aSeen = new Set<number>();
  const rows: DiffChild[] = [];

  // 先在 after 里为每个 before 行找同标识的行；找不到即为「被移除」。
  b.forEach((row, bi) => {
    const id = identityOf(row, bi);
    const ai = a.findIndex((candidate, index) => !aSeen.has(index) && identityOf(candidate, index) === id);
    if (ai >= 0) {
      aSeen.add(ai);
      bSeen.add(bi);
      rows.push({ identity: id, beforeExists: true, afterExists: true, fields: rowFields(row, a[ai]) });
      return;
    }
    rows.push({ identity: id, beforeExists: true, afterExists: false, fields: rowFields(row, undefined) });
  });

  // after 里剩下的都是「新增」。
  a.forEach((row, ai) => {
    if (aSeen.has(ai)) {
      return;
    }
    rows.push({
      identity: identityOf(row, ai),
      beforeExists: false,
      afterExists: true,
      fields: rowFields(undefined, row),
    });
  });

  return { key, rows };
}

/**
 * 比较两个快照，产出可渲染的差异结构。
 *
 * @param before 变更前快照（`null` 表示无前态，如 `CREATE` / `DEMAND_GENERATE`）
 * @param after  变更后快照
 *
 * @example
 * // SUBMIT: { status: 'DRAFT' } -> { status: 'SUBMITTED' }
 * diffSnapshot({ status: 'DRAFT' }, { status: 'SUBMITTED' })
 * // { fields: [{ key: 'status', before: 'DRAFT', after: 'SUBMITTED', changed: true }],
 * //   arrays: [], changedCount: 1 }
 */
export function diffSnapshot(before: unknown, after: unknown): DiffResult {
  const b = (before && typeof before === 'object' ? before : {}) as Record<string, unknown>;
  const a = (after && typeof after === 'object' ? after : {}) as Record<string, unknown>;

  const keys = [...new Set([...Object.keys(b), ...Object.keys(a)])];
  const fields: DiffField[] = [];
  const arrays: DiffArray[] = [];

  for (const key of keys) {
    const isArray = Array.isArray(a[key]) || Array.isArray(b[key]);
    if (isArray) {
      const expanded = arrayOf(key, b[key], a[key]);
      // 空数组对空数组没有可展示内容，跳过以免出现空白分组。
      if (expanded.rows.length) {
        arrays.push(expanded);
      }
      continue;
    }
    fields.push({ key, before: show(b[key]), after: show(a[key]), changed: differs(b[key], a[key]) });
  }

  return { fields, arrays, changedCount: fields.filter((f) => f.changed).length };
}
