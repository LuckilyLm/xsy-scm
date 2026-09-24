/**
 * Finance R0 报表中心的**纯函数**层（新增文件，仿 `inventory/inventory-model.ts` 的取向：
 * 把纪律放进可单测的函数，而不是散在五个页面里的三元表达式）。
 *
 * 五块职责：
 *
 * 1. **日期口径**（计划 §28）：报表中心统一「本月」为默认区间，快捷区间只有
 *    昨日 / 本周 / 上周 / 本月 / 上月五个；周起点固定为**周一**（不是 dayjs 默认的周日），
 *    且「本周 / 本月」一律**截至今天**——把未来日子算进「已发生」的区间，
 *    会让趋势图上凭空多出一段零值尾巴，读起来像「这几天营业额掉到 0」。
 * 2. **查询装配**：`startDate` / `endDate` 由区间映射，空串一律转 `undefined` 后
 *    省略（后端 `@Pattern` 接受 null、拒绝 `''`，空串会把整次查询拒成 30001）。
 * 3. **Tab 隔离**：五个页面的 Tab 共享筛选、不共享分页；切 Tab 只把目标 Tab 的页码归 1。
 * 4. **三态展示**：`null`（没有这个事实）与 `"0.0000"`（真的是零）必须区分；
 *    图表取数时 `null` 保持 `null`（ECharts 断线），不塌成 0。
 * 5. **成本门禁**：无 `scm:report:cost:query` 时成本列整列消失，单元格回落 `—`。
 *
 * **本文件不依赖 Vue、不发请求、不 import 常量**，因此可以被 `node --test` 直接加载。
 * 方向集合 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES` 由调用方传入（见 {@link movementDirection}），
 * 理由与 `inventory-model.ts` 相同：node 的 ESM 解析不做扩展名补全，值导入相对路径要写
 * `.ts`，而本项目 `tsconfig` 未开 `allowImportingTsExtensions`（TS5097），
 * 两者交集就是**node 可加载的模块只能有 type-only 的相对导入**。
 */
import dayjs from 'dayjs';

/** 闭区间日期对，`yyyy-MM-dd`（后端按 Asia/Shanghai 日界解释）。 */
export type DateRange = [string, string];

/** 报表中心的快捷日期，取值固定五个（计划 §1）。 */
export type DatePresetKey = 'YESTERDAY' | 'THIS_WEEK' | 'LAST_WEEK' | 'THIS_MONTH' | 'LAST_MONTH';

/** 预设的中文名，同时作为 `a-range-picker` presets 的标签来源，避免两处各写一份。 */
export const DATE_PRESET_LABELS: Record<DatePresetKey, string> = {
    YESTERDAY: '昨日',
    THIS_WEEK: '本周',
    LAST_WEEK: '上周',
    THIS_MONTH: '本月',
    LAST_MONTH: '上月',
};

/** 报表中心的默认区间（计划 §28 选定「本月」）。 */
export const DEFAULT_DATE_PRESET: DatePresetKey = 'THIS_MONTH';

/** 单次查询的最大跨度（天，含首尾）；超过由后端显式报错，前端提前拦一道。 */
export const MAX_REPORT_RANGE_DAYS = 366;

const DATE_PATTERN = 'YYYY-MM-DD';

function day(value?: Date | string): dayjs.Dayjs {
    return value === undefined ? dayjs() : dayjs(value);
}

/**
 * 快捷区间：返回闭区间 `[start, end]` 的 `yyyy-MM-dd` 字符串。
 *
 * 周以周一为起点；`THIS_WEEK` / `THIS_MONTH` 的终点是**今天**而非周末 / 月末，
 * 见文件头第 1 条。
 *
 * @param today 基准日，默认取当前时间；单测传定值以获得确定性结果
 */
