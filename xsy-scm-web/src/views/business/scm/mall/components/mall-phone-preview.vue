<template>
  <div class="phone-preview" :style="{background: palette.light}">
    <div class="phone-preview__statusbar" :style="{background: palette.dark}">
      <span>9:41</span>
      <span>●●●</span>
    </div>
    <div class="phone-preview__header" :style="{background: palette.primary}">
      <strong>商城</strong>
      <span class="phone-preview__search" :style="{color: palette.ink}">搜索商品</span>
    </div>
    <div class="phone-preview__body">
      <template v-for="block in visibleBlocks" :key="block.key">
        <div v-if="block.type === 'BANNER'" class="phone-preview__banner">
          <img v-if="imageUrls[block.imageFileKey || '']" :src="imageUrls[block.imageFileKey || '']" :alt="block.title || '轮播图'" />
          <div v-else class="phone-preview__banner-fallback" :style="{background: palette.primary}">
            <strong>{{ block.title || '轮播图' }}</strong>
            <span v-if="block.subtitle">{{ block.subtitle }}</span>
          </div>
        </div>
        <div v-else-if="block.type === 'ANNOUNCEMENT'" class="phone-preview__notice" :style="{background: palette.light, color: palette.ink}">
          <span class="phone-preview__notice-tag" :style="{background: palette.primary}">公告</span>
          <span>{{ block.text || '商城公告' }}</span>
        </div>
        <div v-else-if="block.type === 'SHORTCUT'" class="phone-preview__shortcuts">
          <div v-for="index in Math.min(4, Math.max(1, (block.title || '').split('/').length))" :key="index" class="phone-preview__shortcut">
            <span class="phone-preview__shortcut-icon" :style="{background: palette.light}"></span>
            <span :style="{color: palette.ink}">{{ shortcutLabel(block.title, index) }}</span>
          </div>
        </div>
        <div v-else-if="block.type === 'RECOMMENDATION'" class="phone-preview__recommend">
          <strong :style="{color: palette.ink}">{{ block.title || '推荐商品' }}</strong>
          <div class="phone-preview__products">
            <div v-for="index in 4" :key="index" class="phone-preview__product">
              <span class="phone-preview__product-image"></span>
              <span class="phone-preview__product-name">商品 {{ index }}</span>
            </div>
          </div>
        </div>
        <div v-else-if="block.type === 'ACTIVITY_TOPIC'" class="phone-preview__topic" :style="{background: palette.light}">
          <strong :style="{color: palette.ink}">{{ block.title || '活动专题' }}</strong>
          <span v-if="block.subtitle" :style="{color: palette.disabled}">{{ block.subtitle }}</span>
        </div>
      </template>
      <div v-if="!visibleBlocks.length" class="phone-preview__empty">还没有启用的首页组件</div>
    </div>
    <div class="phone-preview__tabbar" :style="{borderColor: palette.light}">
      <span :style="{color: palette.primary}">首页</span>
      <span :style="{color: palette.disabled}">分类</span>
      <span :style="{color: palette.disabled}">购物车</span>
      <span :style="{color: palette.disabled}">我的</span>
    </div>
    <div v-if="showPopup" class="phone-preview__popup" role="dialog" aria-label="首页弹窗预览">
      <button type="button" class="phone-preview__popup-close" aria-label="关闭弹窗预览" @click="popupDismissed = true">×</button>
      <img v-if="popup?.items[0]?.imageUrl" :src="popup.items[0].imageUrl" alt="首页弹窗" />
      <div v-else class="phone-preview__popup-placeholder">弹窗图片</div>
      <span v-if="(popup?.items.length || 0) > 1" class="phone-preview__popup-dots">{{ popup?.items.length }} 张轮播</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {fileApi} from '/@/api/support/file-api';
import {mallHomeApi, type HomeBlock, type HomePopup} from '/@/api/business/scm/mall-home-api';
import {mallThemeBaseColor, mallThemePalette} from '/@/utils/mall-theme';

const props = defineProps<{
  themeCode?: string | null;
  themeColor?: string | null;
  blocks: HomeBlock[];
}>();

