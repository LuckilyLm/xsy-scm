<template>
  <span
    ref="element"
    class="scm-fit-number"
    :title="fullText"
    :aria-label="fullText"
    :tabindex="text !== String(value) ? 0 : undefined"
    :style="{fontSize: `${fontSize}px`, minHeight: `${size * 1.15}px`, justifyContent: align}"
  >
    <span v-if="prefix" class="scm-fit-affix">{{ prefix }}</span>
    <span class="scm-fit-digits">{{ text }}</span>
    <span v-if="unit" class="scm-fit-affix">{{ unit }}</span>
  </span>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {formatScreenCompact} from '../format';

const props = withDefaults(
  defineProps<{
    value: string | number;
    prefix?: string;
    unit?: string;
    size?: number;
    minSize?: number;
    align?: 'start' | 'center' | 'end';
  }>(),
  {prefix: '', unit: '', size: 14, minSize: 11, align: 'start'}
);

const element = ref<HTMLElement>();
const text = ref(String(props.value));
const fontSize = ref(props.size);
const fullText = computed(() => `${props.prefix}${props.value}${props.unit ? ` ${props.unit}` : ''}`);
let observer: ResizeObserver | undefined;
let frame = 0;
let active = true;

async function fit() {
  const el = element.value;
  if (!el?.clientWidth) return;
  const full = String(props.value);
  text.value = full;
  fontSize.value = props.size;
  // 按浏览器实际字形测量，等宽数字和字体回退不会导致预估偏差。
  await nextTick();
  if (!active || !el.clientWidth || full !== String(props.value)) return;
  const measure = () => Array.from(el.children).reduce((width, child) => width + (child as HTMLElement).offsetWidth, 0);
  const available = Math.max(1, el.clientWidth - (el.children.length - 1) * 3 - 2);
  let width = measure();
  if ((width * props.minSize) / props.size > available) {
    const compact = formatScreenCompact(full);
    if (compact !== full) {
      text.value = compact;
      await nextTick();
      if (!active || full !== String(props.value)) return;
      width = measure();
    }
  }
  fontSize.value = Math.min(props.size, Math.floor((props.size * available * 100) / Math.max(1, width)) / 100);
}

function scheduleFit() {
  if (!active || frame) return;
  frame = requestAnimationFrame(() => {
    frame = 0;
    fit();
  });
}

watch(() => [props.value, props.prefix, props.unit, props.size, props.minSize], scheduleFit, {flush: 'post'});
onMounted(() => {
  let width = -1;
  observer = new ResizeObserver(([entry]) => {
    if (Math.abs(entry.contentRect.width - width) < 0.1) return;
    width = entry.contentRect.width;
    scheduleFit();
  });
  if (element.value) observer.observe(element.value);
  scheduleFit();
  document.fonts?.ready.then(scheduleFit);
});
onBeforeUnmount(() => {
  active = false;
  observer?.disconnect();
  cancelAnimationFrame(frame);
});
</script>

<style lang="less" scoped>
@import '../styles/variables.less';
.scm-fit-number {
  display: flex;
  align-items: baseline;
  gap: 3px;
  width: 100%;
  min-width: 0;
  font-family: @font-num;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
  letter-spacing: normal;
  white-space: nowrap;
}
.scm-fit-digits {
  flex: 0 0 auto;
}
.scm-fit-affix {
  flex: 0 0 auto;
  font-size: 0.55em;
  font-weight: 500;
}
.scm-fit-number:focus-visible {
  outline: 1px solid @tech-cyan;
  outline-offset: 2px;
  border-radius: 3px;
}
</style>
