<!-- 客户类型价批量调价：整批提交，逐行显示错误。 -->
<template>
  <a-card size="small" :bordered="false">
    <template #title>
      <span>客户类型价批量调价</span>
    </template>
    <a-form ref="formRef" :model="formModel" :rules="formRules" layout="inline" class="smart-query-form">
      <a-row class="smart-query-form-row">
        <a-form-item label="批次号" name="batchKey" class="smart-query-form-item"
          ><a-input v-model:value="batchKey" aria-label="批次号" :maxlength="100" style="width: 320px"
        /></a-form-item>
        <a-form-item class="smart-query-form-item"><a-button @click="add" :disabled="rows.length >= 500 || saving">新增行</a-button></a-form-item>
        <a-form-item class="smart-query-form-item"
          ><a-button type="primary" :loading="saving" v-privilege="'scm:pricing:type-price:batch'" @click="submit">提交整批</a-button></a-form-item
        >
      </a-row>
    </a-form>
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <a-table
      :data-source="rows"
      :columns="columns"
      row-key="rowNumber"
      :pagination="false"
      size="small"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
      :row-class-name="(r: BatchRow) => (failures.some((f) => f.rowNumber === r.rowNumber) ? 'failed-row' : '')"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'customerTypeId'"><CustomerTypeSelect v-model:value="record.customerTypeId" width="180px" /></template>
        <template v-else-if="column.dataIndex === 'skuId'"><SkuSelect v-model:value="record.skuId" width="260px" /></template>
        <template v-else-if="column.dataIndex === 'unitPrice'"
          ><a-input-number
            v-model:value="record.unitPrice"
            string-mode
            :min="'0'"
            :precision="2"
            :step="'0.01'"
            :controls="false"
            aria-label="单价"
            placeholder="0.00"
            style="width: 100%"
        /></template>
        <template v-else-if="column.dataIndex === 'effectiveFrom'"
          ><a-range-picker
            :value="[record.effectiveFrom, record.effectiveTo || '']"
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
            :allow-empty="[false, true]"
            @change="(v: unknown) => setPeriod(record, v)"
        /></template>
        <template v-else-if="column.dataIndex === 'error'"
          ><span class="row-error">{{
            failures
              .filter((f) => f.rowNumber === record.rowNumber)
              .map((f) => f.message)
              .join('；')
          }}</span></template
        >
        <template v-else-if="column.dataIndex === 'action'"
          ><a-button type="link" danger :disabled="saving" @click="rows = rows.filter((r) => r.rowNumber !== record.rowNumber)"
            >移除</a-button
          ></template
        >
      </template>
    </a-table>
  </a-card>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import {useRouter} from 'vue-router';
import dayjs from 'dayjs';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {moneyPayload} from '/@/utils/scm-amount';
import type {BatchRow} from '/@/types/business/scm/pricing';
import {emptyPrice, validateBatch} from './pricing-form-model';
import {pricingError} from './pricing-errors';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const router = useRouter(),
  batchKey = ref('PRICE-' + dayjs().format('YYYYMMDD-HHmmss')),
  rows = ref<BatchRow[]>([]),
  failures = ref<{rowNumber: number; message: string}[]>([]),
  saving = ref(false),
  error = ref('');
let number = 0;
const formRef = ref<FormInstance>();
/** 必填项逐项校验，错误显示在输入框下方。 */
const formRules = {batchKey: [{required: true, message: '请填写批次号', trigger: 'blur'}]};
/** 校验模型必须是稳定对象；reactive 会解包内部 ref，值仍是响应式的。 */
const formModel = reactive({batchKey});
/** 错误列只在整批校验失败后出现，空表时不占一列位置。 */
const BASE_COLUMNS: TableColumnsType<BatchRow> = [
  {title: '行号', dataIndex: 'rowNumber', width: 65},
  {
    title: '客户类型',
    dataIndex: 'customerTypeId',
    width: 200,
  },
  {title: '商品规格', dataIndex: 'skuId', width: 280},
  {
    title: '单价',
    dataIndex: 'unitPrice',
    width: 140,
  },
  {title: '有效区间', dataIndex: 'effectiveFrom', width: 400},
  {title: '操作', dataIndex: 'action', width: 80, align: 'center'},
];
const columns = computed<TableColumnsType<BatchRow>>(() => {
  if (!failures.value.length) {
    return BASE_COLUMNS;
  }
  const list = [...BASE_COLUMNS];
  list.splice(5, 0, {title: '错误', dataIndex: 'error', width: 230});
  return list;
});

function add() {
  rows.value.push({...emptyPrice(), rowNumber: ++number});
}

function setPeriod(row: BatchRow, value: unknown) {
  const v = value as string[] | null;
  row.effectiveFrom = v?.[0] || '';
  row.effectiveTo = v?.[1] || null;
}

async function submit() {
  error.value = '';
  failures.value = validateBatch(rows.value);
  // 批次号走字段校验；至少一行用 toast 提示
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  if (!rows.value.length) {
    message.warning('请至少添加一行');
    return;
  }
  if (failures.value.length) {
    error.value = `${failures.value.length} 项校验失败，整批未提交`;
    return;
  }
  saving.value = true;
  try {
    const payloadRows = rows.value.map((row) => ({...row, unitPrice: moneyPayload(row.unitPrice)}));
    const r = await pricingApi.batch({batchKey: batchKey.value, rows: payloadRows});
    if (!r.data.committed) {
      failures.value = r.data.failures;
      error.value = `${r.data.failures.length} 项错误，整批未写入`;
    } else {
      message.success(`已提交 ${r.data.rowCount} 条价格`);
      router.push('/pricing/customer-type-price-list');
    }
  } catch (e) {
    error.value = pricingError(e);
  } finally {
    saving.value = false;
  }
}

add();
</script>
<style scoped>
.row-error {
  color: var(--scm-error);
}

:deep(.failed-row td) {
  background: var(--scm-error-bg, #fff2f0);
}
</style>
