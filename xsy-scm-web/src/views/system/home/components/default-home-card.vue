<template>
  <a-card class="home-card" :bordered="false">
    <template #title>
      <div class="home-card__title">
        <span v-if="iconName" class="home-card__title-visual">
          <scm-icon :name="iconName" :size="20"/>
        </span>
        <component v-else-if="icon" :is="$antIcons[icon]" class="home-card__icon" aria-hidden="true"/>
        <slot name="title"><span class="home-card__title-text">{{ title }}</span></slot>
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
  border-radius: 14px;
  color: var(--scm-text);
  background: var(--scm-bg-container);
  box-shadow: 0 1px 2px rgba(15, 44, 32, 0.03);

  :deep(.ant-card-head) {
    min-height: 56px;
    padding: 0 20px;
    border-bottom: 1px solid var(--scm-border);
    color: var(--scm-text);
  }

  :deep(.ant-card-head-title) {
    padding: 14px 0;
  }

  :deep(.ant-card-body) {
    padding: 20px;
  }
}

.home-card__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}

.home-card__title-visual {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--scm-fill);
  color: var(--scm-primary);
}

.home-card__title-text {
  letter-spacing: 0.2px;
}

.home-card__icon {
  font-size: 18px;
  color: var(--scm-primary);
}
</style>
