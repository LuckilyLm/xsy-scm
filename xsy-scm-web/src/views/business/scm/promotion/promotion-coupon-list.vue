<!--
  优惠券（ADM-12 第一阶段）。

  正常流程：新建（草稿）→ 启用 → 发券。只有生效中的券模板可以发，因此草稿必须先启用；
  启用只是发布模板，实际发券还要落在券自己的有效期内（服务端再判一次窗口）。

  券模板只在草稿 / 停用状态可改：生效中的券被改内容，会让同一批**已发出**的券按两套规则核销。
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
        :scroll="{ x: 1300 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="promotionStatuses[record.status as PromotionStatus]?.color || 'default'">
            {{ promotionStatuses[record.status as PromotionStatus]?.label || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'discountText'">{{ discountText(record) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" v-privilege="'scm:promotion:coupon:edit'" @click="openEdit(record)">编辑</a-button>
            <a-button
                v-if="record.status !== 'ACTIVE'"
                type="link"
                v-privilege="'scm:promotion:coupon:status'"
                @click="changeStatus(record, 'ACTIVE')"
            >启用
            </a-button>
            <a-button
                v-else
                type="link"
                danger
                v-privilege="'scm:promotion:coupon:status'"
                @click="changeStatus(record, 'STOPPED')"
            >停用
            </a-button>
            <a-button
                type="link"
                v-privilege="'scm:promotion:coupon:issue'"
                :disabled="record.status !== 'ACTIVE'"
                @click="openIssue(record)"
            >发券
            </a-button>
          </div>
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
      width="640px"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="editOpen = false"
  >
    <a-alert v-if="editError" :message="editError" type="error" show-icon class="banner"/>
    <a-form layout="vertical">
      <a-row :gutter="12">
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
        <a-col :span="12">
          <a-form-item label="优惠类型" required>
            <a-select v-model:value="form.discountType" :options="discountTypeOptions"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item :label="form.discountType === 'RATE' ? '折扣率（0~1）' : '减免金额'" required>
            <a-input v-model:value="form.discountValue"
                     :placeholder="form.discountType === 'RATE' ? '0.95 表示 95 折' : '例如 5.0000'"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="门槛金额">
            <a-input v-model:value="form.minOrderAmount" placeholder="0 表示无门槛"/>
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="12">
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
      <a-form-item label="备注">
        <a-input v-model:value="form.remark"/>
      </a-form-item>
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
import {promotionApi} from '/@/api/business/scm/promotion-api';
import {
  promotionError,
  promotionStatuses,
  type Id,
  type PromotionCoupon,
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

const columns: TableColumnsType<PromotionCoupon> = [
  {title: '券编码', dataIndex: 'couponCode', width: 180},
  {title: '券名称', dataIndex: 'couponName', width: 200},
  {title: '优惠', dataIndex: 'discountText', width: 180},
  {title: '门槛金额', dataIndex: 'minOrderAmount', align: 'right', width: 130},
  {title: '生效时间', dataIndex: 'validFrom', width: 180},
  {title: '失效时间', dataIndex: 'validTo', width: 180},
  {title: '状态', dataIndex: 'status', width: 100},
  {title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 220},
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

const issueForm = reactive<{ couponId?: Id; customerId?: Id; quantity: number }>({quantity: 1});

function discountText(record: PromotionCoupon): string {
  if (record.discountType === 'RATE') return `折扣率 ${record.discountValue ?? '—'}`;
  return `减 ${record.discountValue ?? '—'}`;
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
  editError.value = '';
  editOpen.value = true;
}

async function submit() {
  editError.value = '';
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
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  margin-left: 8px;
}

.banner {
  margin-bottom: 12px;
}
</style>
