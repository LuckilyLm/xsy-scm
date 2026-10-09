<template>
  <a-modal
      :open="open"
      :title="title"
      :confirm-loading="busy"
      :ok-type="mode === 'cancel' ? 'danger' : 'primary'"
      :ok-text="mode === 'assign' ? '确认指派' : '确认'"
      @ok="emit('submit')"
      @cancel="emit('update:open', false)"
  >
    <a-alert v-if="tip" type="warning" show-icon :message="tip"/>
    <a-alert v-if="error" type="error" show-icon :message="error"/>
    <a-form layout="vertical">
      <a-form-item v-if="mode === 'assign'" label="受指派人" required>
        <EmployeeSelect
            :value="assignee"
            placeholder="选择分拣员"
            @update:value="emit('update:assignee', $event)"
        />
      </a-form-item>
      <a-form-item v-if="mode !== 'assign'" label="原因" required>
        <a-textarea
            :value="reason"
            :rows="3"
            :maxlength="500"
            show-count
            :placeholder="reasonHint"
            @update:value="emit('update:reason', $event)"
        />
      </a-form-item>
      <a-form-item v-if="mode === 'assign'" label="原因">
        <a-input
            :value="reason"
            :maxlength="500"
            placeholder="可选，例如：原分拣员请假"
            @update:value="emit('update:reason', $event)"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';

type SortingTaskActionMode = 'assign' | 'cancel' | 'reopen';

const props = defineProps<{
  open: boolean;
  mode: SortingTaskActionMode;
  busy: boolean;
  error: string;
  assignee?: number;
  reason: string;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  'update:assignee': [value: number | undefined];
  'update:reason': [value: string];
  submit: [];
}>();

const title = computed(() => ({
  assign: '指派 / 改派',
  cancel: '取消分拣任务',
  reopen: '重开分拣任务',
}[props.mode]));

// 只有「取消」会不可逆地释放占用位，需要常驻告知；改派与重开是正常操作，不再解释。
const tip = computed(() => props.mode === 'cancel'
    ? '取消会释放本任务全部明细的占用位，被释放的订单行才能重新进入新任务。此操作不可撤销。'
    : '');

const reasonHint = computed(() =>
    props.mode === 'cancel' ? '例如：订单行下错、客户临时取消' : '例如：完成后发现一行称重有误'
);
</script>
