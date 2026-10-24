<template>
  <!-- workspace：下钻宽表就是阅读内容本身，抽屉要宽才能和上层粒度对照。 -->
  <a-drawer :open="open" :title="title" :width="scmDrawerWidth('workspace')" @close="emit('update:open', false)">
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button size="small" @click="emit('change')">重试</a-button>
      </template>
    </a-alert>

    <a-table
      :id="SCM_REPORT_TABLE_ID.PURCHASE_DRILLDOWN"
      size="small"
      :data-source="rows"
      :columns="columns"
      :row-key="(row: PurchaseProductRow) => `${row.skuId}-${row.purchaseUnit ?? ''}`"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '暂无该供应商 / 采购员的商品明细'}"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <slot name="bodyCell" :record="record" :column="column" />
      </template>
      <template #summary v-if="showSummary">
        <slot name="summary" />
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
        show-quick-jumper
        :current="pageNum"
        :page-size="pageSize"
        :total="total"
        @change="
          (page: number, size: number) => {
            emit('update:pageNum', page);
            emit('update:pageSize', size);
            emit('change');
          }
        "
        @showSizeChange="(_: unknown, size: number) => emit('update:pageSize', size)"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import type {TableColumnsType} from 'ant-design-vue';
import {SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import type {PurchaseProductRow} from '../report-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

/** 采购下钻抽屉：右侧看供应商或采购员的商品明细。列和分页由调用方传入。 */
defineProps<{
  open: boolean;
  title: string;
  columns: TableColumnsType<PurchaseProductRow>;
  rows: PurchaseProductRow[];
  loading: boolean;
  error: string;
  total: number;
  pageNum: number;
  pageSize: number;
  showSummary?: boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  'update:pageNum': [value: number];
  'update:pageSize': [value: number];
  /** 分页变化即重新查询。 */
  change: [];
}>();
</script>
