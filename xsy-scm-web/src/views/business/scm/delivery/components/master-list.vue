<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-form-item :label="isDriver ? '司机编码' : '车牌号'">
      <a-input v-model:value="query.keyword" allow-clear @pressEnter="search"/>
    </a-form-item>
    <a-form-item label="状态">
      <a-select v-model:value="query.status" allow-clear :options="statusOptions" class="status-select"/>
    </a-form-item>
    <a-form-item
    >
      <a-space>
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </a-space>
    </a-form-item
    >
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon
  >
    <template #action>
      <a-button @click="load">重试</a-button>
    </template>
  </a-alert
  >
  <a-card size="small" :bordered="false">
    <div class="smart-table-btn-block">
      <a-button type="primary" v-privilege="editPermission" @click="open()">新建{{ label }}</a-button>
    </div>
    <a-table
        :id="`scm-delivery-${kind}-table`"
        :columns="columns"
        :data-source="rows"
        row-key="id"
        size="small"
        bordered
        :pagination="false"
        :loading="loading"
        :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'"
        >
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template
        >
        <template v-else-if="column.dataIndex === 'employeeName'"
        >
          <span v-if="record.employeeName">{{ record.employeeName }}</span>
          <a-tag v-else color="orange">未绑定</a-tag>
        </template
        >
        <template v-else-if="column.dataIndex === 'action'"
        >
          <a-button type="link" v-privilege="editPermission" @click="open(record)">编辑</a-button>
        </template
        >
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          v-model:current="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          show-size-changer
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
      />
    </div>
  </a-card>
  <a-modal v-model:open="visible" :title="`${form.id ? '编辑' : '新建'}${label}`" :confirm-loading="saving" @ok="save">
    <a-alert v-if="formError" :message="formError" type="error" show-icon/>
    <a-form layout="vertical">
      <template v-if="isDriver">
        <a-form-item label="司机编码" required>
          <a-input v-model:value="form.driverCode" :maxlength="64"/>
        </a-form-item>
        <a-form-item label="司机姓名" required>
          <a-input v-model:value="form.driverName" :maxlength="100"/>
        </a-form-item>
        <a-form-item label="联系电话" required>
          <a-input v-model:value="form.phone" :maxlength="32"/>
        </a-form-item>
        <a-form-item label="绑定员工" required>
          <EmployeeSelect v-model:value="employeeValue" placeholder="请选择绑定的系统员工" width="100%"/>
          <div class="ant-form-item-extra">启用司机必须绑定员工：它把登录人映射回司机档案，是其线路数据范围的唯一依据。</div>
        </a-form-item>
      </template>
      <template v-else>
        <a-form-item label="车牌号" required>
          <a-input v-model:value="form.vehicleNo" :maxlength="32"/>
        </a-form-item>
        <a-form-item label="车型">
          <a-input v-model:value="form.vehicleType" :maxlength="64"/>
        </a-form-item>
        <a-row :gutter="16"
        >
          <a-col :span="12"
          >
            <a-form-item label="载重（kg）">
              <a-input-number v-model:value="form.loadWeight" string-mode :min="0" :precision="4"/>
            </a-form-item
            >
          </a-col>
          <a-col :span="12"
          >
            <a-form-item label="容积（m³）"
            >
              <a-input-number v-model:value="form.loadVolume" string-mode :min="0" :precision="4"/>
            </a-form-item>
          </a-col
          >
        </a-row>
      </template>
      <a-form-item label="状态">
        <a-select v-model:value="form.status" :options="statusOptions"/>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" :maxlength="500" :rows="2"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import {deliveryError, type Driver, type Vehicle, type Query} from '../delivery-types';

