<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-list.vue 的行内抽屉表单
复制日期：2026-09-16。Copy First + Adapt（C 把表单内嵌在列表页里，这里拆成独立组件）。
剪枝：C 的裸 ID 数字输入（`supplierId` / `buyerId` / `categoryId`）、`totalAmount` 手填、`qrcodeUrl`。
适配：供应商选择器（A9）、**新增仓库选择器**（A10）、员工选择器（A11）、`version`（A8）、
      多需求分配编辑器（A31）、单位不一致禁止分配（A32）、`a-form-item` 带 `name`（A29）、
      `scm:purchase:*`（A22）、loading/error/retry（A27）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-drawer
      :title="form.id ? '编辑采购单' : '新建采购单'"
      :open="visible"
      width="min(1280px, 96vw)"
      @close="visible = false"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <a-spin :spinning="loading">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="供应商" name="supplierId" required>
              <SupplierSelect v-if="!form.id" v-model:value="form.supplierId"/>
              <a-input v-else :value="form.supplierName" disabled/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
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
          </a-col>
          <a-col :span="12">
            <a-form-item label="采购员">
              <a-input v-if="form.id" :value="form.purchaserName || '未分配'" disabled/>
              <EmployeeSelect v-else v-model:value="purchaserValue" :disabled="!canAssign"/>
              <div class="ant-form-item-extra">{{ purchaserHint }}</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="计划到货日期" name="plannedArrivalDate">
              <a-date-picker
                  v-model:value="form.plannedArrivalDate"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark">
              <a-input v-model:value="form.remark" maxlength="500"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-divider orientation="left">采购明细与需求分配</a-divider>
        <ItemTable :items="form.items ?? []"/>
      </a-form>
    </a-spin>

    <template #footer>
      <a-space>
        <a-button @click="visible = false">关闭</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save">保存草稿</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import type {Id, Order} from '../purchase-types';
import {newOrder, payload, validateOrder} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import {hasPermission} from '../../common/scm-permission';
import ItemTable from './purchase-order-item-editable-table.vue';

const emit = defineEmits<{ saved: [] }>();

const form = ref<Order>(newOrder());
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const warehouseLoading = ref(false);
const warehouses = ref<{ id: Id; warehouseCode?: string; name?: string }[]>([]);
/** 竞态保护：慢的旧详情不得覆盖新打开的单。 */
let requestId = 0;

const warehouseOptions = computed(() =>
    warehouses.value.map((w) => ({
      value: w.id,
      label: `${w.name ?? ''}（${w.warehouseCode ?? ''}）`,
    }))
);

/**
 * 采购员下拉的桥接（A11）。
 *
 * V2 原生 `EmployeeSelect` 的 `value` prop 声明是 `[Number, Array]`，
 * 直接绑 `Id | null | undefined` 会因为 `string` / `null` 报 TS2322。
 * 员工 id 在后端是自增数字，这里显式收敛成 `number | undefined`；
 * 清空时写回 `null`（后端 `purchaserId` 可空，不用 `undefined` 表达「未选」）。
 */
const purchaserValue = computed<number | undefined>({
  get: () => (form.value.purchaserId == null ? undefined : Number(form.value.purchaserId)),
  set: (value) => {
    form.value.purchaserId = value ?? null;
  },
});

/** 分配/改派权：与服务端 PurchaseOwnerResolver 同一口径 —— 无此权者新建一律落自己名下。 */
const canAssign = computed(() => hasPermission('scm:purchase:assign'));

/** 采购员字段的形态说明：编辑只读（/update 永不动归属）、无分配权自动归属自己、有分配权可指定或留空。 */
const purchaserHint = computed(() => {
  if (form.value.id) {
    return '归属变更请使用列表中的「改派采购员」；编辑保存不会改动采购员。';
  }
  if (!canAssign.value) {
    return '无分配权限，新建后将自动归属当前账号。';
  }
  return '留空表示暂不分配（未分配单据仅持分配权或全量范围者可见）。';
});

async function loadWarehouses() {
  if (warehouses.value.length) {
    return;
  }
  warehouseLoading.value = true;
  try {
    const r = await warehouseApi.list();
    warehouses.value = r.data ?? [];
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    warehouseLoading.value = false;
  }
}

async function open(id?: Id) {
  const current = ++requestId;
  error.value = '';
  form.value = newOrder();
  visible.value = true;
  await loadWarehouses();
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const r = await purchaseOrderApi.detail(id);
    if (current !== requestId) {
      return;
    }
    const detail = r.data;
    // 表单里的 `plannedQuantity` / `purchasePrice` 就是 VO 的同名字段；
    // 分配集合原样带过来，`demandVersion` 用详情里的当前值（写错会 40972）。
    form.value = {
      ...detail,
      items: (detail.items ?? []).map((item) => ({
        ...item,
        allocations: (item.allocations ?? []).map((row) => ({
          ...row,
          quantity: row.quantity,
        })),
      })),
    };
  } catch (e) {
    if (current === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (current === requestId) {
      loading.value = false;
    }
  }
}

async function save() {
  error.value = '';
  const invalid = validateOrder(form.value);
  if (invalid) {
    error.value = invalid;
    return;
  }
  saving.value = true;
  try {
    const body = payload(form.value);
    if (body.id === undefined) {
      await purchaseOrderApi.create(body);
    } else {
      await purchaseOrderApi.update(body);
    }
    message.success('草稿已保存');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
