<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="emit('search')">
    <a-form-item label="关键词" class="smart-query-form-item">
      <a-input
          :value="keyword"
          placeholder="任务号 / 仓库 / 分拣员 / 订单号 / 商品 / 客户"
          allow-clear
          :maxlength="100"
          style="width: 260px"
          @update:value="emit('update:keyword', $event)"
          @pressEnter="emit('search')"
      />
    </a-form-item>
    <a-form-item label="状态" class="smart-query-form-item">
      <a-select
          :value="status"
          :options="statusOptions"
          placeholder="全部"
          allow-clear
          style="width: 130px"
          @update:value="emit('update:status', $event)"
      />
    </a-form-item>
    <a-form-item label="仓库" class="smart-query-form-item">
      <WarehouseSelect
          :value="warehouseId"
          placeholder="全部仓库"
          width="200px"
          @update:value="emit('update:warehouseId', $event)"
      />
    </a-form-item>
    <!-- 未指派队列与按人筛选只对持指派权（即跨指派人可见）的人有意义。 -->
    <template v-if="isQueueManager">
      <a-form-item label="只看未指派" class="smart-query-form-item">
        <a-switch :checked="unassignedOnly" @update:checked="emit('update:unassignedOnly', $event)"/>
      </a-form-item>
      <a-form-item label="受指派人" class="smart-query-form-item">
        <EmployeeSelect
            :value="assigneeId"
            placeholder="全部"
            width="180px"
            @update:value="emit('update:assigneeId', $event)"
        />
      </a-form-item>
    </template>
    <!-- 三个维度按建单时冻结的快照筛选，不随主档或配送线路变化。 -->
    <a-form-item label="送货时间" class="smart-query-form-item">
      <a-range-picker
          v-model:value="deliveryRangeModel"
          show-time
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          style="width: 340px"
      />
    </a-form-item>
    <a-form-item label="预配送波次" class="smart-query-form-item">
      <a-input
          :value="deliveryWave"
          placeholder="波次"
          allow-clear
          style="width: 140px"
          @update:value="emit('update:deliveryWave', $event)"
          @pressEnter="emit('search')"
      />
    </a-form-item>
    <a-form-item label="供应商" class="smart-query-form-item">
      <SupplierSelect
          :value="supplierId"
          width="200px"
          placeholder="全部"
          @update:value="emit('update:supplierId', $event)"
      />
    </a-form-item>
    <a-form-item class="smart-query-form-item">
      <a-button-group>
        <a-button type="primary" v-privilege="'scm:sorting:task:query'" @click="emit('search')">查询</a-button>
        <a-button @click="emit('reset')">重置</a-button>
      </a-button-group>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {SCM_SORTING_TASK_STATUS_ENUM} from '/@/constants/business/scm/sorting-const';
import type {Id, SortingTaskStatus} from '../sorting-types';

const props = defineProps<{
  keyword?: string;
  status?: SortingTaskStatus;
  warehouseId?: Id;
  unassignedOnly?: boolean;
  assigneeId?: number;
  deliveryRange?: [string, string];
  deliveryWave?: string;
  supplierId?: Id;
  isQueueManager: boolean;
}>();

const emit = defineEmits<{
  'update:keyword': [value: string | undefined];
  'update:status': [value: SortingTaskStatus | undefined];
  'update:warehouseId': [value: Id | undefined];
  'update:unassignedOnly': [value: boolean];
  'update:assigneeId': [value: number | undefined];
  'update:deliveryRange': [value: [string, string] | undefined];
  'update:deliveryWave': [value: string | undefined];
  'update:supplierId': [value: Id | undefined];
  search: [];
  reset: [];
}>();

const deliveryRangeModel = computed({
  get: () => props.deliveryRange,
  set: (value: [string, string] | undefined) => emit('update:deliveryRange', value),
});

const statusOptions = Object.values(SCM_SORTING_TASK_STATUS_ENUM).map((item) => ({
  value: item.value,
  label: item.desc,
}));
</script>
