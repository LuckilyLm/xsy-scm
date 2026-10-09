<template>
  <a-drawer :title="form.customerTypePriceId?'编辑客户类型价':'新增客户类型价'" :open="visible" :width="scmDrawerWidth('s')"
            @close="visible=false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon/>
      <a-form ref="formRef" :model="{...form, range}" :rules="formRules" layout="vertical" class="app-drawer-form">
        <a-form-item label="客户类型" name="customerTypeId">
          <CustomerTypeSelect v-model:value="form.customerTypeId"/>
        </a-form-item>
        <a-form-item label="商品规格" name="skuId">
          <SkuSelect v-model:value="form.skuId"/>
        </a-form-item>
        <a-form-item name="unitPrice">
          <template #label>
            单价
            <ScmFieldHelp label="单价" text="0 元也是有效价格；最多四位小数"/>
          </template>
          <a-input-number v-model:value="form.unitPrice" string-mode :min="0" :max="'99999999999999.9999'"
                          :precision="4" :step="'0.01'" addon-before="¥" style="width: 100%"/>
        </a-form-item>
        <a-form-item name="range">
          <template #label>
            有效区间
            <ScmFieldHelp label="有效区间" text="开始时间包含，结束时间不包含；结束留空表示长期有效"/>
          </template>
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
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {emptyPrice, validatePrice} from '../pricing-form-model';
import {pricingError} from '../pricing-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('');
const form = reactive<PriceForm>(emptyPrice());
const range = ref<[string, string] | undefined>();
const formRef = ref();
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  customerTypeId: [{required: true, message: '请选择客户类型', trigger: 'change'}],
  skuId: [{required: true, message: '请选择商品规格', trigger: 'change'}],
  unitPrice: [{required: true, message: '请填写单价（零价也是有效价格，填 0 即可）', trigger: 'blur'}],
  range: [{
    validator: () => (range.value?.[0] ? Promise.resolve() : Promise.reject(new Error('请选择生效时间'))),
    trigger: 'change',
  }],
};
const api = pricingApi.typePrice;
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
  // 必填项走表单校验：错误显示在对应输入框下方
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  form.effectiveFrom = range.value?.[0] || '';
  form.effectiveTo = range.value?.[1] || null;
  // 剩下的跨字段业务规则不是字段必填，用 toast
  const problems = validatePrice(form, 'customerTypeId');
  if (problems.length) {
    message.warning(problems.join('；'));
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    if (form.customerTypePriceId) await api.update({...form}); else await api.add({...form});
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
