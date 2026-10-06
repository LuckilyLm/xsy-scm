<template>
  <a-drawer :title="form.agreementPriceId?'编辑客户协议价':'新增客户协议价'"
            :open="visible" :width="scmDrawerWidth('s')" @close="visible=false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon class="drawer-error"/>
      <a-form layout="vertical" :model="form">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">定价对象</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="客户" required>
                <CustomerSelect v-model:value="form.customerId"/>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="商品规格" required>
                <SkuSelect v-model:value="form.skuId"/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">价格</h3>
            <span class="scm-form-section__hint">零价也是有效价格</span>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="单价" required>
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
            <span class="scm-form-section__hint">开始时间包含，结束时间不包含；结束留空表示长期有效</span>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="有效区间" required>
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
import {fixed4} from '../../common/scm-fixed';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref('');
const form = reactive<PriceForm>(emptyPrice());
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
  if (unitPrice.value === null) {
    error.value = '请填写单价（零价也是有效价格，填 0 即可）';
    return;
  }
  form.unitPrice = fixed4(unitPrice.value) as string;
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
<style scoped>
.drawer-error {
  margin-bottom: 12px;
}
</style>
