<template>
  <div class="conversion-row">
    <span>{{ sku.specName }} · 销售单位 {{ sku.saleUnit }}</span>
    <a-button size="small" @click="open">维护换算</a-button>
    <a-modal v-model:open="visible" :title="`${sku.specName} · 库存单位换算`" :footer="null" width="680px">
      <a-spin :spinning="loading">
        <div v-for="row in rows" :key="row.stockUnit" class="conversion-item">
          <span>1 {{ sku.saleUnit }} = {{ row.stockUnitsPerSaleUnit }} {{ row.stockUnit }}</span>
          <a-popconfirm title="确定删除这条换算？" @confirm="remove(row.stockUnit)">
            <a-button size="small" danger>删除</a-button>
          </a-popconfirm>
        </div>
        <a-empty v-if="!rows.length && !loading" description="尚未配置换算" />
      </a-spin>
      <a-divider>新增或更新换算规则</a-divider>
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="10">
            <a-form-item label="库存记账单位" required>
              <a-input v-model:value="stockUnit" :maxlength="32" placeholder="例如 kg" />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item :label="`1 ${sku.saleUnit} 对应库存数量`" required>
              <a-input-number
                :value="factor"
                string-mode
                :min="'0.0001'"
                :precision="4"
                :controls="false"
                style="width: 100%"
                @update:value="factor = $event === null ? '' : String($event)"
              />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="操作">
              <a-button type="primary" :loading="saving" @click="save">保存</a-button>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {message} from 'ant-design-vue';
import {productSkuApi, type ProductSkuStockConversion} from '/@/api/business/scm/product-sku-api';
import type {ProductSku} from '/@/types/business/scm/product';

const props = defineProps<{sku: ProductSku}>();
const visible = ref(false);
const rows = ref<ProductSkuStockConversion[]>([]);
const loading = ref(false);
const saving = ref(false);
const stockUnit = ref('');
const factor = ref('1.0000');

async function load() {
  if (!props.sku.skuId) return;
  loading.value = true;
  try {
    rows.value = (await productSkuApi.stockConversions(props.sku.skuId)).data || [];
  } catch (error) {
    message.error(error instanceof Error ? error.message : '换算规则加载失败');
  } finally {
    loading.value = false;
  }
}
async function open() {
  visible.value = true;
  await load();
}
async function save() {
  const unit = stockUnit.value.trim();
  const amount = Number(factor.value);
  if (!props.sku.skuId || !unit || !Number.isFinite(amount) || amount <= 0 || (unit === props.sku.saleUnit && amount !== 1)) {
    message.error('请填写有效的库存单位和比例；同单位换算比例必须为 1');
    return;
  }
  saving.value = true;
  try {
    await productSkuApi.saveStockConversion(props.sku.skuId, unit, factor.value);
    message.success('换算关系已保存');
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}
async function remove(unit: string) {
  if (!props.sku.skuId) return;
  try {
    await productSkuApi.deleteStockConversion(props.sku.skuId, unit);
    message.success('换算关系已删除');
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败');
  }
}
</script>
<style scoped>
.conversion-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
}
.conversion-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
</style>
