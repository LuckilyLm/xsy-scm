<!--
  * 商品-供应商关系（只读反查）
  *
  * 来源：**C 骨架剪枝**。
  * C 有 `views/business/purchase/supplier-product-list.vue` 这类「供货关系」页，
  * 但它是 **SPU 级**（`product-supplier-api.ts`），且**可编辑**。
  * V2 的 `supplier_sku` 是 **SKU 级**，且写路径只有一个「整表替换」入口
  * （挂在供应商列表的「关联商品」抽屉里，因为它需要按供应商整体覆盖）。
  *
  * 因此这一页只保留 C 的**查询 + 表格骨架**，剪掉全部编辑能力：
  * - 删除行内编辑 / 新增 / 删除 / 批量保存；
  * - 删除 SPU 级字段（供货价挂在 SPU 上的那套形状）。
  *
  * 定位：按商品（SKU）反查「有哪些供应商能供」，属于采购域的高频只读视图，
  * 与供应商列表的「按供应商看商品」互补。写操作请回到供应商列表 →「关联商品」。
-->
<template>
  <section aria-label="商品-供应商关系">
    <a-form class="smart-query-form" layout="inline" @finish="search">
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="filters.supplierId" width="240px" />
      </a-form-item>
      <a-form-item label="商品规格" class="smart-query-form-item">
        <SkuSelect v-model:value="filters.skuId" width="300px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.status" enum-name="SUPPLIER_SKU_STATUS_ENUM" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-space>
          <a-button type="primary" html-type="submit">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-typography-text type="secondary">本页为只读反查；维护关联请到「供应商档案」→「关联商品」。</a-typography-text>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SUPPLIER_SKU" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action><a-button size="small" @click="load">重新加载</a-button></template>
      </a-alert>
      <a-table
        :data-source="rows"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="false"
        size="small"
        bordered
        :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'supplierNameSnapshot'">{{ record.supplierNameSnapshot }}</span>
          <span v-else-if="column.dataIndex === 'skuNameSnapshot'">{{ record.skuNameSnapshot }}</span>
          <span v-else-if="column.dataIndex === 'spec'">{{ specText(record.specValuesSnapshot) }}</span>
          <span v-else-if="column.dataIndex === 'referencePrice'" class="amount">{{ record.referencePrice ?? '—' }}</span>
          <span v-else-if="column.dataIndex === 'purchaserName'">{{ record.purchaserName || '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'defaultFlag'" :color="record.defaultFlag ? 'blue' : 'default'">
            {{ record.defaultFlag ? '默认来源' : '—' }}
          </a-tag>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ statusText(record.status) }}
          </a-tag>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          show-size-changer
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
        />
      </div>
    </a-card>
  </section>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import type { TableColumnsType } from 'ant-design-vue';
  import { supplierSkuApi } from '/@/api/business/scm/supplier-sku-api';
  import type { EnableStatus, SupplierSkuQuery, SupplierSkuRow } from '/@/types/business/scm/supplier';
  import { SUPPLIER_SKU_STATUS_ENUM } from '/@/constants/business/scm/supplier-const';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
  import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
  import { supplierError } from './supplier-errors';

  const filters = reactive<SupplierSkuQuery>({ pageNum: 1, pageSize: 20 });
  const rows = ref<SupplierSkuRow[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref('');

  const statusText = (value: EnableStatus): string => SUPPLIER_SKU_STATUS_ENUM[value]?.desc || value;
  const specText = (spec: Record<string, string> | undefined): string => {
    const values = Object.values(spec ?? {});
    return values.length ? values.join('/') : '—';
  };

  const columns = ref<TableColumnsType<SupplierSkuRow>>([
    { title: '供应商（快照）', dataIndex: 'supplierNameSnapshot', width: 200 },
    { title: '供应商编码', dataIndex: 'supplierCodeSnapshot', width: 150 },
    { title: '商品名称（快照）', dataIndex: 'skuNameSnapshot', width: 200 },
    { title: '规格', dataIndex: 'spec', width: 150 },
    { title: '规格编码', dataIndex: 'skuCodeSnapshot', width: 160 },
    { title: '采购单位', dataIndex: 'purchaseUnit', width: 100 },
    { title: '参考价', dataIndex: 'referencePrice', width: 120, align: 'right' },
    { title: '采购员', dataIndex: 'purchaserName', width: 110 },
    { title: '默认来源', dataIndex: 'defaultFlag', width: 110, align: 'center' },
    { title: '状态', dataIndex: 'status', width: 90, align: 'center' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 190 },
  ]);

  let requestId = 0;

  async function load() {
    const request = ++requestId;
    loading.value = true;
    error.value = '';
    try {
      const response = await supplierSkuApi.query({ ...filters });
      if (request === requestId) {
        rows.value = response.data.list;
        total.value = Number(response.data.total);
      }
    } catch (e) {
      if (request === requestId) error.value = supplierError(e);
    } finally {
      if (request === requestId) loading.value = false;
    }
  }

  function search() {
    filters.pageNum = 1;
    void load();
  }

  function reset() {
    Object.assign(filters, { pageNum: 1, supplierId: undefined, skuId: undefined, status: undefined, sortItemList: undefined });
    void load();
  }

  onMounted(load);
</script>

<style scoped>
  .amount {
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
</style>
