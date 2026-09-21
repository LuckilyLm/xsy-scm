<template>
  <div class="location-field">
    <a-space wrap>
      <a-tag :color="isLocated(value) ? 'green' : 'default'">{{ isLocated(value) ? '已定位' : '未定位' }}</a-tag>
      <a-button @click="openPicker">地图定位</a-button>
      <a-button v-if="value.longitude != null || value.latitude != null" @click="emit('change', emptyLocation())">
        清空定位
      </a-button>
    </a-space>
    <div class="location-inputs">
      <a-input-number
          :value="value.longitude"
          :min="-180"
          :max="180"
          :precision="8"
          string-mode
          aria-label="经度"
          placeholder="经度"
          @update:value="update('longitude', $event)"
      />
      <a-input-number
          :value="value.latitude"
          :min="-90"
          :max="90"
          :precision="8"
          string-mode
          aria-label="纬度"
          placeholder="纬度"
          @update:value="update('latitude', $event)"
      />
      <a-select
          :value="value.geomCrs ?? undefined"
          placeholder="坐标系"
          aria-label="坐标系"
          :options="crsOptions"
          @update:value="update('geomCrs', $event)"
      />
    </div>
    <div class="ant-form-item-extra">地址变更后请重新定位。经纬度与坐标系须成组保存。</div>
  </div>
  <a-modal v-model:open="visible" title="确认地址定位" :width="800" :destroy-on-close="true" @ok="confirm">
    <a-space direction="vertical" class="picker-content">
      <a-input-search v-model:value="searchAddress" placeholder="省市、街道及门牌号" enter-button="查找地址"
                      :loading="searching" @search="search"/>
      <a-alert v-if="error" type="warning" :message="error" show-icon/>
      <ScmMap v-if="visible" :points="[{ ...draft, label: '配送地址' }]" pickable @pick="draft = $event"/>
      <p>点击地图或拖动标记修正位置，确认后保存。{{
          isLocated(draft) ? `${draft.longitude}, ${draft.latitude} · ${draft.geomCrs}` : '尚未选点'
        }}</p>
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import ScmMap from './scm-map.vue';
import {geocode} from './map-provider';
import {emptyLocation, isLocated, type ScmLocation} from './types';

const props = defineProps<{ value: ScmLocation; address?: string | null }>();
const emit = defineEmits<{ change: [ScmLocation] }>();
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
.location-field {
  display: grid;
  gap: 8px;
}

.location-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr 150px;
  gap: 8px;
}

.location-inputs :deep(.ant-input-number) {
  width: 100%;
}

.picker-content {
  width: 100%;
}

@media (max-width: 600px) {
  .location-inputs {
    grid-template-columns: 1fr;
  }
}
</style>
