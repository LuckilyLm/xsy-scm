<template>
  <a-modal
    :open="visible"
    title="订单标签"
    :confirm-loading="saving"
    :ok-button-props="{disabled: tagId === undefined || loading || creating}"
    ok-text="保存标签"
    @ok="apply"
    @cancel="close"
  >
    <p class="xsy-margin-bottom10">对已选 {{ ids.length }} 张订单操作</p>
    <a-form layout="vertical" class="xsy-margin-top10">
      <a-form-item label="选择标签" required>
        <a-select
          v-model:value="tagId"
          :options="tagOptions"
          :loading="loading"
          placeholder="请选择已有标签"
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <p class="scm-note">每张订单最多一个标签。选择新标签会替换原标签，选择“不设置标签”可清空。</p>
    </a-form>
    <a-divider>创建新标签</a-divider>
    <a-space wrap>
      <a-input v-model:value="name" :maxlength="32" placeholder="输入标签名称" style="width: 170px" />
      <a-select v-model:value="color" :options="colorOptions" style="width: 125px" />
      <a-button :disabled="!name.trim()" :loading="creating" @click="create">创建标签</a-button>
    </a-space>
    <a-alert v-if="error" type="error" show-icon class="xsy-margin-top10" :message="error" />
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import {orderTagApi} from '/@/api/business/scm/order-tag-api';
import type {OrderTag, OrderTagColor} from '/@/api/business/scm/order-tag-api';
import type {Id} from '../order-types';

const emit = defineEmits<{(e: 'saved'): void}>();
const visible = ref(false);
const ids = ref<Id[]>([]);
const tags = ref<OrderTag[]>([]);
const tagId = ref<Id>();
const name = ref('');
const color = ref<OrderTagColor>('BLUE');
const saving = ref(false),
  creating = ref(false),
  loading = ref(false),
  error = ref('');
const colors: OrderTagColor[] = ['BLUE', 'GREEN', 'ORANGE', 'RED', 'PURPLE', 'GRAY'];
const colorOptions = colors.map((value) => ({
  value,
  label: {BLUE: '蓝色', GREEN: '绿色', ORANGE: '橙色', RED: '红色', PURPLE: '紫色', GRAY: '灰色'}[value],
}));
const tagOptions = computed(() => [{value: '', label: '不设置标签'}, ...tags.value.map((t) => ({value: t.tagId, label: t.name}))]);

async function fetchTags(): Promise<void> {
  loading.value = true;
  try {
    tags.value = (await orderTagApi.list({suppressGlobalErrorMessage: true})).data;
  } catch {
    error.value = '加载标签失败，请稍后再试';
  } finally {
    loading.value = false;
  }
}
function open(orderIds: Id[], currentTagId?: Id | null): void {
  const unique = [...new Set(orderIds.map(String))];
  if (unique.length < 1 || unique.length > 20 || unique.some((id) => !/^[1-9]\d*$/.test(id))) {
    void message.warning('请选择 1～20 张订单');
    return;
  }
  ids.value = unique;
  tagId.value = currentTagId === null ? '' : currentTagId;
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
    const created = await orderTagApi.create(trimmed, color.value, {suppressGlobalErrorMessage: true});
    await fetchTags();
    tagId.value = created.data;
    name.value = '';
    void message.success('标签创建成功，确认后即可打标');
  } catch (cause) {
    error.value = String((cause as Error)?.message || '创建标签失败或名称已存在');
  } finally {
    creating.value = false;
  }
}
async function apply(): Promise<void> {
  if (tagId.value === undefined || saving.value || creating.value || loading.value) return;
  saving.value = true;
  error.value = '';
  try {
    await orderTagApi.apply(tagId.value === '' ? null : tagId.value, ids.value, tagId.value === '' ? 'CLEAR' : 'SET', {
      suppressGlobalErrorMessage: true,
    });
    void message.success(tagId.value === '' ? '标签已清空' : '标签已设置');
    visible.value = false;
    emit('saved');
  } catch (cause) {
    error.value = String((cause as Error)?.message || '打标失败，所有订单均未修改');
  } finally {
    saving.value = false;
  }
}
defineExpose({open});
</script>
