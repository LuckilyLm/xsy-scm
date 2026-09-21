<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-generate.vue
复制日期：2026-09-16。Copy First + Adapt（C 是整页，这里降级为弹窗；W5 无 `preview` 端点）。
剪枝：`calculateStock` 计算库存开关与「现有库存」列（A20 / A-D3：W5 不做库存抵扣）、
      `preview` 预览端点（W5 只提供 `generate`，返回 `GenerateResult` 计数）、
      `purchaseQuantity` / `unitPrice` 列（那是 A 源的汇总预览列，W5 的汇总在服务端完成）。
适配：**A21 半开区间** `[startAt, endAt)`（不是 `startTime`/`endTime`，也不是闭区间）、
      Q6a `demand_date` 取 `confirmed_at` 在上海时区下的日期、
      补 `warehouseId`（必填）/ `supplierId` / `purchaserId`、`scm:purchase:demand:generate`（A22）、
      `a-form-item` 带 `name`（A29）、loading/error/retry（A27）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-modal
      :open="open"
      title="汇总生成采购需求"
      width="720px"
      :confirm-loading="saving"
      ok-text="生成需求"
      @ok="generate"
      @cancel="close"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <a-alert
        type="info"
        show-icon
        message="区间是半开区间 [开始, 结束)"
        description="只汇总区间内「已确认」的销售订单行；重复汇总不会重复生成，已存在的来源行会被计入「已跳过」。"
    />
    <a-form layout="vertical" class="form">
      <a-form-item label="统计时间段" name="range" required>
        <a-range-picker
            v-model:value="range"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
        />
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
        <SupplierSelect v-model:value="form.supplierId"/>
      </a-form-item>
      <a-form-item label="采购员（可选）" name="purchaserId">
        <EmployeeSelect v-model:value="purchaserValue"/>
      </a-form-item>
    </a-form>

    <a-descriptions v-if="result" bordered size="small" :column="2">
      <a-descriptions-item label="区间内来源行">{{ result.sourceLineCount }}</a-descriptions-item>
      <a-descriptions-item label="本次新建需求">{{ result.createdCount }}</a-descriptions-item>
      <a-descriptions-item label="已跳过（已存在需求）">{{ result.skippedCount }}</a-descriptions-item>
      <a-descriptions-item label="涉及需求 id 数">{{ result.demandIds?.length ?? 0 }}</a-descriptions-item>
    </a-descriptions>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {message} from 'ant-design-vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import type {GenerateResult, Id} from '../purchase-types';
import {purchaseError} from '../purchase-errors';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; generated: [] }>();

const range = ref<[string, string] | undefined>(undefined);
const form = ref<{ warehouseId?: Id; supplierId?: Id; purchaserId?: Id }>({});
const saving = ref(false);
const error = ref('');
const result = ref<GenerateResult>();
const warehouseLoading = ref(false);
const warehouses = ref<{ id: Id; warehouseCode?: string; name?: string }[]>([]);

const warehouseOptions = computed(() =>
    warehouses.value.map((w) => ({value: w.id, label: `${w.name ?? ''}（${w.warehouseCode ?? ''}）`}))
);

/**
 * 采购员下拉的桥接（A11）。
 *
 * V2 原生 `EmployeeSelect` 的 `value` prop 声明是 `[Number, Array]`，
 * 直接绑 `Id | undefined` 会因为 `string` 报 TS2322。
 * 员工 id 在后端是自增数字，这里显式收敛成 `number | undefined`；
 * 本弹窗的 `purchaserId` 本就是可选（提交时 `?? null`），因此清空写回 `undefined`。
 */
const purchaserValue = computed<number | undefined>({
  get: () => (form.value.purchaserId == null ? undefined : Number(form.value.purchaserId)),
  set: (value) => {
    form.value.purchaserId = value;
  },
});

async function loadWarehouses() {
  if (warehouses.value.length) {
    return;
  }
  warehouseLoading.value = true;
  try {
    warehouses.value = (await warehouseApi.list()).data ?? [];
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    warehouseLoading.value = false;
  }
}

void loadWarehouses();

// 弹窗每次打开都清掉上一次的区间与结果，避免「以为重新生成了其实没有」
watch(
    () => props.open,
    (open) => {
      if (open) {
        range.value = undefined;
        result.value = undefined;
        error.value = '';
        void loadWarehouses();
      }
    }
);

async function generate() {
  error.value = '';
  if (!range.value || range.value.length !== 2) {
    error.value = '请选择统计时间段';
    return;
  }
  if (!form.value.warehouseId) {
    error.value = '请选择收货仓库';
    return;
  }
  saving.value = true;
  try {
    const r = await purchaseDemandApi.generate({
      startAt: range.value[0],
      endAt: range.value[1],
      warehouseId: form.value.warehouseId,
      supplierId: form.value.supplierId ?? null,
      purchaserId: form.value.purchaserId ?? null,
    });
    result.value = r.data;
    message.success(`本次新建 ${r.data.createdCount} 条需求，跳过 ${r.data.skippedCount} 条`);
    emit('generated');
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

function close() {
  emit('close');
}
</script>

<style scoped>
.form {
  margin-top: 12px;
}
</style>
