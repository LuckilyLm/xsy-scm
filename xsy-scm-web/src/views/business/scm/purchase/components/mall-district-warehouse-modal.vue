<template>
  <a-modal v-model:open="visible" title="商城区县配送仓配置" :footer="null" width="740px">
    <p class="scm-note">客户未绑定配送仓时，按收货地址区县匹配。</p>
    <a-form layout="vertical" class="district-form">
      <a-form-item label="配送区县" required>
        <AreaCascader type="province_city_district" v-model:value="area" placeholder="选择省 / 市 / 区县" @change="onAreaChange" />
      </a-form-item>
    </a-form>
    <a-alert v-if="warehousesError" type="error" show-icon :message="warehousesError" class="district-alert">
      <template #action><a-button size="small" @click="loadWarehouses">重试</a-button></template>
    </a-alert>
    <div v-if="districtCode">
      <a-spin :spinning="loading">
        <a-alert v-if="rulesError" type="error" show-icon :message="rulesError" class="district-alert">
          <template #action><a-button size="small" @click="load">重试</a-button></template>
        </a-alert>
        <div v-for="row in rules" :key="String(row.warehouseId)" class="rule-line">
          <span>{{ row.warehouseName }}（优先级 {{ row.priority }}）</span>
          <a-popconfirm title="删除这条区县配送规则？" @confirm="remove(row.warehouseId)">
            <a-button size="small" danger v-privilege="'scm:warehouse:update'">移除</a-button>
          </a-popconfirm>
        </div>
        <a-empty v-if="!rules.length && !loading && !rulesError" description="当前区县尚未配置配送仓" />
      </a-spin>
      <a-divider>新增或调整规则</a-divider>
      <a-row :gutter="12">
        <a-col :span="13">
          <a-select
            v-model:value="selectedWarehouseId"
            show-search
            option-filter-prop="label"
            style="width: 100%"
            placeholder="选择可用配送仓"
            aria-label="选择可用配送仓"
            :options="warehouseOptions"
            :disabled="!warehouseOptions.length"
          />
        </a-col>
        <a-col :span="6">
          <a-input-number v-model:value="priority" :min="0" :precision="0" style="width: 100%" placeholder="优先级" aria-label="配送优先级" />
        </a-col>
        <a-col :span="5">
          <a-button
            type="primary"
            :loading="saving"
            :disabled="!selectedWarehouseId || !warehouseOptions.length"
            v-privilege="'scm:warehouse:update'"
            @click="save"
            >保存规则</a-button
          >
        </a-col>
      </a-row>
      <p class="scm-note" style="margin-top: 12px">同一区县可配置多个仓库，但下单只选择一个配送仓，不合并其他仓库的库存。</p>
    </div>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf} from '../../common/scm-area';
import {mallWarehouseAdminApi, type MallWarehouseRule} from '/@/api/business/scm/mall-warehouse-api';

const visible = ref(false);
const area = ref<AreaNode[]>([]);
const rules = ref<MallWarehouseRule[]>([]);
const warehouses = ref<MallWarehouseRule[]>([]);
const selectedWarehouseId = ref<string | number>();
const priority = ref<number>(0);
const loading = ref(false);
const saving = ref(false);
const warehousesError = ref('');
const rulesError = ref('');
const districtCode = computed(() => {
  const value = areaColumnsOf(area.value).districtCode;
  return value == null ? null : Number(value);
});
const warehouseOptions = computed(() =>
  warehouses.value.map((row) => ({
    value: row.warehouseId,
    label: row.warehouseName,
  }))
);

async function open(): Promise<void> {
  visible.value = true;
  area.value = [];
  rules.value = [];
  selectedWarehouseId.value = undefined;
  priority.value = 0;
  warehousesError.value = '';
  rulesError.value = '';
  await loadWarehouses();
}

/** 每次打开都重新取一次可用仓，避免沿用上次打开时的旧选项。 */
async function loadWarehouses(): Promise<void> {
  warehousesError.value = '';
  try {
    warehouses.value = (await mallWarehouseAdminApi.options({suppressGlobalErrorMessage: true})).data || [];
  } catch {
    warehouses.value = [];
    warehousesError.value = '配送仓列表加载失败，请重试';
  }
}

async function load(): Promise<void> {
  rules.value = [];
  rulesError.value = '';
  if (!districtCode.value) return;
  loading.value = true;
  try {
    rules.value = (await mallWarehouseAdminApi.districtRules(districtCode.value, {suppressGlobalErrorMessage: true})).data || [];
  } catch {
    rulesError.value = '区县配送规则加载失败，请重试';
  } finally {
    loading.value = false;
  }
}
function onAreaChange(_value: unknown, nodes: AreaNode[]): void {
  area.value = nodes;
  selectedWarehouseId.value = undefined;
  load();
}
async function save(): Promise<void> {
  if (!districtCode.value || !selectedWarehouseId.value || priority.value < 0) {
    message.error('请选择区县、配送仓和非负优先级');
    return;
  }
  saving.value = true;
  try {
    await mallWarehouseAdminApi.saveDistrictRule({
      districtCode: districtCode.value,
      warehouseId: selectedWarehouseId.value,
      priority: priority.value,
    });
    message.success('区县配送规则已保存');
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}
async function remove(warehouseId: string | number): Promise<void> {
  if (!districtCode.value) return;
  try {
    await mallWarehouseAdminApi.removeDistrictRule(districtCode.value, warehouseId);
    message.success('配送规则已移除');
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '移除失败');
  }
}
defineExpose({open});
</script>
<style scoped>
.district-form {
  margin-top: 16px;
}
.district-alert {
  margin-bottom: 12px;
}
.rule-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
</style>
