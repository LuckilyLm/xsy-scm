<template>
  <a-drawer v-model:open="visible" :title="routeId == null ? '新建配送线路' : '编辑配送线路'" :width="560" :mask-closable="!saving">
    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-spin :spinning="loading">
      <a-form layout="vertical">
        <a-form-item label="线路名称" required><a-input v-model:value="form.routeName" :maxlength="100" placeholder="例如：南山 1 线" /></a-form-item>
        <a-row :gutter="16"
          ><a-col :span="12"
            ><a-form-item label="配送日期" required><a-date-picker v-model:value="form.deliveryDate" value-format="YYYY-MM-DD" /></a-form-item
          ></a-col>
          <a-col :span="12"
            ><a-form-item label="计划发车时间"
              ><a-date-picker v-model:value="departure" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" /></a-form-item></a-col
        ></a-row>
        <a-form-item label="起点仓库" required
          ><a-select v-model:value="form.warehouseId" :options="warehouses.map((w) => ({ value: w.id, label: w.name }))" placeholder="选择启用仓库"
        /></a-form-item>
        <a-alert
          v-if="warehouse && !isLocated(warehouse)"
          message="该仓库尚未定位。可先保存草稿，确认规划前需在仓库管理补齐定位，再编辑本线路刷新起点。"
          type="warning"
          show-icon
        />
        <a-row :gutter="16"
          ><a-col :span="12"
            ><a-form-item label="司机"
              ><a-select
                v-model:value="form.driverId"
                :options="drivers.map((d) => ({ value: d.id, label: `${d.driverName} · ${d.phone}` }))"
                placeholder="可稍后分配"
                allow-clear /></a-form-item
          ></a-col>
          <a-col :span="12"
            ><a-form-item label="车辆"
              ><a-select
                v-model:value="form.vehicleId"
                :options="vehicles.map((v) => ({ value: v.id, label: v.vehicleNo }))"
                placeholder="可稍后分配"
                allow-clear /></a-form-item></a-col
        ></a-row>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" /></a-form-item>
      </a-form>
    </a-spin>
    <template #footer
      ><a-space
        ><a-button @click="visible = false">取消</a-button
        ><a-button type="primary" :loading="saving" :disabled="loading || !optionsReady" @click="save">保存线路</a-button></a-space
      ></template
    >
  </a-drawer>
</template>
<script setup lang="ts">
  import { computed, ref } from 'vue';
  import dayjs from 'dayjs';
  import { message } from 'ant-design-vue';
  import { deliveryApi } from '/@/api/business/scm/delivery-api';
  import { isLocated } from '/@/components/business/scm/map/types';
  import type { Warehouse } from '../../purchase/purchase-types';
  import { deliveryError, type Id, type RouteForm, type Driver, type Vehicle, type DeliveryRoute } from '../delivery-types';
  const emit = defineEmits<{ saved: [Id] }>();
  const visible = ref(false),
    loading = ref(false),
    saving = ref(false),
    optionsReady = ref(false),
    error = ref('');
  const routeId = ref<Id>();
  const form = ref<RouteForm>({ routeName: '', deliveryDate: dayjs().format('YYYY-MM-DD') });
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
      : { routeName: '', deliveryDate: dayjs().format('YYYY-MM-DD') };
    departure.value = route?.plannedDepartureTime ? dayjs(route.plannedDepartureTime).format('YYYY-MM-DDTHH:mm:ssZ') : undefined;
    try {
      const [w, d, v] = await Promise.all([deliveryApi.warehouses(), deliveryApi.drivers(), deliveryApi.vehicles()]);
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
    if (!form.value.routeName.trim() || !form.value.deliveryDate || !form.value.warehouseId) {
      error.value = '请填写线路名称、配送日期并选择仓库';
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
    } catch (e) {
      error.value = deliveryError(e);
    } finally {
      saving.value = false;
    }
  }
  defineExpose({ open });
</script>
