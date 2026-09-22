<!--
  * 客户详情（独立隐藏路由，可深链）—— Wave 7 起为客户 360° 业务上下文。
  *
  * 来源：**W1 派生** —— 结构照抄 `views/business/scm/product/product-detail.vue`。
  * V2 需要可深链的独立页（对应 `t_menu` 434「客户详情」，`visible_flag = false`）。
  *
  * Wave 7 适配（§11）：在原「基础资料」之上补 4 个只读上下文 Tab，全部锁定同一个 customerId。
  * - 最近订单 / 协议价 / 可售商品分别复用订单、价格中心、客户 SKU 可见性的**既有查询接口**，
  *   不新建第二份事实，也不落副本；各 Tab 仍受各自领域权限约束（无订单权限时订单/常购 Tab 退化为错误提示）。
  * - 常购商品是后端只读聚合（§7.5 口径：仅已确认订单、按 SKU+单位分组、订购量非结算量、
  *   最近价为锁定单价缺失即空不兜底）。
  * 非基础资料 Tab 采用**首次进入才加载**（lazy），切换客户时整体复位。
-->
<template>
  <a-card size="small" :bordered="false">
    <a-space class="smart-margin-bottom10">
      <a-button @click="router.push('/customer/customer-list')">返回客户列表</a-button>
      <a-button @click="reloadActive">刷新</a-button>
    </a-space>

    <a-tabs v-model:activeKey="activeTab">
      <!-- 基础资料 -->
      <a-tab-pane key="base" tab="基础资料">
        <a-spin :spinning="baseLoading">
          <a-alert v-if="baseError" :message="baseError" type="error" show-icon>
            <template #action>
              <a-button size="small" @click="loadBase">重新加载</a-button>
            </template>
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
        </a-spin>
      </a-tab-pane>

      <!-- 最近订单 -->
      <a-tab-pane key="orders" tab="最近订单">
        <a-alert v-if="orders.error.value" :message="orders.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="orders.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <a-table v-else :data-source="orders.rows.value" :columns="orderCols" row-key="orderId" size="small" bordered
                 :loading="orders.loading.value" :pagination="false" :scroll="{ x: 760 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderSource'">{{ SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc || record.orderSource }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag>{{ SCM_ORDER_STATUS_ENUM[record.status]?.desc || record.status }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'orderedTotalAmount'"><span class="amount">{{ formatAmountOrDash(record.orderedTotalAmount) }}</span></template>
            <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
          </template>
        </a-table>
        <div v-if="!orders.error.value" class="smart-query-table-page">
          <a-pagination v-model:current="ordersPage.pageNum" v-model:page-size="ordersPage.pageSize" :total="orders.total.value"
                        size="small" show-size-changer :page-size-options="['20', '50']" @change="orders.reload()" :show-total="(n: number) => `共 ${n} 条`" />
        </div>
      </a-tab-pane>

      <!-- 常购商品 -->
      <a-tab-pane key="frequent" tab="常购商品">
        <a-space class="smart-margin-bottom10">
          <span>统计窗口</span>
          <a-select v-model:value="freqDays" style="width: 120px" :options="freqDayOptions" @change="onFreqDaysChange" />
          <span class="smart-font-size12 smart-color-gray">按 SKU＋单位分组，数量为订购量（非实重／结算量），不跨单位求和</span>
        </a-space>
        <a-alert v-if="frequent.error.value" :message="frequent.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="frequent.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <a-table v-else :data-source="frequent.rows.value" :columns="frequentCols" :row-key="(r: CustomerFrequentSku) => `${r.skuId}-${r.unit}`" size="small" bordered
                 :loading="frequent.loading.value" :pagination="false" :scroll="{ x: 960 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderedQuantity'"><span class="amount">{{ record.orderedQuantity }}</span></template>
            <template v-else-if="column.dataIndex === 'recentUnitPrice'"><span class="amount">{{ formatAmountOrDash(record.recentUnitPrice) }}</span></template>
            <template v-else-if="column.dataIndex === 'lastConfirmedAt'">{{ datetime(record.lastConfirmedAt) }}</template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- 协议价 -->
      <a-tab-pane key="agreement" tab="协议价">
        <a-alert v-if="agreement.error.value" :message="agreement.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="agreement.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <a-table v-else :data-source="agreement.rows.value" :columns="agreementCols" row-key="agreementPriceId" size="small" bordered
                 :loading="agreement.loading.value" :pagination="false" :scroll="{ x: 900 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'unitPrice'"><span class="amount">{{ formatAmount(record.unitPrice) }}</span></template>
            <template v-else-if="column.dataIndex === 'effectiveFrom'">{{ datetime(record.effectiveFrom) }}</template>
            <template v-else-if="column.dataIndex === 'effectiveTo'">{{ record.effectiveTo ? datetime(record.effectiveTo) : '长期有效' }}</template>
          </template>
        </a-table>
        <div v-if="!agreement.error.value" class="smart-query-table-page">
          <a-pagination v-model:current="agreementPage.pageNum" v-model:page-size="agreementPage.pageSize" :total="agreement.total.value"
                        size="small" show-size-changer :page-size-options="['20', '50']" @change="agreement.reload()" :show-total="(n: number) => `共 ${n} 条`" />
        </div>
      </a-tab-pane>

      <!-- 可售商品 -->
      <a-tab-pane key="visibility" tab="可售商品">
        <a-alert v-if="visibility.error.value" :message="visibility.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="visibility.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <template v-else>
          <a-alert v-if="visPolicyText" :message="visPolicyText" type="info" show-icon class="smart-margin-bottom10" />
          <a-table :data-source="visRows" :columns="visibilityCols" :row-key="(r: VisibilityRow) => String(r.skuId)" size="small" bordered
                   :loading="visibility.loading.value" :pagination="false" :scroll="{ x: 720 }">
            <template #bodyCell="{ record, column }">
              <template v-if="column.dataIndex === 'skuStatus'">
                <a-tag>{{ record.skuStatus || '—' }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
            </template>
          </a-table>
          <div class="smart-query-table-page">
            <a-pagination v-model:current="visibilityPage.pageNum" v-model:page-size="visibilityPage.pageSize" :total="visibility.total.value"
                          size="small" show-size-changer :page-size-options="['20', '50']" @change="visibility.reload()" :show-total="(n: number) => `共 ${n} 条`" />
          </div>
        </template>
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, shallowRef, watch, type Ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {orderApi} from '/@/api/business/scm/order-api';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {customerVisibilityApi} from '/@/api/business/scm/customer-visibility-api';
import type {CustomerDetail, CustomerFrequentSku, CustomerStatus} from '/@/types/business/scm/customer';
import type {PriceRow, VisibilityRow} from '/@/types/business/scm/pricing';
import type {Order} from '/@/views/business/scm/order/order-types';
import {
  CREDIT_PERIOD_TYPE_ENUM,
  CREDIT_PERIOD_UNIT_ENUM,
  CUSTOMER_STATUS_ENUM,
  SETTLE_MODE_ENUM
} from '/@/constants/business/scm/customer-const';
import {SCM_ORDER_SOURCE_ENUM, SCM_ORDER_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import {customerError} from './customer-errors';
import {orderError} from '../order/order-errors';
import {pricingError} from '../pricing/pricing-errors';
import {formatAmount, formatAmountOrDash} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';

const route = useRoute();
const router = useRouter();

const activeTab = ref<'base' | 'orders' | 'frequent' | 'agreement' | 'visibility'>('base');

/** 深链参数即全部 Tab 的唯一上下文；非纯数字视为无效，任何 Tab 都不据此发请求。 */
const customerId = computed(() => {
  const id = route.query.customerId;
  return typeof id === 'string' && /^\d+$/.test(id) ? id : '';
});

// ---------------------------------------------------------------------------
// 基础资料（沿用 W2 的详情加载与竞态守卫）
// ---------------------------------------------------------------------------
const customer = ref<CustomerDetail>();
const baseLoading = ref(false);
const baseError = ref('');
let baseReq = 0;

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

async function loadBase() {
  const request = ++baseReq;
  if (!customerId.value) {
    customer.value = undefined;
    baseError.value = '客户链接缺少有效编号';
    return;
  }
  baseLoading.value = true;
  baseError.value = '';
  customer.value = undefined;
  try {
    const response = await customerApi.detail(customerId.value);
    if (request === baseReq) customer.value = response.data;
  } catch (e) {
    if (request === baseReq) baseError.value = customerError(e);
  } finally {
    if (request === baseReq) baseLoading.value = false;
  }
}

// ---------------------------------------------------------------------------
// 上下文 Tab：统一的「首次进入才加载 + 竞态守卫 + 复位」抽象，
// 各 Tab 只声明自己的取数（复用既有查询接口）与错误口径，不各自复制一套加载逻辑。
// ---------------------------------------------------------------------------
interface TabLoader<T> {
  rows: Ref<T[]>;
  total: Ref<number>;
  loading: Ref<boolean>;
  error: Ref<string>;
  loaded: Ref<boolean>;
  reload: () => Promise<void>;
  ensure: () => void;
  reset: () => void;
}

function useTab<T>(fetch: () => Promise<{ list: T[]; total: number }>, toError: (e: unknown) => string): TabLoader<T> {
  const rows = shallowRef<T[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref('');
  const loaded = ref(false);
  let seq = 0;
  async function reload(): Promise<void> {
    const id = ++seq;
    loading.value = true;
    error.value = '';
    try {
      const r = await fetch();
      if (id === seq) {
        rows.value = r.list;
        total.value = r.total;
        loaded.value = true;
      }
    } catch (e) {
      if (id === seq) error.value = toError(e);
    } finally {
      if (id === seq) loading.value = false;
    }
  }
  function ensure(): void {
    if (customerId.value && !loaded.value && !loading.value) void reload();
  }
  function reset(): void {
    seq++; // 作废在途请求
    rows.value = [];
    total.value = 0;
    error.value = '';
    loaded.value = false;
    loading.value = false;
  }
  return {rows, total, loading, error, loaded, reload, ensure, reset};
}

const ordersPage = reactive({pageNum: 1, pageSize: 20});
const orders = useTab<Order>(
    () => orderApi.query({pageNum: ordersPage.pageNum, pageSize: ordersPage.pageSize, customerId: customerId.value})
        .then((r) => ({list: r.data.list, total: r.data.total})),
    orderError);

const freqDays = ref(90);
const freqDayOptions = [
  {value: 30, label: '近 30 天'},
  {value: 90, label: '近 90 天'},
  {value: 180, label: '近 180 天'},
  {value: 365, label: '近一年'}
];
const frequent = useTab<CustomerFrequentSku>(
    () => customerApi.frequentSkus(customerId.value, freqDays.value, 20).then((r) => ({list: r.data, total: r.data.length})),
    customerError);

const agreementPage = reactive({pageNum: 1, pageSize: 20});
const agreement = useTab<PriceRow>(
    () => pricingApi.agreement.query({pageNum: agreementPage.pageNum, pageSize: agreementPage.pageSize, customerId: customerId.value})
        .then((r) => ({list: r.data.list, total: r.data.total})),
    pricingError);

const visibilityPage = reactive({pageNum: 1, pageSize: 20});
const visibility = useTab<VisibilityRow>(
    () => customerVisibilityApi.query({pageNum: visibilityPage.pageNum, pageSize: visibilityPage.pageSize, customerId: customerId.value})
        .then((r) => ({list: r.data.list, total: r.data.total})),
    customerError);

function ensureTab(key: typeof activeTab.value): void {
  if (key === 'orders') orders.ensure();
  else if (key === 'frequent') frequent.ensure();
  else if (key === 'agreement') agreement.ensure();
  else if (key === 'visibility') visibility.ensure();
}

function resetAllTabs(): void {
  orders.reset();
  frequent.reset();
  agreement.reset();
  visibility.reset();
}

function reloadActive(): void {
  const key = activeTab.value;
  if (key === 'base') void loadBase();
  else if (key === 'orders') void orders.reload();
  else if (key === 'frequent') void frequent.reload();
  else if (key === 'agreement') void agreement.reload();
  else void visibility.reload();
}

function onFreqDaysChange(): void {
  if (customerId.value) void frequent.reload();
}

// 可售商品反查对「全部可见」客户返回一行 SKU 为空的策略行，这里据实拆开：策略文案 + 白名单 SKU 明细。
const visPolicyText = computed(() => {
  const policy = visibility.rows.value[0]?.visibilityPolicy;
  if (!policy) return '';
  return policy === 'ALL_ENABLED'
      ? '可见范围：全部已启用商品（未设 SKU 白名单）'
      : '可见范围：按白名单可售，以下为该客户已授权 SKU';
});
const visRows = computed(() => visibility.rows.value.filter((r) => r.skuId != null));

// ---------------------------------------------------------------------------
// 列定义（静态）
// ---------------------------------------------------------------------------
const orderCols: TableColumnsType<Order> = [
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '来源', dataIndex: 'orderSource', width: 110},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '订单金额', dataIndex: 'orderedTotalAmount', align: 'right', width: 130},
  {title: '创建时间', dataIndex: 'createdAt', width: 180}
];
const frequentCols: TableColumnsType<CustomerFrequentSku> = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 140},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '规格', dataIndex: 'specName', width: 120},
  {title: '单位', dataIndex: 'unit', width: 80},
  {title: '订购次数', dataIndex: 'orderCount', align: 'right', width: 100},
  {title: '订购量', dataIndex: 'orderedQuantity', align: 'right', width: 110},
  {title: '最近确认', dataIndex: 'lastConfirmedAt', width: 180},
  {title: '最近成交价', dataIndex: 'recentUnitPrice', align: 'right', width: 120}
];
const agreementCols: TableColumnsType<PriceRow> = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '规格', dataIndex: 'specName', width: 120},
  {title: '协议单价', dataIndex: 'unitPrice', align: 'right', width: 120},
  {title: '生效时间', dataIndex: 'effectiveFrom', width: 180},
  {title: '结束时间', dataIndex: 'effectiveTo', width: 180}
];
const visibilityCols: TableColumnsType<VisibilityRow> = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '规格', dataIndex: 'specName', width: 120},
  {title: 'SKU 状态', dataIndex: 'skuStatus', align: 'center', width: 110},
  {title: '加入白名单时间', dataIndex: 'createdAt', width: 180}
];

watch(customerId, () => {
  resetAllTabs();
  void loadBase();
  ensureTab(activeTab.value);
});
watch(activeTab, (key) => ensureTab(key));

onMounted(() => {
  void loadBase();
  ensureTab(activeTab.value);
});
</script>

<style scoped>
.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
