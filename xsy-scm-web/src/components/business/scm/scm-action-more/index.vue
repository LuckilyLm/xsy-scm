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

/** 操作列的「更多」菜单：收纳低频与危险动作，二次确认由调用方处理。 */
const props = withDefaults(defineProps<{actions: ScmActionItem[]; label?: string}>(), {
  label: '更多',
});
const emit = defineEmits<{select: [key: string]}>();

const visibleActions = computed(() => props.actions.filter((action) => !action.hidden));

const onSelect: MenuProps['onClick'] = (info) => emit('select', String(info.key));
</script>
