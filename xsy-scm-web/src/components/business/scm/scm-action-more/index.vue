<template>
  <a-dropdown v-if="visibleActions.length > 0" :trigger="['click']">
    <a-button type="link" size="small" :aria-label="label">
      {{ label }}
      <DownOutlined />
    </a-button>
    <template #overlay>
      <a-menu @click="onSelect">
        <a-menu-item v-for="action in visibleActions" :key="action.key" :disabled="action.disabled" :danger="action.danger">
          {{ action.label }}
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {DownOutlined} from '@ant-design/icons-vue';
import type {MenuProps} from 'ant-design-vue';
import type {ScmActionItem} from './action-item';

/**
 * 列表操作列的「更多」菜单。
 *
 * 操作列超过 3 个动作时，低频与危险动作收进这里，把常驻宽度留给业务字段。
 * 危险动作的二次确认由调用方处理，本组件只负责收纳与派发。
 *
 * `label` 默认「更多」；当一组动作<b>完全平级</b>、收纳不是按频率而是按「同类归组」时，
 * 可以换成更准确的说法（例如报表下钻的「下钻」），避免「更多」暗示存在主次。
 */
const props = withDefaults(defineProps<{ actions: ScmActionItem[]; label?: string }>(), {
  label: '更多',
});
const emit = defineEmits<{ select: [key: string] }>();

const visibleActions = computed(() => props.actions.filter((action) => !action.hidden));

const onSelect: MenuProps['onClick'] = (info) => emit('select', String(info.key));
</script>
