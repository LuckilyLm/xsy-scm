<!-- 商品导出字段选择：点「导出」时先选字段，默认全选。 -->
<template>
  <a-modal :open="visible" title="选择导出字段" :width="680" :mask-closable="false" :destroy-on-close="true" @cancel="hide">
    <FormNote :lines="['默认导出全部字段；取消勾选可缩小范围，至少保留一个字段。']" />
    <div class="scm-export-grid">
      <label v-for="item in options" :key="item.value" class="scm-export-grid__cell">
        <a-checkbox :checked="selectedKeys.includes(item.value)" @change="toggle(item.value)" />
        <span class="scm-export-grid__label" :title="item.label">{{ item.label }}</span>
      </label>
    </div>
    <template #footer>
      <div class="scm-export-footer">
        <a-space :size="8">
          <a-button @click="selectAll">全选</a-button>
          <a-button :disabled="!selectedKeys.length" @click="clearAll">清空</a-button>
        </a-space>
        <a-space :size="8">
          <a-button @click="hide">取消</a-button>
          <a-button type="primary" :loading="loading" :disabled="!selectedKeys.length" @click="confirm">导出</a-button>
        </a-space>
      </div>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import FormNote from '/@/components/framework/form-note/index.vue';

type ExportColumn = {label: string; value: string};

defineExpose({show});

const visible = ref(false);
const loading = ref(false);
const options = ref<ExportColumn[]>([]);
const selectedKeys = ref<string[]>([]);

let submitHandler: ((keys: string[]) => Promise<void>) | null = null;

/** 打开弹窗；字段和导出回调由调用方每次传入。 */
function show(columns: ExportColumn[], onSubmit: (keys: string[]) => Promise<void>) {
  options.value = columns;
  // 默认全选，减少逐项勾选。
  selectedKeys.value = columns.map((column) => column.value);
  submitHandler = onSubmit;
  visible.value = true;
}

function hide() {
  visible.value = false;
}

function toggle(key: string) {
  const index = selectedKeys.value.indexOf(key);
  if (index > -1) {
    selectedKeys.value.splice(index, 1);
  } else {
    selectedKeys.value.push(key);
  }
}

function selectAll() {
  selectedKeys.value = options.value.map((column) => column.value);
}

function clearAll() {
  selectedKeys.value = [];
}

async function confirm() {
  if (!submitHandler || !selectedKeys.value.length) {
    return;
  }
  loading.value = true;
  try {
    await submitHandler(selectedKeys.value.slice());
    hide();
  } catch {
    // 导出失败保持弹窗，错误由请求层提示。
  } finally {
    loading.value = false;
  }
}
</script>
<style scoped lang="less">
.scm-export-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px 12px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.scm-export-grid__cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 7px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f0f9eb;
  }
}

.scm-export-grid__label {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.88);
}

.scm-export-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
