<!--
  优惠券（ADM-12 第一阶段）。

  正常流程：新建（草稿）→ 启用 → 发券。只有生效中的券模板可以发，因此草稿必须先启用；
  启用只是发布模板，实际发券还要落在券自己的有效期内（服务端再判一次窗口）。

  券模板只在草稿 / 停用状态可改：生效中的券被改内容，会让同一批已发出的券按两套规则核销。
  发券一次批量落库（带 Idempotency-Key，重试回放不重复发），每张券带自己的实例号，
  因此「这个客户有几张、用到第几张」都能查。

  券的占用 / 核销 / 释放不在本页：它们由下单与退款流程驱动（试算不占用，确认下单才占用）。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="关键词" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="券编码 / 名称" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" style="width: 140px"
                  placeholder="全部" allow-clear/>
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
        <span class="hint">券的占用与核销由下单 / 退款流程驱动；试算不占用券</span>
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
        :scroll="{ x: 1160 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'couponName'">
          <!-- 券编码是业务识别信息，但不值得独占一列：作为名称下方的 secondary text -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.couponName || '—' }}</span>
            <span v-if="record.couponCode" class="scm-cell-stack__sub">{{ record.couponCode }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'discountType'">
          <ScmStatusTag tone="processing" :label="discountTypeLabel(record.discountType)"/>
        </template>
        <template v-else-if="column.dataIndex === 'discountText'">{{ discountText(record) }}</template>
        <template v-else-if="column.dataIndex === 'minOrderAmount'">
          <!-- 「0 表示无门槛」是业务语义，不是缺值：显示成「无门槛」而不是 ¥ 0.0000 -->
          <span v-if="isNoThreshold(record.minOrderAmount)" class="scm-cell-hint">无门槛</span>
          <span v-else class="scm-money">¥ {{ record.minOrderAmount }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'validity'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ datetime(record.validFrom) }}</span>
            <span class="scm-cell-stack__sub">至 {{ datetime(record.validTo) }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="statusLabelOf(record.status)"/>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:promotion:coupon:edit'"
                      @click="openEdit(record)">编辑
            </a-button>
            <a-button
                v-if="record.status !== 'ACTIVE'"
                type="link"
                size="small"
                v-privilege="'scm:promotion:coupon:status'"
                @click="changeStatus(record, 'ACTIVE')"
            >启用
            </a-button>
            <a-button
                v-else
                type="link"
                size="small"
                danger
                v-privilege="'scm:promotion:coupon:status'"
                @click="changeStatus(record, 'STOPPED')"
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
    <a-alert v-if="editError" :message="editError" type="error" show-icon class="banner"/>
    <a-form layout="vertical">
      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">券基础</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="券编码" required>
              <a-input v-model:value="form.couponCode" :disabled="!!form.id"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="券名称" required>
              <a-input v-model:value="form.couponName"/>
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
            <a-form-item label="优惠类型" required>
              <a-select v-model:value="form.discountType" :options="discountTypeOptions"
                        @change="onDiscountTypeChange"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="form.discountType === 'RATE' ? '折扣率' : '减免金额'" required>
              <a-input-number
                  v-if="form.discountType === 'RATE'"
                  v-model:value="discountValue"
                  :min="0.01"
                  :max="100"
                  :precision="2"
                  :step="1"
                  addon-after="%"
                  placeholder="95"
                  style="width: 100%"
              />
              <a-input-number
                  v-else
                  v-model:value="discountValue"
                  :min="0"
                  :precision="4"
                  :step="1"
                  addon-before="¥"
                  placeholder="0.0000"
                  style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col v-if="form.discountType === 'RATE'" :span="24">
            <span class="scm-cell-hint">填 95 表示按原价的 95% 计价（即 9.5 折）。</span>
          </a-col>
          <a-col :span="12">
            <a-form-item label="门槛金额">
              <a-input-number
                  v-model:value="minOrderAmount"
                  :min="0"
                  :precision="4"
                  :step="1"
                  addon-before="¥"
                  placeholder="0.0000"
                  style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <span class="scm-cell-hint">填 0 表示无门槛。</span>
          </a-col>
        </a-row>
      </section>

      <section class="scm-form-section">
        <div class="scm-form-section__head">
          <h3 class="scm-form-section__title">有效期</h3>
        </div>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="生效时间" required>
              <a-date-picker v-model:value="form.validFrom" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                             style="width: 100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="失效时间" required>
              <a-date-picker v-model:value="form.validTo" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                             style="width: 100%"/>
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

  <a-modal v-model:open="issueOpen" title="发券" :confirm-loading="saving" @ok="submitIssue">
    <a-alert v-if="issueError" :message="issueError" type="error" show-icon class="banner"/>
    <a-form layout="vertical">
      <a-form-item label="客户" required>
        <CustomerSelect v-model:value="issueForm.customerId" width="100%"/>
      </a-form-item>
      <a-form-item label="张数" required>
        <a-input-number v-model:value="issueForm.quantity" :min="1" :max="200" :precision="0" style="width: 100%"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {fixed4} from '../common/scm-fixed';
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
const error = ref('');
const editOpen = ref(false);
const issueOpen = ref(false);
const saving = ref(false);
const editError = ref('');
const issueError = ref('');

const statusOptions = Object.entries(promotionStatuses).map(([value, item]) => ({value, label: item.label}));
const discountTypeOptions = [
  {value: 'AMOUNT', label: '满减券（减免金额）'},
  {value: 'RATE', label: '折扣券（折扣率）'},
];

/** 状态视觉：草稿 = 待处理（橙），生效中 = 绿，已停用 = 灰。 */
const PROMOTION_STATUS_TONE: Record<PromotionStatus, ScmStatusTone> = {
  DRAFT: 'warning',
  ACTIVE: 'success',
  STOPPED: 'neutral',
};
const statusTone = (status: PromotionStatus): ScmStatusTone => PROMOTION_STATUS_TONE[status] ?? 'neutral';

/**
 * 表格 slot 里的 `record` 是 `any`，索引映射表会触发 TS7053；在这里收口一次类型，
 * 枚举缺项时回落原值（后端加了新状态而前端没跟上时，英文常量本身就是有用信号）。
 */
function statusLabelOf(value: string): string {
  return promotionStatuses[value as PromotionStatus]?.label ?? value;
}

function discountTypeLabel(value: PromotionCouponDiscountType): string {
  return discountTypeOptions.find((option) => option.value === value)?.label ?? value;
}

/**
 * 列按「叫什么 / 什么券 / 优惠多少 / 什么门槛 / 什么时候有效 / 上没上线」排列。
 * 券编码下沉为名称的次要行；生效与失效时间合成一格（它们回答同一个问题）。
 * 「发放量 / 已领取 / 已使用」当前 VO 不返回，属于后端缺口
 * （登记于 docs/plan/active/frontend-ui-backend-gap-inventory.md 的 B7），前端不臆造。
 */
const columns: TableColumnsType<PromotionCoupon> = [
  {title: '券名称', dataIndex: 'couponName', width: 200},
  {title: '券类型', dataIndex: 'discountType', width: 130},
  {title: '优惠规则', dataIndex: 'discountText', width: 180},
  {title: '门槛金额', dataIndex: 'minOrderAmount', align: 'right', width: 130},
  {title: '有效期', dataIndex: 'validity', width: 220},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
];

const form = reactive<PromotionCouponSave>({
  couponCode: '',
  couponName: '',
  discountType: 'AMOUNT',
  discountValue: '',
  minOrderAmount: '0',
  validFrom: '',
  validTo: '',
  remark: null,
});

/**
 * 优惠值与门槛金额在表单里是 `number`（InputNumber 只接受数字），
 * 提交时才由 `fixed4` / `percentToRate` 收口回后端要的定点字符串。
 * 不做双向桥接，免得每次击键都往返一次格式化把控件自己的编辑态冲掉。
 */
const discountValue = ref<number | null>(null);
const minOrderAmount = ref<number | null>(0);

const issueForm = reactive<{ couponId?: Id; customerId?: Id; quantity: number }>({quantity: 1});

/** 门槛 0 是「无门槛」这个业务事实，不是缺值。 */
function isNoThreshold(value?: string | null): boolean {
  return value === null || value === undefined || Number(value) === 0;
}

function discountText(record: PromotionCoupon): string {
  if (record.discountType === 'RATE') {
    const percent = rateToPercent(record.discountValue);
    return percent === null ? '—' : `折扣率 ${percent}%`;
  }
  return `减 ¥ ${record.discountValue ?? '—'}`;
}

/** 比率与金额是两种量纲：切换类型后残留的数值会被按新类型解释（0.95 变成 0.95 元），必须清空。 */
function onDiscountTypeChange() {
  discountValue.value = null;
}

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    const r = await promotionApi.couponQuery({...queryForm});
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
    remark: null,
    version: undefined,
  });
  discountValue.value = null;
  minOrderAmount.value = 0;
  editError.value = '';
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
    remark: record.remark ?? null,
    version: record.version,
  });
  discountValue.value = record.discountValue == null || record.discountValue === ''
      ? null
      : record.discountType === 'RATE'
          ? rateToPercent(record.discountValue)
          : Number(record.discountValue);
  minOrderAmount.value = record.minOrderAmount == null ? null : Number(record.minOrderAmount);
  editError.value = '';
  editOpen.value = true;
}

async function submit() {
  editError.value = '';
  form.discountValue = form.discountType === 'RATE'
      ? percentToRate(discountValue.value) ?? ''
      : fixed4(discountValue.value) ?? '';
  form.minOrderAmount = fixed4(minOrderAmount.value) ?? '0';
  if (!form.couponCode || !form.couponName || !form.discountValue || !form.validFrom || !form.validTo) {
    editError.value = '券编码、名称、优惠值与生效时间都必须填写';
    return;
  }
  saving.value = true;
  try {
    await promotionApi.couponSave({...form});
    message.success('优惠券已保存');
    editOpen.value = false;
    await queryData();
  } catch (e) {
    editError.value = promotionError(e);
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

/**
 * 启停券模板。券新建后是草稿，必须先启用才能发券；已过期的券不能再次启用（服务端 41327 兜底）。
 */
async function changeStatus(record: PromotionCoupon, status: 'ACTIVE' | 'STOPPED') {
  try {
    await promotionApi.couponStatus(record.id, record.version, status);
    message.success(status === 'ACTIVE' ? '优惠券已启用' : '优惠券已停用');
    await queryData();
  } catch (e) {
    error.value = promotionError(e);
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
  } catch (e) {
    issueError.value = promotionError(e);
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