export function presetRange(preset: DatePresetKey, today?: Date | string): DateRange {
    const base = day(today);
    switch (preset) {
        case 'YESTERDAY': {
            const y = base.subtract(1, 'day');
            return [y.format(DATE_PATTERN), y.format(DATE_PATTERN)];
        }
        case 'THIS_WEEK': {
            // dayjs 的 day()：0=周日 … 6=周六；换算成「距本周周一的天数」
            const weekday = base.day();
            const sinceMonday = weekday === 0 ? 6 : weekday - 1;
            const monday = base.subtract(sinceMonday, 'day');
            return [monday.format(DATE_PATTERN), base.format(DATE_PATTERN)];
        }
        case 'LAST_WEEK': {
            const weekday = base.day();
            const sinceMonday = weekday === 0 ? 6 : weekday - 1;
            const lastMonday = base.subtract(sinceMonday + 7, 'day');
            return [lastMonday.format(DATE_PATTERN), lastMonday.add(6, 'day').format(DATE_PATTERN)];
        }
        case 'THIS_MONTH':
            return [base.startOf('month').format(DATE_PATTERN), base.format(DATE_PATTERN)];
        case 'LAST_MONTH': {
            const lastMonth = base.subtract(1, 'month');
            return [lastMonth.startOf('month').format(DATE_PATTERN), lastMonth.endOf('month').format(DATE_PATTERN)];
        }
        default:
            return defaultDateRange(today);
    }
}

/** 页面初始区间：本月 1 日 ~ 今天。 */
export function defaultDateRange(today?: Date | string): DateRange {
    return presetRange(DEFAULT_DATE_PRESET, today);
}

/**
 * `a-range-picker` 的 `presets` 数据源。
 *
 * 值给的是 `yyyy-MM-dd` **字符串**而不是 dayjs 对象：picker 上绑了
 * `value-format="YYYY-MM-DD"`，给对象会让组件在写回时再格式化一次，
 * 两条路径都产生同一个字符串没问题，但保持单一形状更好排查。
 */
export function datePresets(today?: Date | string): Array<{label: string; value: DateRange}> {
    return (Object.keys(DATE_PRESET_LABELS) as DatePresetKey[]).map((key) => ({
        label: DATE_PRESET_LABELS[key],
        value: presetRange(key, today),
    }));
}

/**
 * 区间跨度是否超过 {@link MAX_REPORT_RANGE_DAYS}（含首尾两天）。
 *
 * 返回错误文案而不是抛异常：页面把它接到 `error` ref 上，既挡住请求又能就地重试。
 * 区间不完整时返回 `''`（交给后端日期校验，前端不猜）。
 */
export function rangeOverLimitError(range: DateRange | undefined | null, maxDays = MAX_REPORT_RANGE_DAYS): string {
    if (!range || !range[0] || !range[1]) {
        return '';
    }
    const days = day(range[1]).diff(day(range[0]), 'day') + 1;
    return days > maxDays ? `查询跨度最多 ${maxDays} 天，当前 ${days} 天，请缩小日期区间` : '';
}

/** 单元格文本：`null` / `undefined` / 空串 → `—`，其余原样返回（定点数字符串不做二次格式化）。 */
export function textOrDash(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return value;
}

/**
 * 整数计数（订单数、客户数、SKU 种类数）的展示文本。
 *
 * 与定点数一样要区分三态：`null` 表示「后端没有这个事实」，`0` 表示「确实是零笔」。
 * 后端把 `COUNT(*)` 恒返回数字，但跨接口拼装的对象里计数字段可能缺席，
 * 这里统一按三态处理，**不把 undefined 显示成 0**。
 */
export function countText(value: number | null | undefined): string {
    if (value === null || value === undefined) {
        return '—';
    }
    return String(value);
}

/**
 * 图表取数：定点数字符串 → number，**`null` 保持 `null`**。
 *
 * 只服务坐标轴刻度与折线点位，不参与任何业务算式：金额、均价、数量的数值一律
 * 由后端算好，前端 `Number()` 一次只为画图，`moneyText` 仍按原字符串展示。
 * 非法串（脏数据）返回 `null` 而不是 0：把坏数据画成零值柱子，比画不出柱子更难查。
 */
export function chartValue(value: string | number | null | undefined): number | null {
    if (value === null || value === undefined || value === '') {
        return null;
    }
    const num = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(num) ? num : null;
}

/**
 * 成本列的展示文本：无成本权限时**恒为 `—`**。
 *
 * 后端已经对缺权限的调用者把成本字段置 `null`，这里再拦一道是纵深防御：
 * 万一某个 VO 忘了清空（成本字段散在多个报表行里，漏一处是真实风险），
 * 前端也不该把别人的成本金额显示给没有成本权限的人。
 */
export function costText(value: string | null | undefined, canSeeCost: boolean): string {
    if (!canSeeCost) {
        return '—';
    }
    return textOrDash(value);
}

