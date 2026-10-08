<template>
  <!-- workspace：报表下钻 —— 下钻的宽表（scroll.x 1500）就是被阅读的内容本身，
       抽屉的价值在于「就地在两个粒度之间对照」，收窄会直接损害对照能力。 -->
  <a-drawer
      :open="open"
      :title="title"
      :width="scmDrawerWidth('workspace')"
      @close="emit('update:open', false)"
  >
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_DRILLDOWN"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="skuId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无该供应商 / 采购员的商品明细'}"
        :scroll="{x: 1500}"
    >
      <template #bodyCell="{ record, column }">
        <slot name="bodyCell" :record="record" :column="column"/>
      </template>
      <template #summary v-if="showSummary">
        <slot name="summary"/>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :current="pageNum"
          :page-size="pageSize"
          :total="total"
          :show-total="(n: number) => `共${n}条`"
          @change="changePage"
      />
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import {SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import type {PurchaseProductRow} from '../report-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

/**
 * 采购下钻抽屉：点供应商 / 采购员的一行，右侧看该实体的商品维度明细。
 *
 * 刻意用右侧抽屉而不是新页面，也不在报表里再造一份详情页 ——
 * 报表的价值是「就地在两个粒度之间对照」，跳走就要重新选一次条件。
 *
 * 列与分页都由调用方传入：抽屉只负责「右侧滑出 + 一张表 + 一个分页条」，
 * 不拥有任何查询口径，因此供应商和采购员两种下钻共用它，不会出现两套列。
 */
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
    /** 分页变化即重查（与列表页的 `@change="queryData"` 同语义）。 */
    change: [];
}>();

/** 先落页码再通知重查：调用方按当前的 `pageNum` / `pageSize` 装配请求。 */
function changePage(page: number, size: number) {
    emit('update:pageNum', page);
    emit('update:pageSize', size);
    emit('change');
}
</script>
