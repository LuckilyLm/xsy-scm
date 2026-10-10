<!--
  审计快照差异（跨模块共享）：把操作日志的 `beforeData` / `afterData` 全量快照
  渲染成人能读的字段级视图，替代裸 `JSON.stringify` 塞进 `<pre>`。

  三种形态，按数据自动切换：

  - 无前态（`CREATE` / `GENERATE` / `DEMAND_GENERATE`…）：这不是「对比」，
    而是「这条记录当时长什么样」。用描述列表列出有值的字段。
  - 有前后态：每处变化一行「字段　旧值 → 新值」，默认只列真正变化的字段，
    未变更但有值的字段收在「显示未变更的 N 项」后面。
  - 数组字段：无前态时按行铺成明细表；有前后态时逐行列出变化。

  为什么不是三列表格：字段名占一列、旧值新值各占一列时，内容一短就满屏空白格线，
  表头「字段 / 变更前 / 变更后」也在每行「旧值 → 新值」里被重复了一遍。
  改成对齐的行内写法后，宽度跟着内容走，也没有多余表头。

  其余约定：
  - 只读：这是审计证据，不提供任何编辑入口；
  - 字段名走 `scm-audit-field-labels` 的中文映射，原始键名保留在 `title` 里可追溯；
  - 技术字段（主键、外键、版本号、坐标…见 `scm-audit-hidden-keys`）整条不展示，
    用户要看的是状态、金额、数量这些业务事实；
  - 枚举码走 `scm-audit-enum-values` 翻成中文，ISO 时间戳重排为 `YYYY-MM-DD HH:mm:ss`，
    两者的原始串都留在 `title` 里；同名字段跨单据含义不同时由父组件的 `scope` 指定；
  - 完全受控：父组件传 `before` / `after`，不自持状态。
-->
<template>
  <div class="scm-diff">
    <template v-if="mode === 'empty'">
      <a-empty :image="simpleImage" description="该记录没有可对比的变更内容"/>
    </template>

    <!-- 无前态：呈现「记录时的内容」 -->
    <template v-else-if="mode === 'created'">
      <div class="scm-diff-head">
        <a-tag color="blue">初始记录</a-tag>
        <span class="scm-diff-head-note">该操作没有变更前快照，以下是记录时的内容</span>
      </div>
      <a-descriptions v-if="createdFields.length" bordered size="small" class="scm-diff-desc"
                      :column="{xs: 1, sm: 2, xl: 3}">
        <a-descriptions-item v-for="field in createdFields" :key="field.key" :label="field.label">
          <span class="scm-diff-value scm-cell-wrap" :title="field.after" :class="{ 'is-empty': field.after === '—' }">
            {{ field.after }}
          </span>
        </a-descriptions-item>
      </a-descriptions>
    </template>

    <!-- 有前后态：一行一处变化 -->
    <template v-else>
      <div class="scm-diff-head">
        <a-tag :color="changedCount ? 'orange' : 'default'">已变更 {{ changedCount }} 项</a-tag>
        <a-button v-if="quietFields.length" type="link" size="small" @click="showQuiet = !showQuiet">
          {{ showQuiet ? '只看变更项' : `显示未变更的 ${quietFields.length} 项` }}
        </a-button>
      </div>
      <div v-if="diffFields.length" class="scm-diff-lines">
        <template v-for="field in diffFields" :key="field.key">
          <span class="scm-diff-label" :class="{ 'is-quiet': !field.changed }" :title="field.key">
            {{ field.label }}
          </span>
          <span class="scm-diff-value scm-cell-wrap" :class="{ 'is-empty': field.before === '—', 'is-quiet': !field.changed }"
                :title="field.beforeRaw">{{ field.before }}</span>
          <span class="scm-diff-arrow" :class="{ 'is-quiet': !field.changed }">→</span>
          <span class="scm-diff-value scm-cell-wrap" :class="{ 'is-empty': field.after === '—', 'is-quiet': !field.changed }"
                :title="field.afterRaw">{{ field.after }}</span>
        </template>
      </div>
      <p v-if="!diffFields.length && !changedInArrays()" class="scm-diff-more">本次操作没有字段发生变化</p>
    </template>

    <!-- 数组字段：整组都没变化就不显示，否则只剩一个空标题 -->
    <section v-for="entry in shownGroups" :key="entry.group.key" class="scm-diff-group">
      <div class="scm-diff-group-title">
        <span class="scm-diff-group-name">{{ labelOf(entry.group.key) }}</span>
        <a-tag>{{ entry.rows.length }} / {{ entry.group.rows.length }} 行</a-tag>
      </div>

      <!-- 无前态：一张明细表，比逐行竖排好扫 -->
      <a-table v-if="mode === 'created'" class="scm-diff-table" size="small" bordered
               :data-source="rowsOf(entry.rows)" :columns="columnsOf(entry.rows)" row-key="__row"
               :pagination="false" :scroll="{x: arrayScroll(entry.rows)}">
        <template #bodyCell="{ column, record }">
          <!-- 单元格默认 nowrap + 省略号；快照值要能整段读，加 scm-cell-wrap 退出裁剪 -->
          <span v-if="column.dataIndex !== '__row'" class="scm-cell-wrap">{{ record[String(column.dataIndex)] }}</span>
        </template>
      </a-table>

      <!-- 有前后态：逐行列出变化 -->
      <template v-else>
        <div v-for="(row, index) in entry.rows" :key="`${entry.group.key}-${index}`" class="scm-diff-row">
          <div class="scm-diff-row-title">
            <span :title="row.identity">{{ rowTitle(row) }}</span>
            <a-tag v-if="!row.beforeExists" color="green">新增</a-tag>
            <a-tag v-else-if="!row.afterExists" color="red">移除</a-tag>
          </div>
          <div class="scm-diff-lines">
            <template v-for="field in visibleRowFields(row)" :key="field.key">
              <span class="scm-diff-label" :title="field.key">{{ field.label }}</span>
              <span class="scm-diff-value scm-cell-wrap" :class="{ 'is-empty': field.before === '—' }"
                    :title="field.beforeRaw">{{ field.before }}</span>
              <span class="scm-diff-arrow">→</span>
              <span class="scm-diff-value scm-cell-wrap" :class="{ 'is-empty': field.after === '—' }"
                    :title="field.afterRaw">{{ field.after }}</span>
            </template>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {Empty} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import dayjs from 'dayjs';
