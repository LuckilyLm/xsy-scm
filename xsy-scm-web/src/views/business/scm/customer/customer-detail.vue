<!--
  客户详情（独立隐藏路由，可深链）—— 客户 360° 业务档案。

  四个上下文 Tab 全部锁定同一个 customerId，各自复用订单 / 价格中心 / 客户 SKU 可见性
  的既有查询接口，不新建第二份事实、不落副本；各 Tab 仍受各自领域权限约束
  （无订单权限时订单 / 常购 Tab 退化为错误提示）。非基础资料 Tab 首次进入才加载，
  切换客户时整体复位。

  只读口径：本页不发起任何客户写命令，编辑走列表同一只 `CustomerDrawer`。
  经营概览只用详情接口已有字段，不臆造接口没有的指标。
-->
<template>
  <a-card size="small" :bordered="false">
    <!-- 页头：整个页面的上下文，必须留在 Tabs 之外 -->
    <header class="scm-detail-header">
      <div class="scm-detail-header__main">
        <a-button type="link" class="scm-detail-header__back" @click="backToList">
          <ArrowLeftOutlined/>
          客户档案
        </a-button>
        <div class="scm-detail-header__title-row">
          <h1 class="scm-detail-header__title">{{ customer?.name || '客户详情' }}</h1>
          <ScmStatusTag v-if="customer" :color="statusColor(customer.status)" :label="statusText(customer.status)"/>
        </div>
        <p v-if="customer" class="scm-detail-header__meta">
          <span class="scm-mono">{{ customer.customerCode }}</span>
          <span v-if="customer.customerTypeName"> · {{ customer.customerTypeName }}</span>
          <span v-if="customer.sellerName"> · {{ customer.sellerName }}</span>
        </p>
      </div>
      <div class="scm-detail-header__actions">
        <a-button v-privilege="'scm:customer:update'" :disabled="!customerId" @click="openEditDrawer">
          编辑客户
        </a-button>
        <a-tooltip title="刷新">
          <a-button :disabled="!customerId" aria-label="刷新" @click="reloadActive">
            <ReloadOutlined/>
          </a-button>
        </a-tooltip>
        <ScmActionMore :actions="headerActions" @select="onHeaderAction"/>
      </div>
    </header>

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
            <!-- 阅读宽度：基础资料不铺满超宽屏；表格类 Tab 仍用完整宽度 -->
            <div class="scm-detail-read">
              <!-- 1. 客户经营概览：仅用详情接口已有字段 -->
              <section class="scm-summary-section">
                <h3 class="scm-detail-card__title">客户经营概览</h3>
                <div class="scm-summary">
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">客户状态</span>
                    <span class="scm-summary__value">
                      <ScmStatusTag :color="statusColor(customer.status)" :label="statusText(customer.status)"/>
                    </span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">结算方式</span>
                    <span class="scm-summary__value">{{ settleModeText(customer.settleMode) }}</span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">授信额度</span>
                    <span class="scm-summary__value scm-money">{{ creditLimitText }}</span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">账期</span>
                    <span class="scm-summary__value">{{ creditPeriodSummary }}</span>
                  </div>
                </div>
              </section>

              <div class="scm-detail-grid">
                <!-- 2. 联系与地址 -->
                <section class="scm-detail-card">
                  <h3 class="scm-detail-card__title">联系与地址</h3>
                  <dl class="scm-field-list">
                    <div class="scm-field">
                      <dt class="scm-field__label">联系人</dt>
                      <dd class="scm-field__value">{{ customer.contactName || '—' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">电话</dt>
                      <dd class="scm-field__value">{{ customer.contactPhone || '—' }}</dd>
                    </div>
                    <div class="scm-field scm-field--wide">
                      <dt class="scm-field__label">地址</dt>
                      <dd class="scm-field__value">{{ customer.address || '—' }}</dd>
                    </div>
                  </dl>
                </section>

                <!-- 3. 归属关系 -->
                <section class="scm-detail-card">
                  <h3 class="scm-detail-card__title">归属关系</h3>
                  <dl class="scm-field-list">
                    <div class="scm-field">
                      <dt class="scm-field__label">上级集团</dt>
                      <dd class="scm-field__value">{{ customer.parentCustomerName || '—' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">统一结算方</dt>
                      <dd class="scm-field__value">{{ customer.settlementCustomerName || customer.name }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">归属业务员</dt>
                      <dd class="scm-field__value">{{ customer.sellerName || '未分配' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">绑定供应商</dt>
                      <dd class="scm-field__value">{{ customer.supplierName || '—' }}</dd>
                    </div>
                  </dl>
                </section>

                <!-- 4. 授信与账期：整行卡片 -->
                <section class="scm-detail-card scm-detail-card--full">
                  <h3 class="scm-detail-card__title">授信与账期</h3>
                  <dl class="scm-field-list scm-field-list--3">
                    <div class="scm-field">
                      <dt class="scm-field__label">授信额度</dt>
                      <dd class="scm-field__value scm-money">{{ creditLimitText }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">账期类型</dt>
                      <dd class="scm-field__value">{{ creditPeriodTypeText }}</dd>
                    </div>
                    <template v-if="customer.creditPeriodType === 'BY_AMOUNT'">
                      <div class="scm-field">
                        <dt class="scm-field__label">金额阈值</dt>
                        <dd class="scm-field__value scm-money">{{ amountOrDash(customer.creditAmountThreshold) }}</dd>
                      </div>
                    </template>
                    <template v-else-if="customer.creditPeriodType === 'BY_TIME'">
                      <div class="scm-field">
                        <dt class="scm-field__label">账期值</dt>
                        <dd class="scm-field__value">{{ customer.creditPeriodValue ?? '—' }}</dd>
                      </div>
                      <div class="scm-field">
                        <dt class="scm-field__label">账期单位</dt>
                        <dd class="scm-field__value">{{ creditPeriodUnitText }}</dd>
                      </div>
                      <div v-if="customer.creditPeriodUnit === 'MONTH'" class="scm-field">
                        <dt class="scm-field__label">固定结算日</dt>
                        <dd class="scm-field__value">{{ customer.settleDay ?? '—' }}</dd>
                      </div>
                    </template>
                  </dl>
                </section>
              </div>

              <!-- 5. 系统信息：弱化卡片，编码与时间不占核心区域 -->
              <section class="scm-detail-card scm-detail-card--muted scm-detail-block">
                <h3 class="scm-detail-card__title">系统信息</h3>
                <dl class="scm-field-list scm-field-list--3">
                  <div class="scm-field">
                    <dt class="scm-field__label">客户编码</dt>
                    <dd class="scm-field__value scm-mono">{{ customer.customerCode }}</dd>
                  </div>
                  <div class="scm-field">
                    <dt class="scm-field__label">创建时间</dt>
                    <dd class="scm-field__value">{{ datetime(customer.createdAt) }}</dd>
                  </div>
                  <div class="scm-field">
                    <dt class="scm-field__label">更新时间</dt>
                    <dd class="scm-field__value">{{ datetime(customer.updatedAt) }}</dd>
                  </div>
                  <div class="scm-field scm-field--wide">
                    <dt class="scm-field__label">备注</dt>
                    <dd class="scm-field__value">{{ customer.remark || '—' }}</dd>
                  </div>
                </dl>
              </section>
            </div>
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
                 :loading="orders.loading.value" :pagination="false" :scroll="{ x: 710 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderNo'"><span class="scm-mono">{{ record.orderNo }}</span></template>
            <template v-else-if="column.dataIndex === 'orderSource'">{{ SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc || record.orderSource }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <ScmStatusTag :tone="orderStatusTone(record.status)" :label="SCM_ORDER_STATUS_ENUM[record.status]?.desc || record.status"/>
            </template>
            <template v-else-if="column.dataIndex === 'orderedTotalAmount'"><span class="scm-money">{{ formatAmountOrDash(record.orderedTotalAmount) }}</span></template>
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
        <a-space class="smart-margin-bottom10" :size="12">
          <span>统计窗口</span>
          <a-segmented v-model:value="freqDays" :options="freqDayOptions" @change="onFreqDaysChange"/>
          <span class="smart-font-size12 smart-color-gray">按商品规格＋单位分组，数量为订购量（非实重／结算量），不跨单位求和</span>
        </a-space>
        <a-alert v-if="frequent.error.value" :message="frequent.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="frequent.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <a-table v-else :data-source="frequent.rows.value" :columns="frequentCols" :row-key="(r: CustomerFrequentSku) => `${r.skuId}-${r.unit}`" size="small" bordered
                 :loading="frequent.loading.value" :pagination="false" :scroll="{ x: 950 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'productName'">
              <div class="scm-cell-stack">
                <span class="scm-cell-stack__main">{{ record.productName || '—' }}</span>
                <span v-if="record.skuCode" class="scm-cell-stack__sub scm-mono">{{ record.skuCode }}</span>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'orderedQuantity'"><span class="scm-quantity">{{ record.orderedQuantity }}</span></template>
            <template v-else-if="column.dataIndex === 'recentUnitPrice'"><span class="scm-money">{{ formatAmountOrDash(record.recentUnitPrice) }}</span></template>
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
                 :loading="agreement.loading.value" :pagination="false" :scroll="{ x: 790 }">
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'productName'">
              <div class="scm-cell-stack">
                <span class="scm-cell-stack__main">{{ record.productName || '—' }}</span>
                <span v-if="record.skuCode" class="scm-cell-stack__sub scm-mono">{{ record.skuCode }}</span>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'unitPrice'"><span class="scm-money">{{ formatAmount(record.unitPrice) }}</span></template>
            <template v-else-if="column.dataIndex === 'effectivePeriod'">{{ effectivePeriodText(record) }}</template>
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
                   :loading="visibility.loading.value" :pagination="false" :scroll="{ x: 650 }">
            <template #bodyCell="{ record, column }">
              <template v-if="column.dataIndex === 'productName'">
                <div class="scm-cell-stack">
                  <span class="scm-cell-stack__main">{{ record.productName || '—' }}</span>
                  <span v-if="record.skuCode" class="scm-cell-stack__sub scm-mono">{{ record.skuCode }}</span>
                </div>
              </template>
              <template v-else-if="column.dataIndex === 'skuStatus'">
                <ScmStatusTag v-if="shelfStatusLabel(record.skuStatus)" :tone="shelfStatusTone(record.skuStatus)"
                              :label="shelfStatusLabel(record.skuStatus)"/>
                <span v-else>—</span>
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

    <!-- 编辑入口复用列表的同一只抽屉：本页不新增写端点，也不改表单口径 -->
    <CustomerDrawer ref="editDrawer" @saved="loadBase"/>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, shallowRef, watch, type Ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ArrowLeftOutlined, ReloadOutlined} from '@ant-design/icons-vue';
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
import {SHELF_STATUS_ENUM} from '/@/constants/business/scm/product-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {customerError} from './customer-errors';
import {orderError} from '../order/order-errors';
import {pricingError} from '../pricing/pricing-errors';
import {hasPermission} from '../common/scm-permission';
import {formatAmount, formatAmountOrDash} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';
import CustomerDrawer from './components/customer-form-drawer.vue';

const route = useRoute();
const router = useRouter();

const activeTab = ref<'base' | 'orders' | 'frequent' | 'agreement' | 'visibility'>('base');

/** 深链参数即全部 Tab 的唯一上下文；非纯数字视为无效，任何 Tab 都不据此发请求。 */
const customerId = computed(() => {
  const id = route.query.customerId;
  return typeof id === 'string' && /^\d+$/.test(id) ? id : '';
});

function backToList(): void {
  void router.push('/customer/customer-list');
}

// ---------------------------------------------------------------------------
// 基础资料（沿用详情加载与竞态守卫）
// ---------------------------------------------------------------------------
const customer = ref<CustomerDetail>();
const baseLoading = ref(false);
const baseError = ref('');
let baseReq = 0;

/** 编辑入口复用列表的同一只抽屉：只暴露 open(customerId)，写命令仍由抽屉自己发起。 */
const editDrawer = ref<InstanceType<typeof CustomerDrawer>>();

function openEditDrawer(): void {
  if (customerId.value) editDrawer.value?.open(customerId.value);
}

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

/** 授信额度：`null` 是「未设置」，与 `"0.0000"`（明确授信为零）语义不同，不能混成一个 0。 */
const creditLimitText = computed(() =>
  customer.value?.creditLimit == null ? '未设置' : formatAmount(customer.value.creditLimit)
);

const amountOrDash = (value: string | null | undefined): string => formatAmountOrDash(value);

/** 概览里的「账期」一格：把类型与取值合成一句，明细仍在「授信与账期」卡片里逐项列出。 */
const creditPeriodSummary = computed(() => {
  const current = customer.value;
  if (!current?.creditPeriodType) return '未设置账期';
  const type = creditPeriodTypeText.value;
  if (current.creditPeriodType === 'BY_AMOUNT') {
    return current.creditAmountThreshold == null ? type : `${type} · 阈值 ${formatAmount(current.creditAmountThreshold)}`;
  }
  if (current.creditPeriodValue == null) return type;
  const unit = current.creditPeriodUnit ? creditPeriodUnitText.value : '';
  return `${type} · ${current.creditPeriodValue}${unit}`;
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
/** 只有这四档：用 segmented 一次摊开，避免为一个四选一的窗口点两次下拉。 */
const freqDayOptions = [
  {value: 30, label: '30天'},
  {value: 90, label: '90天'},
  {value: 180, label: '180天'},
  {value: 365, label: '1年'}
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

/** 页头「更多」：本页唯一低频动作是操作日志，与列表一样用同一口径的权限裁剪。 */
const canViewOperateLog = computed(() => hasPermission('support:operateLog:query'));
const headerActions = computed<ScmActionItem[]>(() => [
  {key: 'operateLog', label: '操作日志', hidden: !canViewOperateLog.value || !customerId.value},
]);

function onHeaderAction(key: string): void {
  if (key === 'operateLog') openOperateLog();
}

// 携带业务上下文跳到通用操作日志页，按 customerId 精确筛选。
function openOperateLog(): void {
  if (!customerId.value) return;
  void router.push({
    path: '/support/operate-log/operate-log-list',
    query: {businessType: 'CUSTOMER', businessId: customerId.value},
  });
}

// 可售商品反查对「全部可见」客户返回一行 SKU 为空的策略行，这里据实拆开：策略文案 + 白名单 SKU 明细。
const visPolicyText = computed(() => {
  const policy = visibility.rows.value[0]?.visibilityPolicy;
  if (!policy) return '';
  return policy === 'ALL_ENABLED'
      ? '可见范围：全部已启用商品（未设商品规格白名单）'
      : '可见范围：按白名单可售，以下为该客户已授权商品规格';
});
const visRows = computed(() => visibility.rows.value.filter((r) => r.skuId != null));

// ---------------------------------------------------------------------------
// 枚举翻译与列定义（静态）
// ---------------------------------------------------------------------------

/** 商品规格状态：后端给的是枚举码，展示必须走翻译，不能把 `ON_SHELF` 直接摊给用户。 */
const shelfStatusLabel = (value?: string | null): string =>
    SHELF_STATUS_ENUM.find((item) => item.value === value)?.label ?? '';
const shelfStatusTone = (value?: string | null): ScmStatusTone => (value === 'ON_SHELF' ? 'success' : 'neutral');

/** 草稿/待确认=待处理，已确认=处理中，已取消=失效（与订单列表同一套档位）。 */
const ORDER_STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  PENDING: 'warning',
  CONFIRMED: 'processing',
  CANCELLED: 'neutral',
};
const orderStatusTone = (value?: string | null): ScmStatusTone => ORDER_STATUS_TONE[value ?? ''] ?? 'neutral';

/** 协议价有效期：无结束时间即长期有效（不是缺数据，不能显示破折号）。 */
const effectivePeriodText = (row: PriceRow): string =>
    `${datetime(row.effectiveFrom)} ～ ${row.effectiveTo ? datetime(row.effectiveTo) : '长期有效'}`;

const orderCols: TableColumnsType<Order> = [
  {title: '订单号', dataIndex: 'orderNo', width: 180},
  {title: '来源', dataIndex: 'orderSource', width: 110},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '订单金额', dataIndex: 'orderedTotalAmount', align: 'right', width: 140},
  {title: '创建时间', dataIndex: 'createdAt', width: 180}
];
const frequentCols: TableColumnsType<CustomerFrequentSku> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '单位', dataIndex: 'unit', align: 'center', width: 80},
  {title: '订购次数', dataIndex: 'orderCount', align: 'right', width: 100},
  {title: '订购量', dataIndex: 'orderedQuantity', align: 'right', width: 110},
  {title: '最近成交价', dataIndex: 'recentUnitPrice', align: 'right', width: 120},
  {title: '最近购买', dataIndex: 'lastConfirmedAt', width: 180}
];
const agreementCols: TableColumnsType<PriceRow> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '协议单价', dataIndex: 'unitPrice', align: 'right', width: 130},
  {title: '有效期', dataIndex: 'effectivePeriod', width: 300}
];
const visibilityCols: TableColumnsType<VisibilityRow> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '状态', dataIndex: 'skuStatus', align: 'center', width: 110},
  {title: '加入时间', dataIndex: 'createdAt', width: 180}
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
