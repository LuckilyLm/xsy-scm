<template>
  <a-drawer :title="form.agreementPriceId?'编辑客户协议价':'新增客户协议价'"
            :open="visible" :width="scmDrawerWidth('s')" @close="visible=false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon class="drawer-error"/>
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
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
              <a-form-item label="协议价公式">
                <a-select v-model:value="form.formulaType" :options="formulaOptions"/>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item v-if="form.formulaType === 'FIXED'" name="unitPriceInput">
                <template #label>
                  单价
                  <ScmFieldHelp label="单价" text="0 元也是有效价格"/>
                </template>
                <a-input-number
                    v-model:value="form.unitPriceInput"
                    :min="0"
                    :precision="4"
                    :step="0.01"
                    addon-before="¥"
                    placeholder="0.0000"
                    aria-label="单价"
                    style="width:100%"
                />
              </a-form-item>
              <a-form-item v-else name="formulaParameter" :label="form.formulaType === 'MARKET_PLUS_AMOUNT' ? '固定加价金额' : '加价率'">
                <a-input v-model:value="form.formulaParameter" placeholder="输入非负四位定点数；0.1000 表示 10%"/>
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
              <a-form-item name="effectiveRange">
                <template #label>
                  有效区间
                  <ScmFieldHelp label="有效区间" text="开始时间包含，结束时间不包含；结束留空表示长期有效"/>
                </template>
                <a-range-picker v-model:value="form.effectiveRange" show-time value-format="YYYY-MM-DD HH:mm:ss"
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
import type {AgreementFormulaType} from '/@/types/business/scm/pricing';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {emptyPrice, validatePrice} from '../pricing-form-model';
import {pricingError} from '../pricing-errors';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {fixed4} from '../../common/scm-fixed';

/** 表单模型：单价输入与有效期是本地字段，提交时才折进 PriceForm（后端要定点数字符串）。 */
type AgreementPriceModel = PriceForm & {
  unitPriceInput: number | null;
  effectiveRange?: [string, string];
};

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('');
/**
 * 表单模型必须是一个稳定对象。
 *
 * 原来写 `:model="{...form, unitPrice, range}"`：每次渲染都会生成新对象，而 change 触发的校验
 * 发生在重渲染之前，读到的是上一份快照——于是「选了客户仍提示请选择客户」「填了单价仍提示请填写单价」。
 * 把单价与有效期并入 form 后，校验读到的就是当前值。
 */
const form = reactive<AgreementPriceModel>({...emptyPrice(), unitPriceInput: null, effectiveRange: undefined});
const formulaOptions: {value: AgreementFormulaType; label: string}[] = [
  {value: 'FIXED', label: '固定协议价'},
  {value: 'MARKET_PLUS_AMOUNT', label: '市场价加固定金额'},
  {value: 'COST_MARKUP_RATE', label: '配送仓成本加价率'},
  {value: 'MARKET_MARKUP_RATE', label: '市场价加价率'}
];
const formRef = ref();
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  customerId: [{required: true, message: '请选择客户', trigger: 'change'}],
  skuId: [{required: true, message: '请选择商品规格', trigger: 'change'}],
  unitPriceInput: [{
    validator: () => (form.formulaType === 'FIXED' && form.unitPriceInput == null
        ? Promise.reject(new Error('请填写单价（零价也是有效价格，填 0 即可）'))
        : Promise.resolve()),
    trigger: 'blur',
  }],
  formulaParameter: [{
    validator: () => (form.formulaType !== 'FIXED' && !/^\d{1,14}(\.\d{1,4})?$/.test(form.formulaParameter || '')
        ? Promise.reject(new Error('请填写非负四位定点公式参数')) : Promise.resolve()),
    trigger: 'blur',
  }],
  effectiveRange: [{
    validator: () => (form.effectiveRange?.[0] ? Promise.resolve() : Promise.reject(new Error('请选择生效时间'))),
    trigger: 'change',
  }],
};
const api = pricingApi.agreement;
let requestId = 0;

async function open(id?: ScmId) {
  const request = ++requestId;
  visible.value = true;
  error.value = '';
  for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key];
  Object.assign(form, emptyPrice());
  form.formulaType = 'FIXED';
  form.formulaParameter = null;
  form.effectiveRange = undefined;
  form.unitPriceInput = null;
  if (!id) return;
  loading.value = true;
  try {
    const r = await api.detail(id, {suppressGlobalErrorMessage: true});
    if (request === requestId) {
      Object.assign(form, r.data);
      form.effectiveRange = [r.data.effectiveFrom, r.data.effectiveTo || ''];
      form.formulaType = r.data.formulaType || 'FIXED';
      form.unitPriceInput = r.data.unitPrice == null || r.data.unitPrice === '' ? null : Number(r.data.unitPrice);
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
  form.unitPrice = form.formulaType === 'FIXED' ? fixed4(form.unitPriceInput) as string : null;
  form.formulaParameter = form.formulaType === 'FIXED' ? null : form.formulaParameter?.trim();
  form.effectiveFrom = form.effectiveRange?.[0] || '';
  form.effectiveTo = form.effectiveRange?.[1] || null;
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
  } catch {
    // 保存失败只走全局 toast；抽屉里的 error 留给加载失败
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
