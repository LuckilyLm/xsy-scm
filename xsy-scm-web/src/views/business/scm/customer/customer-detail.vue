<!-- 客户详情：按页签展示资料、订单和交易情况，只读。 -->
<template>
  <a-card size="small" :bordered="false">
    <!-- 页头在 Tabs 之外，切页签后仍可见 -->
    <header class="scm-detail-header">
      <div class="scm-detail-header__main">
        <a-button type="link" class="scm-detail-header__back" aria-label="客户档案" @click="backToList">
          <ArrowLeftOutlined />
          客户档案
        </a-button>
        <div class="scm-detail-header__title-row">
          <h1 class="scm-detail-header__title">{{ customer?.name || '客户详情' }}</h1>
        </div>
        <p v-if="customer?.sellerName" class="scm-detail-header__meta">
          <span>{{ customer.sellerName }}</span>
        </p>
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
            <!-- 基础资料限制阅读宽度 -->
            <div class="scm-detail-read">
              <!-- 客户经营概览 -->
              <section class="scm-summary-section">
                <h3 class="scm-detail-card__title">客户经营概览</h3>
                <div class="scm-summary">
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">客户状态</span>
                    <span class="scm-summary__value">{{ statusText(customer.status) }}</span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">结算方式</span>
                    <span class="scm-summary__value">{{ settleModeText(customer.settleMode) }}</span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">赊欠额度</span>
                    <span class="scm-summary__value scm-money">{{ creditLimitText }}</span>
                  </div>
                  <div class="scm-summary__item">
                    <span class="scm-summary__label">账期</span>
                    <span class="scm-summary__value">{{ creditPeriodSummary }}</span>
                  </div>
                </div>
              </section>

              <div class="scm-detail-grid">
                <!-- 联系与地址 -->
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

                <!-- 归属关系 -->
                <section class="scm-detail-card">
                  <h3 class="scm-detail-card__title">客户关系</h3>
                  <dl class="scm-field-list">
                    <div class="scm-field">
                      <dt class="scm-field__label">客户分类</dt>
                      <dd class="scm-field__value">{{ customer.businessCategoryName || '未分类' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">上级集团</dt>
                      <dd class="scm-field__value">{{ customer.parentCustomerName || '—' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">统一结算方</dt>
                      <dd class="scm-field__value">{{ customer.settlementCustomerName || customer.name }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">负责业务员</dt>
                      <dd class="scm-field__value">{{ customer.sellerName || '未分配' }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">绑定供应商</dt>
                      <dd class="scm-field__value">{{ customer.supplierName || '—' }}</dd>
                    </div>
                  </dl>
                </section>

                <!-- 授信与账期 -->
                <section class="scm-detail-card scm-detail-card--full">
                  <h3 class="scm-detail-card__title">赊欠额度与付款期限</h3>
                  <dl class="scm-field-list scm-field-list--3">
                    <div class="scm-field">
                      <dt class="scm-field__label">赊欠额度</dt>
                      <dd class="scm-field__value scm-money">{{ creditLimitText }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">账期类型</dt>
                      <dd class="scm-field__value">{{ creditPeriodTypeText }}</dd>
                    </div>
                    <div class="scm-field">
                      <dt class="scm-field__label">商城货到付款</dt>
                      <dd class="scm-field__value">{{ customer.codAllowed ? '已开通' : '未开通' }}</dd>
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

              <section class="scm-detail-card scm-detail-card--full scm-detail-block" v-privilege="'scm:mall:account:query'">
                <div class="scm-detail-card__head">
                  <h3 class="scm-detail-card__title">商城账号</h3>
                  <a-button v-privilege="'scm:mall:account:create'" type="primary" size="small" @click="openMallAccount('create')"
                    >开通商城账号</a-button
                  >
                </div>
                <a-spin :spinning="mallAccountLoading">
                  <a-alert v-if="mallAccountError" type="error" show-icon :message="mallAccountError" />
                  <a-table
                    v-else-if="mallAccounts.length"
                    :data-source="mallAccounts"
                    :columns="mallAccountCols"
                    row-key="accountId"
                    size="small"
                    bordered
                    :pagination="false"
                    :scroll="{x: scmColumnsWidth(mallAccountCols)}"
                  >
                    <template #bodyCell="{record, column}">
                      <template v-if="column.dataIndex === 'loginPhone'"
                        ><span class="scm-mono">{{ record.loginPhone }}</span></template
                      >
                      <template v-else-if="column.dataIndex === 'status'">{{ record.status === 'ENABLED' ? '已启用' : '已禁用' }}</template>
                      <template v-else-if="column.dataIndex === 'firstPasswordChange'">{{
                        record.firstPasswordChange ? '等待客户修改' : '已修改'
                      }}</template>
                      <template v-else-if="column.dataIndex === 'lastLoginAt'">{{ datetime(record.lastLoginAt) }}</template>
                      <template v-else-if="column.dataIndex === 'action'">
                        <a-space>
                          <a-button type="link" size="small" v-privilege="'scm:mall:account:status'" @click="toggleMallAccount(record)">{{
                            record.status === 'ENABLED' ? '禁用账号' : '启用账号'
                          }}</a-button>
                          <a-button type="link" size="small" v-privilege="'scm:mall:account:password:reset'" @click="openMallAccount('reset', record)"
                            >重置临时密码</a-button
                          >
                        </a-space>
                      </template>
                    </template>
                  </a-table>
                  <a-empty v-else description="该客户尚未开通商城账号" />
                </a-spin>
              </section>

              <!-- 系统信息 -->
              <section class="scm-detail-card scm-detail-card--muted scm-detail-block">
                <h3 class="scm-detail-card__title">系统信息</h3>
                <dl class="scm-field-list scm-field-list--3">
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
        <a-table
          v-else
          :data-source="orders.rows.value"
          :columns="orderCols"
          row-key="orderId"
          size="small"
          bordered
          :loading="orders.loading.value"
          :pagination="false"
          :scroll="{x: scmColumnsWidth(orderCols)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'orderNo'"
              ><span class="scm-mono">{{ record.orderNo }}</span></template
            >
            <template v-else-if="column.dataIndex === 'orderSource'">{{
              SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc || record.orderSource
            }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <ScmStatusTag :tone="orderStatusTone(record.status)" :label="SCM_ORDER_STATUS_ENUM[record.status]?.desc || '—'" />
            </template>
            <template v-else-if="column.dataIndex === 'orderedTotalAmount'"
              ><span class="scm-money">{{ formatAmountOrDash(record.orderedTotalAmount) }}</span></template
            >
            <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
          </template>
        </a-table>
        <div v-if="!orders.error.value" class="xsy-query-table-page">
          <a-pagination
            show-quick-jumper
            show-less-items
            v-model:current="ordersPage.pageNum"
            v-model:page-size="ordersPage.pageSize"
            :total="orders.total.value"
            size="small"
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
            @change="orders.reload()"
            :show-total="(n: number) => `共 ${n} 条`"
          />
        </div>
      </a-tab-pane>

      <!-- 常购商品 -->
      <a-tab-pane key="frequent" tab="常购商品">
        <a-space class="xsy-margin-bottom10" :size="12">
          <span>统计窗口</span>
          <a-segmented v-model:value="freqDays" :options="freqDayOptions" @change="onFreqDaysChange" />
          <span class="xsy-font-size12 xsy-color-gray">按商品规格＋单位分组，数量为订购量（非实重／结算量），不跨单位求和</span>
        </a-space>
        <a-alert v-if="frequent.error.value" :message="frequent.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="frequent.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <a-table
          v-else
          :data-source="frequent.rows.value"
          :columns="frequentCols"
          :row-key="(r: CustomerFrequentSku) => `${r.skuId}-${r.unit}`"
          size="small"
          bordered
          :loading="frequent.loading.value"
          :pagination="false"
          :scroll="{x: scmColumnsWidth(frequentCols)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'productName'">
              <span>{{ record.productName || '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderedQuantity'"
              ><span class="scm-quantity">{{ record.orderedQuantity }}</span></template
            >
            <template v-else-if="column.dataIndex === 'recentUnitPrice'"
              ><span class="scm-money">{{ formatAmountOrDash(record.recentUnitPrice) }}</span></template
            >
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
        <a-table
          v-else
          :data-source="agreement.rows.value"
          :columns="agreementCols"
          row-key="agreementPriceId"
          size="small"
          bordered
          :loading="agreement.loading.value"
          :pagination="false"
          :scroll="{x: scmColumnsWidth(agreementCols)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'productName'">
              <span>{{ record.productName || '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitPrice'"
              ><span class="scm-money">{{ formatAmount(record.unitPrice) }}</span></template
            >
            <template v-else-if="column.dataIndex === 'effectivePeriod'">{{ effectivePeriodText(record) }}</template>
          </template>
        </a-table>
        <div v-if="!agreement.error.value" class="xsy-query-table-page">
          <a-pagination
            show-quick-jumper
            show-less-items
            v-model:current="agreementPage.pageNum"
            v-model:page-size="agreementPage.pageSize"
            :total="agreement.total.value"
            size="small"
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
            @change="agreement.reload()"
            :show-total="(n: number) => `共 ${n} 条`"
          />
        </div>
      </a-tab-pane>

      <!-- 可售商品 -->
      <a-tab-pane key="visibility" tab="可售商品">
        <!-- 入口不受列表读取失败影响：有更新权限就能调整。 -->
        <div class="scm-visibility-toolbar">
          <p v-if="visPolicyText" class="scm-note">{{ visPolicyText }}</p>
          <a-button v-if="hasPermission('scm:customer:update')" type="primary" @click="openVisibility()">调整可售商品</a-button>
        </div>
        <a-alert v-if="visibility.error.value" :message="visibility.error.value" type="error" show-icon>
          <template #action>
            <a-button size="small" @click="visibility.reload()">重新加载</a-button>
          </template>
        </a-alert>
        <template v-else>
          <a-table
            :data-source="visRows"
            :columns="visibilityCols"
            :row-key="(r: VisibilityRow) => String(r.skuId)"
            size="small"
            bordered
            :loading="visibility.loading.value"
            :pagination="false"
            :scroll="{x: scmColumnsWidth(visibilityCols)}"
          >
            <template #bodyCell="{record, column}">
              <template v-if="column.dataIndex === 'productName'">
                <span>{{ record.productName || '—' }}</span>
              </template>
              <template v-else-if="column.dataIndex === 'skuStatus'">
                <ScmStatusTag :tone="shelfStatusTone(record.skuStatus)" :label="shelfStatusLabel(record.skuStatus)" />
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
            </template>
          </a-table>
          <div class="xsy-query-table-page">
            <a-pagination
              show-quick-jumper
              show-less-items
              v-model:current="visibilityPage.pageNum"
              v-model:page-size="visibilityPage.pageSize"
              :total="visibility.total.value"
              size="small"
              show-size-changer
              :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
              @change="visibility.reload()"
              :show-total="(n: number) => `共 ${n} 条`"
            />
          </div>
        </template>
      </a-tab-pane>
      <a-tab-pane key="aliases" tab="客户商品别名">
        <a-alert v-if="aliasError" type="error" :message="aliasError" show-icon>
          <template #action><a-button size="small" @click="loadAliases">重新加载</a-button></template>
        </a-alert>
        <a-space v-if="hasPermission('scm:customer:update')" style="margin-bottom: 12px">
          <a-button type="primary" @click="openAlias()">设置别名</a-button>
        </a-space>
        <a-table
          :data-source="aliases"
          :columns="aliasCols"
          :row-key="(row: CustomerSkuAlias) => String(row.skuId)"
          :loading="aliasLoading"
          size="small"
          bordered
          :pagination="{pageSize: 20}"
          :scroll="{x: scmColumnsWidth(aliasCols)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'action'">
              <a-space v-if="hasPermission('scm:customer:update')">
                <a-button type="link" size="small" @click="openAlias(record)">编辑</a-button>
                <a-popconfirm title="删除这个客户商品别名？" @confirm="removeAlias(record)">
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
    <a-modal v-model:open="aliasModal" title="客户商品别名" :confirm-loading="aliasSaving" @ok="saveAlias">
      <a-form layout="vertical">
        <a-form-item label="商品规格" required>
          <span v-if="aliasForm.version !== undefined">{{ aliasForm.productName }} / {{ aliasForm.specName }}</span>
          <SkuSelect v-else v-model:value="aliasSkuId" :disabled-statuses="[]" />
        </a-form-item>
        <a-form-item label="客户商品别名" required>
          <a-input v-model:value="aliasForm.alias" :maxlength="100" placeholder="仅当前客户的商城显示此名称" />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:open="mallAccountModal"
      :title="mallAccountMode === 'create' ? '开通商城账号' : '重置临时密码'"
      :confirm-loading="mallAccountSaving"
      @ok="saveMallAccount"
    >
      <a-form layout="vertical">
        <a-form-item v-if="mallAccountMode === 'create'" label="登录手机号" required>
          <a-input v-model:value="mallAccountForm.loginPhone" :maxlength="32" />
        </a-form-item>
        <a-form-item label="临时密码" required>
          <a-input-password v-model:value="mallAccountForm.temporaryPassword" :maxlength="64" placeholder="至少8个字符" />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="visibilityModal" title="调整可售商品" :confirm-loading="visibilitySaving" @ok="saveVisibility">
      <a-spin :spinning="visibilitySeeding">
        <a-form layout="vertical">
          <a-form-item label="可见范围">
            <a-radio-group v-model:value="visibilityPolicy">
              <a-radio value="ALL_ENABLED">全部已启用商品</a-radio>
              <a-radio value="ALLOWLIST">只售指定规格</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item v-if="visibilityPolicy === 'ALLOWLIST'" label="可售规格" required>
            <SkuSelect v-model:value="visibilitySkuIds" mode="multiple" :limit="200" :selected-options="visibilitySeeds" :disabled-statuses="[]" />
            <div class="scm-note scm-note--block">未勾选的规格，客户在商城看不到，也无法下单。</div>
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref, shallowRef, watch, type Ref} from 'vue';
import {message} from 'ant-design-vue';
import {useRoute, useRouter} from 'vue-router';
import {ArrowLeftOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi, mallAccountApi, type CustomerSkuAlias, type MallAccount} from '/@/api/business/scm/customer-api';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {customerVisibilityApi} from '/@/api/business/scm/customer-visibility-api';
import type {CustomerDetail, CustomerFrequentSku, CustomerStatus, CustomerVisibilityPolicy, ScmId} from '/@/types/business/scm/customer';
import type {PriceRow, VisibilityRow} from '/@/types/business/scm/pricing';
import type {Order} from '/@/views/business/scm/order/order-types';
import {orderStatusTone} from '/@/views/business/scm/order/order-status-tone';
import {CREDIT_PERIOD_TYPE_ENUM, CREDIT_PERIOD_UNIT_ENUM, CUSTOMER_STATUS_ENUM, SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {SCM_ORDER_SOURCE_ENUM, SCM_ORDER_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import {shelfStatusLabel} from '/@/constants/business/scm/product-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {customerError} from './customer-errors';
import {orderError} from '../order/order-errors';
import {pricingError} from '../pricing/pricing-errors';
import {formatAmount, formatAmountOrDash} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';
import {confirmDialog} from '../common/scm-unsaved-guard';
import {hasPermission} from '../common/scm-permission';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

const route = useRoute();
const router = useRouter();

const activeTab = ref<'base' | 'orders' | 'frequent' | 'agreement' | 'visibility' | 'aliases'>('base');

/** 客户编号来自路由；非法时不发请求。 */
const customerId = computed(() => {
  const id = route.query.customerId;
  return typeof id === 'string' && /^\d+$/.test(id) ? id : '';
});

const aliases = ref<CustomerSkuAlias[]>([]);
const aliasLoading = ref(false);
const aliasError = ref('');
const aliasModal = ref(false);
const aliasSaving = ref(false);
const aliasSkuId = ref<string | number | (string | number)[]>();
const aliasForm = reactive<{skuId?: string | number; productName?: string; specName?: string; alias: string; version?: number}>({alias: ''});
let aliasRequest = 0;
const aliasCols: TableColumnsType<CustomerSkuAlias> = [
  {title: '商品', dataIndex: 'productName', width: 190},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '客户商品别名', dataIndex: 'alias', width: 190},
  {title: '操作', dataIndex: 'action', width: 120, align: 'center'},
];

async function loadAliases(): Promise<void> {
  if (!customerId.value) return;
  const request = ++aliasRequest;
  aliasLoading.value = true;
  aliasError.value = '';
  try {
    const result = await customerApi.skuAliases(customerId.value);
    if (request === aliasRequest) aliases.value = result.data || [];
  } catch (error) {
    if (request === aliasRequest) aliasError.value = customerError(error);
  } finally {
    if (request === aliasRequest) aliasLoading.value = false;
  }
}

function openAlias(row?: CustomerSkuAlias): void {
  aliasSkuId.value = row?.skuId;
  Object.assign(aliasForm, {
    skuId: row?.skuId,
    productName: row?.productName,
    specName: row?.specName,
    alias: row?.alias || '',
    version: row?.version,
  });
  aliasModal.value = true;
}

async function saveAlias(): Promise<void> {
  const skuId = aliasForm.version === undefined ? aliasSkuId.value : aliasForm.skuId;
  if (skuId == null || Array.isArray(skuId) || !aliasForm.alias.trim()) {
    void message.warning('请选择商品规格并填写客户商品别名');
    return;
  }
  aliasSaving.value = true;
  try {
    await customerApi.saveSkuAlias({customerId: customerId.value, skuId, alias: aliasForm.alias.trim(), version: aliasForm.version});
    aliasModal.value = false;
    await loadAliases();
    void message.success('客户商品别名已保存');
  } catch (error) {
    void message.error(customerError(error));
  } finally {
    aliasSaving.value = false;
  }
}

async function removeAlias(row: CustomerSkuAlias): Promise<void> {
  try {
    await customerApi.saveSkuAlias({customerId: customerId.value, skuId: row.skuId, alias: '', version: row.version});
    await loadAliases();
    void message.success('客户商品别名已删除');
  } catch (error) {
    void message.error(customerError(error));
  }
}

function backToList(): void {
  void router.push('/customer/customer-list');
}

// 基础资料
const customer = ref<CustomerDetail>();
const baseLoading = ref(false);
const baseError = ref('');
const mallAccounts = ref<MallAccount[]>([]);
const mallAccountLoading = ref(false);
const mallAccountError = ref('');
const mallAccountModal = ref(false);
const mallAccountMode = ref<'create' | 'reset'>('create');
const mallAccountSaving = ref(false);
const mallAccountForm = reactive({loginPhone: '', temporaryPassword: ''});
const mallAccountTarget = ref<MallAccount | null>(null);
let baseReq = 0;

const mallAccountCols: TableColumnsType<MallAccount> = [
  {title: '登录手机号', dataIndex: 'loginPhone', width: 160},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '临时密码', dataIndex: 'firstPasswordChange', width: 140},
  {title: '最近登录', dataIndex: 'lastLoginAt', width: 180},
  {title: '操作', dataIndex: 'action', width: 180, align: 'center'},
];

const statusText = (value: CustomerStatus): string => CUSTOMER_STATUS_ENUM[value]?.desc || value;
const settleModeText = (value: string): string => SETTLE_MODE_ENUM[value]?.desc || value;
const creditPeriodTypeText = computed(() => {
  const type = customer.value?.creditPeriodType;
  return type ? CREDIT_PERIOD_TYPE_ENUM[type]?.desc || type : '未设置账期';
});
const creditPeriodUnitText = computed(() => {
  const unit = customer.value?.creditPeriodUnit;
  return unit ? CREDIT_PERIOD_UNIT_ENUM[unit]?.desc || unit : '—';
});

/** 授信额度：null 是未设置，与 0 区分。 */
const creditLimitText = computed(() => (customer.value?.creditLimit == null ? '未设置' : formatAmount(customer.value.creditLimit)));

const amountOrDash = (value: string | null | undefined): string => formatAmountOrDash(value);

/** 概览的账期摘要；明细在「授信与账期」卡片。 */
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

async function loadMallAccount(): Promise<void> {
  if (!customerId.value) return;
  mallAccountLoading.value = true;
  mallAccountError.value = '';
  try {
    const response = await mallAccountApi.list(customerId.value);
    mallAccounts.value = response.data || [];
  } catch (e) {
    mallAccountError.value = customerError(e);
  } finally {
    mallAccountLoading.value = false;
  }
}

function openMallAccount(mode: 'create' | 'reset', account: MallAccount | null = null): void {
  mallAccountMode.value = mode;
  mallAccountTarget.value = account;
  mallAccountForm.loginPhone = customer.value?.contactPhone || '';
  mallAccountForm.temporaryPassword = '';
  mallAccountModal.value = true;
}

async function saveMallAccount(): Promise<void> {
  if (mallAccountForm.temporaryPassword.length < 8) {
    void message.warning('临时密码至少8个字符');
    return;
  }
  mallAccountSaving.value = true;
  try {
    if (mallAccountMode.value === 'create') {
      await mallAccountApi.create({customerId: customerId.value, ...mallAccountForm});
    } else if (mallAccountTarget.value) {
      await mallAccountApi.resetPassword({accountId: mallAccountTarget.value.accountId, temporaryPassword: mallAccountForm.temporaryPassword});
    }
    mallAccountModal.value = false;
    await loadMallAccount();
    void message.success(mallAccountMode.value === 'create' ? '商城账号已开通' : '临时密码已重置');
  } catch (error) {
    void message.error(customerError(error));
  } finally {
    mallAccountSaving.value = false;
  }
}

async function toggleMallAccount(account: MallAccount): Promise<void> {
  if (account.status === 'ENABLED') {
    const confirmed = await confirmDialog({
      title: '禁用商城账号',
      content: `禁用后手机号 ${account.loginPhone} 将无法登录商城下单，确认禁用？`,
      okText: '确认禁用',
      okType: 'danger',
      cancelText: '取消',
    });
    if (!confirmed) return;
  }
  try {
    await mallAccountApi.status({accountId: account.accountId, enabled: account.status !== 'ENABLED'});
    await loadMallAccount();
    void message.success('账号状态已更新');
  } catch (error) {
    void message.error(customerError(error));
  }
}

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
    if (request === baseReq) {
      customer.value = response.data;
      if (hasPermission('scm:mall:account:query')) void loadMallAccount();
    }
  } catch (e) {
    if (request === baseReq) baseError.value = customerError(e);
  } finally {
    if (request === baseReq) baseLoading.value = false;
  }
}

// 页签数据：首次进入才加载，请求序号防乱序。
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

function useTab<T>(fetch: () => Promise<{list: T[]; total: number}>, toError: (e: unknown) => string): TabLoader<T> {
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
  () =>
    orderApi
      .query({pageNum: ordersPage.pageNum, pageSize: ordersPage.pageSize, customerId: customerId.value})
      .then((r) => ({list: r.data.list, total: r.data.total})),
  orderError
);

const freqDays = ref(90);
/** 统计窗口四档，一次全部展开。 */
const freqDayOptions = [
  {value: 30, label: '30天'},
  {value: 90, label: '90天'},
  {value: 180, label: '180天'},
  {value: 365, label: '1年'},
];
const frequent = useTab<CustomerFrequentSku>(
  () => customerApi.frequentSkus(customerId.value, freqDays.value, 20).then((r) => ({list: r.data, total: r.data.length})),
  customerError
);

const agreementPage = reactive({pageNum: 1, pageSize: 20});
const agreement = useTab<PriceRow>(
  () =>
    pricingApi.agreement
      .query({pageNum: agreementPage.pageNum, pageSize: agreementPage.pageSize, customerId: customerId.value})
      .then((r) => ({list: r.data.list, total: r.data.total})),
  pricingError
);

const visibilityPage = reactive({pageNum: 1, pageSize: 20});
const visibility = useTab<VisibilityRow>(
  () =>
    customerVisibilityApi
      .query({pageNum: visibilityPage.pageNum, pageSize: visibilityPage.pageSize, customerId: customerId.value})
      .then((r) => ({list: r.data.list, total: r.data.total})),
  customerError
);

function ensureTab(key: typeof activeTab.value): void {
  if (key === 'orders') orders.ensure();
  else if (key === 'frequent') frequent.ensure();
  else if (key === 'agreement') agreement.ensure();
  else if (key === 'visibility') visibility.ensure();
  else if (key === 'aliases') void loadAliases();
}

function resetAllTabs(): void {
  aliasRequest++;
  aliases.value = [];
  aliasError.value = '';
  aliasModal.value = false;
  aliasSkuId.value = undefined;
  visibilityModal.value = false;
  visibilitySaving.value = false;
  visibilitySeeding.value = false;
  visibilityPolicy.value = 'ALL_ENABLED';
  visibilitySkuIds.value = [];
  visibilitySeeds.value = [];
  orders.reset();
  frequent.reset();
  agreement.reset();
  visibility.reset();
}

function onFreqDaysChange(): void {
  if (customerId.value) void frequent.reload();
}

// 调整可售商品：详情里的 visibilities 是 id + version 的权威清单，列表只用来回显名称。
const visibilityModal = ref(false);
const visibilitySaving = ref(false);
const visibilitySeeding = ref(false);
const visibilityPolicy = ref<CustomerVisibilityPolicy>('ALL_ENABLED');
const visibilitySkuIds = ref<ScmId[]>([]);
const visibilitySeeds = ref<
  Array<{
    skuId: ScmId;
    productName: string;
    specName: string;
    status?: string;
    spuStatus?: string;
    categoryStatus?: string;
  }>
>([]);

async function openVisibility(): Promise<void> {
  // 清单的 id/version 以详情为准；资料还没加载完就打开会把已有白名单当成空清单。
  if (!customer.value && customerId.value) await loadBase();
  if (!customer.value) {
    void message.warning('客户资料尚未加载，请稍后重试');
    return;
  }
  visibilityPolicy.value = customer.value.visibilityPolicy ?? 'ALL_ENABLED';
  visibilitySkuIds.value = (customer.value.visibilities ?? []).map((item) => item.skuId);
  visibilitySeeds.value = [];
  visibilityModal.value = true;
  visibilitySeeding.value = true;
  try {
    const response = await customerVisibilityApi.query({
      pageNum: 1,
      pageSize: 500,
      customerId: customerId.value,
    });
    if (!visibilityModal.value) return;
    visibilitySeeds.value = (response.data.list ?? [])
      .filter((row) => row.skuId != null)
      .map((row) => ({
        skuId: row.skuId as ScmId,
        productName: row.productName || '',
        specName: row.specName || '',
        status: row.skuStatus ?? undefined,
        spuStatus: row.spuStatus ?? undefined,
        categoryStatus: 'ENABLED',
      }));
  } catch (error) {
    // 名称回显失败不阻断调整：已选 ID 仍能按详情清单提交。
    void message.warning(customerError(error));
  } finally {
    visibilitySeeding.value = false;
  }
}

async function saveVisibility(): Promise<void> {
  if (!customerId.value) return;
  if (visibilityPolicy.value === 'ALLOWLIST' && visibilitySkuIds.value.length === 0) {
    void message.warning('只售指定规格时，至少要选择一个商品规格');
    return;
  }
  const existingBySku = new Map((customer.value?.visibilities ?? []).map((item) => [String(item.skuId), item]));
  const visibilities =
    visibilityPolicy.value === 'ALL_ENABLED'
      ? []
      : visibilitySkuIds.value.map((skuId) => {
          const existing = existingBySku.get(String(skuId));
          return existing ? {id: existing.id, version: existing.version, skuId} : {skuId};
        });
  visibilitySaving.value = true;
  try {
    await customerVisibilityApi.replace({
      customerId: customerId.value,
      visibilityPolicy: visibilityPolicy.value,
      visibilities,
    });
    visibilityModal.value = false;
    await loadBase();
    await visibility.reload();
    void message.success('可售商品已更新');
  } catch (error) {
    void message.error(customerError(error));
  } finally {
    visibilitySaving.value = false;
  }
}

// 全部可见时接口返回一行策略行，拆成说明文字和 SKU 明细。
const visPolicyText = computed(() => {
  const policy = visibility.rows.value[0]?.visibilityPolicy;
  if (!policy) return '';
  return policy === 'ALL_ENABLED' ? '可见范围：全部已启用商品（未单独指定可售规格）' : '可见范围：只卖指定规格，以下为该客户已授权商品规格';
});
const visRows = computed(() => visibility.rows.value.filter((r) => r.skuId != null));

// 枚举翻译与列定义

const shelfStatusTone = (value?: string | null): ScmStatusTone => (value === 'ON_SHELF' ? 'success' : value === 'OFF_SHELF' ? 'neutral' : 'warning');

/** 协议价无结束时间即长期有效。 */
const effectivePeriodText = (row: PriceRow): string =>
  `${datetime(row.effectiveFrom)} ～ ${row.effectiveTo ? datetime(row.effectiveTo) : '长期有效'}`;

const orderCols: TableColumnsType<Order> = [
  {title: '订单号', dataIndex: 'orderNo', width: 200},
  {title: '来源', dataIndex: 'orderSource', width: 110, align: 'center'},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '订单金额', dataIndex: 'orderedTotalAmount', width: 140},
  {title: '创建时间', dataIndex: 'createdAt', width: 180},
];
const frequentCols: TableColumnsType<CustomerFrequentSku> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '单位', dataIndex: 'unit', width: 80},
  {title: '订购次数', dataIndex: 'orderCount', width: 92},
  {title: '订购量', dataIndex: 'orderedQuantity', width: 110},
  {title: '最近成交价', dataIndex: 'recentUnitPrice', width: 120},
  {title: '最近购买', dataIndex: 'lastConfirmedAt', width: 180},
];
const agreementCols: TableColumnsType<PriceRow> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '协议单价', dataIndex: 'unitPrice', width: 130},
  {title: '有效期', dataIndex: 'effectivePeriod', width: 300},
];
const visibilityCols: TableColumnsType<VisibilityRow> = [
  {title: '商品', dataIndex: 'productName', width: 220},
  {title: '商品规格', dataIndex: 'specName', width: 140},
  {title: '状态', dataIndex: 'skuStatus', align: 'center', width: 96},
  {title: '加入时间', dataIndex: 'createdAt', width: 180},
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
.scm-visibility-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.scm-visibility-toolbar .scm-note {
  margin: 0;
}
</style>
