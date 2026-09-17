<template>
  <div>
    <a-card size="small" :bordered="false" class="smart-query-form">
      <a-form class="smart-query-form" layout="inline" @finish="query">
        <a-form-item label="统计时间段">
          <a-range-picker v-model:value="dateRange" show-time format="YYYY-MM-DD HH:mm:ss" />
        </a-form-item>
        <a-form-item label="计算库存">
          <a-switch v-model:checked="form.calculateStock" checked-children="是" un-checked-children="否" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" v-privilege="'purchase:generate:preview'">预览汇总</a-button>
          <a-button class="smart-margin-left20" type="primary" @click="doGenerate" v-privilege="'purchase:generate'">生成采购单</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card size="small" :bordered="false" class="smart-margin-top20">
      <a-table
        rowKey="rowKey"
        :columns="columns"
        :dataSource="tableData"
        :pagination="false"
        :loading="loading"
        size="small"
        bordered
      >
        <template #bodyCell="{ text, column }">
          <template v-if="column.dataIndex === 'stockQuantity'">
            <span>{{ form.calculateStock ? text : '—' }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue';
  import dayjs from 'dayjs';
  import { Modal, message } from 'ant-design-vue';
  import { purchaseGenerateApi } from '/@/api/business/purchase/purchase-generate-api';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { smartSentry } from '/@/lib/smart-sentry';
  import type { PurchaseGenerateParam, PurchaseGeneratePreview } from '/@/api/business/purchase/purchase-generate-api';

  interface RowData {
    rowKey: string;
    supplierName: string;
    productId: number;
    productName: string;
    skuId: number;
    requireQuantity: number;
    stockQuantity: number;
    purchaseQuantity: number;
    unitPrice: number;
  }

  const dateRange = ref<any[]>([]);
  const form = reactive({ calculateStock: false });
  const loading = ref(false);
  const previewList = ref<PurchaseGeneratePreview[]>([]);

  const columns = [
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    { title: '商品', dataIndex: 'productName', width: 180 },
    { title: '规格ID', dataIndex: 'skuId', width: 90 },
    { title: '需求量', dataIndex: 'requireQuantity', width: 100 },
    { title: '现有库存', dataIndex: 'stockQuantity', width: 100 },
    { title: '计划采购量', dataIndex: 'purchaseQuantity', width: 110 },
    { title: '供应价', dataIndex: 'unitPrice', width: 100 },
  ];

  const tableData = computed<RowData[]>(() => {
    const list: RowData[] = [];
    previewList.value.forEach((v) => {
      v.items.forEach((i) => {
        list.push({
          rowKey: v.supplierId + '-' + i.productId + '-' + i.skuId,
          supplierName: v.supplierName,
          ...i,
        } as RowData);
      });
    });
    return list;
  });

  function buildParam(): PurchaseGenerateParam | null {
    if (!dateRange.value || dateRange.value.length !== 2) {
      message.error('请选择统计时间段');
      return null;
    }
    return {
      startTime: dateRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: dateRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
      calculateStock: form.calculateStock,
    };
  }

  async function query() {
    const param = buildParam();
    if (!param) {
      return;
    }
    try {
      SmartLoading.show();
      const res = await purchaseGenerateApi.preview(param);
      previewList.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function doGenerate() {
    const param = buildParam();
    if (!param) {
      return;
    }
    Modal.confirm({
      title: '提示',
      content: '确定按当前时间段汇总生成采购单吗？',
      okText: '生成',
      onOk() {
        generate(param);
      },
    });
  }

  async function generate(param: PurchaseGenerateParam) {
    try {
      SmartLoading.show();
      const res = await purchaseGenerateApi.generate(param);
      message.success(res.data || '生成成功');
      query();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
