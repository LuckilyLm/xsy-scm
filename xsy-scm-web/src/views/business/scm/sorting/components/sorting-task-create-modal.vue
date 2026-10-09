<template>
  <a-modal
      :open="open"
      :width="1100"
      :confirm-loading="creating"
      :ok-button-props="{disabled: !selectedIds.length || loading || rows.length === 0}"
      :ok-text="`创建任务（${selectedIds.length} 行）`"
      @cancel="emit('update:open', false)"
      @ok="emit('create')"
  >
    <template #title>
      <div class="create-title">
        <span>新建分拣任务</span>
        <p class="scm-note">候选行是已确认订单上未被占用的明细。</p>
      </div>
    </template>
    <a-alert v-if="error" type="error" show-icon :message="error"/>
    <a-form layout="inline" class="create-form" @submit.prevent="emit('search')">
      <a-form-item label="仓库" required>
        <WarehouseSelect
            :value="warehouseId"
            width="220px"
            @update:value="emit('update:warehouseId', $event)"
        />
      </a-form-item>
      <a-form-item label="受指派人">
        <EmployeeSelect
            :value="assigneeEmployeeId"
            placeholder="暂不指派"
            width="200px"
            @update:value="emit('update:assigneeEmployeeId', $event)"
        />
      </a-form-item>
      <a-form-item label="备注">
        <a-input
            :value="remark"
            :maxlength="500"
            style="width: 220px"
            placeholder="可选"
            @update:value="emit('update:remark', $event)"
        />
      </a-form-item>
      <a-form-item label="订单 / 客户 / 商品">
        <a-input
            :value="keyword"
            placeholder="订单 / 客户 / 商品"
            allow-clear
            :maxlength="100"
            @update:value="emit('update:keyword', $event)"
            @pressEnter="emit('search')"
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" @click="emit('search')">查询候选行</a-button>
          <a-button @click="emit('reset')">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
    <a-table
        :id="SCM_SORTING_TABLE_ID.CANDIDATE_LINE"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="salesOrderItemId"
        :loading="loading"
        :pagination="false"
        :scroll="{x: 940, y: 320}"
        :row-selection="{selectedRowKeys: selectedIds, onChange: onSelectionChange, preserveSelectedRowKeys: true}"
        :locale="{emptyText: '暂无可分拣的订单行'}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'actualQuantity'">
          <span class="scm-quantity">{{ quantityText(record.actualQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.orderedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'productTypeSnapshot'">
          {{ productTypeDesc(record.productTypeSnapshot) }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          :current="pageNum"
          :page-size="pageSize"
          :total="total"
          show-size-changer
          :page-size-options="['10', '20', '50', '100']"
          @change="onPageChange"
      />
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {SCM_SORTING_PRODUCT_TYPE_ENUM, SCM_SORTING_TABLE_ID} from '/@/constants/business/scm/sorting-const';
import {quantityText} from '../sorting-types';
import type {Id, SortingCandidateLine} from '../sorting-types';

defineProps<{
  open: boolean;
  creating: boolean;
  loading: boolean;
  error: string;
  warehouseId?: Id;
  assigneeEmployeeId?: number;
  remark?: string;
  keyword?: string;
  rows: SortingCandidateLine[];
  total: number;
  selectedIds: Id[];
  pageNum: number;
  pageSize: number;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  'update:warehouseId': [value: Id | undefined];
  'update:assigneeEmployeeId': [value: number | undefined];
  'update:remark': [value: string | undefined];
  'update:keyword': [value: string | undefined];
  'update:selectedIds': [value: Id[]];
  search: [];
  reset: [];
  pageChange: [pageNum: number, pageSize: number];
  create: [];
}>();

const columns = ref<TableColumnsType<SortingCandidateLine>>([
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 160},
  {title: '商品', dataIndex: 'productNameSnapshot', width: 170},
  {title: '商品规格', dataIndex: 'specNameSnapshot', width: 130},
  {title: '类型', dataIndex: 'productTypeSnapshot', align: 'center', width: 90},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 80},
  {title: '订购量', dataIndex: 'orderedQuantity', align: 'right', width: 110},
  {title: '实发量', dataIndex: 'actualQuantity', align: 'right', width: 110},
]);

function productTypeDesc(type?: string | null) {
  return type ? SCM_SORTING_PRODUCT_TYPE_ENUM[type]?.desc ?? type : '—';
}

function onSelectionChange(keys: Array<string | number>) {
  emit('update:selectedIds', keys);
}

function onPageChange(pageNum: number, pageSize: number) {
  emit('pageChange', pageNum, pageSize);
}
</script>

<style scoped>
/* 说明紧跟标题同一行、左对齐，不另起一行 */
.create-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.create-title .scm-note {
  margin: 0;
  font-weight: 400;
}

.create-form {
  gap: 12px 0;
  margin: 16px 0;
}
</style>
