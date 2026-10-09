<template>
  <a-drawer :title="form.agreementPriceId?'编辑客户协议价':'新增客户协议价'"
            :open="visible" :width="scmDrawerWidth('s')" @close="visible=false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon class="drawer-error"/>
      <a-form ref="formRef" :model="{...form, unitPrice, range}" :rules="formRules" layout="vertical" class="app-drawer-form">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">定价对象</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="客户" name="customerId">
                <CustomerSelect v-model:value="form.customerId"/>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="商品规格" name="skuId">
                <SkuSelect v-model:value="form.skuId"/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">价格</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item name="unitPrice">
                <template #label>
                  单价
                  <ScmFieldHelp label="单价" text="0 元也是有效价格"/>
                </template>
                <a-input-number
                    v-model:value="unitPrice"
                    :min="0"
                    :precision="4"
                    :step="0.01"
                    addon-before="¥"
                    placeholder="0.0000"
                    aria-label="单价"
                    style="width:100%"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">有效期</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item name="range">
                <template #label>
                  有效区间
                  <ScmFieldHelp label="有效区间" text="开始时间包含，结束时间不包含；结束留空表示长期有效"/>
                </template>
                <a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                                :allow-empty="[false,true]" style="width:100%"/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>
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
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import type {ScmId} from '/@/types/business/scm/customer';
import type {PriceForm} from '/@/types/business/scm/pricing';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {emptyPrice, validatePrice} from '../pricing-form-model';
import {pricingError} from '../pricing-errors';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {fixed4} from '../../common/scm-fixed';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('');
const form = reactive<PriceForm>(emptyPrice());
const formRef = ref();
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  customerId: [{required: true, message: '请选择客户', trigger: 'change'}],
  skuId: [{required: true, message: '请选择商品规格', trigger: 'change'}],
  unitPrice: [{
    validator: () => (unitPrice.value == null
        ? Promise.reject(new Error('请填写单价（零价也是有效价格，填 0 即可）'))
        : Promise.resolve()),
    trigger: 'blur',
  }],
  range: [{
    validator: () => (range.value?.[0] ? Promise.resolve() : Promise.reject(new Error('请选择生效时间'))),
    trigger: 'change',
  }],
};
const range = ref<[string, string] | undefined>();
/**
 * 单价在表单里是 `number`（InputNumber 只接受数字），而 `PriceForm.unitPrice` 是
 * 定点字符串（后端拒绝 JSON 数字）。这里用一个独立 ref 承载控件值：
 * 打开时从字符串解出，提交时由 `fixed4` 收口回字符串 —— 不做双向桥接，
 * 免得每次击键都往返一次格式化，把控件自己的编辑态冲掉。
 */
const unitPrice = ref<number | null>(null);
const api = pricingApi.agreement;
let requestId = 0;

async function open(id?: ScmId) {
  const request = ++requestId;
  visible.value = true;
  error.value = '';
  for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key];
  Object.assign(form, emptyPrice());
  range.value = undefined;
  unitPrice.value = null;
  if (!id) return;
  loading.value = true;
  try {
    const r = await api.detail(id);
    if (request === requestId) {
      Object.assign(form, r.data);
      range.value = [r.data.effectiveFrom, r.data.effectiveTo || ''];
      unitPrice.value = r.data.unitPrice === '' ? null : Number(r.data.unitPrice);
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
  form.unitPrice = fixed4(unitPrice.value) as string;
  form.effectiveFrom = range.value?.[0] || '';
  form.effectiveTo = range.value?.[1] || null;
  // 剩下的跨字段业务规则（结束时间必须晚于开始时间）不是字段必填，用 toast
  const problems = validatePrice(form, 'customerId');
  if (problems.length) {
    message.warning(problems.join('；'));
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
<style scoped>
.drawer-error {
  margin-bottom: 12px;
}
</style>
