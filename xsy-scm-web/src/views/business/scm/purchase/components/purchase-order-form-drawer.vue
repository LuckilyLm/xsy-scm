<template>
  <a-drawer
      :title="form.id ? '编辑采购单' : '新建采购单'"
      :open="visible"
      :width="scmDrawerWidth('xl')"
      @close="visible = false"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
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
          <a-col v-if="form.id || canAssign" :span="12">
            <a-form-item>
              <template #label>
                采购员
                <ScmFieldHelp label="采购员" :text="form.id ? '请在采购单列表中改派采购员' : '可留空，保存后暂不分配'"/>
              </template>
              <span v-if="form.id" class="scm-form-readonly">{{ form.purchaserName || '未分配' }}</span>
              <EmployeeSelect v-else v-model:value="purchaserValue" placeholder="请选择采购员"/>
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
              <a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" show-count/>
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
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';

const emit = defineEmits<{ saved: [] }>();

const form = ref<Order>(newOrder());
const formRef = ref();
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  supplierId: [{required: true, message: '请选择供应商', trigger: 'change'}],
  warehouseId: [{required: true, message: '请选择收货仓库', trigger: 'change'}],
};
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
 * 采购员下拉的桥接。
 *
 * 原生 `EmployeeSelect` 的 `value` prop 声明是 `[Number, Array]`，
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
  // 必填项走表单校验：错误显示在对应输入框下方
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // 明细行（不是表单字段）与其余业务规则用 toast
  const invalid = validateOrder(form.value);
  if (invalid) {
    message.warning(invalid);
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
