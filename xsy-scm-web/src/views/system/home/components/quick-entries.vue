<template>
  <nav v-if="available.length" class="home-quick-entries" aria-label="业务快捷入口">
    <router-link v-for="entry in entries" :key="entry.id" :to="entry.path" class="home-quick-entries__link">
      {{ entry.title }}
    </router-link>
    <a-popover title="快捷入口" trigger="click" placement="bottomRight">
      <template #content>
        <div class="home-quick-entries__choices">
          <a-checkbox
            v-for="entry in available" :key="entry.id" :checked="selected.includes(entry.id)"
            @change="toggle(entry.id, $event.target.checked)"
          >
            {{ entry.title }}
          </a-checkbox>
        </div>
      </template>
      <a-button size="small" type="text" aria-label="设置快捷入口">
        <template #icon><setting-outlined/></template>
        设置
      </a-button>
    </a-popover>
  </nav>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import {SettingOutlined} from '@ant-design/icons-vue';
import localKey from '/@/constants/local-storage-key-const';
import {localRead, localSave} from '/@/utils/local-util';
import {hasPermission} from '/@/views/business/scm/common/scm-permission';
import {HOME_QUICK_ENTRIES, readQuickEntryIds} from './quick-entries';

function loadSelection(): string[] {
  try {
    return readQuickEntryIds(localRead(localKey.HOME_QUICK_ENTRY));
  } catch {
    return HOME_QUICK_ENTRIES.map((entry) => entry.id);
  }
}

const selected = ref(loadSelection());
const available = computed(() => HOME_QUICK_ENTRIES.filter((entry) => entry.permissions.every(hasPermission)));
const entries = computed(() => available.value.filter((entry) => selected.value.includes(entry.id)));

function toggle(id: string, checked: boolean) {
  const next = checked ? [...new Set([...selected.value, id])] : selected.value.filter((item) => item !== id);
  selected.value = next;
  try {
    localSave(localKey.HOME_QUICK_ENTRY, JSON.stringify({version: 1, ids: next}));
  } catch {
    message.warning('快捷入口已调整，但无法保存到此浏览器');
  }
}
</script>

<style lang="less" scoped>
.home-quick-entries {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px 12px;
  min-width: 0;
}

.home-quick-entries__link {
  padding: 6px 0;
  color: var(--scm-text-secondary);
  font-size: 13px;

  &:hover {
    color: var(--scm-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: 2px;
  }
}

.home-quick-entries__choices {
  display: flex;
  flex-direction: column;
  gap: 10px;

  :deep(.ant-checkbox-wrapper) {
    margin-inline-start: 0;
  }
}

@media (max-width: 767px) {
  .home-quick-entries {
    justify-content: flex-start;
  }
}
</style>
