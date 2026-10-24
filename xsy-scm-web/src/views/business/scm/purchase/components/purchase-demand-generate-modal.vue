<!-- 采购需求生成弹窗。时间段含开始日、不含结束日。
     先生成冻结批次，再由同一批次生成需求，重复生成不会重复建需求。 -->
<template>
  <a-modal :open="open" title="汇总生成采购需求" width="720px" :confirm-loading="saving" ok-text="生成需求" @ok="generate" @cancel="close">
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <FormNote title="含开始日，不含结束日" :lines="['只汇总区间内「已确认」的销售订单行']" />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="form">
      <a-form-item label="统计时间段" name="range" required>
        <a-range-picker v-model:value="form.range" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
      </a-form-item>
      <a-form-item label="收货仓库" name="warehouseId" required>
        <a-select
          v-model:value="form.warehouseId"
          :options="warehouseOptions"
          :loading="warehouseLoading"
          show-search
          option-filter-prop="label"
          placeholder="请选择收货仓库"
        />
      </a-form-item>
      <a-form-item label="供应商（可选）" name="supplierId">
        <SupplierSelect v-model:value="form.supplierId" />
      </a-form-item>
      <a-form-item label="采购员（可选）" name="purchaserId">
        <EmployeeSelect v-model:value="purchaserValue" />
      </a-form-item>
    </a-form>

    <a-descriptions v-if="batch" bordered size="small" :column="2">
      <a-descriptions-item label="冻结批次">{{ batch.batchId }}</a-descriptions-item>
      <a-descriptions-item label="候选行">{{ batch.candidateLineCount }}</a-descriptions-item>
      <a-descriptions-item label="状态">{{ batch.status }}</a-descriptions-item>
      <a-descriptions-item label="冻结解释行">{{ batch.summary?.length ?? 0 }} 个商品规格</a-descriptions-item>
    </a-descriptions>
    <div v-if="batch" class="batch-actions">
      <a-button v-privilege="'scm:purchase:demand:batch:query'" @click="viewBatch"> 查看冻结批次明细 </a-button>
    </div>
    <a-descriptions v-if="result" bordered size="small" :column="2">
      <a-descriptions-item label="区间内来源行">{{ result.sourceLineCount }}</a-descriptions-item>
      <a-descriptions-item label="本次新建需求">{{ result.createdCount }}</a-descriptions-item>
      <a-descriptions-item label="已跳过（已存在需求）">{{ result.skippedCount }}</a-descriptions-item>
      <a-descriptions-item label="涉及需求 id 数">{{ result.demandIds?.length ?? 0 }}</a-descriptions-item>
    </a-descriptions>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {message, type FormInstance} from 'ant-design-vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import type {DemandCalculationBatch, GenerateResult, Id} from '../purchase-types';
import {purchaseError} from '../purchase-errors';
import FormNote from '/@/components/framework/form-note/index.vue';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const props = defineProps<{open: boolean}>();
const emit = defineEmits<{close: []; generated: []; viewBatch: [batchId: Id]}>();

/** 表单模型要保持同一个对象；每次渲染新建对象会让校验读到旧值。 */
const form = reactive<{
  warehouseId?: Id;
  supplierId?: Id;
  purchaserId?: Id;
  range?: [string, string];
}>({});
const formRef = ref<FormInstance>();
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  range: [
    {
      validator: () => (form.range?.[0] && form.range?.[1] ? Promise.resolve() : Promise.reject(new Error('请选择统计时间段'))),
      trigger: 'change',
    },
  ],
  warehouseId: [{required: true, message: '请选择收货仓库', trigger: 'change'}],
};
const saving = ref(false);
const error = ref('');
const result = ref<GenerateResult>();
const batch = ref<DemandCalculationBatch>();
const warehouseLoading = ref(false);
const warehouses = ref<{id: Id; warehouseCode?: string; name?: string}[]>([]);

const warehouseOptions = computed(() => warehouses.value.map((w) => ({value: w.id, label: w.name ?? ''})));

/** 员工选择器的 value 只接受数字，这里把 id 转成 number；清空时回写 undefined。 */
const purchaserValue = computed<number | undefined>({
  get: () => (form.purchaserId == null ? undefined : Number(form.purchaserId)),
  set: (value) => {
    form.purchaserId = value;
  },
});

async function loadWarehouses() {
  if (warehouses.value.length) {
    return;
  }
  warehouseLoading.value = true;
  try {
    warehouses.value = (await warehouseApi.list({suppressGlobalErrorMessage: true})).data ?? [];
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    warehouseLoading.value = false;
  }
}

void loadWarehouses();

// 每次打开清掉上一次的区间和结果
watch(
  () => props.open,
  (open) => {
    if (open) {
      form.range = undefined;
      result.value = undefined;
      batch.value = undefined;
      error.value = '';
      void loadWarehouses();
    }
  },
  {immediate: true}
);

async function generate() {
  error.value = '';
  // 必填项走表单校验：错误显示在对应输入框下方
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  if (!form.range || form.warehouseId === undefined) {
    error.value = '请选择时间范围与仓库';
    return;
  }
  saving.value = true;
  try {
    const r = await purchaseDemandApi.createBatch({
      startAt: form.range[0],
      endAt: form.range[1],
      warehouseId: form.warehouseId,
      supplierId: form.supplierId ?? null,
      purchaserId: form.purchaserId ?? null,
    });
    batch.value = r.data;
    const generated = await purchaseDemandApi.generateBatch({batchId: r.data.batchId});
    result.value = generated.data;
    message.success(`冻结批次 ${r.data.batchId} 已生成 ${generated.data.createdCount} 条需求，跳过 ${generated.data.skippedCount} 条`);
    emit('generated');
  } catch {
    // 生成失败只走全局 toast；弹窗里的 error 留给本地校验
  } finally {
    saving.value = false;
  }
}

function close() {
  emit('close');
}

/** 复用列表页的批次抽屉，避免再挂一份重复请求。 */
function viewBatch() {
  if (batch.value) {
    emit('viewBatch', batch.value.batchId);
  }
}
</script>

<style scoped>
.form {
  margin-top: 12px;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
</style>