const popup = ref<HomePopup>();
const popupDismissed = ref(false);
const imageUrls = ref<Record<string, string>>({});
const palette = computed(() => mallThemePalette(mallThemeBaseColor(props.themeCode, props.themeColor)));
const visibleBlocks = computed(() => [...props.blocks].filter((block) => block.enabled).sort((left, right) => left.sortOrder - right.sortOrder));
const showPopup = computed(() => Boolean(popup.value?.enabled && popup.value.items.length && !popupDismissed.value));

function shortcutLabel(title: string | null | undefined, index: number): string {
  return title?.split('/')[index - 1]?.trim() || `入口${index}`;
}

let resolveId = 0;
async function resolveImages(): Promise<void> {
  const request = ++resolveId;
  const keys = visibleBlocks.value.map((block) => block.imageFileKey).filter((key): key is string => Boolean(key));
  if (!keys.length) {
    imageUrls.value = {};
    return;
  }
  try {
    const urls = (await fileApi.getUrl([...new Set(keys)].join(','))).data.split(',').filter(Boolean);
    if (request === resolveId) imageUrls.value = Object.fromEntries([...new Set(keys)].map((key, index) => [key, urls[index] || '']));
  } catch {
    if (request === resolveId) imageUrls.value = {};
  }
}

watch(visibleBlocks, resolveImages, {deep: true});

async function refreshPopup(): Promise<void> {
  try {
    popup.value = (await mallHomeApi.popup()).data;
    popupDismissed.value = false;
  } catch {
    popup.value = undefined;
  }
}

onMounted(async () => {
  await resolveImages();
  await refreshPopup();
});

defineExpose({refreshPopup});
</script>

<style scoped>
.phone-preview {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 300px;
  height: 600px;
  margin: 0 auto;
  overflow: hidden;
  border: 8px solid #1f2329;
  border-radius: 28px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.22);
  font-size: 11px;
}

.phone-preview__statusbar {
  display: flex;
  justify-content: space-between;
  padding: 4px 14px;
  color: #fff;
  font-size: 10px;
}

.phone-preview__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  color: #fff;
}

.phone-preview__header strong {
  font-size: 14px;
}

.phone-preview__search {
  flex: 1;
  padding: 5px 10px;
  border-radius: 999px;
  background: #fff;
  font-size: 10px;
  line-height: 1;
}

.phone-preview__body {
  flex: 1;
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 10px;
  overflow-y: auto;
}

.phone-preview__banner {
  overflow: hidden;
  border-radius: 10px;
}

.phone-preview__banner img {
  display: block;
  width: 100%;
  height: 120px;
  object-fit: cover;
}

.phone-preview__banner-fallback {
  display: grid;
  place-content: center;
  gap: 4px;
  height: 120px;
  padding: 8px;
  color: #fff;
  text-align: center;
}

.phone-preview__notice {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
}

.phone-preview__notice-tag {
  padding: 1px 5px;
  border-radius: 4px;
  color: #fff;
  font-size: 9px;
}

.phone-preview__shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  background: #fff;
  border-radius: 10px;
  padding: 10px 6px;
}

.phone-preview__shortcut {
  display: grid;
  justify-items: center;
  gap: 4px;
  font-size: 9px;
  text-align: center;
}

.phone-preview__shortcut-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
}

.phone-preview__recommend {
  display: grid;
  gap: 6px;
}

.phone-preview__products {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.phone-preview__product {
  display: grid;
  gap: 3px;
  padding: 4px;
  background: #fff;
  border-radius: 8px;
}

.phone-preview__product-image {
  height: 54px;
  border-radius: 6px;
  background: #eef0f3;
}

.phone-preview__product-name {
  font-size: 9px;
  color: #333;
}

.phone-preview__topic {
  display: grid;
  gap: 3px;
  padding: 10px;
  border-radius: 10px;
}

.phone-preview__empty {
  margin-top: 60px;
  color: #8a919f;
  text-align: center;
}

.phone-preview__tabbar {
  display: flex;
  justify-content: space-around;
  padding: 7px 0 9px;
  border-top: 1px solid #eef0f3;
  background: #fff;
  font-size: 10px;
}

.phone-preview__popup {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  padding: 26px;
  background: rgba(15, 23, 42, 0.55);
}

.phone-preview__popup img {
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  border-radius: 12px;
}

.phone-preview__popup-placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 360px;
  border-radius: 12px;
  background: #fff;
  color: #8a919f;
}

.phone-preview__popup-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.phone-preview__popup-dots {
  margin-top: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 9px;
}
</style>
