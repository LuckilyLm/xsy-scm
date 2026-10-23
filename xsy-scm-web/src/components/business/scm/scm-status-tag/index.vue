<template>
  <a-tag :color="normalizedColor" class="scm-status-tag">
    <slot>{{ label }}</slot>
  </a-tag>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {scmStatusColor} from '/@/theme/scm/scm-status';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {SCM_STATUS_TONE_COLOR} from '/@/theme/scm/scm-status';

/** 统一状态标签：接受 antd 色名或语义档位。 */
const props = defineProps<{color?: string | null; tone?: ScmStatusTone; label?: string}>();

const normalizedColor = computed(() => scmStatusColor(props.tone ? SCM_STATUS_TONE_COLOR[props.tone] : props.color));
</script>
