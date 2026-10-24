<template>
  <!-- 只剩一个普通动作时直接放在行内，别让用户点开只有一项的「更多」 -->
  <a-button v-if="inlineAction" type="link" size="small" :disabled="inlineAction.disabled" @click="emit('select', inlineAction.key)">
    {{ inlineAction.label }}
  </a-button>
  <a-dropdown v-else-if="visibleActions.length > 0" :trigger="['click']">
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

/**
 * 危险动作仍留在「更多」里：避免和普通动作同排常驻时被误点。
 * 其余唯一动作直接外露，减少一次点击。
 */
const inlineAction = computed(() => {
  const actions = visibleActions.value;
  return actions.length === 1 && !actions[0].danger ? actions[0] : undefined;
});

const onSelect: MenuProps['onClick'] = (info) => emit('select', String(info.key));
</script>
