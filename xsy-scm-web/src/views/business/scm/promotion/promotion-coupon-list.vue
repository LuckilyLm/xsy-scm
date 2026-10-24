<!-- 优惠券：发放、领取和使用统计以服务端为准；券来源与让利投影的缺口见 docs/status.md B7。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="关键词" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="券编码 / 名称" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" style="width: 140px" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="'scm:promotion:coupon:query'" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:promotion:coupon:edit'" @click="openCreate">新建券</a-button>
      </div>
    </a-row>
    <p class="hint">累计数据按发放、领取及实际使用记录统计。</p>

    <a-table
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'couponCode'">
          <span class="scm-mono">{{ record.couponCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'couponName'">{{ record.couponName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'discountType'">
          <ScmStatusTag tone="processing" :label="discountTypeLabel(record.discountType)" />
        </template>
        <template v-else-if="column.dataIndex === 'discountText'">{{ discountText(record) }}</template>
        <template v-else-if="column.dataIndex === 'minOrderAmount'">
          <!-- 0 表示无门槛，显示成「无门槛」而不是 ¥ 0 -->
          <span v-if="isNoThreshold(record.minOrderAmount)" class="scm-cell-hint">无门槛</span>
          <span v-else class="scm-money">{{ formatAmount(record.minOrderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'validFrom'">{{ datetime(record.validFrom) }}</template>
        <template v-else-if="column.dataIndex === 'validTo'">{{ datetime(record.validTo) }}</template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="statusLabelOf(record.status)" />
        </template>
        <template v-else-if="column.dataIndex === 'claimable'">
          <ScmStatusTag :tone="record.claimable ? 'success' : 'neutral'" :label="record.claimable ? '开放领取' : '仅后台发券'" />
        </template>
        <template v-else-if="column.dataIndex === 'claimLimits'">
          {{ record.claimable ? `每人 ${record.perCustomerLimit ?? '不限'} / 总量 ${record.totalLimit ?? '不限'}` : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'adminIssuedCount'">
          <span class="scm-quantity">{{ record.usageStats?.adminIssuedCount ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'mallClaimedCount'">
          <span class="scm-quantity">{{ record.usageStats?.mallClaimedCount ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'usedCount'">
          <span class="scm-quantity">{{ record.usageStats?.usedCount ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'usedDiscountAmount'">
          <span class="scm-money">{{ record.usageStats?.usedDiscountAmount ?? '—' }}</span>
          <span v-if="record.usageStats?.discountMissingCount" class="scm-cell-hint">
            （{{ record.usageStats.discountMissingCount }} 张缺少冻结让利）
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:promotion:coupon:edit'" @click="openEdit(record)">编辑 </a-button>
            <a-button
              v-if="record.status !== 'ACTIVE'"
              type="link"
              size="small"
              v-privilege="'scm:promotion:coupon:status'"
              @click="changeStatus(record, 'ACTIVE')"
              >启用
            </a-button>
            <a-button v-else type="link" size="small" danger v-privilege="'scm:promotion:coupon:status'" @click="changeStatus(record, 'STOPPED')"
              >停用
            </a-button>
            <a-button
              type="link"
              size="small"
              v-privilege="'scm:promotion:coupon:issue'"
              :disabled="record.status !== 'ACTIVE'"
              @click="openIssue(record)"
              >发券
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
    :title="form.id ? '编辑优惠券' : '新建优惠券'"
    width="700px"
    :confirm-loading="saving"
    @ok="submit"
    @cancel="editOpen = false"
  >
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">券基础</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="券编码" name="couponCode">
              <a-input v-model:value="form.couponCode" :disabled="!!form.id" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="券名称" name="couponName">
              <a-input v-model:value="form.couponName" />
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">优惠规则</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="优惠类型" name="discountType">
              <a-select v-model:value="form.discountType" :options="discountTypeOptions" @change="onDiscountTypeChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="form.discountType === 'RATE' ? '折扣率' : '减免金额'" name="discountValueInput">
              <a-input-number
                v-if="form.discountType === 'RATE'"
                v-model:value="form.discountValueInput"
                :min="0.01"
                :max="100"
                :precision="2"
                :step="1"
                addon-after="%"
                placeholder="95 即 9.5 折"
                style="width: 100%"
              />
              <a-input-number
                v-else
                v-model:value="form.discountValueInput"
                :min="0"
                :precision="2"
                :step="0.01"
                addon-before="¥"
                placeholder="0.00"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="门槛金额">
              <a-input-number
                v-model:value="minOrderAmount"
                :min="0"
                :precision="2"
                :step="0.01"
                addon-before="¥"
                placeholder="0 表示无门槛"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">有效期</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="生效时间" name="validFrom">
              <a-date-picker v-model:value="form.validFrom" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="失效时间" name="validTo">
              <a-date-picker v-model:value="form.validTo" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">备注</h3>
        </div>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" />
        </a-form-item>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">商城领取</h3>
        </div>
        <a-form-item label="允许客户自助领取">
          <a-switch v-model:checked="form.claimable" />
          <span class="scm-cell-hint">仅影响商城领取，后台发券不受限制</span>
        </a-form-item>
        <a-row v-if="form.claimable" :gutter="20">
          <a-col :span="12">
            <a-form-item label="每人限领张数">
              <a-input-number v-model:value="form.perCustomerLimit" :min="1" :precision="0" placeholder="不填表示不限" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="自助领取总量">
              <a-input-number v-model:value="form.totalLimit" :min="1" :precision="0" placeholder="不填表示不限" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </section>
    </a-form>
  </a-modal>

  <a-modal v-model:open="issueOpen" title="发券" :confirm-loading="saving" @ok="submitIssue">
    <a-alert v-if="issueError" :message="issueError" type="error" show-icon class="banner" />
    <a-form layout="vertical">
      <a-form-item label="客户" required>
        <CustomerSelect v-model:value="issueForm.customerId" width="100%" />
      </a-form-item>
      <a-form-item label="张数" required>
        <a-input-number v-model:value="issueForm.quantity" :min="1" :max="200" :precision="0" style="width: 100%" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {formatAmount, moneyPayload, normalizeMoneyInput} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import {
  percentToRate,
  promotionError,
  promotionStatuses,
  rateToPercent,
  type Id,
  type PromotionCoupon,
  type PromotionCouponDiscountType,
  type PromotionCouponQuery,
  type PromotionCouponSave,
  type PromotionStatus,
} from './promotion-types';

const queryForm = reactive<PromotionCouponQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<PromotionCoupon[]>([]);
const total = ref(0);
const loading = ref(false);
let requestId = 0;
const error = ref('');
const editOpen = ref(false);
const issueOpen = ref(false);
const saving = ref(false);
const issueError = ref('');

const statusOptions = Object.entries(promotionStatuses).map(([value, item]) => ({value, label: item.label}));
const discountTypeOptions = [
  {value: 'AMOUNT', label: '满减券（减免金额）'},
  {value: 'RATE', label: '折扣券（折扣率）'},
];

/** 状态配色：草稿橙、生效绿、停用灰。 */
const PROMOTION_STATUS_TONE: Record<PromotionStatus, ScmStatusTone> = {
  DRAFT: 'warning',
  ACTIVE: 'success',
  STOPPED: 'neutral',
};
const statusTone = (status: PromotionStatus): ScmStatusTone => PROMOTION_STATUS_TONE[status] ?? 'neutral';

/** 枚举缺项时保留原值，便于发现后端新增状态。 */
function statusLabelOf(value: string): string {
  return promotionStatuses[value as PromotionStatus]?.label ?? value;
}

function discountTypeLabel(value: PromotionCouponDiscountType): string {
  return discountTypeOptions.find((option) => option.value === value)?.label ?? value;
}

// 优惠券列表的列定义
const columns: TableColumnsType<PromotionCoupon> = [
  {title: '券编码', dataIndex: 'couponCode', width: 130},
  {title: '券名称', dataIndex: 'couponName', width: 180},
  {title: '券类型', dataIndex: 'discountType', width: 130, align: 'center'},
  {title: '优惠规则', dataIndex: 'discountText', width: 180},
  {title: '门槛金额', dataIndex: 'minOrderAmount', width: 130},
  {title: '生效时间', dataIndex: 'validFrom', width: 170},
  {title: '失效时间', dataIndex: 'validTo', width: 170},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '商城领取', dataIndex: 'claimable', width: 120},
  {title: '领取限制', dataIndex: 'claimLimits', width: 190},
  {title: '后台发放', dataIndex: 'adminIssuedCount', width: 92},
  {title: '商城领取', dataIndex: 'mallClaimedCount', width: 92},
  {title: '已使用', dataIndex: 'usedCount', width: 90},
  {title: '已知券让利', dataIndex: 'usedDiscountAmount', width: 148},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
];

/** 表单模型要保持同一个对象；优惠值用数字输入，提交时转成定点字符串。 */
const form = reactive<PromotionCouponSave & {discountValueInput: number | null}>({
  couponCode: '',
  couponName: '',
  discountType: 'AMOUNT',
  discountValue: '',
  discountValueInput: null,
  minOrderAmount: '0',
  validFrom: '',
  validTo: '',
  claimable: false,
  perCustomerLimit: null,
  totalLimit: null,
  remark: null,
});
const formRef = ref<FormInstance>();
/** 必填项逐项校验。 */
const formRules = {
  couponCode: [{required: true, message: '请填写券编码', trigger: 'blur'}],
  couponName: [{required: true, message: '请填写券名称', trigger: 'blur'}],
  discountType: [{required: true, message: '请选择优惠类型', trigger: 'change'}],
  discountValueInput: [
    {
      validator: () =>
        form.discountValueInput == null
          ? Promise.reject(new Error(form.discountType === 'RATE' ? '请填写折扣率' : '请填写减免金额'))
          : Promise.resolve(),
      trigger: 'blur',
    },
  ],
  validFrom: [{required: true, message: '请选择生效时间', trigger: 'change'}],
  validTo: [{required: true, message: '请选择失效时间', trigger: 'change'}],
};

/** 门槛金额用数字输入，提交时转字符串。 */
const minOrderAmount = ref<number | null>(0);

const issueForm = reactive<{couponId?: Id; customerId?: Id; quantity: number}>({quantity: 1});

/** 门槛 0 表示无门槛，不是缺值。 */
function isNoThreshold(value?: string | null): boolean {
  return value === null || value === undefined || Number(value) === 0;
}

function discountText(record: PromotionCoupon): string {
  if (record.discountType === 'RATE') {
    const percent = rateToPercent(record.discountValue);
    return percent === null ? '—' : `折扣率 ${percent}%`;
  }
  return `减 ${record.discountValue == null ? '—' : formatAmount(record.discountValue)}`;
}

/** 切换优惠类型后清空数值，避免按新类型解释旧值。 */
function onDiscountTypeChange() {
  form.discountValueInput = null;
}

async function queryData() {
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const r = await promotionApi.couponQuery({...queryForm}, {suppressGlobalErrorMessage: true});
    if (current !== requestId) return;
    tableData.value = r.data.list ?? [];
    total.value = r.data.total ?? 0;
  } catch (e) {
    if (current === requestId) error.value = promotionError(e);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  onSearch();
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    couponCode: '',
    couponName: '',
    discountType: 'AMOUNT',
    discountValue: '',
    minOrderAmount: '0',
    validFrom: '',
    validTo: '',
    claimable: false,
    perCustomerLimit: null,
    totalLimit: null,
    remark: null,
    version: undefined,
  });
  form.discountValueInput = null;
  minOrderAmount.value = 0;
  editOpen.value = true;
}

function openEdit(record: PromotionCoupon) {
  Object.assign(form, {
    id: record.id,
    couponCode: record.couponCode,
    couponName: record.couponName,
    discountType: record.discountType,
    discountValue: record.discountValue ?? '',
    minOrderAmount: record.minOrderAmount ?? '0',
    validFrom: record.validFrom,
    validTo: record.validTo,
    claimable: record.claimable ?? false,
    perCustomerLimit: record.perCustomerLimit ?? null,
    totalLimit: record.totalLimit ?? null,
    remark: record.remark ?? null,
    version: record.version,
  });
  form.discountValueInput =
    record.discountValue == null || record.discountValue === ''
      ? null
      : record.discountType === 'RATE'
      ? rateToPercent(record.discountValue)
      : Number(normalizeMoneyInput(record.discountValue));
  minOrderAmount.value = record.minOrderAmount == null ? null : Number(normalizeMoneyInput(record.minOrderAmount));
  editOpen.value = true;
}

async function submit() {
  form.discountValue = form.discountType === 'RATE' ? percentToRate(form.discountValueInput) ?? '' : moneyPayload(form.discountValueInput);
  form.minOrderAmount = moneyPayload(minOrderAmount.value) || '0';
  // 必填项由表单校验
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    await promotionApi.couponSave({...form});
    message.success('优惠券已保存');
    editOpen.value = false;
    await queryData();
  } catch {
    // 保存失败由全局 toast 提示
  } finally {
    saving.value = false;
  }
}

function openIssue(record: PromotionCoupon) {
  issueForm.couponId = record.id;
  issueForm.customerId = undefined;
  issueForm.quantity = 1;
  issueError.value = '';
  issueOpen.value = true;
}

/** 启停券模板；草稿必须先启用才能发券。 */
async function changeStatus(record: PromotionCoupon, status: 'ACTIVE' | 'STOPPED') {
  try {
    await promotionApi.couponStatus(record.id, record.version, status);
    message.success(status === 'ACTIVE' ? '优惠券已启用' : '优惠券已停用');
    await queryData();
  } catch {
    // 启停失败由全局 toast 提示
  }
}

async function submitIssue() {
  issueError.value = '';
  if (issueForm.couponId === undefined || issueForm.customerId === undefined) {
    issueError.value = '请选择客户';
    return;
  }
  saving.value = true;
  try {
    const count = (await promotionApi.couponIssue(issueForm.couponId, issueForm.customerId, issueForm.quantity)).data;
    message.success(`已发出 ${count} 张券`);
    issueOpen.value = false;
    await queryData();
  } catch {
    // 发券失败由全局 toast 提示；issueError 只用于本地校验
  } finally {
    saving.value = false;
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
