<template>
  <div class="location-field">
    <div class="location-field__status">
      <!-- 状态用图标 + 文字，不再套一层标签边框：标签是分类语义，定位状态不是分类 -->
      <span class="location-status" :class="isLocated(value) ? 'location-status--located' : 'location-status--empty'">
        <EnvironmentOutlined aria-hidden="true" />
        {{ isLocated(value) ? '已定位' : '未定位' }}
      </span>
      <a-button type="link" size="small" class="location-action" @click="openPicker">
        <AimOutlined aria-hidden="true" />
        {{ isLocated(value) ? '重新定位' : '地图定位' }}
      </a-button>
      <a-button
        v-if="value.longitude != null || value.latitude != null"
        type="link"
        size="small"
        class="location-clear"
        @click="emit('change', emptyLocation())"
      >
        清空定位
      </a-button>
    </div>
    <div class="location-field__inputs">
      <label class="location-input">
        <span class="location-input__label">经度</span>
        <a-input-number
          :value="value.longitude"
          :min="-180"
          :max="180"
          :precision="8"
          string-mode
          aria-label="经度"
          placeholder="例如 121.47370100"
          @update:value="update('longitude', $event)"
        />
      </label>
      <label class="location-input">
        <span class="location-input__label">纬度</span>
        <a-input-number
          :value="value.latitude"
          :min="-90"
          :max="90"
          :precision="8"
          string-mode
          aria-label="纬度"
          placeholder="例如 31.23041600"
          @update:value="update('latitude', $event)"
        />
      </label>
      <label class="location-input location-input--wide">
        <span class="location-input__label">坐标系</span>
        <a-select
          :value="value.geomCrs ?? undefined"
          placeholder="选择经纬度所属坐标系"
          aria-label="坐标系"
          :options="crsOptions"
          @update:value="update('geomCrs', $event)"
        />
      </label>
    </div>
    <p class="location-field__hint">地址变更后请重新定位；经纬度与坐标系须成套保存。</p>
  </div>
  <a-modal v-model:open="visible" title="确认地址定位" :width="800" :destroy-on-close="true" @ok="confirm">
    <a-space direction="vertical" class="picker-content">
      <a-input-search v-model:value="searchAddress" placeholder="省市、街道及门牌号" enter-button="查找地址" :loading="searching" @search="search" />
      <a-alert v-if="error" type="warning" :message="error" show-icon />
      <ScmMap v-if="visible" :points="[{...draft, label: '配送地址'}]" pickable @pick="draft = $event" />
      <p>点击地图或拖动标记修正位置，确认后保存。{{ isLocated(draft) ? `${draft.longitude}, ${draft.latitude} · ${draft.geomCrs}` : '尚未选点' }}</p>
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {AimOutlined, EnvironmentOutlined} from '@ant-design/icons-vue';
import ScmMap from './scm-map.vue';
import {geocode} from './map-provider';
import {emptyLocation, isLocated, type ScmLocation} from './types';

const props = defineProps<{value: ScmLocation; address?: string | null}>();
const emit = defineEmits<{change: [ScmLocation]}>();
const visible = ref(false),
  searching = ref(false),
  error = ref('');
const draft = ref<ScmLocation>(emptyLocation());
const searchAddress = ref('');
const crsOptions = [
  {label: 'GCJ02（高德）', value: 'GCJ02'},
  {label: 'WGS84（GPS）', value: 'WGS84'},
];

function update(field: keyof ScmLocation, value: unknown) {
  emit('change', {
    longitude: props.value.longitude ?? null,
    latitude: props.value.latitude ?? null,
    geomCrs: props.value.geomCrs ?? null,
    [field]: value ?? null,
  });
}

function openPicker() {
  draft.value = {...props.value};
  searchAddress.value = props.address ?? '';
  error.value = '';
  visible.value = true;
}

async function search() {
  searching.value = true;
  error.value = '';
  try {
    draft.value = await geocode(searchAddress.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '地址查找失败';
  } finally {
    searching.value = false;
  }
}

function confirm() {
  if (!isLocated(draft.value)) {
    error.value = '请先选定准确位置';
    return;
  }
  emit('change', {longitude: draft.value.longitude, latitude: draft.value.latitude, geomCrs: draft.value.geomCrs});
  visible.value = false;
}
</script>
<style scoped>
/* 地图定位字段组：经纬度两列，坐标系独占一行，每个输入框带标签 */
.location-field {
  display: grid;
  gap: 10px;
}

.location-field__status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}

/* 定位状态是信息，不是标签：只保留图标与文字，靠颜色区分已定位/未定位 */
.location-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  line-height: 20px;

  :deep(.anticon) {
    font-size: 16px;
  }
}

.location-status--located {
  color: var(--scm-primary);
}

.location-status--empty {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

/* 动作收成文字链接，去掉按钮边框；锚点仍保留 24px 行高，键盘和指针都够点 */
.location-action,
.location-clear {
  height: 24px;
  padding: 0;
  margin-inline-start: 0;
}

.location-action :deep(.anticon) {
  margin-inline-end: 4px;
}

.location-clear {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));

  &:hover {
    color: var(--scm-error);
  }
}

.location-field__inputs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.location-input {
  display: block;
  min-width: 0;
}

.location-input--wide {
  grid-column: 1 / -1;
}

.location-input__label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.location-input :deep(.ant-input-number),
.location-input :deep(.ant-select) {
  width: 100%;
}

.location-field__hint {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.picker-content {
  width: 100%;
}

@media (max-width: 600px) {
  .location-field__inputs {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