import {diffSnapshot, type DiffArray, type DiffChild, type DiffField} from '/@/views/business/scm/common/scm-diff';
import {auditFieldLabel} from '/@/views/business/scm/common/scm-audit-field-labels';
import {auditValueLabel} from '/@/views/business/scm/common/scm-audit-enum-values';
import {isHiddenAuditKey, isHiddenChildAuditKey} from '/@/views/business/scm/common/scm-audit-hidden-keys';

// `a-empty` 的 `image` 需要一个 URL；用 antd 内置的简洁插图，避免依赖外部图片资源。
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

const props = defineProps<{
  /** 变更前快照；`null` / `undefined` / 空对象表示无前态。 */
  before?: unknown;
  /** 变更后快照。 */
  after?: unknown;
  /** 覆盖或补充字段中文名；不传就用公共映射表。 */
  labels?: Record<string, string>;
  /** 这条日志的操作类型（如 `RETURN` / `REFUND`）；同名字段跨单据含义不同时用来选枚举。 */
  scope?: string;
}>();

/** 展示用的一行：在 DiffField 上补出中文名与原始值（供 title 追溯）。 */
interface FieldRow extends DiffField {
  label: string;
  beforeRaw: string;
  afterRaw: string;
}

const labelOf = (key: string) => props.labels?.[key] ?? auditFieldLabel(key);

/** 严格 ISO-8601（含 `Z` 或偏移、秒与小数秒可省）才重排，避免误伤普通字符串。 */
const ISO_DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})$/;

/**
 * 值的展示：枚举码先翻成中文，布尔值转「是 / 否」，ISO 时间戳再按全站口径重排为本地
 * `YYYY-MM-DD HH:mm:ss`，其余一律原样。原始串始终留在 `title` 里，审计可逐字回查。
 */
const BOOLEAN_TEXT: Record<string, string> = {true: '是', false: '否'};

