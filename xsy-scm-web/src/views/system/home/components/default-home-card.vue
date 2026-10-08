<template>
  <a-card class="home-card" :bordered="false">
    <template #title>
      <div class="home-card__title">
        <scm-icon v-if="iconName" :name="iconName" :size="28"/>
        <component :is="$antIcons[icon]" v-else-if="icon" class="home-card__icon" aria-hidden="true"/>
        <slot name="title"><span>{{ title }}</span></slot>
      </div>
    </template>
    <template v-if="extra || $slots.extra" #extra>
      <slot name="extra">
        <a-button type="link" size="small" @click="emit('extraClick')">{{ extra }}</a-button>
      </slot>
    </template>
    <slot/>
  </a-card>
</template>

<script setup lang="ts">
import ScmIcon from './scm-icon.vue';

defineProps<{icon?: string; iconName?: string; title?: string; extra?: string}>();
const emit = defineEmits<{extraClick: []}>();
</script>

<style lang="less" scoped>
.home-card {
  height: 100%;
  min-width: 0;
  border: 1px solid var(--scm-border);
  border-radius: 12px;
  color: var(--scm-text);
  background: var(--scm-bg-container);

  :deep(.ant-card-head) {
    min-height: 52px;
    padding: 0 20px;
    border-bottom: 1px solid var(--scm-border);
    color: var(--scm-text);
  }

  :deep(.ant-card-body) {
    padding: 20px;
  }
}

.home-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.home-card__icon {
  font-size: 18px;
  color: var(--scm-primary);
}

</style>
