<!--
  * 仓库授权维护（employee_warehouse_scope），挂在「仓库管理」页的两类入口上：
  * - open()：员工维度的整体替换编辑器（对应 POST /scm/warehouse/scope/update 的 employeeId + warehouseIds 形状）。
  * - openEmployees(warehouseId)：仓库维度的只读查看（对应 GET /scm/warehouse/scope/employees）。
  *
  * 写侧刻意做成「选人 → 勾他的全部可见仓 → 整体提交」，而不是在某一仓里增减人：
  * update 是员工维度的替换语义，若从仓库侧增删一人，就得改写此人其它所有仓的授权集合，
  * 既不是原子操作，也会把「改一个仓的可见性」放大成对人不一致的覆盖。
-->
<template>
  <a-modal
      v-model:open="visible"
      :title="isView ? '该仓库已授权员工' : '员工仓库授权维护'"
      :width="isView ? 560 : 640"
      :ok-button-props="{ style: isView ? { display: 'none' } : undefined }"
      :confirm-loading="saving"
      @ok="save"
  >
    <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>

    <template v-if="isView">
      <p>仓库：<strong>{{ viewWarehouseName }}</strong></p>
      <a-table
          size="small"
          :columns="viewColumns"
          :data-source="employees"
          row-key="employeeId"
          :loading="viewLoading"
          :pagination="false"
          :locale="{ emptyText: '该仓库暂无被授权员工' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'disabledFlag'">
            <a-tag :color="record.disabledFlag ? 'red' : 'green'">
              {{ record.disabledFlag ? '已停用' : '在职' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </template>

    <a-form ref="formRef" v-else :model="{employeeId}" :rules="formRules" layout="vertical">
      <a-form-item name="employeeId">
        <template #label>
          员工
          <ScmFieldHelp label="员工" text="选择后载入当前仓库授权；保存时按勾选结果整体替换"/>
        </template>
        <EmployeeSelect v-model:value="employeeValue" placeholder="请选择要维护授权的员工" width="100%"/>
      </a-form-item>
      <a-form-item>
        <template #label>
          可见仓库
          <ScmFieldHelp label="可见仓库" text="保留已停用仓库的勾选即维持授权，取消勾选即回收"/>
        </template>
        <a-select
            v-model:value="warehouseIds"
            mode="multiple"
            :options="warehouseOptions"
            :loading="warehouseLoading"
            :disabled="employeeId == null"
            show-search
            option-filter-prop="label"
            :placeholder="employeeId == null ? '请先选择员工' : '请选择可见仓库'"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import type {
    Id,
    Warehouse,
    WarehouseScopeEmployee,
} from '../purchase-types';
import {purchaseError} from '../purchase-errors';

const visible = ref(false);
const saving = ref(false);
const error = ref('');

const isView = ref(false);
const viewWarehouseName = ref('');
const viewLoading = ref(false);
const employees = ref<WarehouseScopeEmployee[]>([]);

/** 员工维度的编辑目标；EmployeeSelect 的 value 不接受 null（[Number, Array]），用 undefined 桥接未选。 */
const employeeId = ref<number | undefined>(undefined);
const formRef = ref();
/** 必填项逐项校验：错误显示在输入框下方，不再用顶部一条汇总红条。 */
const formRules = {employeeId: [{required: true, message: '请先选择员工', trigger: 'change'}]};
const warehouseIds = ref<Id[]>([]);
const warehouses = ref<Warehouse[]>([]);
const warehouseLoading = ref(false);

const viewColumns: TableColumnsType = [
  {title: '员工姓名', dataIndex: 'actualName'},
  {title: '登录名', dataIndex: 'loginName'},
  {title: '在职状态', dataIndex: 'disabledFlag', align: 'center', width: 120},
];

const warehouseOptions = computed(() =>
    warehouses.value.map((w) => ({
      value: w.id,
      label: `${w.name ?? ''}（${w.warehouseCode ?? ''}）${w.status === 'DISABLED' ? '（已停用）' : ''}`,
    }))
);

const employeeValue = computed<number | undefined>({
  get: () => employeeId.value,
  set: (value) => {
    employeeId.value = value;
    void loadEmployeeWarehouses(value);
  },
});

/** 仓库主数据不套数据范围（scm:warehouse:query 即可读全量），一次取全含停用，避免授权到停用仓时选项缺失。 */
async function ensureWarehouses() {
  if (warehouses.value.length) {
    return;
  }
  warehouseLoading.value = true;
  try {
    const r = await warehouseApi.query({pageNum: 1, pageSize: 1000});
    warehouses.value = r.data.list;
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    warehouseLoading.value = false;
  }
}

async function loadEmployeeWarehouses(id?: number) {
  if (id == null) {
    warehouseIds.value = [];
    return;
  }
  try {
    const r = await warehouseApi.scopeWarehouses(id);
    warehouseIds.value = r.data.map((row) => row.warehouseId);
  } catch (e) {
    error.value = purchaseError(e);
  }
}

async function open() {
  isView.value = false;
  error.value = '';
  employeeId.value = undefined;
  warehouseIds.value = [];
  visible.value = true;
  await ensureWarehouses();
}

async function openEmployees(warehouseId: Id, warehouseName: string) {
  isView.value = true;
  error.value = '';
  viewWarehouseName.value = warehouseName;
  employees.value = [];
  visible.value = true;
  viewLoading.value = true;
  try {
    const r = await warehouseApi.scopeEmployees(warehouseId);
    employees.value = r.data;
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    viewLoading.value = false;
  }
}

async function save() {
  // 必填项走表单校验：错误显示在输入框下方
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  const target = employeeId.value!;
  Modal.confirm({
    title: '确认更新仓库授权',
    content: '当前勾选将替换该员工的全部仓库授权；未勾选的授权会被收回。',
    okText: '确认更新',
    onOk: () => saveConfirmed(target),
  });
}

async function saveConfirmed(target: number) {
  saving.value = true;
  error.value = '';
  try {
    // 整体替换：提交当前勾选的全集，空数组即回收该员工全部仓库授权。
    await warehouseApi.scopeUpdate({employeeId: target, warehouseIds: warehouseIds.value});
    message.success('授权已更新');
    visible.value = false;
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open, openEmployees});
</script>

<style scoped>
.smart-margin-bottom10 {
  margin-bottom: 10px;
}
</style>
