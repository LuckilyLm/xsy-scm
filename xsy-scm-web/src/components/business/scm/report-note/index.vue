<!-- 报表说明：按需展开 Popover 或 Drawer，不占首屏。Popover 最多 3 条，每条不超过 40 字。 -->
<template>
  <a-popover v-if="mode === 'popover'" trigger="click" placement="bottomRight" :title="title">
    <template #content>
      <ul class="report-note__list">
        <li v-for="(point, index) in points" :key="index">{{ point }}</li>
      </ul>
    </template>
    <a-button v-if="variant === 'text'" type="link" size="small" class="report-note__trigger">{{ title }}</a-button>
    <InfoCircleOutlined v-else class="report-note__icon" :aria-label="title" />
  </a-popover>
  <template v-else>
    <a-button v-if="variant === 'text'" type="link" size="small" class="report-note__trigger" @click="open = true">
      {{ title }}
    </a-button>
    <InfoCircleOutlined v-else class="report-note__icon" :aria-label="title" @click="open = true" />
    <a-drawer v-model:open="open" :title="title" :width="scmDrawerWidth('s')">
      <section v-for="section in sections" :key="section.label" class="report-note__section">
        <h4 class="report-note__section-title">{{ section.label }}</h4>
        <ul class="report-note__list">
          <li v-for="(item, index) in section.items" :key="index">{{ item }}</li>
        </ul>
      </section>
    </a-drawer>
  </template>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {InfoCircleOutlined} from '@ant-design/icons-vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

export interface ReportNoteSection {
  label: string;
  items: string[];
}

const props = withDefaults(
  defineProps<{
    /** 触发文案，默认「统计说明」。 */
    title?: string;
    /** Popover 要点，最多 3 条、每条 ≤ 40 字。 */
    points?: string[];
    /** 分组说明；提供时自动改用 Drawer。 */
    sections?: ReportNoteSection[];
    /** text 渲染为文字按钮，icon 渲染为 ⓘ。 */
    variant?: 'text' | 'icon';
  }>(),
  {title: '统计说明', variant: 'text'}
);

const mode = computed(() => (props.sections?.length ? 'drawer' : 'popover'));
const open = ref(false);

if (import.meta.env.DEV) {
  if (props.points && props.points.length > 3) {
    console.warn(`[report-note] Popover 最多 3 条，当前 ${props.points.length} 条：${props.title}`);
  }
  props.points?.forEach((point) => {
    if (point.length > 40) console.warn(`[report-note] 每条 ≤ 40 字，当前 ${point.length} 字：${point}`);
  });
}
</script>

<style scoped lang="less">
.report-note__trigger {
  padding: 0 4px;
  height: auto;
}

.report-note__icon {
  color: var(--scm-text-secondary);
  cursor: pointer;
}

.report-note__list {
  margin: 0;
  padding-left: 18px;
  max-width: 360px;
  color: var(--scm-text);
}

.report-note__section + .report-note__section {
  margin-top: 16px;
}

.report-note__section-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 500;
}
</style>
