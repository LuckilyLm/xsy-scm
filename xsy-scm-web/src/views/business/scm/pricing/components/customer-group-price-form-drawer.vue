<template>
  <a-drawer :title="editingId ? '编辑客户分组价' : '新增客户分组价'" :open="visible" :width="scmDrawerWidth('s')" @close="visible = false">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon class="drawer-error">
        <template #action>
          <a-button size="small" :loading="loading" @click="open(editingId)">重试</a-button>
        </template>
      </a-alert>
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">定价对象</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="客户分组" name="groupId">
                <CustomerGroupSelect v-model:value="form.groupId" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item name="skuId">
                <template #label>
                  商品规格
                  <ScmFieldHelp label="商品规格" text="选任一规格确定商品与销售单位：同一商品同单位的规格共用这条价格，跨单位需分别维护" />
                </template>
                <SkuSelect v-model:value="form.skuId" />
              </a-form-item>
            </a-col>
            <a-col v-if="editingId && detail" :span="24">
              <div class="scm-form-readonly">当前定价：{{ detail.productName || '—' }} · 销售单位 {{ detail.saleUnit || '—' }}</div>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">价格</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item name="unitPriceInput">
                <template #label>
                  分组价（按销售单位）
                  <ScmFieldHelp label="分组价" text="0 元也是有效价格；下单时仍可对单个客户手动改价" />
                </template>
                <a-input-number
                  v-model:value="form.unitPriceInput"
                  :min="0"
                  :precision="2"
                  :step="0.01"
                  addon-before="¥"
                  placeholder="0.00"
                  aria-label="分组价"
                  style="width: 100%"
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
              <a-form-item name="effectiveRange">
                <template #label>
                  有效区间
                  <ScmFieldHelp label="有效区间" text="开始时间包含，结束时间不包含；结束留空表示长期有效" />
                </template>
                <a-range-picker
                  v-model:value="form.effectiveRange"
                  show-time
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :allow-empty="[false, true]"
                  style="width: 100%"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </section>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading || (!!editingId && !!error)" @click="submit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {reactive, ref} from 'vue';
import {message, type FormInstance} from 'ant-design-vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import type {ScmId} from '/@/types/business/scm/customer';
import type {GroupPriceForm, GroupPriceRow} from '/@/types/business/scm/pricing';
import CustomerGroupSelect from '/@/components/business/scm/customer-group-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {pricingError} from '../pricing-errors';
import {moneyPayload, normalizeMoneyInput} from '/@/utils/scm-amount';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

/** 表单模型：单价输入和有效期是本地字段，提交时再写进 GroupPriceForm（后端要定点数字符串）。 */
type GroupPriceModel = GroupPriceForm & {
  unitPriceInput: number | null;
  effectiveRange?: [string, string];
};

const emit = defineEmits<{saved: []}>();
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const detail = ref<GroupPriceRow>();
const form = reactive<GroupPriceModel>({
  groupId: undefined,
  skuId: undefined,
  unitPrice: null,
  effectiveFrom: '',
  effectiveTo: null,
  unitPriceInput: null,
  effectiveRange: undefined,
});
const formRef = ref<FormInstance>();
const formRules = {
  groupId: [{required: true, message: '请选择客户分组', trigger: 'change'}],
  skuId: [{required: true, message: '请选择商品规格', trigger: 'change'}],
  unitPriceInput: [{required: true, message: '请填写分组价（零价也是有效价格，填 0 即可）', trigger: 'blur'}],
  effectiveRange: [
    {
      validator: () => (form.effectiveRange?.[0] ? Promise.resolve() : Promise.reject(new Error('请选择生效时间'))),
      trigger: 'change',
    },
  ],
};
const api = pricingApi.groupPrice;
let requestId = 0;
/** 当前编辑对象；加载失败时仍保留，避免提交退化成新增。 */
const editingId = ref<ScmId>();

async function open(id?: ScmId) {
  const request = ++requestId;
  editingId.value = id;
  visible.value = true;
  error.value = '';
  detail.value = undefined;
  Object.assign(form, {
    groupId: undefined,
    skuId: undefined,
    unitPrice: null,
    effectiveFrom: '',
    effectiveTo: null,
    unitPriceInput: null,
    effectiveRange: undefined,
  });
  if (!id) return;
  loading.value = true;
  try {
    const response = await api.detail(id, {suppressGlobalErrorMessage: true});
    if (request === requestId) {
      detail.value = response.data;
      Object.assign(form, response.data);
      form.effectiveRange = [response.data.effectiveFrom, response.data.effectiveTo || ''];
      form.unitPriceInput =
        response.data.unitPrice == null || response.data.unitPrice === '' ? null : Number(normalizeMoneyInput(response.data.unitPrice));
    }
  } catch (e) {
    error.value = pricingError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

async function submit() {
  // 编辑对象未加载成功时禁止提交，避免空表单走新增
  if (editingId.value && error.value) return;
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  const from = form.effectiveRange?.[0];
  const to = form.effectiveRange?.[1] || null;
  if (!from || (to && to <= from)) {
    message.warning('结束时间必须晚于开始时间');
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    const payload: GroupPriceForm = {
      groupId: form.groupId,
      skuId: form.skuId,
      unitPrice: moneyPayload(form.unitPriceInput),
      effectiveFrom: from,
      effectiveTo: to,
    };
    if (editingId.value != null) {
      await api.update({...payload, groupPriceId: editingId.value, version: detail.value?.version});
    } else {
      await api.add(payload);
    }
    message.success('分组价已保存');
    visible.value = false;
    emit('saved');
  } catch {
    // 保存失败由全局 toast 提示；error 只用于加载失败
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
