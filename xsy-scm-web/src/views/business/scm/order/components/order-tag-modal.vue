<template>
  <a-modal :open="visible" title="订单标签" :confirm-loading="saving" :ok-button-props="{disabled: !tagId}"
           ok-text="应用标签" @ok="apply" @cancel="close">
    <a-alert type="info" show-icon :message="`对已选 ${ids.length} 张订单操作`"/>
    <a-form layout="vertical" class="smart-margin-top10">
      <a-form-item label="选择标签" required>
        <a-select v-model:value="tagId" :options="tagOptions" :loading="loading"
                  placeholder="请选择已有标签" show-search option-filter-prop="label"/>
      </a-form-item>
      <a-form-item label="操作">
        <a-radio-group v-model:value="action">
          <a-radio value="ADD">打标</a-radio>
          <a-radio value="REMOVE">取消标签</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
    <a-divider>创建新标签</a-divider>
    <a-space wrap>
      <a-input v-model:value="name" :maxlength="32" placeholder="输入标签名称" style="width: 170px"/>
      <a-select v-model:value="color" :options="colorOptions" style="width: 125px"/>
      <a-button :disabled="!name.trim()" :loading="creating" @click="create">创建标签</a-button>
    </a-space>
    <a-alert v-if="error" type="error" show-icon class="smart-margin-top10" :message="error"/>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import {orderTagApi} from '/@/api/business/scm/order-tag-api';
import type {OrderTag, OrderTagAction, OrderTagColor} from '/@/api/business/scm/order-tag-api';
import type {Id} from '../order-types';

const emit = defineEmits<{(e: 'saved'): void}>();
const visible = ref(false);
const ids = ref<Id[]>([]);
const tags = ref<OrderTag[]>([]);
const tagId = ref<Id>();
const action = ref<OrderTagAction>('ADD');
const name = ref('');
const color = ref<OrderTagColor>('BLUE');
const saving = ref(false), creating = ref(false), loading = ref(false), error = ref('');
const colors: OrderTagColor[] = ['BLUE','GREEN','ORANGE','RED','PURPLE','GRAY'];
const colorOptions = colors.map(value => ({
  value, label: {BLUE:'蓝色',GREEN:'绿色',ORANGE:'橙色',RED:'红色',PURPLE:'紫色',GRAY:'灰色'}[value]
}));
const tagOptions = computed(() => tags.value.map(t => ({
  value: t.tagId, label: t.name
})));

async function fetchTags(): Promise<void> {
  loading.value = true;
  try {
    tags.value = (await orderTagApi.list()).data;
  } catch {
    error.value = '加载标签失败，请稍后再试';
  } finally {loading.value = false;}
}
function open(orderIds: Id[]): void {
  const unique = [...new Set(orderIds.map(String))];
  if (unique.length < 1 || unique.length > 20 || unique.some(id => !/^[1-9]\d*$/.test(id))) {
    void message.warning('请选择 1～20 张订单');
    return;
  }
  ids.value = unique;
  tagId.value = undefined;
  action.value = 'ADD';
  name.value = '';
  error.value = '';
  visible.value = true;
  void fetchTags();
}
function close(): void {
  if (saving.value || creating.value) return;
  visible.value = false;
}
async function create(): Promise<void> {
  const trimmed = name.value.trim();
  if (!trimmed || creating.value || saving.value) return;
  creating.value = true;
  error.value = '';
  try {
    const created = await orderTagApi.create(trimmed, color.value);
    await fetchTags();
    tagId.value = created.data;
    name.value = '';
    void message.success('标签创建成功，确认后即可打标');
  } catch (cause) {
    error.value = String((cause as Error)?.message || '创建标签失败或名称已存在');
  } finally {creating.value = false;}
}
async function apply(): Promise<void> {
  if (!tagId.value || saving.value || creating.value) return;
  saving.value = true;
  error.value = '';
  try {
    await orderTagApi.apply(tagId.value, ids.value, action.value);
    void message.success(action.value === 'ADD' ? '标签已应用' : '标签已移除');
    visible.value = false;
    emit('saved');
  } catch (cause) {
    error.value = String((cause as Error)?.message || '打标失败，所有订单均未修改');
  } finally {saving.value = false;}
}
defineExpose({open});
</script>
