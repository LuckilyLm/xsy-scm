<!-- 可选字段的导出入口共用勾选弹窗，默认全选服务端允许的字段。 -->
<template>
  <a-modal
    :open="visible"
    title="选择导出字段"
    :width="680"
    :mask-closable="false"
    :closable="!loading"
    :keyboard="!loading"
    :destroy-on-close="true"
    @cancel="hide"
  >
    <FormNote :lines="['默认导出全部字段；取消勾选可缩小范围，至少保留一个字段。']" />
    <a-checkbox-group v-model:value="selectedKeys" :disabled="loading" class="scm-export-grid">
      <a-checkbox v-for="item in options" :key="item.value" :value="item.value" class="scm-export-grid__cell">{{ item.label }}</a-checkbox>
    </a-checkbox-group>
    <template #footer>
      <div class="scm-export-footer">
        <a-space :size="8">
          <a-button :disabled="loading" @click="selectAll">全选</a-button>
          <a-button :disabled="loading || !selectedKeys.length" @click="clearAll">清空</a-button>
        </a-space>
        <a-space :size="8">
          <a-button :disabled="loading" @click="hide">取消</a-button>
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

/**
 * 打开弹窗，默认勾选当前服务端允许的全部字段；空目录不打开，导出中不替换回调。
 * @param columns 正式接口返回的字段名称与key，不传无权限字段。
 * @param onSubmit 用户确认后执行的导出，接收独立的字段key数组。
 */
function show(columns: ExportColumn[], onSubmit: (keys: string[]) => Promise<void>) {
  if (loading.value || !columns.length) return;
  options.value = columns;
  // 默认全选，减少逐项勾选。
  selectedKeys.value = columns.map((column) => column.value);
  submitHandler = onSubmit;
  visible.value = true;
}

function hide() {
  if (loading.value) return;
  visible.value = false;
}

function selectAll() {
  selectedKeys.value = options.value.map((column) => column.value);
}

function clearAll() {
  selectedKeys.value = [];
}

async function confirm() {
  if (loading.value || !submitHandler || !selectedKeys.value.length) {
    return;
  }
  loading.value = true;
  try {
    await submitHandler(selectedKeys.value.slice());
    visible.value = false;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
  width: 100%;
  max-height: min(420px, 55vh);
  overflow-y: auto;
  padding: 8px 0;
}

.scm-export-grid__cell {
  display: flex;
  align-items: flex-start;
  min-width: 0;
  margin-inline-start: 0;
  overflow-wrap: anywhere;
}

.scm-export-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 575px) {
  .scm-export-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
