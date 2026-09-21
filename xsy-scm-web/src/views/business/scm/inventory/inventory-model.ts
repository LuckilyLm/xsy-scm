/**
 * W6 库存域的**纯函数**（新增文件，仿 W5 `purchase-form-model.ts` 的取向：把纪律放进可单测的函数，
 * 而不是散在模板里的三元表达式）。
 *
 * 三块职责：
 * 1. **Q12 默认仓库**：{@link singleWarehouseDefault} —— 恰好只有一个启用仓库时默认带出它，
 *    否则**不自动选任何一个**。这是「多仓下随便选一个仓会让人误以为在看全部库存」的防线，
 *    因此必须是一条可断言的规则，而不是页面里的一个 `if`。
 * 2. **三态展示**（A18 同族）：`null` / `undefined` → `—`，`"0.0000"` → `0.0000`。
 *    「没有值」与「值是零」是两种不同事实，合并显示会掩盖数据问题。
 * 3. **枚举文案**：`movementType` 的中文描述；枚举缺 desc 时回落到原值，不显示空白。
 *
 * 本文件**不依赖 Vue、不发请求**，因此可以被 `node --test` 直接导入。
 *
 * <p><b>为什么这里只允许「类型导入」而不能值导入常量</b>：本模块会被
 * `node --experimental-strip-types --test` 加载，而 node 的 ESM 解析**不做扩展名补全**
 * —— 值导入必须写 `.ts` 后缀；但本项目 `tsconfig` 未开启 `allowImportingTsExtensions`，
 * 值导入写 `.ts` 会触发 TS5097（见 `tools/ts_baseline_ratchet.py` 的 SCM 零错误区）。
 * 两个约束的交集就是：**node 可加载的模块只能有 type-only 的相对导入**（会被类型擦除）。
 * 因此枚举文案由调用方传入（见 {@link movementTypeText}），而不是在这里 import 常量。
 */
import type {Id} from './inventory-types.ts';

/** 仓库的最小形状（只用到 id，避免把整个 `Warehouse` 类型拖进来）。 */
export interface WarehouseLike {
    id: Id;
}

/**
 * Q12：系统**恰好只有 1 个**启用仓库时，返回它的 id 作为余额页的默认筛选；否则返回 `undefined`。
 *
 * <p>为什么 `!= 1` 时**不**选任何一个：多仓场景下自动选中某一个仓，用户看到的余额只是
 * 那一个仓的，但他会以为自己在看全部 —— 这种误解比多一次点击昂贵得多。
 * 0 个仓库时同样不选（没什么可选，且 0 个仓库本身就是异常状态，页面应当以空态呈现）。
 *
 * @param warehouses `GET /scm/warehouse/list` 的返回（只含启用仓库）
 */
export function singleWarehouseDefault(
    warehouses: WarehouseLike[] | null | undefined
): Id | undefined {
    if (!warehouses || warehouses.length !== 1) {
        return undefined;
    }
    return warehouses[0].id;
}

/**
 * 三态数量 / 金额的展示文本。
 *
 * `null` / `undefined` / 空串 → `—`；其余**原样返回**（后端已保证是 4 位定点字符串，
 * 前端不做二次格式化，否则会与后端口径分叉）。
 */
export function quantityText(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return value;
}

/**
 * 金额 / 均价的展示文本（V34）。
 *
 * **与 `quantityText` 一样是「null 安全 + 原样透传」，刻意不做二次换算**：
 * 千分位、补零、四舍五入都已经在源头定好 —— 均价由后端按 4 位小数存，
 * 金额由 SQL `ROUND(..., 2)` 收敛到 2 位。前端再格式化一次就会出现
 * 「同一笔钱在列表和详情里位数不同」这类只能靠肉眼发现的问题。
 *
 * 唯一的例外是 `null` → `—`：它表示「没有这个事实」，与 `"0.00"`（真的是零）必须区分。
 */
export function moneyText(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return value;
}

/**
 * `specValues`（JSONB，形状 `{"规格":"散装"}`）→ 可读文本。
 *
 * 无值或空对象 → `—`。刻意不抛错：规格是展示字段，坏形状不该让整页崩掉。
 */
export function specText(specValues: Record<string, unknown> | null | undefined): string {
    if (!specValues) {
        return '—';
    }
    const pairs = Object.entries(specValues).map(([key, value]) => `${key}：${String(value)}`);
    return pairs.length ? pairs.join('，') : '—';
}

/** 枚举文案表的最小结构（`SmartEnum` 的每一项都满足它；刻意不 import 常量，见文件头注释）。 */
export type EnumLabels = Record<string, { desc?: string } | undefined>;

/**
 * 流水类型的中文描述。
 *
 * 枚举里没有该值时**回落到原值**（而不是 `—`）：一个未知的类型字符串本身就是有用的信息
 * （说明后端加了新类型而前端还没跟上），把它显示成「—」会把这个信号藏起来。
 *
 * @param labels 枚举文案表（调用方传 `SCM_INVENTORY_MOVEMENT_TYPE_ENUM`）
 */
export function movementTypeText(
    value: string | null | undefined,
    labels: EnumLabels
): string {
    if (!value) {
        return '—';
    }
    return labels[value]?.desc || value;
}
