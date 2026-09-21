<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/product/product-price-list.vue
 复制日期：2026-09-15。Copy First + Adapt。
 剪枝：裸 ID、四值价格类型、批量删除、resizable、强制 productId 查询。
 适配：SKU/客户维度、定点字符串、版本、权限、时间区间、请求竞态及错误状态。
 验收：W3 Playwright、TS baseline、ESLint。 -->
<template>
  <a-drawer :title="form.agreementPriceId?'编辑客户协议价':'新增客户协议价'" :open="visible" :width="620"
            @close="visible=false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon/>
      <a-form layout="vertical" :model="form">
        <a-form-item label="客户" required>
          <CustomerSelect v-model:value="form.customerId"/>
        </a-form-item>
        <a-form-item label="SKU" required>
          <SkuSelect v-model:value="form.skuId"/>
        </a-form-item>
        <a-form-item label="单价" required help="零价也是有效价格；最多四位小数">
          <a-input v-model:value="form.unitPrice" aria-label="单价" inputmode="decimal"/>
        </a-form-item>
        <a-form-item label="有效区间" required help="开始时间包含，结束时间不包含；结束留空表示长期有效">
          <a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                          :allow-empty="[false,true]" style="width:100%"/>
        </a-form-item>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="visible=false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="submit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import type {ScmId} from '/@/types/business/scm/customer';
import type {PriceForm} from '/@/types/business/scm/pricing';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {emptyPrice, validatePrice} from '../pricing-form-model';
import {pricingError} from '../pricing-errors';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('');
const form = reactive<PriceForm>(emptyPrice());
const range = ref<[string, string] | undefined>();
const api = pricingApi.agreement;
let requestId = 0;

async function open(id?: ScmId) {
  const request = ++requestId;
  visible.value = true;
  error.value = '';
  for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key];
  Object.assign(form, emptyPrice());
  range.value = undefined;
  if (!id) return;
  loading.value = true;
  try {
    const r = await api.detail(id);
    if (request === requestId) {
      Object.assign(form, r.data);
      range.value = [r.data.effectiveFrom, r.data.effectiveTo || ''];
    }
  } catch (e) {
    error.value = pricingError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

async function submit() {
  form.effectiveFrom = range.value?.[0] || '';
  form.effectiveTo = range.value?.[1] || null;
  const problems = validatePrice(form, 'customerId');
  if (problems.length) {
    error.value = problems.join('；');
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    if (form.agreementPriceId) await api.update({...form}); else await api.add({...form});
    message.success('价格已保存');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = pricingError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
