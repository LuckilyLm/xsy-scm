<!--
  * 客户详情（独立隐藏路由，可深链）
  *
  * 来源：**W1 派生** —— 结构照抄 `views/business/scm/product/product-detail.vue`。
  * C 的客户详情是抽屉内的只读区，没有独立详情页；V2 需要可深链的独立页
  * （对应 `t_menu` 434「客户详情」，`visible_flag = false`，与 W1 商品详情同策略）。
  *
  * 适配：
  * - 路由参数从 `spuId` 改为 `customerId`；
  * - 账期字段在列表 VO 里没有，只有详情 VO 才有，因此这一页是**唯一**能看到完整账期配置的地方；
  * - 账期三形态（不设置 / 按金额 / 按时间）用 `a-descriptions` 分形态展示，
  *   不把不适用的字段渲染成「—」，避免用户误以为「值是空的」而不是「这个形态没有该字段」。
-->
<template>
  <a-card size="small" :bordered="false" :loading="loading">
    <a-space class="smart-margin-bottom10">
      <a-button @click="router.push('/customer/customer-list')">返回客户列表</a-button>
      <a-button @click="load">刷新详情</a-button>
    </a-space>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action><a-button size="small" @click="load">重新加载</a-button></template>
    </a-alert>
    <template v-else-if="customer">
      <a-descriptions :title="customer.name" bordered :column="{ xs: 1, sm: 2, lg: 3 }">
        <a-descriptions-item label="客户编码">{{ customer.customerCode }}</a-descriptions-item>
        <a-descriptions-item label="客户类型">{{ customer.customerTypeName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColor(customer.status)">{{ statusText(customer.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="结算方式">{{ settleModeText(customer.settleMode) }}</a-descriptions-item>
        <a-descriptions-item label="上级集团">{{ customer.parentCustomerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="归属业务员">{{ customer.sellerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="绑定供应商">{{ customer.supplierName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="联系人">{{ customer.contactName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="联系电话">{{ customer.contactPhone || '—' }}</a-descriptions-item>
        <a-descriptions-item label="地址" :span="3">{{ customer.address || '—' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ datetime(customer.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ datetime(customer.updatedAt) }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="3">{{ customer.remark || '—' }}</a-descriptions-item>
      </a-descriptions>

      <a-divider orientation="left">授信与账期</a-divider>
      <a-descriptions bordered :column="{ xs: 1, sm: 2, lg: 3 }">
        <a-descriptions-item label="授信额度">{{ customer.creditLimit ?? '未设置' }}</a-descriptions-item>
        <a-descriptions-item label="账期类型">{{ creditPeriodTypeText }}</a-descriptions-item>
        <template v-if="customer.creditPeriodType === 'BY_AMOUNT'">
          <a-descriptions-item label="金额阈值">{{ customer.creditAmountThreshold ?? '—' }}</a-descriptions-item>
        </template>
        <template v-else-if="customer.creditPeriodType === 'BY_TIME'">
          <a-descriptions-item label="账期值">{{ customer.creditPeriodValue ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="账期单位">{{ creditPeriodUnitText }}</a-descriptions-item>
          <a-descriptions-item v-if="customer.creditPeriodUnit === 'MONTH'" label="固定结算日">
            {{ customer.settleDay ?? '—' }}
          </a-descriptions-item>
        </template>
      </a-descriptions>
    </template>
  </a-card>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { customerApi } from '/@/api/business/scm/customer-api';
  import type { CustomerDetail, CustomerStatus } from '/@/types/business/scm/customer';
  import { CREDIT_PERIOD_TYPE_ENUM, CREDIT_PERIOD_UNIT_ENUM, CUSTOMER_STATUS_ENUM, SETTLE_MODE_ENUM } from '/@/constants/business/scm/customer-const';
  import { customerError } from './customer-errors';
  import { datetime } from '../common/scm-display';

  const route = useRoute();
  const router = useRouter();
  const customer = ref<CustomerDetail>();
  const loading = ref(false);
  const error = ref('');

  const statusText = (value: CustomerStatus): string => CUSTOMER_STATUS_ENUM[value]?.desc || value;
  const settleModeText = (value: string): string => SETTLE_MODE_ENUM[value]?.desc || value;
  const statusColor = (value: CustomerStatus): string => {
    if (value === 'COOPERATING') return 'green';
    if (value === 'SUSPENDED') return 'orange';
    if (value === 'BLACKLIST') return 'red';
    return 'default';
  };

  const creditPeriodTypeText = computed(() => {
    const type = customer.value?.creditPeriodType;
    return type ? CREDIT_PERIOD_TYPE_ENUM[type]?.desc || type : '未设置账期';
  });

  const creditPeriodUnitText = computed(() => {
    const unit = customer.value?.creditPeriodUnit;
    return unit ? CREDIT_PERIOD_UNIT_ENUM[unit]?.desc || unit : '—';
  });

  let requestId = 0;

  async function load() {
    const request = ++requestId;
    const id = route.query.customerId;
    if (typeof id !== 'string' || !/^\d+$/.test(id)) {
      customer.value = undefined;
      error.value = '客户链接缺少有效编号';
      return;
    }
    loading.value = true;
    error.value = '';
    customer.value = undefined;
    try {
      const response = await customerApi.detail(id);
      if (request === requestId) customer.value = response.data;
    } catch (e) {
      if (request === requestId) error.value = customerError(e);
    } finally {
      if (request === requestId) loading.value = false;
    }
  }

  watch(() => route.query.customerId, load, { immediate: true });
</script>
