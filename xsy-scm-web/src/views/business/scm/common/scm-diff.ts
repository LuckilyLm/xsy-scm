/**
 * SCM 域审计快照差异工具：把操作日志的 `beforeData` / `afterData` 全量快照
 * 整理成字段级差异表（一行一个字段，只标注是否变更）。
 *
 * 不翻译值：日志是审计证据，把 `311` 渲染成供应商名会引入一层可能与当时不一致的
 * 实时查询，把 `DRAFT` 猜成中文词会在枚举演进后失真 —— 展示层拿 `fields` 里的字段名
 * 自行交给枚举去翻。`null` / `undefined` 一律渲染 `—`，不做值猜测性解析；
 * 数组按行比较（逐元素铺平会炸行数），配不上的标为新增 / 移除。
 *
 * 对象值（如订单的地址快照）展开成多行「键: 值」，不再甩一行 JSON —— 同样不翻译内容，
 * 只是把 17 个键的压缩 JSON 换成能逐行扫读的排版，空的键合并计数。
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
        // 对象值不再甩成一行三百多字的 JSON（如订单的地址快照：17 个键、大半是 null）。
        return objectLines(value as Record<string, unknown>);
    }
    return String(value);
}

/**
 * 对象值展开成多行「键: 值」。
 *
 * 只换排版、不换内容：审计证据仍逐字可见，也不做任何值翻译。
 * 空的键不逐行占位（地址快照 17 个键里 9 个是 null，逐行铺开只会把表格撑高），
 * 合并成一句「（其余 N 项为空）」——信息没丢，但一眼能扫到有值的部分。
 *
 * 单元格的 `.scm-diff-value` 是 `white-space: pre-wrap`，这里的 `\n` 会逐行渲染。
 */
function objectLines(value: Record<string, unknown>): string {
    const entries = Object.entries(value);
    if (!entries.length) {
        return '—';
    }
    const lines: string[] = [];
    let emptyCount = 0;
    for (const [key, raw] of entries) {
        if (raw === null || raw === undefined || raw === '') {
            emptyCount += 1;
            continue;
        }
        // 再往里嵌套的对象 / 数组仍给紧凑 JSON：不递归，免得把表格撑成一棵树。
        lines.push(`${key}: ${typeof raw === 'object' ? JSON.stringify(raw) : String(raw)}`);
    }
    if (emptyCount) {
        lines.push(`（其余 ${emptyCount} 项为空）`);
    }
    return lines.length ? lines.join('\n') : '—';
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

/** 找出这一行会用作标识的键名（与 identityOf 同一优先级）。 */
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
            rows.push({identity: id, beforeExists: true, afterExists: true, fields: rowFields(row, a[ai])});
            return;
        }
        rows.push({identity: id, beforeExists: true, afterExists: false, fields: rowFields(row, undefined)});
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

    return {key, rows};
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
        fields.push({key, before: show(b[key]), after: show(a[key]), changed: differs(b[key], a[key])});
    }

    return {fields, arrays, changedCount: fields.filter((f) => f.changed).length};
}
