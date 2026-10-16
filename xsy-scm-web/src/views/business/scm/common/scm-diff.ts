/** 把操作日志的修改前后内容整理为字段差异；保留历史原值，不使用当前主档替换旧信息。 */
import {isHiddenChildAuditKey} from './scm-audit-hidden-keys';

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
    /** 标量字段；对象值已拍平成独立字段。 */
    fields: DiffField[];
    /** 数组字段的逐行展开。 */
    arrays: DiffArray[];
    /** 发生变化的标量字段数（不含数组内部行数变化）。 */
    changedCount: number;
}

/** 字段名 → 展示名。默认原样返回，展示层按需注入中文名。 */
export type LabelFn = (key: string) => string;

/** 值的展示形式：`null` / `undefined` / 空串 → `—`。 */
function show(value: unknown, labelOf: LabelFn): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    if (typeof value === 'object') {
        // 对象值不再甩成一行三百多字的 JSON（如订单的地址快照：17 个键、大半是 null）。
        return objectLines(value as Record<string, unknown>, labelOf);
    }
    return String(value);
}

/** 审计差异中的嵌套对象按字段展开，空值合并展示，内部技术字段过滤，不直接呈现大段 JSON。 */
function objectLines(value: Record<string, unknown>, labelOf: LabelFn): string {
    const entries = Object.entries(value).filter(([key]) => !isHiddenChildAuditKey(key));
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
        lines.push(`${labelOf(key)}: ${typeof raw === 'object' ? JSON.stringify(raw) : String(raw)}`);
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

/** 值是不是「一个对象」（数组不算，数组走逐行比较）。 */
function isPlainObject(value: unknown): value is Record<string, unknown> {
    return !!value && typeof value === 'object' && !Array.isArray(value);
}

/** 操作日志中的地址等嵌套字段展开成可读的独立行，避免同名字段覆盖原有数据。 */
function pushFlattened(target: DiffField[], before: unknown, after: unknown, labelOf: LabelFn,
                       reserved: Set<string>): void {
    const b = isPlainObject(before) ? before : {};
    const a = isPlainObject(after) ? after : {};
    for (const inner of [...new Set([...Object.keys(b), ...Object.keys(a)])]) {
        if (isHiddenChildAuditKey(inner) || reserved.has(inner) || target.some((f) => f.key === inner)) continue;
        target.push({
            key: inner,
            before: show(b[inner], labelOf),
            after: show(a[inner], labelOf),
            changed: differs(b[inner], a[inner]),
        });
    }
}

/** 一次比较里既不是数组、也不是对象值的键；这些键名在拍平对象时不能被覆盖。 */
function scalarKeysOf(keys: string[], b: Record<string, unknown>, a: Record<string, unknown>): Set<string> {
    return new Set(keys.filter((key) => !Array.isArray(a[key]) && !Array.isArray(b[key])
        && !isPlainObject(a[key]) && !isPlainObject(b[key])));
}

/** 把一行对象拍平成 `DiffField[]`（忽略其中的嵌套数组，嵌套数组不再递归展开）。 */
function fieldsOf(before: unknown, after: unknown, labelOf: LabelFn): DiffField[] {
    const b = isPlainObject(before) ? before : {};
    const a = isPlainObject(after) ? after : {};
    const keys = [...new Set([...Object.keys(b), ...Object.keys(a)])];
    const reserved = scalarKeysOf(keys, b, a);
    const fields: DiffField[] = [];
    for (const key of keys) {
        if (Array.isArray(a[key]) || Array.isArray(b[key])) {
            continue;
        }
        if (isPlainObject(a[key]) || isPlainObject(b[key])) {
            pushFlattened(fields, b[key], a[key], labelOf, reserved);
            continue;
        }
        fields.push({key, before: show(b[key], labelOf), after: show(a[key], labelOf), changed: differs(b[key], a[key])});
    }
    return fields;
}

/**
 * 行内字段：把「行标识」本身去掉。
 *
 * 行标识已经作为分组标题显示（如 `skuId=520`、`id=104`），
 * 再在明细里重复一行 `id 104 → 104` 只是噪音。
 */
function rowFields(before: unknown, after: unknown, labelOf: LabelFn): DiffField[] {
    const identityKey = identityKeyOf(before) ?? identityKeyOf(after);
    return fieldsOf(before, after, labelOf).filter((f) => f.key !== identityKey);
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
function arrayOf(key: string, before: unknown, after: unknown, labelOf: LabelFn): DiffArray {
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
            rows.push({identity: id, beforeExists: true, afterExists: true, fields: rowFields(row, a[ai], labelOf)});
            return;
        }
        rows.push({identity: id, beforeExists: true, afterExists: false, fields: rowFields(row, undefined, labelOf)});
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
            fields: rowFields(undefined, row, labelOf),
        });
    });

    return {key, rows};
}

/**
 * 比较两个快照，产出可渲染的差异结构。
 *
 * @param before  变更前快照（`null` 表示无前态，如 `CREATE` / `DEMAND_GENERATE`）
 * @param after   变更后快照
 * @param labelOf 字段名 → 展示名；对象值拍平后的键也走它。默认原样返回，保证
 *                `diffSnapshot` 不依赖任何文案表。
 *
 * @example
 * // SUBMIT: { status: 'DRAFT' } -> { status: 'SUBMITTED' }
 * diffSnapshot({ status: 'DRAFT' }, { status: 'SUBMITTED' })
 * // { fields: [{ key: 'status', before: 'DRAFT', after: 'SUBMITTED', changed: true }],
 * //   arrays: [], changedCount: 1 }
 */
export function diffSnapshot(before: unknown, after: unknown, labelOf: LabelFn = (key) => key): DiffResult {
    const b = (before && typeof before === 'object' ? before : {}) as Record<string, unknown>;
    const a = (after && typeof after === 'object' ? after : {}) as Record<string, unknown>;

    const keys = [...new Set([...Object.keys(b), ...Object.keys(a)])];
    const fields: DiffField[] = [];
    const arrays: DiffArray[] = [];
    const reserved = scalarKeysOf(keys, b, a);

    for (const key of keys) {
        const isArray = Array.isArray(a[key]) || Array.isArray(b[key]);
        if (isArray) {
            const expanded = arrayOf(key, b[key], a[key], labelOf);
            // 空数组对空数组没有可展示内容，跳过以免出现空白分组。
            if (expanded.rows.length) {
                arrays.push(expanded);
            }
            continue;
        }
        if (isPlainObject(a[key]) || isPlainObject(b[key])) {
            pushFlattened(fields, b[key], a[key], labelOf, reserved);
            continue;
        }
        fields.push({key, before: show(b[key], labelOf), after: show(a[key], labelOf), changed: differs(b[key], a[key])});
    }

    return {fields, arrays, changedCount: fields.filter((f) => f.changed).length};
}