/**
 * 「成本和不完整」提示：后端跳过了一些无成本流水时，聚合金额只是**部分和**。
 *
 * 不提示就会让用户把部分和当成全量入库成本，这类误读比多一行字昂贵得多。
 * 返回 `''` 表示无需提示。
 */
export function incompleteCostHint(missingCount: number | null | undefined, label: string): string {
    if (!missingCount || missingCount <= 0) {
        return '';
    }
    return `${label}已跳过 ${missingCount} 行无成本流水，不是全部${label}`;
}

/**
 * 按成本权限裁剪列：`canSeeCost` 为假时移除 `costIndexes` 列出的列。
 *
 * 隐藏整列而不是显示一串 `—`：一列全是 `—` 会让用户以为「数据还没进来」，
 * 而事实是「你不该看这一列」，两者要能被区分。
 *
 * 约束刻意只用 `object`、`dataIndex` 由访问器读出：ant-design-vue 的 `ColumnType`
 * 是四十多个成员且含递归 `children` 与泛型比较器的深类型，拿结构接口去约束它会触发
 * TS2589「类型实例化过深」，而泛型原样返回 `T[]` 又必须让调用方写断言。
 * 默认访问器读的就是 antd 列的 `dataIndex`，页面侧不必重复传。
 */
export function filterCostColumns<T extends object>(
    columns: T[],
    costIndexes: readonly string[],
    canSeeCost: boolean,
    dataIndexOf: (column: T) => unknown = (column) => (column as {dataIndex?: unknown}).dataIndex
): T[] {
    if (canSeeCost) {
        return columns;
    }
    return columns.filter((column) => {
        const index = dataIndexOf(column);
        return !(typeof index === 'string' && costIndexes.includes(index));
    });
}

/**
 * 由流水类型派生方向。
 *
 * @param inboundTypes 「入」方向类型集合，调用方传 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES`
 *                     （集合与后端 `ScmInventoryMovementTypeEnum.getInbound()` 同源，
 *                     报表侧不再自己判 IN / OUT 字符串）
 */
export function movementDirection(movementType: string | null | undefined, inboundTypes: readonly string[]): 'IN' | 'OUT' | null {
    if (!movementType) {
        return null;
    }
    return inboundTypes.includes(movementType) ? 'IN' : 'OUT';
}

/** 方向的中文文案；未知类型返回 `—`（与 {@link movementDirection} 的 null 分支一致）。 */
export function directionText(direction: 'IN' | 'OUT' | null): string {
    if (direction === 'IN') {
        return '入';
    }
    if (direction === 'OUT') {
        return '出';
    }
    return '—';
}

/** 枚举文案表的最小结构（`SmartEnum` 的每一项都满足它；刻意不 import 常量，见文件头注释）。 */
export type EnumLabels = Record<string, { desc?: string } | undefined>;

/**
 * 枚举的中文描述；命不中时**回落原值**。
 *
 * 一个未知的枚举串本身就是有用的信息（后端加了类型而前端没跟上），
 * 显示成 `—` 会把这个信号藏起来。与 `inventory-model.movementTypeText` 同一取向，
 * 这里给报表侧用，因为报表页要展示的枚举横跨订单来源、采购状态、流水类型三类。
 */
export function enumDescText(value: string | null | undefined, labels: EnumLabels): string {
    if (!value) {
        return '—';
    }
    return labels[value]?.desc || value;
}

/** 扁平选项表（`product-const` 那类 `{value, label}[]`）的文案；命不中回落原值。 */
export function optionLabel(
    options: ReadonlyArray<{value: string; label: string}>,
    value: string | null | undefined
): string {
    if (!value) {
        return '—';
    }
    return options.find((option) => option.value === value)?.label ?? value;
}

/** 布尔事实的文案：`null` / `undefined` 是「不知道」，与 `false` 的「否」必须可辨。 */
export function yesNoText(value: boolean | null | undefined): string {
    if (value === null || value === undefined) {
        return '—';
    }
    return value ? '是' : '否';
}

/**
 * 一个 Tab 的全部视图状态。
 *
 * **每个 Tab 一份**（不是共享一个对象）：共用 `pageNum` 时，A Tab 翻到第 3 页再切到 B Tab，
 * B 会直接落在第 3 页甚至空态 —— 这就是「Tab 串条件」。`rows` / `error` / `loading`
 * 分开也是同一个道理：A Tab 的报错横幅不该盖在 B Tab 的表格上。
 *
 * 共享的是**筛选**（日期区间 + 常用条件），它留在页面级对象上。
 */