function display(key: string, text: string): string {
  if (text === '—') return text;
  const labelled = auditValueLabel(key, text, props.scope);
  if (labelled !== text) return labelled;
  if (text === 'true' || text === 'false') return BOOLEAN_TEXT[text];
  if (!ISO_DATE_TIME.test(text)) return text;
  const date = dayjs(new Date(text));
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : text;
}

function toRow(field: DiffField): FieldRow {
  return {...field, label: labelOf(field.key), beforeRaw: field.before, afterRaw: field.after,
          before: display(field.key, field.before), after: display(field.key, field.after)};
}

const result = computed(() => diffSnapshot(props.before, props.after, labelOf));

/** 有前态 = before 是个非空对象。空对象与 null 一样视为「没有变更前记录」。 */
const hasBefore = computed(() => !!props.before && typeof props.before === 'object'
    && Object.keys(props.before as object).length > 0);

const mode = computed(() => {
  if (!result.value.fields.length && !result.value.arrays.length) return 'empty';
  return hasBefore.value ? 'diff' : 'created';
});

/** 系统审计列排在业务字段之后：先看「这条单是什么」，再看「谁在什么时候改的」。 */
const SYSTEM_KEYS = new Set(['id', 'version', 'deleted', 'sortOrder', 'createdAt', 'createdBy', 'updatedAt', 'updatedBy']);

const allFields = computed<FieldRow[]>(() => {
  const rows = result.value.fields.filter((f) => !isHiddenAuditKey(f.key)).map(toRow);
  return [...rows.filter((r) => !SYSTEM_KEYS.has(r.key)), ...rows.filter((r) => SYSTEM_KEYS.has(r.key))];
});

/** 无前态：只列有值的字段。 */
const createdFields = computed(() => allFields.value.filter((f) => f.after !== '—'));

const changedFields = computed(() => allFields.value.filter((f) => f.changed));
const quietFields = computed(() => allFields.value.filter((f) => !f.changed && (f.before !== '—' || f.after !== '—')));

/**
 * 明细行里显示出来的变化条数。
 *
 * `changedCount` 只看顶层标量字段，会漏掉「只改了明细行」的操作（如实重录入：顶层一个字段都没变，
 * 变的全在 `items` 里）—— 那样表头会写着「已变更 0 项」，下面却列着几行变化。
 */
function changedInArrays(): number {
  return result.value.arrays.reduce((total, group) => total
      + group.rows.reduce((n, row) => n + visibleRowFields(row).length, 0), 0);
}

const changedCount = computed(() => changedFields.value.length + changedInArrays());

const showQuiet = ref(false);
const diffFields = computed(() => showQuiet.value ? [...changedFields.value, ...quietFields.value] : changedFields.value);

// ---- 数组：无前态时的明细表 ----

/**
 * 明细表的列：只保留「至少一行有实际内容」的键。
 *
 * 全空的列是审计噪音；整列都是 `false` 的布尔列（如每行「人工改价 = 否」）同样是 ——
 * 没有任何一行偏离默认值，说明这列没有信息，不该占一个表头。
 */
function groupKeys(rows: DiffChild[]): string[] {
  const keys: string[] = [];
  for (const row of rows) {
    for (const field of row.fields) {
      if (field.after === '—' || field.after === 'false') continue;
      if (isHiddenChildAuditKey(field.key) || keys.includes(field.key)) continue;
      keys.push(field.key);
    }
  }
  return keys;
}

function rowsOf(rows: DiffChild[]): Record<string, string>[] {
  const keys = groupKeys(rows);
  return rows.map((row, index) => {
    const record: Record<string, string> = {__row: String(index + 1)};
    for (const key of keys) {
      const field = row.fields.find((f) => f.key === key);
      record[key] = field ? display(key, field.after) : '—';
    }
    return record;
  });
}

function columnsOf(rows: DiffChild[]): TableColumnsType<Record<string, string>> {
  return [
    {title: '#', dataIndex: '__row', width: 56, align: 'center'},
    ...groupKeys(rows).map((key) => ({title: labelOf(key), dataIndex: key, width: 160})),
  ];
}

function arrayScroll(rows: DiffChild[]): number {
  return 56 + groupKeys(rows).length * 160;
}

/** 有前后态时只保留真正有变化的行：没变化的行只剩一个标题，比不显示更费解。 */
function shownRows(group: DiffArray): DiffChild[] {
  return group.rows.filter((row) => visibleRowFields(row).length > 0);
}

