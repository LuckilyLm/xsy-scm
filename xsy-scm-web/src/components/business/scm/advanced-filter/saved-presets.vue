<template>
  <a-space wrap class="scm-saved-presets">
    <span>我的筛选方案</span>
    <a-select v-model:value="selectedId" style="width: 210px" allow-clear
              placeholder="选择已保存方案" :loading="loading"
              :options="options" @change="applySelection"/>
    <a-button size="small" :disabled="!canSave" @click="saveOpen = true">保存当前条件</a-button>
    <a-popconfirm title="确认删除这个筛选方案？" ok-text="删除" cancel-text="取消"
                  @confirm="removeSelected">
      <a-button size="small" danger :disabled="!selectedId || loading">删除方案</a-button>
    </a-popconfirm>
    <a-button size="small" :loading="loading" @click="reload">刷新</a-button>
    <a-typography-text type="secondary">只保存组合条件，不保存日期、数据或其他筛选条件</a-typography-text>
  </a-space>
  <a-modal :open="saveOpen" title="保存个人筛选方案" :confirm-loading="saving"
           @ok="saveCurrent" @cancel="saveOpen = false">
    <a-input v-model:value="name" placeholder="输入方案名称" :maxlength="50" show-count/>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {message} from 'ant-design-vue';
import {scmFilterPresetApi} from '/@/api/business/scm/filter-preset-api';
import type {ScmFilterPreset, ScmFilterPresetView} from '/@/api/business/scm/filter-preset-api';
import type {ScmAdvancedFilterCondition} from '/@/types/business/scm/advanced-filter';

const props = defineProps<{
  viewKey: ScmFilterPresetView;
  conditions: ScmAdvancedFilterCondition[];
}>();
const emit = defineEmits<{(e: 'apply', conditions: ScmAdvancedFilterCondition[]): void}>();
const presets = ref<ScmFilterPreset[]>([]);
const selectedId = ref<string>();
const loading = ref(false);
const saving = ref(false);
const saveOpen = ref(false);
const name = ref('');
const canSave = computed(() => props.conditions.length > 0
  && props.conditions.every(c => !!c.key && !!c.value?.trim()));
const options = computed(() => presets.value.map(p => ({
  label: p.available ? p.name : p.name + '（已失效，可删除）',
  value: String(p.id)
})));

async function reload(): Promise<void> {
  loading.value = true;
  try {
    presets.value = (await scmFilterPresetApi.list(props.viewKey)).data;
    if (selectedId.value && !presets.value.some(p => String(p.id) === selectedId.value)) {
      selectedId.value = undefined;
    }
  } catch {
    void message.error('读取个人筛选方案失败');
  } finally {
    loading.value = false;
  }
}
function applySelection(): void {
  const preset = presets.value.find(p => String(p.id) === selectedId.value);
  if (preset?.available) {
    emit('apply', preset.conditions.map(c => ({...c})));
  } else if (preset) {
    void message.warning('此方案的筛选字段已变更，无法应用，可删除后重新保存');
  }
}
async function saveCurrent(): Promise<void> {
  const title = name.value.trim();
  if (!canSave.value || !title) {
    void message.warning('请填写名称并补全组合筛选条件');
    return;
  }
  saving.value = true;
  try {
    const created = await scmFilterPresetApi.create(
      props.viewKey, title, props.conditions.map(c => ({...c}))
    );
    saveOpen.value = false;
    name.value = '';
    await reload();
    selectedId.value = String(created.data);
    void message.success('个人筛选方案已保存');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '保存失败，请检查名称是否重复'));
  } finally {
    saving.value = false;
  }
}
async function removeSelected(): Promise<void> {
  const preset = presets.value.find(p => String(p.id) === selectedId.value);
  if (!preset) return;
  loading.value = true;
  try {
    await scmFilterPresetApi.remove(props.viewKey, preset.id);
    selectedId.value = undefined;
    await reload();
    void message.success('筛选方案已删除');
  } catch {
    void message.error('删除筛选方案失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {void reload();});
</script>
<style scoped>
.scm-saved-presets {padding: 4px 0 12px}
</style>
