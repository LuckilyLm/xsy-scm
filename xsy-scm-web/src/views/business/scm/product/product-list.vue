<template>
  <section aria-label="商品档案">
    <a-form class="smart-query-form" layout="inline" @finish="search">
      <a-form-item label="分类" class="smart-query-form-item"><CategorySelect v-model:value="filters.categoryId" :categories="categories" style="width: 220px" /></a-form-item>
      <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="filters.keyword" allow-clear placeholder="商品名 / 编码 / 条码" style="width: 240px" /></a-form-item>
      <a-form-item label="商品状态" class="smart-query-form-item"><a-select v-model:value="filters.status" allow-clear :options="SHELF_STATUS_ENUM" style="width: 110px" /></a-form-item>
      <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" html-type="submit">查询</a-button><a-button @click="reset">重置</a-button><a-button type="link" @click="advanced = !advanced">{{ advanced ? '收起筛选' : '高级筛选' }}</a-button></a-space></a-form-item>
      <template v-if="advanced">
        <a-form-item label="SKU 状态" class="smart-query-form-item"><a-select v-model:value="filters.skuStatus" allow-clear :options="SHELF_STATUS_ENUM" style="width: 120px" /></a-form-item>
        <a-form-item label="商品类型" class="smart-query-form-item"><a-select v-model:value="filters.productType" allow-clear :options="PRODUCT_TYPE_ENUM" style="width: 120px" /></a-form-item>
      </template>
    </a-form>
    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:product:add'" type="primary" @click="drawer?.open()">新增商品</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRODUCT" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10"><template #action><a-button size="small" @click="load">重新加载</a-button></template></a-alert>
      <a-table :data-source="rows" :columns="columns" row-key="spuId" :loading="loading" :pagination="false" size="small" bordered :scroll="{ x: 1500 }" @change="sortChanged">
        <template #expandedRowRender="{ record }"><SkuTable :rows="record.skuList" /></template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'"><a-button type="link" @click="detail(record.spuId)">{{ record.name }}</a-button></template>
          <template v-else-if="column.dataIndex === 'primaryImageUrl'"><a-image v-if="record.primaryImageUrl" :src="record.primaryImageUrl" :width="40" :height="40" :alt="record.name" /><span v-else>—</span></template>
          <span v-else-if="column.dataIndex === 'saleUnit'">{{ record.defaultSku?.saleUnit || '—' }}</span>
          <span v-else-if="column.dataIndex === 'price'" class="price">{{ priceRange(record.minMarketPrice, record.maxMarketPrice) }}</span>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ON_SHELF' ? 'green' : 'default'">{{ record.status === 'ON_SHELF' ? '上架' : '下架' }}</a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
            <a-button v-privilege="'scm:product:update'" type="link" size="small" @click="drawer?.open(record.spuId)">编辑</a-button>
            <a-popconfirm :title="`确认${record.status === 'ON_SHELF' ? '下架' : '上架'}此商品？`" @confirm="toggleStatus(record)"><a-button v-privilege="'scm:product:status'" type="link" size="small">{{ record.status === 'ON_SHELF' ? '下架' : '上架' }}</a-button></a-popconfirm>
            <a-popconfirm title="确认删除商品及其规格和图集？" @confirm="remove(record)"><a-button v-privilege="'scm:product:delete'" type="link" danger size="small">删除</a-button></a-popconfirm>
          </a-space>
        </template>
      </a-table>
      <div class="smart-query-table-page"><a-pagination v-model:current="filters.pageNum" v-model:page-size="filters.pageSize" :total="total" show-size-changer :show-total="(n: number) => `共 ${n} 条`" @change="load" /></div>
    </a-card>
    <ProductDrawer ref="drawer" @saved="load" />
  </section>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import type { TableColumnsType, TableProps } from 'ant-design-vue';
import { productApi } from '/@/api/business/scm/product-api';
import { productCategoryApi } from '/@/api/business/scm/product-category-api';
import type { ProductCategory, ProductId, ProductQuery, ProductRow } from '/@/types/business/scm/product';
import { priceRange, SHELF_STATUS_ENUM, PRODUCT_TYPE_ENUM } from '/@/constants/business/scm/product-const';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ProductDrawer from './components/product-form-drawer.vue';
import SkuTable from './components/product-sku-table.vue';
import { productError } from './product-errors';
const router = useRouter();
const filters = reactive<ProductQuery>({ pageNum: 1, pageSize: 20 });
const categories = ref<ProductCategory[]>([]), rows = ref<ProductRow[]>([]), total = ref(0), loading = ref(false), error = ref(''), advanced = ref(false);
const drawer = ref<InstanceType<typeof ProductDrawer>>();
const columns = ref<TableColumnsType<ProductRow>>([
  { title: '主图', dataIndex: 'primaryImageUrl', width: 65 }, { title: '商品名称', dataIndex: 'name', width: 200, sorter: true },
  { title: 'SPU 编码', dataIndex: 'spuCode', width: 170, sorter: true }, { title: '分类', dataIndex: 'categoryPath', width: 210 },
  { title: '单位', dataIndex: 'saleUnit', width: 65 }, { title: '市场价', dataIndex: 'price', width: 205, align: 'right' },
  { title: 'SKU 数', dataIndex: 'skuCount', width: 80, align: 'right' }, { title: '状态', dataIndex: 'status', width: 80, align: 'center', sorter: true },
  { title: '别名', dataIndex: 'alias', width: 140 }, { title: '更新时间', dataIndex: 'updatedAt', width: 190, sorter: true },
  { title: '操作', dataIndex: 'action', width: 180, align: 'right', fixed: 'right' },
]);
let requestId = 0;
async function load() {
  const request = ++requestId; loading.value = true; error.value = '';
  try { const response = await productApi.query({ ...filters }); if (request === requestId) { rows.value = response.data.list; total.value = Number(response.data.total); } }
  catch (e) { if (request === requestId) error.value = productError(e); }
  finally { if (request === requestId) loading.value = false; }
}
function search() { filters.pageNum = 1; void load(); }
function reset() { Object.assign(filters, { pageNum: 1, keyword: undefined, categoryId: undefined, status: undefined, skuStatus: undefined, productType: undefined, sortItemList: undefined }); void load(); }
const sortChanged: TableProps<ProductRow>['onChange'] = (_page, _filters, sort) => {
  const item = Array.isArray(sort) ? sort[0] : sort;
  const names: Record<string, string> = { spuCode: 'spu_code', name: 'name', status: 'status', updatedAt: 'updated_at' };
  const column = names[String(item.field)]; filters.sortItemList = item.order && column ? [{ column, isAsc: item.order === 'ascend' }] : undefined; search();
};
function detail(id: ProductId) { void router.push({ path: '/product/product-detail', query: { spuId: String(id) } }); }
async function toggleStatus(row: ProductRow) {
  try { await productApi.status(row.spuId, row.version, row.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF'); message.success('商品状态已更新'); await load(); }
  catch (e) { error.value = productError(e); }
}
async function remove(row: ProductRow) {
  try { await productApi.delete(row.spuId, row.version); message.success('商品已删除'); await load(); } catch (e) { error.value = productError(e); }
}
onMounted(async () => { void load(); try { categories.value = (await productCategoryApi.tree()).data; } catch (e) { error.value = productError(e); } });
</script>
<style scoped>.price { font-variant-numeric: tabular-nums; white-space: nowrap; }</style>