/** 整个数组都没变化就整组不显示，否则会留下一排只有标题的空分组。 */
const shownGroups = computed(() => result.value.arrays
    .map((group) => ({group, rows: shownRows(group)}))
    .filter((entry) => entry.rows.length > 0));

// ---- 数组：有前后态时的逐行变化 ----

/**
 * 行标题：优先用行内的人类可读快照（商品名称 / 商品规格编码…），
 * 只在拿不到可读名时才退回行标识本身 —— `商品规格 ID：2034` 对用户没有意义。
 * 原始标识串留在 `title` 里可追溯。
 */
const ROW_TITLE_KEYS = ['productNameSnapshot', 'skuCodeSnapshot', 'spuCodeSnapshot', 'specNameSnapshot',
  'customerNameSnapshot', 'supplierNameSnapshot', 'counterpartyNameSnapshot', 'skuNameSnapshot'];

function rowTitle(row: DiffChild): string {
  for (const key of ROW_TITLE_KEYS) {
    const field = row.fields.find((f) => f.key === key);
    const text = field && field.after !== '—' ? field.after : field?.before;
    if (text && text !== '—') return `${labelOf(key)}：${text}`;
  }
  const at = row.identity.indexOf('=');
  return at > 0 ? `${labelOf(row.identity.slice(0, at))}：${row.identity.slice(at + 1)}` : row.identity;
}

/**
 * 单行对比里只看变化项；新增 / 移除行没有可比较的一侧，列出有值的那一侧
 * （处于默认值的布尔字段不列 —— 「人工改价 否」对读的人没有信息）。
 */
function visibleRowFields(row: DiffChild): FieldRow[] {
  const rows = row.fields.filter((f) => !isHiddenChildAuditKey(f.key)).map(toRow);
  if (!row.beforeExists || !row.afterExists) {
    return rows.filter((f) => {
      const value = f.afterRaw !== '—' ? f.afterRaw : f.beforeRaw;
      return value !== '—' && value !== 'false';
    });
  }
  return rows.filter((f) => f.changed);
}
</script>

<style scoped>
.scm-diff-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.scm-diff-head-note {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.scm-diff-more {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
  margin: 8px 0 0;
}

.scm-diff-desc {
  margin-bottom: 4px;
}

/*
  变化行：四列网格（字段 / 旧值 / 箭头 / 新值）。
  用 `max-content` 而不是固定宽度，列宽跟着内容走 —— 短值不会撑出一片空白，
  长值也不会被压到换行；各行的旧值、新值仍然纵向对齐。
*/
.scm-diff-lines {
  display: grid;
  grid-template-columns: max-content max-content max-content minmax(0, 1fr);
  gap: 6px 10px;
  align-items: baseline;
  margin-bottom: 12px;
}

.scm-diff-label {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  white-space: nowrap;
}

.scm-diff-arrow {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

/* 未变更项（点开「显示未变更的 N 项」后出现）整体弱化，让变化项先被看到。 */
.scm-diff-label.is-quiet,
.scm-diff-value.is-quiet,
.scm-diff-arrow.is-quiet {
  color: rgba(0, 0, 0, 0.3);
}

.scm-diff-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 空值（—）弱化，避免把「没有值」读成「值是破折号」。 */
.scm-diff-value.is-empty {
  color: rgba(0, 0, 0, 0.25);
}

.scm-diff-table {
  margin-bottom: 12px;
}

/* 无前态的明细表随内容收窄，不被容器撑到满宽留出空白。 */
.scm-diff-table :deep(.ant-table-container),
.scm-diff-table :deep(.ant-table-content) {
  width: fit-content;
  max-width: 100%;
}

/*
  数组分组：给一个左边框 + 缩进，让逐行明细在视觉上从属于这个数组字段，
  否则会和上层的标量字段连成一片，分不清层级。
*/
.scm-diff-group {
  margin: 16px 0;
  padding-left: 12px;
  border-left: 3px solid #d9d9d9;
}

.scm-diff-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  margin-bottom: 8px;
}

.scm-diff-row + .scm-diff-row {
  margin-top: 12px;
}

.scm-diff-row-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 6px;
}
</style>
