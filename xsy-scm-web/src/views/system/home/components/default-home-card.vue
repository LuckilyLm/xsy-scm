<template>
  <a-card class="home-card" :bordered="false">
    <template #title>
      <div class="home-card__title">
        <span v-if="iconName" class="home-card__title-visual">
          <scm-icon :name="iconName" :size="20" />
        </span>
        <component v-else-if="icon" :is="($antIcons as Record<string, Component>)[icon]" class="home-card__icon" aria-hidden="true" />
        <slot name="title"
          ><span class="home-card__title-text">{{ title }}</span></slot
        >
      </div>
    </template>
    <template v-if="extra || $slots.extra" #extra>
      <slot name="extra">
        <a-button type="link" size="small" @click="emit('extraClick')">{{ extra }}</a-button>
      </slot>
    </template>
    <slot />
  </a-card>
</template>

<script setup lang="ts">
import type {Component} from 'vue';
import ScmIcon from './scm-icon.vue';

defineProps<{icon?: string; iconName?: string; title?: string; extra?: string}>();
const emit = defineEmits<{extraClick: []}>();
</script>

<style lang="less" scoped>
.home-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  border: 1px solid var(--scm-border);
  border-radius: var(--home-card-radius, 14px);
  color: var(--scm-text);
  background: var(--scm-bg-container);
  box-shadow: var(--home-card-shadow, 0 3px 12px rgba(15, 44, 32, 0.05));

  :deep(.ant-card-head) {
    flex: 0 0 auto;
    min-height: 56px;
    padding: 0 20px;
    border-bottom: 1px solid var(--scm-border);
    border-radius: var(--home-card-radius, 14px) var(--home-card-radius, 14px) 0 0;
    color: var(--scm-text);
    background: linear-gradient(110deg, color-mix(in srgb, var(--scm-primary-bg) 20%, var(--scm-bg-container)), var(--scm-bg-container) 65%);
  }

  :deep(.ant-card-head-title) {
    min-width: 0;
    padding: 12px 0;
    white-space: normal;
  }

  :deep(.ant-card-extra) {
    flex-shrink: 0;
    margin-inline-start: 12px;
    padding: 12px 0;
  }

  :deep(.ant-card-extra .ant-btn) {
    font-size: 13px;
  }

  :deep(.ant-card-body) {
    flex: 1;
    min-width: 0;
    min-height: 0;
    padding: 20px;
  }

  :deep(.ant-empty:not(.home-region-error)) {
    margin: 0;
    padding: 16px 0;
  }

  :deep(.ant-empty:not(.home-region-error) .ant-empty-image) {
    height: 72px;
    margin-bottom: 12px;
    opacity: 0.85;
  }

  :deep(.ant-empty-description) {
    color: var(--scm-text-secondary);
    font-size: 13px;
    line-height: 1.6;
    overflow-wrap: anywhere;
  }
}

.home-card__title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

.home-card__title-visual {
  display: inline-flex;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid color-mix(in srgb, var(--scm-primary) 10%, transparent);
  border-radius: 9px;
  background: color-mix(in srgb, var(--scm-primary-bg) 68%, var(--scm-bg-container));
  color: var(--scm-primary);
}

.home-card__title-text {
  min-width: 0;
  letter-spacing: 0.2px;
  overflow-wrap: anywhere;
}

.home-card__icon {
  font-size: 18px;
  color: var(--scm-primary);
}

@media (max-width: 575px) {
  .home-card {
    :deep(.ant-card-head) {
      padding: 0 16px;
    }

    :deep(.ant-card-body) {
      padding: 16px;
    }
  }
}
</style>
