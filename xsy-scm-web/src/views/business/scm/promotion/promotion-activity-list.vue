<!--
  营销活动列表与编辑。

  活动规则是受控键值（不是表达式）：每种类型只接受自己的键，多传未知键会被服务端拒收（41324），
  因此切换类型时必须清空规则，编辑表单也只提交当前类型用得上的键。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="关键词" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="活动名称 / 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="类型" class="smart-query-form-item">
        <a-select v-model:value="queryForm.activityType" :options="typeOptions" allow-clear style="width: 140px"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" allow-clear style="width: 140px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:promotion:activity:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon/>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:promotion:activity:edit'" @click="openCreate">新建活动</a-button>
      </div>
    </a-row>

    <a-table
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1520 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'activityCode'">
          <span class="scm-mono">{{ record.activityCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'activityName'">{{ record.activityName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'activityType'">
          <ScmStatusTag :color="typeColorOf(record.activityType)" :label="typeLabelOf(record.activityType)"/>
        </template>
        <template v-else-if="column.dataIndex === 'ruleText'">{{ ruleText(record.rule) }}</template>
        <template v-else-if="column.dataIndex === 'validFrom'">{{ datetime(record.validFrom) }}</template>
        <template v-else-if="column.dataIndex === 'validTo'">{{ datetime(record.validTo) }}</template>
        <template v-else-if="column.dataIndex === 'exclusiveGroup'">{{ record.exclusiveGroup || '—' }}</template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="statusLabelOf(record.status)"/>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:promotion:activity:edit'"
                      @click="openEdit(record)">编辑
            </a-button>
            <a-button
                v-if="record.status !== 'ACTIVE'"
                type="link"
                size="small"
                v-privilege="'scm:promotion:activity:status'"
                @click="changeStatus(record, 'ACTIVE')"
            >上线
            </a-button>
            <a-button
                v-else
                type="link"
                size="small"
                danger
                v-privilege="'scm:promotion:activity:status'"
                @click="changeStatus(record, 'STOPPED')"
            >停用
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <a-modal
      :open="editOpen"
      :title="form.id ? '编辑活动' : '新建活动'"
      width="760px"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="editOpen = false"
  >
    <a-form ref="formRef" :model="{...form, ...rule}" :rules="formRules" layout="vertical">
      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">活动基础</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="活动编码" name="activityCode">
              <a-input v-model:value="form.activityCode" :disabled="!!form.id"
                       placeholder="字母、数字、下划线或连字符"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="活动名称" name="activityName">
              <a-input v-model:value="form.activityName"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="活动类型" name="activityType">
              <a-select v-model:value="form.activityType" :options="typeOptions" @change="onTypeChange"/>
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">活动规则</h3>
        </div>
        <a-row :gutter="20">
          <template v-if="form.activityType === 'DISCOUNT'">
            <a-col :span="12">
              <a-form-item label="折扣率" name="discountPercent">
                <a-input-number
                    v-model:value="rule.discountPercent"
                    :min="0.01"
                    :max="100"
                    :precision="2"
                    :step="1"
                    addon-after="%"
                    placeholder="95 即 9.5 折"
                    style="width: 100%"
                />
              </a-form-item>
            </a-col>
          </template>
          <template v-else-if="form.activityType === 'SPECIAL_PRICE'">
            <a-col :span="12">
              <a-form-item label="特价商品规格" name="skuId">
                <SkuSelect
                    :value="rule.skuId ?? null"
                    width="100%"
                    @update:value="onSpecialSkuChange"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="特价单价" name="specialPrice">
                <a-input-number
                    v-model:value="rule.specialPrice"
                    :min="0"
                    :precision="4"
                    :step="0.1"
                    addon-before="¥"
                    placeholder="0.0000"
                    style="width: 100%"
                />
              </a-form-item>
            </a-col>
          </template>
          <template v-else>
            <a-col :span="12">
              <a-form-item label="门槛金额" name="thresholdAmount">
                <a-input-number
                    v-model:value="rule.thresholdAmount"
                    :min="0"
                    :precision="4"
                    :step="1"
                    addon-before="¥"
                    placeholder="0.0000"
                    style="width: 100%"
                />
              </a-form-item>
            </a-col>
            <a-col v-if="form.activityType === 'FULL_REDUCE'" :span="12">
              <a-form-item label="减免金额" name="reduceAmount">
                <a-input-number
                    v-model:value="rule.reduceAmount"
                    :min="0"
                    :precision="4"
                    :step="1"
                    addon-before="¥"
                    placeholder="0.0000"
                    style="width: 100%"
                />
              </a-form-item>
            </a-col>
            <template v-if="form.activityType === 'FULL_GIFT'">
              <a-col :span="12">
                <a-form-item label="赠送商品规格" name="giftSkuId">
                  <SkuSelect
                      :value="rule.giftSkuId ?? null"
                      width="100%"
                      @update:value="onGiftSkuChange"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="赠品数量" name="giftQuantity">
                  <a-input-number
                      v-model:value="rule.giftQuantity"
                      :min="0"
                      :precision="4"
                      :step="1"
                      placeholder="0.0000"
                      style="width: 100%"
                  />
                </a-form-item>
              </a-col>
            </template>
          </template>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">有效期</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="生效时间" name="validFrom">
              <a-date-picker v-model:value="form.validFrom" show-time value-format="YYYY-MM-DD HH:mm:ss"
                             style="width: 100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="失效时间" name="validTo">
              <a-date-picker v-model:value="form.validTo" show-time value-format="YYYY-MM-DD HH:mm:ss"
                             style="width: 100%"/>
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">叠加与互斥</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item>
              <template #label>
                互斥组
                <ScmFieldHelp label="互斥组" text="同一互斥组内的活动不能叠加"/>
              </template>
              <a-input v-model:value="form.exclusiveGroup" placeholder="例如 SUMMER"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="优先级">
              <a-input-number v-model:value="form.priority" :min="0" :precision="0" style="width: 100%"/>
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">备注</h3>
        </div>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark"/>
        </a-form-item>
      </section>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {fixed4} from '../common/scm-fixed';
import {datetime} from '../common/scm-display';
import type {ScmId} from '/@/types/business/scm/customer';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import {
  activityTypes,
  percentToRate,
  promotionError,
  promotionStatuses,
  rateToPercent,
  type PromotionActivity,
  type PromotionActivityQuery,
  type PromotionActivitySave,
  type PromotionActivityType,
  type PromotionRule,
  type PromotionStatus,
} from './promotion-types';

const queryForm = reactive<PromotionActivityQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<PromotionActivity[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const editOpen = ref(false);
const saving = ref(false);

const typeOptions = Object.entries(activityTypes).map(([value, item]) => ({value, label: item.label}));
const statusOptions = Object.entries(promotionStatuses).map(([value, item]) => ({value, label: item.label}));

/** 状态视觉：草稿 = 待处理（橙），生效中 = 绿，已停用 = 灰。 */
const PROMOTION_STATUS_TONE: Record<PromotionStatus, ScmStatusTone> = {
  DRAFT: 'warning',
  ACTIVE: 'success',
  STOPPED: 'neutral',
};
const statusTone = (status: PromotionStatus): ScmStatusTone => PROMOTION_STATUS_TONE[status] ?? 'neutral';

/**
 * 表格 slot 里的 `record` 是 `any`，索引映射表会触发 TS7053。
 * 在这里把类型收口一次，避免 `as` 散落到模板各处；枚举缺项时回落原值
 * （后端加了新类型而前端没跟上时，一个英文常量本身就是有用信号）。
 */
function typeColorOf(value: string): string | undefined {
  return activityTypes[value as PromotionActivityType]?.color;
}

function typeLabelOf(value: string): string {
  return activityTypes[value as PromotionActivityType]?.label ?? value;
}

function statusLabelOf(value: string): string {
  return promotionStatuses[value as PromotionStatus]?.label ?? value;
}

/**
 * 列按「叫什么 / 什么类型 / 怎么算 / 什么时候有效 / 能不能叠加 / 上没上线」排列。
 * 活动编码下沉为名称的次要行；生效与失效时间合成一格（它们回答同一个问题）。
 */
const columns: TableColumnsType<PromotionActivity> = [
  {title: '活动编码', dataIndex: 'activityCode', width: 130},
  {title: '活动名称', dataIndex: 'activityName', width: 200},
  {title: '类型', dataIndex: 'activityType', align: 'center', width: 110},
  {title: '规则摘要', dataIndex: 'ruleText', width: 260},
  {title: '优先级', dataIndex: 'priority', align: 'right', width: 90},
  {title: '生效时间', dataIndex: 'validFrom', width: 170},
  {title: '失效时间', dataIndex: 'validTo', width: 170},
  {title: '互斥组', dataIndex: 'exclusiveGroup', width: 130},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 160},
];

/**
 * 表单里的规则：数值字段用 `number`（InputNumber 只接受数字），提交时才收口成定点字符串。
 * `skuId` / `giftSkuId` 是选择器给的 id，按字符串提交（与后端规则的键类型一致）。
 */
interface RuleForm {
  skuId?: string;
  discountPercent: number | null;
  specialPrice: number | null;
  thresholdAmount: number | null;
  reduceAmount: number | null;
  giftSkuId?: string;
  giftQuantity: number | null;
}

const emptyRule = (): RuleForm => ({
  skuId: undefined,
  discountPercent: null,
  specialPrice: null,
  thresholdAmount: null,
  reduceAmount: null,
  giftSkuId: undefined,
  giftQuantity: null,
});

const rule = reactive<RuleForm>(emptyRule());
const formRef = ref();
/**
 * 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。
 * 规则区的字段按活动类型条件渲染，未挂载的 form-item 不会被校验，所以这里可以一次列全。
 */
const formRules = {
  activityCode: [{required: true, message: '请填写活动编码', trigger: 'blur'}],
  activityName: [{required: true, message: '请填写活动名称', trigger: 'blur'}],
  activityType: [{required: true, message: '请选择活动类型', trigger: 'change'}],
  discountPercent: [{required: true, message: '请填写折扣率', trigger: 'blur'}],
  skuId: [{required: true, message: '请选择特价商品规格', trigger: 'change'}],
  specialPrice: [{required: true, message: '请填写特价单价', trigger: 'blur'}],
  thresholdAmount: [{required: true, message: '请填写门槛金额', trigger: 'blur'}],
  reduceAmount: [{required: true, message: '请填写减免金额', trigger: 'blur'}],
  giftSkuId: [{required: true, message: '请选择赠送商品规格', trigger: 'change'}],
  giftQuantity: [{required: true, message: '请填写赠品数量', trigger: 'blur'}],
  validFrom: [{required: true, message: '请选择生效时间', trigger: 'change'}],
  validTo: [{required: true, message: '请选择失效时间', trigger: 'change'}],
};

const form = reactive<PromotionActivitySave>({
  activityCode: '',
  activityName: '',
  activityType: 'FULL_REDUCE',
  exclusiveGroup: null,
  priority: 0,
  validFrom: '',
  validTo: '',
  rule: {},
  remark: null,
});

/** 规则的可读描述：受控键值 → 一句话，便于在列表上直接看出「这条活动减多少」。 */
function ruleText(source?: PromotionRule): string {
  if (!source) return '—';
  if (source.discountRate) return `折扣率 ${rateToPercent(source.discountRate) ?? source.discountRate}%`;
  if (source.specialPrice) return `商品规格 ${source.skuId ?? '—'} 限时特价 ¥ ${source.specialPrice}`;
  if (source.reduceAmount) return `满 ¥ ${source.thresholdAmount ?? '—'} 减 ¥ ${source.reduceAmount}`;
  if (source.giftSkuId) return `满 ¥ ${source.thresholdAmount ?? '—'} 赠送商品规格 ${source.giftSkuId} × ${source.giftQuantity ?? '—'}`;
  return '—';
}

/** 把后端规则摊回表单：比率换算成百分比，定点字符串换算成 number。 */
function ruleToForm(source?: PromotionRule) {
  Object.assign(rule, emptyRule());
  if (!source) return;
  rule.skuId = source.skuId;
  rule.giftSkuId = source.giftSkuId;
  rule.discountPercent = rateToPercent(source.discountRate);
  rule.specialPrice = source.specialPrice == null ? null : Number(source.specialPrice);
  rule.thresholdAmount = source.thresholdAmount == null ? null : Number(source.thresholdAmount);
  rule.reduceAmount = source.reduceAmount == null ? null : Number(source.reduceAmount);
  rule.giftQuantity = source.giftQuantity == null ? null : Number(source.giftQuantity);
}

/**
 * 表单规则 → 后端规则：数值补足 4 位定点，空值不进载荷。
 * 多带一个当前类型用不上的键会被服务端当成未知键拒收（41324）。
 */
function formToRule(source: RuleForm): PromotionRule {
  const out: Record<string, string> = {};
  const put = (key: string, value: string | undefined) => {
    if (value !== undefined) out[key] = value;
  };
  put('skuId', source.skuId);
  put('giftSkuId', source.giftSkuId);
  put('discountRate', percentToRate(source.discountPercent));
  put('specialPrice', fixed4(source.specialPrice));
  put('thresholdAmount', fixed4(source.thresholdAmount));
  put('reduceAmount', fixed4(source.reduceAmount));
  put('giftQuantity', fixed4(source.giftQuantity));
  return out as PromotionRule;
}

/** SKU 选择器给出的是 id（可能是数字）；规则里的键统一按字符串提交。 */
function onSpecialSkuChange(value: ScmId | ScmId[] | undefined) {
  rule.skuId = value === undefined || value === null ? undefined : String(value);
}

function onGiftSkuChange(value: ScmId | ScmId[] | undefined) {
  rule.giftSkuId = value === undefined || value === null ? undefined : String(value);
}

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
    const r = await promotionApi.activityQuery({...queryForm}, {suppressGlobalErrorMessage: true});
    tableData.value = r.data.list ?? [];
    total.value = r.data.total ?? 0;
  } catch (e) {
    error.value = promotionError(e);
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.activityType = undefined;
  queryForm.status = undefined;
  onSearch();
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    activityCode: '',
    activityName: '',
    activityType: 'FULL_REDUCE',
    exclusiveGroup: null,
    priority: 0,
    validFrom: '',
    validTo: '',
    rule: {} as PromotionRule,
    remark: null,
    version: undefined,
  });
  Object.assign(rule, emptyRule());
  editOpen.value = true;
}

