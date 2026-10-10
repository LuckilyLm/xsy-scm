<template>
  <a-drawer v-model:open="visible" :title="routeId == null ? '新建配送线路' : '编辑配送线路'"
            :width="scmDrawerWidth('s')" :mask-closable="!saving">
    <a-alert v-if="error" type="error" :message="error" show-icon class="drawer-error"/>
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">基础信息</h3>
          </div>
          <a-form-item label="线路名称" name="routeName">
            <a-input v-model:value="form.routeName" :maxlength="100" placeholder="例如：南山 1 线"/>
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="配送日期" name="deliveryDate">
                <a-date-picker v-model:value="form.deliveryDate" value-format="YYYY-MM-DD"/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="计划发车时间">
                <a-date-picker v-model:value="departure" show-time value-format="YYYY-MM-DD HH:mm:ss"/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">运力</h3>
          </div>
          <a-form-item label="起点仓库" name="warehouseId">
            <a-select v-model:value="form.warehouseId" :options="warehouses.map((w) => ({ value: w.id, label: w.name }))"
                      placeholder="选择启用仓库"
            />
          </a-form-item>
          <!-- 仓库未定位的提示紧贴字段，不额外撑开表单节奏 -->
          <a-alert
              v-if="warehouse && !isLocated(warehouse)"
              class="field-warning"
              message="该仓库尚未定位。可先保存草稿，确认规划前需在仓库管理补齐定位，再编辑本线路刷新起点。"
              type="warning"
              show-icon
          />
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="司机">
                <a-select
                    v-model:value="form.driverId"
                    :options="drivers.map((d) => ({ value: d.id, label: `${d.driverName} · ${d.phone}` }))"
                    placeholder="可稍后分配"
                    allow-clear/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="车辆">
                <a-select
                    v-model:value="form.vehicleId"
                    :options="vehicles.map((v) => ({ value: v.id, label: v.vehicleNo }))"
                    placeholder="可稍后分配"
                    allow-clear/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">备注</h3>
          </div>
          <a-form-item label="备注">
            <a-textarea v-model:value="form.remark" :maxlength="500" :rows="3"/>
          </a-form-item>
        </section>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading || !optionsReady" @click="save">保存线路</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import dayjs from 'dayjs';
import {message} from 'ant-design-vue';
import {deliveryApi} from '/@/api/business/scm/delivery-api';
import {isLocated} from '/@/components/business/scm/map/types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import type {Warehouse} from '../../purchase/purchase-types';
import {deliveryError, type Id, type RouteForm, type Driver, type Vehicle, type DeliveryRoute} from '../delivery-types';

const emit = defineEmits<{ saved: [Id] }>();
const visible = ref(false),
    loading = ref(false),
    saving = ref(false),
    optionsReady = ref(false),
    error = ref('');
const routeId = ref<Id>();
const form = ref<RouteForm>({routeName: '', deliveryDate: dayjs().format('YYYY-MM-DD')});
const formRef = ref();

/** 逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  routeName: [{required: true, message: '请填写线路名称', trigger: 'blur'}],
  deliveryDate: [{required: true, message: '请选择配送日期', trigger: 'change'}],
  warehouseId: [{required: true, message: '请选择起点仓库', trigger: 'change'}],
};
const departure = ref<string>();
const warehouses = ref<Warehouse[]>([]),
    drivers = ref<Driver[]>([]),
    vehicles = ref<Vehicle[]>([]);
const warehouse = computed(() => warehouses.value.find((w) => String(w.id) === String(form.value.warehouseId)));

async function open(route?: DeliveryRoute) {
  routeId.value = route?.id;
  error.value = '';
  visible.value = true;
  loading.value = true;
  optionsReady.value = false;
  form.value = route
      ? {
        version: route.version,
        routeName: route.routeName,
        deliveryDate: route.deliveryDate,
        warehouseId: route.warehouseId,
        driverId: route.driverId,
        vehicleId: route.vehicleId,
        remark: route.remark,
      }
      : {routeName: '', deliveryDate: dayjs().format('YYYY-MM-DD')};
  departure.value = route?.plannedDepartureTime ? dayjs(route.plannedDepartureTime).format('YYYY-MM-DD HH:mm:ss') : undefined;
  try {
    // 选项加载失败由抽屉内 Alert 承担，不再让全局 toast 重复说一遍
    const [w, d, v] = await Promise.all([
      deliveryApi.warehouses({suppressGlobalErrorMessage: true}),
      deliveryApi.drivers({suppressGlobalErrorMessage: true}),
      deliveryApi.vehicles({suppressGlobalErrorMessage: true}),
    ]);
    warehouses.value = w.data;
    drivers.value = d.data;
    vehicles.value = v.data;
    optionsReady.value = true;
  } catch (e) {
    error.value = deliveryError(e);
  } finally {
    loading.value = false;
  }
}

async function save() {
  // 校验交给 antd 表单：缺哪个必填项就在哪个输入框下方提示
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  error.value = '';
  saving.value = true;
  const payload = {
    ...form.value,
    driverId: form.value.driverId ?? null,
    vehicleId: form.value.vehicleId ?? null,
    plannedDepartureTime: departure.value || null,
  };
  try {
    const id = routeId.value;
    const savedId = id == null ? (await deliveryApi.create(payload)).data : (await deliveryApi.update(id, payload), id);
    visible.value = false;
    message.success('线路已保存');
    emit('saved', savedId);
  } catch {
    // 保存失败只走全局 toast；抽屉里的 error 留给选项加载失败
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
/* 抽屉顶部的错误条与字段内联提示：都不参与表单纵向节奏 */
.drawer-error {
  margin-bottom: 12px;
}

.field-warning {
  margin: -12px 0 20px;
}
</style>
