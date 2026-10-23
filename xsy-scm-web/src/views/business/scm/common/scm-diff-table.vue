<!-- 操作日志用业务字段显示新增内容或“修改前 → 修改后”；只读。 -->
<template>
  <div class="scm-diff">
    <template v-if="mode === 'empty'">
      <a-empty :image="simpleImage" description="该记录没有可对比的变更内容" />
    </template>

    <!-- 无前态：呈现「记录时的内容」 -->
    <template v-else-if="mode === 'created'">
      <div class="scm-diff-head">
        <a-tag color="blue">初始记录</a-tag>
        <span class="scm-diff-head-note">该操作没有变更前内容，以下是操作时的记录</span>
      </div>
      <a-descriptions v-if="createdFields.length" bordered size="small" class="scm-diff-desc" :column="{xs: 1, sm: 2, xl: 3}">
        <a-descriptions-item v-for="field in createdFields" :key="field.key" :label="field.label">
          <span class="scm-diff-value scm-cell-wrap" :title="field.after" :class="{'is-empty': field.after === '—'}">
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
          <span class="scm-diff-label" :class="{'is-quiet': !field.changed}" :title="field.key">
            {{ field.label }}
          </span>
          <span
            class="scm-diff-value scm-cell-wrap"
            :class="{'is-empty': field.before === '—', 'is-quiet': !field.changed}"
            :title="field.beforeRaw"
            >{{ field.before }}</span
          >
          <span class="scm-diff-arrow" :class="{'is-quiet': !field.changed}">→</span>
          <span class="scm-diff-value scm-cell-wrap" :class="{'is-empty': field.after === '—', 'is-quiet': !field.changed}" :title="field.afterRaw">{{
            field.after
          }}</span>
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
      <a-table
        v-if="mode === 'created'"
        class="scm-diff-table"
        size="small"
        bordered
        :data-source="rowsOf(entry.rows)"
        :columns="columnsOf(entry.rows)"
        row-key="__row"
        :pagination="false"
        :scroll="{x: arrayScroll(entry.rows)}"
      >
        <template #bodyCell="{column, record}">
          <!-- 允许换行，不被省略 -->
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
              <span class="scm-diff-value scm-cell-wrap" :class="{'is-empty': field.before === '—'}" :title="field.beforeRaw">{{
                field.before
              }}</span>
              <span class="scm-diff-arrow">→</span>
              <span class="scm-diff-value scm-cell-wrap" :class="{'is-empty': field.after === '—'}" :title="field.afterRaw">{{ field.after }}</span>
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

// a-empty 需要图片 URL，用 antd 内置插图
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

const props = defineProps<{
  /** 变更前的数据；空对象表示无前态。 */
  before?: unknown;
  /** 变更后的数据。 */
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

/** 枚举值转中文，布尔值转“是/否”，ISO 时间重排；原始值保留在 title 中。 */
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
  return {
    ...field,
    label: labelOf(field.key),
    beforeRaw: field.before,
    afterRaw: field.after,
    before: display(field.key, field.before),
    after: display(field.key, field.after),
  };
}

const result = computed(() => diffSnapshot(props.before, props.after, labelOf));

/** before 是非空对象才有前态；空对象和 null 一样视为无前态。 */
const hasBefore = computed(() => !!props.before && typeof props.before === 'object' && Object.keys(props.before as object).length > 0);

const mode = computed(() => {
  if (!result.value.fields.length && !result.value.arrays.length) return 'empty';
  return hasBefore.value ? 'diff' : 'created';
});

/** 系统审计列排在业务字段之后。 */
const SYSTEM_KEYS = new Set(['id', 'version', 'deleted', 'sortOrder', 'createdAt', 'createdBy', 'updatedAt', 'updatedBy']);

const allFields = computed<FieldRow[]>(() => {
  const rows = result.value.fields.filter((f) => !isHiddenAuditKey(f.key)).map(toRow);
  return [...rows.filter((r) => !SYSTEM_KEYS.has(r.key)), ...rows.filter((r) => SYSTEM_KEYS.has(r.key))];
});

/** 无前态：只列有值的字段。 */
const createdFields = computed(() => allFields.value.filter((f) => f.after !== '—'));

const changedFields = computed(() => allFields.value.filter((f) => f.changed));
const quietFields = computed(() => allFields.value.filter((f) => !f.changed && (f.before !== '—' || f.after !== '—')));

/** 明细行中的变化数，避免只改明细时表头显示 0 项。 */
function changedInArrays(): number {
  return result.value.arrays.reduce((total, group) => total + group.rows.reduce((n, row) => n + visibleRowFields(row).length, 0), 0);
}

const changedCount = computed(() => changedFields.value.length + changedInArrays());

const showQuiet = ref(false);
const diffFields = computed(() => (showQuiet.value ? [...changedFields.value, ...quietFields.value] : changedFields.value));

// ---- 数组：无前态时的明细表 ----

/** 明细表只保留至少一行有内容的列。 */
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
  return [{title: '#', dataIndex: '__row', width: 56}, ...groupKeys(rows).map((key) => ({title: labelOf(key), dataIndex: key, width: 160}))];
}

function arrayScroll(rows: DiffChild[]): number {
  return 56 + groupKeys(rows).length * 160;
}

/** 有前后态时只保留有变化的行。 */
function shownRows(group: DiffArray): DiffChild[] {
  return group.rows.filter((row) => visibleRowFields(row).length > 0);
}

/** 整组没有变化就不显示。 */
const shownGroups = computed(() => result.value.arrays.map((group) => ({group, rows: shownRows(group)})).filter((entry) => entry.rows.length > 0));

// ---- 数组：有前后态时的逐行变化 ----

/** 行标题优先用商品名、规格编码等可读值，取不到再用行标识。 */
const ROW_TITLE_KEYS = [
  'productNameSnapshot',
  'skuCodeSnapshot',
  'spuCodeSnapshot',
  'specNameSnapshot',
  'customerNameSnapshot',
  'supplierNameSnapshot',
  'counterpartyNameSnapshot',
  'skuNameSnapshot',
];

function rowTitle(row: DiffChild): string {
  for (const key of ROW_TITLE_KEYS) {
    const field = row.fields.find((f) => f.key === key);
    const text = field && field.after !== '—' ? field.after : field?.before;
    if (text && text !== '—') return `${labelOf(key)}：${text}`;
  }
  const at = row.identity.indexOf('=');
  return at > 0 ? `${labelOf(row.identity.slice(0, at))}：${row.identity.slice(at + 1)}` : row.identity;
}

/** 已有行只显示变化字段；新增或移除行显示有值的一侧。 */
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

/* 四列网格：字段、旧值、箭头、新值；列宽随内容变化。 */
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

/* 未变更项弱化显示 */
.scm-diff-label.is-quiet,
.scm-diff-value.is-quiet,
.scm-diff-arrow.is-quiet {
  color: rgba(0, 0, 0, 0.3);
}

.scm-diff-value {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 空值弱化，避免误读成破折号 */
.scm-diff-value.is-empty {
  color: rgba(0, 0, 0, 0.25);
}

.scm-diff-table {
  margin-bottom: 12px;
}

/* 无前态的明细表按内容收窄 */
.scm-diff-table :deep(.ant-table-container),
.scm-diff-table :deep(.ant-table-content) {
  width: fit-content;
  max-width: 100%;
}

/* 分组左边框 + 缩进，区分层级。 */
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