const props = defineProps<{ kind: 'driver' | 'vehicle' }>();
const isDriver = computed(() => props.kind === 'driver');
const label = computed(() => (isDriver.value ? '司机' : '车辆'));
const editPermission = computed(() => `scm:delivery:${props.kind}:edit`);
const statusOptions = [
  {label: '启用', value: 'ENABLED'},
  {label: '停用', value: 'DISABLED'},
];
const query = reactive<Query>({pageNum: 1, pageSize: 20});
const rows = ref<(Driver | Vehicle)[]>([]),
    total = ref(0),
    loading = ref(false),
    error = ref('');
const visible = ref(false),
    saving = ref(false),
    formError = ref('');
const form = ref<Partial<Driver & Vehicle>>({status: 'ENABLED'});
let originalStatus = '',
    generation = 0;

/**
 * 绑定员工下拉的桥接。
 *
 * V2 原生 `EmployeeSelect` 的 `value` prop 声明是 `[Number, Array]`，直接绑 `Id | null`
 * 会因 `string` / `null` 报 TS2322；这里把 `null` 折成 `undefined`，清空写回 `null`（解绑）。
 */
const employeeValue = computed<number | undefined>({
  get: () => (form.value.employeeId == null ? undefined : Number(form.value.employeeId)),
  set: (value) => {
    form.value.employeeId = value ?? null;
  },
});

const columns = computed<TableColumnsType>(() => [
  ...(isDriver.value
      ? [
        {title: '司机编码', dataIndex: 'driverCode'},
        {title: '姓名', dataIndex: 'driverName'},
        {title: '电话', dataIndex: 'phone'},
        {title: '绑定员工', dataIndex: 'employeeName'},
      ]
      : [
        {title: '车牌号', dataIndex: 'vehicleNo'},
        {title: '车型', dataIndex: 'vehicleType'},
        {title: '载重（kg）', dataIndex: 'loadWeight', align: 'right' as const},
        {title: '容积（m³）', dataIndex: 'loadVolume', align: 'right' as const},
      ]),
  {title: '状态', dataIndex: 'status', align: 'center'},
  {title: '备注', dataIndex: 'remark'},
  {title: '操作', dataIndex: 'action', align: 'right', width: 90},
]);

async function load() {
  const id = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await (isDriver.value ? deliveryApi.queryDrivers(query) : deliveryApi.queryVehicles(query));
    if (id === generation) {
      rows.value = result.data.list;
      total.value = result.data.total;
    }
  } catch (e) {
    if (id === generation) error.value = deliveryError(e);
  } finally {
    if (id === generation) loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = undefined;
  query.status = undefined;
  search();
}

function open(row?: Driver | Vehicle) {
  form.value = row ? {...row} : {status: 'ENABLED'};
  originalStatus = row?.status ?? '';
  formError.value = '';
  visible.value = true;
}

function save() {
  formError.value = '';
  if (isDriver.value && (!form.value.driverCode?.trim() || !form.value.driverName?.trim() || !/^[0-9+() -]{5,32}$/.test(form.value.phone ?? ''))) {
    formError.value = '请填写司机编码、姓名及有效联系电话';
    return;
  }
  // 启用即要求绑定：与服务端 requireBindableEmployee 同一口径，提前挡掉必然失败的提交。
  if (isDriver.value && form.value.status === 'ENABLED' && form.value.employeeId == null) {
    formError.value = '启用司机必须绑定系统员工';
    return;
  }
  if (!isDriver.value && !form.value.vehicleNo?.trim()) {
    formError.value = '请填写车牌号';
    return;
  }
  if (originalStatus === 'ENABLED' && form.value.status === 'DISABLED')
    Modal.confirm({title: `停用该${label.value}？`, content: '停用后不能分配到新线路，历史计划保留快照。', onOk: submit});
  else submit();
}

async function submit() {
  saving.value = true;
  try {
    await (isDriver.value ? deliveryApi.saveDriver(form.value) : deliveryApi.saveVehicle(form.value));
    visible.value = false;
    message.success('保存成功');
    await load();
  } catch (e) {
    formError.value = deliveryError(e);
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
<style scoped>
.status-select {
  width: 140px;
}
</style>
