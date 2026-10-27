<template>
  <a-form class="smart-query-form" layout="inline">
    <a-row class="smart-query-form-row">
      <a-form-item label="关键字" class="smart-query-form-item"
        ><a-input v-model:value="query.keyword" placeholder="客户或商品名称" allow-clear
      /></a-form-item>
      <a-form-item label="客户" class="smart-query-form-item"><CustomerSelect v-model:value="query.customerId" width="190px" /></a-form-item>
      <a-form-item label="商品规格" class="smart-query-form-item"
        ><SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]"
      /></a-form-item>
      <a-form-item label="有效区间" class="smart-query-form-item"
        ><a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true, true]"
      /></a-form-item>
      <a-form-item class="smart-query-form-item"
        ><a-space
          ><a-button type="primary" @click="search" v-privilege="'scm:pricing:agreement:query'">查询</a-button
          ><a-button @click="reset">重置</a-button></a-space
        ></a-form-item
      >
    </a-row>
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon closable @close="error = ''" />
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block"
      ><div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:pricing:agreement:add'" @click="drawer?.open()">新增客户协议价</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_AGREEMENT" :refresh="load" /></div
    ></a-row>
    <a-table
      :data-source="rows"
      :columns="columns"
      row-key="agreementPriceId"
      size="small"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'customerName'">{{ record.customerName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'unitPrice'"
          ><span class="scm-money">{{
            record.formulaType && record.formulaType !== 'FIXED' ? '按公式计算' : formatAmount(record.unitPrice)
          }}</span></template
        >
        <template v-else-if="column.dataIndex === 'formulaType'">{{ formulaText(record) }}</template>
        <template v-else-if="column.dataIndex === 'effectiveRange'"
          >{{ effectiveRangeText(record.effectiveFrom, record.effectiveTo)
          }}<span v-if="!record.effectiveTo" class="scm-cell-hint">（长期有效）</span></template
        >
        <ScmStatusTag v-else-if="column.dataIndex === 'effectiveness'" v-bind="effectiveness(record)" />
        <template v-else-if="column.dataIndex === 'action'"
          ><div class="smart-table-operate scm-table-actions">
            <a-button type="link" v-privilege="'scm:pricing:agreement:update'" @click="drawer?.open(record.agreementPriceId)">编辑</a-button
            ><a-button type="link" danger v-privilege="'scm:pricing:agreement:delete'" @click="remove(record)">删除</a-button>
          </div></template
        >
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        v-model:current="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        show-quick-jumper
        @change="load"
        :show-total="(n: number) => `共 ${n} 条`"
      />
    </div>
  </a-card>
  <PriceDrawer ref="drawer" @saved="load" />
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, reactive, ref} from 'vue';
import {Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import type {PriceQuery, PriceRow} from '/@/types/business/scm/pricing';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {formatAmount, formatQuantity, normalizeMoneyInput} from '/@/utils/scm-amount';
import {pricingError} from './pricing-errors';
import {effectiveRangeText, PRICE_EFFECTIVENESS, priceEffectiveness} from './pricing-display';
import PriceDrawer from './components/agreement-price-form-drawer.vue';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const api = pricingApi.agreement;
const query = reactive<PriceQuery>({pageNum: 1, pageSize: 20});
const range = ref<[string, string] | undefined>();
const rows = ref<PriceRow[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref('');
const drawer = ref<InstanceType<typeof PriceDrawer>>();
let requestId = 0;
// 编码属于内部标识，不上列表，关键字仍可按名称和编码搜索；更新时间看价格变更历史。
type AgreementPriceColumn = TableColumnsType<PriceRow>[number] & {showFlag?: boolean};
const columns = ref<AgreementPriceColumn[]>([
  {title: '客户', dataIndex: 'customerName', width: 180},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'specName', width: 200},
  {title: '协议价', dataIndex: 'unitPrice', width: 130},
  {title: '计算方式', dataIndex: 'formulaType', align: 'center', width: 190},
  {title: '有效期', dataIndex: 'effectiveRange', width: 340},
  {title: '状态', dataIndex: 'effectiveness', align: 'center', width: 100},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 120},
]);

function formulaText(row: PriceRow): string {
  const value =
    row.formulaType === 'MARKET_PLUS_AMOUNT' ? normalizeMoneyInput(row.formulaParameter) || '0.00' : formatQuantity(row.formulaParameter || '0');
  switch (row.formulaType) {
    case 'MARKET_PLUS_AMOUNT':
      return `市场价 + ${value}`;
    case 'COST_MARKUP_RATE':
      return `配送仓成本 × (1 + ${value})`;
    case 'MARKET_MARKUP_RATE':
      return `市场价 × (1 + ${value})`;
    case 'PURCHASE_COST_LOSS_TAX_MARKUP':
      return `采购价 × (1 + 损耗率) × (1 + 税率) × (1 + ${value})`;
    default:
      return '固定协议价';
  }
}

/** 生效状态按有效区间推导，只用于展示。 */
function effectiveness(row: PriceRow) {
  return PRICE_EFFECTIVENESS[priceEffectiveness(row.effectiveFrom, row.effectiveTo)];
}

async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const r = await api.query(
      {
        ...query,
        effectiveFrom: range.value?.[0] || null,
        effectiveTo: range.value?.[1] || null,
      },
      {suppressGlobalErrorMessage: true}
    );
    if (id === requestId) {
      rows.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) error.value = pricingError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = undefined;
  query.customerId = undefined;
  query.skuId = undefined;
  range.value = undefined;
  search();
}

function remove(row: PriceRow) {
  Modal.confirm({
    title: '删除这条客户协议价？',
    content: '删除后保留价格变更记录。',
    onOk: async () => {
      // 删除失败由全局 toast 给出原因；拒绝以保持确认框打开
      await api.delete(row);
      await load();
    },
  });
}

onMounted(load);
</script>
