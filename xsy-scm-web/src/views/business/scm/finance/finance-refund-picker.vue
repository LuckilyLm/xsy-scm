<template>
  <a-modal :open="open" title="选择已完成退款" width="min(1120px, 96vw)" :footer="null" destroy-on-close @cancel="close">
    <a-form class="smart-query-form" layout="inline" @submit.prevent>
      <a-row class="smart-query-form-row">
        <a-form-item label="退款 / 退货 / 订单 / 客户">
          <a-input v-model:value="keyword" allow-clear placeholder="输入退款单号、订单号或客户名称" @press-enter="search"/>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="search">搜索可付款退款</a-button>
        </a-form-item>
      </a-row>
    </a-form>
    <a-alert v-if="error" class="picker-error" type="error" show-icon :message="error">
      <template #action><a-button @click="queryData">重试</a-button></template>
    </a-alert>
    <a-table size="small" row-key="refundId" :data-source="rows" :columns="columns" :loading="loading"
             :pagination="false" :scroll="{x:1040}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='refundAmount'">{{ moneyText(text) }}</template>
        <template v-else-if="column.dataIndex==='completedAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex==='action'"><a-button type="link" @click="choose(record)">选择</a-button></template>
      </template>
    </a-table>
    <a-empty v-if="!loading && !rows.length" description="没有可登记的退款；请确认退款已完成且未登记付款。"/>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="pageNum" v-model:page-size="pageSize"
                    :total="total" @change="queryData" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {financeError} from './finance-errors';
import {dateTimeText, moneyText} from './finance-form-model';
import type {FinanceRefundOption} from './finance-types';

const props = defineProps<{open: boolean}>();
const emit = defineEmits<{(event: 'update:open', value: boolean): void; (event: 'select', value: FinanceRefundOption): void}>();
const keyword = ref('');
const pageNum = ref(1);
const pageSize = ref(10);
const rows = ref<FinanceRefundOption[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const columns = computed<TableColumnsType<FinanceRefundOption>>(() => [
    {title: '退款单号', dataIndex: 'refundNo', width: 180},
    {title: '退货单号', dataIndex: 'returnNo', width: 170},
    {title: '订单号', dataIndex: 'orderNo', width: 180},
    {title: '客户', dataIndex: 'customerName', width: 190},
    {title: '退款金额', dataIndex: 'refundAmount', align: 'right', width: 150},
    {title: '完成时点', dataIndex: 'completedAt', width: 180},
    {title: '操作', dataIndex: 'action', align: 'center', width: 90},
]);

async function queryData() {
    loading.value = true;
    error.value = '';
    try {
        // 加载失败由弹窗内 Alert 承担，不再让全局 toast 重复说一遍
        const response = await financeApi.refundOptions(
            {pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined},
            {suppressGlobalErrorMessage: true});
        rows.value = response.data.list ?? [];
        total.value = response.data.total ?? 0;
    } catch (cause) {
        error.value = financeError(cause);
    } finally {
        loading.value = false;
    }
}

function search() {
    pageNum.value = 1;
    queryData();
}

function choose(option: FinanceRefundOption) {
    emit('select', option);
    close();
}

function close() {
    emit('update:open', false);
}

watch(() => props.open, (isOpen) => {
    if (isOpen) {
        keyword.value = '';
        pageNum.value = 1;
        queryData();
    }
});
</script>

<style scoped>
.picker-error { margin-bottom: 12px; }
</style>