function openEdit(record: PromotionActivity) {
  Object.assign(form, {
    id: record.id,
    activityCode: record.activityCode,
    activityName: record.activityName,
    activityType: record.activityType,
    exclusiveGroup: record.exclusiveGroup ?? null,
    priority: record.priority ?? 0,
    validFrom: record.validFrom,
    validTo: record.validTo,
    rule: {...(record.rule ?? {})},
    remark: record.remark ?? null,
    version: record.version,
  });
  ruleToForm(record.rule);
  editOpen.value = true;
}

/** 切换类型时清空规则：残留的键会被服务端当成「未知键」拒收，提前清掉比提交后才报错好。 */
function onTypeChange() {
  Object.assign(rule, emptyRule());
  form.rule = {};
}

async function submit() {
  // 必填项走表单校验：缺哪个就在哪个输入框下方提示（规则区字段按活动类型条件渲染，只会校验当前挂载的）
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await promotionApi.activitySave({...form, rule: formToRule(rule)});
    message.success('活动已保存');
    editOpen.value = false;
    await queryData();
  } catch {
    // 保存失败只走全局 toast
  } finally {
    saving.value = false;
  }
}

async function changeStatus(record: PromotionActivity, status: 'ACTIVE' | 'STOPPED') {
  try {
    await promotionApi.activityStatus(record.id, record.version, status);
    message.success(status === 'ACTIVE' ? '活动已上线' : '活动已停用');
    await queryData();
  } catch {
    // 启停失败只走全局 toast
  }
}

onMounted(queryData);
</script>

<style scoped>
.hint {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  margin-left: 8px;
}

.banner {
  margin-bottom: 12px;
}
</style>