export interface TabView<T> {
    pageNum: number;
    pageSize: number;
    rows: T[];
    total: number;
    loading: boolean;
    error: string;
}

/** 新建一个 Tab 的视图状态；初始 `rows` 为空数组而不是 `undefined`，模板因此不必到处判空。 */
export function createTabView<T>(pageSize = 20): TabView<T> {
    return {pageNum: 1, pageSize, rows: [], total: 0, loading: false, error: ''};
}

/**
 * 进入某个 Tab：只把该 Tab 的页码归 1，返回它供调用方立刻发请求。
 *
 * 刻意不动 `pageSize`（每页条数是用户的偏好，不是查询条件），也不动其它 Tab。
 */
export function enterTab<T>(view: TabView<T>): TabView<T> {
    view.pageNum = 1;
    return view;
}

/** 查询体里 `undefined` / `null` / `''` 的字段都被省略。 */
type LooseFilters = Record<string, unknown>;

function omitEmpty(source: LooseFilters): LooseFilters {
    const result: LooseFilters = {};
    for (const [key, value] of Object.entries(source)) {
        if (value === undefined || value === null || value === '') {
            continue;
        }
        result[key] = value;
    }
    return result;
}

/**
 * 装配报表查询体：日期区间 + 共享筛选（+ 可选的分页）。
 *
 * 三条规则：
 * - `startDate` / `endDate` 只从区间来，缺失时**省略字段**而不是传 `''`；
 * - 共享筛选里的 `''`（清空的下拉、空关键字）转成省略，避免后端 `@Pattern` 直接拒整次查询；
 * - 导出请求不带分页（传 `tab` 省略即可）：后端强制第 1 页 + 上限行，
 *   前端传分页只会让人误以为导出的是当前页。
 *
 * `T` 由调用方显式指定为对应的查询表单类型；内部是松散的键值装配，
 * 因此在出口做一次收口断言（字段合法性由调用方的 `filters` 类型保证）。
 */
export function buildReportQuery<T extends object>(
    range: DateRange | undefined | null,
    shared: LooseFilters,
    tab?: {pageNum: number; pageSize: number}
): T {
    // 日期区间最后写入：共享筛选里若混进同名字段（页面类型上不该出现），也不许盖掉区间
    const payload: LooseFilters = {...omitEmpty(shared)};
    if (range?.[0] && range?.[1]) {
        payload.startDate = range[0];
        payload.endDate = range[1];
    }
    if (tab) {
        payload.pageNum = tab.pageNum;
        payload.pageSize = tab.pageSize;
    }
    return payload as unknown as T;
}

/** 路由 query 的形状（`vue-router` 的 `LocationQuery` 的子集；同 {@link buildReportQuery} 刻意只用结构类型）。 */
export type RouteQuery = Record<string, string | (string | null)[] | null | undefined>;

function firstQueryValue(raw: RouteQuery[string]): string {
    const value = Array.isArray(raw) ? raw.find((item) => item !== null) : raw;
    return typeof value === 'string' ? value.trim() : '';
}

/**
 * 从路由 query 还原日期区间（概览页点击某日 → 跳到分析页并带上区间）。
 *
 * 两个日期必须**都给且是 `yyyy-MM-dd`** 才生效：URL 是用户可手改、可收藏转发的输入，
 * 半截区间或 `?startDate=<script>` 一律按「未提供」处理，让页面回落到默认的本月，
 * 不能把脏值透传给后端。
 */
export function rangeFromQuery(query: RouteQuery | undefined | null): DateRange | undefined {
    const startDate = firstQueryValue(query?.startDate);
    const endDate = firstQueryValue(query?.endDate);
    const valid = /^\d{4}-\d{2}-\d{2}$/.test(startDate) && /^\d{4}-\d{2}-\d{2}$/.test(endDate);
    return valid ? [startDate, endDate] : undefined;
}

/**
 * 某日 deep-link：概览页表格点一行 → 分析页查**这一天**。
 *
 * 起止同为一天，是闭区间语义下最小的区间；不要图省事只传 `startDate`，
 * 那样目标页会因 {@link rangeFromQuery} 校验不过而回落成本月，看起来像「跳转丢了参数」。
 */
export function dayRange(bizDate: string): DateRange {
    const normalized = /^\d{4}-\d{2}-\d{2}$/.test(bizDate) ? bizDate : defaultDateRange()[0];
    return [normalized, normalized];
}
