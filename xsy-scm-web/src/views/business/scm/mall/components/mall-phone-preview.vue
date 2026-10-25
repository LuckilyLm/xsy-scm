<template>
  <div class="phone-preview">
    <!-- 手机内是效果示意，读屏只读卡片标题，不逐个朗读占位商品与底部导航 -->
    <div class="phone-preview__screen" aria-hidden="true">
      <div class="phone-preview__statusbar" :style="{background: palette.primary}">
        <span>9:41</span>
        <span class="phone-preview__status-icons">●●●</span>
      </div>
      <!-- 与小程序保持一致：主题色顶部只放定位，搜索框单独一条白色区域 -->
      <div class="phone-preview__header" :style="{background: `linear-gradient(180deg, ${palette.primary}, ${palette.dark})`}">
        <span class="phone-preview__location">
          <EnvironmentOutlined aria-hidden="true" />
          <span>选择收货地址</span>
          <DownOutlined class="phone-preview__location-arrow" aria-hidden="true" />
        </span>
      </div>
      <div class="phone-preview__search-wrap">
        <div class="phone-preview__search" :style="{borderColor: palette.primary}">
          <SearchOutlined :style="{color: palette.primary}" aria-hidden="true" />
          <span>搜索商品名称或规格</span>
        </div>
      </div>

      <div class="phone-preview__body">
        <template v-for="section in sections" :key="section.key">
          <div v-if="section.type === 'BANNER_GROUP'" class="phone-preview__banner">
            <img v-if="sectionImage(section)" :src="sectionImage(section)" :alt="sectionTitle(section, '轮播图')" />
            <div v-else class="phone-preview__banner-fallback" :style="{background: `linear-gradient(135deg, ${palette.primary}, ${palette.dark})`}">
              <strong>{{ sectionTitle(section, '轮播图') }}</strong>
              <span v-if="section.items[0]?.subtitle">{{ section.items[0]?.subtitle }}</span>
            </div>
            <div v-if="section.items.length > 1" class="phone-preview__banner-dots">
              <span v-for="(item, index) in section.items" :key="item.key" :class="{'is-active': index === 0}"></span>
            </div>
          </div>

          <!-- 分类横滑是固定区块，不属于后台组件；位置与小程序一致，跟在轮播之后 -->
          <div v-else-if="section.type === 'RAIL'" class="phone-preview__rail">
            <div v-for="item in RAIL_ITEMS" :key="item.key" class="phone-preview__rail-item">
              <span class="phone-preview__rail-icon" :style="{background: palette.light}">{{ item.icon }}</span>
              <span class="phone-preview__rail-text">{{ item.label }}</span>
            </div>
          </div>

          <div v-else-if="section.type === 'ANNOUNCEMENT'" class="phone-preview__notice">
            <span class="phone-preview__notice-tag" :style="{background: palette.primary}">公告</span>
            <span class="phone-preview__notice-text">{{ section.items[0]?.text || '商城公告' }}</span>
          </div>

          <div v-else-if="section.type === 'SHORTCUT_GROUP'" class="phone-preview__shortcuts">
            <div v-for="block in section.items" :key="block.key" class="phone-preview__shortcut">
              <img v-if="shortcutImage(block)" class="phone-preview__shortcut-icon" :src="shortcutImage(block)" alt="" />
              <span v-else class="phone-preview__shortcut-icon" :style="{background: palette.light, color: palette.ink}">
                {{ (block.title || '入').slice(0, 1) }}
              </span>
              <span class="phone-preview__shortcut-text">{{ shortcutLabel(block.title) }}</span>
            </div>
          </div>

          <div v-else-if="section.type === 'ACTIVITY_TOPIC'" class="phone-preview__topic">
            <img v-if="sectionImage(section)" :src="sectionImage(section)" :alt="sectionTitle(section, '活动专题')" />
            <div class="phone-preview__topic-copy">
              <strong>{{ sectionTitle(section, '活动专题') }}</strong>
              <span v-if="section.items[0]?.subtitle">{{ section.items[0]?.subtitle }}</span>
            </div>
          </div>

          <div v-else-if="section.type === 'RECOMMENDATION'" class="phone-preview__recommend">
            <div class="phone-preview__section-head">
              <strong>{{ sectionTitle(section, '推荐商品') }}</strong>
              <span class="phone-preview__section-link" :style="{color: palette.ink}">全部商品</span>
            </div>
            <div v-for="index in 3" :key="index" class="phone-preview__product">
              <span class="phone-preview__product-image" :style="{background: palette.light}"></span>
              <div class="phone-preview__product-main">
                <span class="phone-preview__product-name">商品名称</span>
                <span class="phone-preview__product-spec">规格 · 分类</span>
                <span class="phone-preview__product-price">市场价 <em>¥ 0.00</em><b>/件</b></span>
                <span class="phone-preview__product-stock">库存未知</span>
              </div>
              <span class="phone-preview__product-action" :style="{background: palette.primary}">加购</span>
            </div>
          </div>
        </template>
        <div v-if="!sections.length" class="phone-preview__empty">还没有启用的首页组件</div>
      </div>

      <div class="phone-preview__tabbar">
        <span v-for="tab in TABS" :key="tab.key" class="phone-preview__tab" :style="{color: tab.active ? palette.primary : '#8a919f'}">
          <component :is="tab.icon" aria-hidden="true" />
          <span>{{ tab.label }}</span>
        </span>
      </div>
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
import {
  AppstoreOutlined,
  DownOutlined,
  EnvironmentOutlined,
  HomeOutlined,
  ProfileOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  UserOutlined,
} from '@ant-design/icons-vue';
import {fileApi} from '/@/api/support/file-api';
import {mallHomeApi, type HomeBlock, type HomePopup} from '/@/api/business/scm/mall-home-api';
import {mallThemeBaseColor, mallThemePalette} from '/@/utils/mall-theme';

type PreviewSectionType = HomeBlock['type'] | 'BANNER_GROUP' | 'SHORTCUT_GROUP' | 'RAIL';

interface PreviewSection {
  type: PreviewSectionType;
  key: string;
  items: HomeBlock[];
}

/** 分类横滑是小程序固定区块，后台没有分类数据；用真实分类名做占位，展示结构而不是内容。 */
const RAIL_ITEMS = [
  {key: 'vegetable', label: '蔬菜', icon: '🥬'},
  {key: 'fruit', label: '水果', icon: '🍎'},
  {key: 'meat', label: '肉禽', icon: '🥩'},
  {key: 'coupon', label: '领券中心', icon: '🎁'},
];

const TABS = [
  {key: 'home', label: '首页', icon: HomeOutlined, active: true},
  {key: 'category', label: '分类', icon: AppstoreOutlined, active: false},
  {key: 'cart', label: '购物车', icon: ShoppingCartOutlined, active: false},
  {key: 'order', label: '订单', icon: ProfileOutlined, active: false},
  {key: 'mine', label: '我的', icon: UserOutlined, active: false},
];

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

/** 与小程序一致：相邻轮播合并成一组、相邻快捷入口合并成一个栅格，分类横滑固定跟在首个轮播后。 */
const sections = computed<PreviewSection[]>(() => {
  const result: PreviewSection[] = [];
  for (const block of visibleBlocks.value) {
    const groupType = block.type === 'BANNER' ? 'BANNER_GROUP' : block.type === 'SHORTCUT' ? 'SHORTCUT_GROUP' : null;
    const previous = result[result.length - 1];
    if (groupType && previous?.type === groupType) {
      previous.items.push(block);
    } else if (groupType) {
      result.push({type: groupType, key: block.key, items: [block]});
    } else {
      result.push({type: block.type, key: block.key, items: [block]});
    }
  }
  const bannerIndex = result.findIndex((section) => section.type === 'BANNER_GROUP');
  const rail: PreviewSection = {type: 'RAIL', key: 'rail', items: []};
  if (bannerIndex < 0) result.unshift(rail);
  else result.splice(bannerIndex + 1, 0, rail);
  return result;
});

const showPopup = computed(() => Boolean(popup.value?.enabled && popup.value.items.length && !popupDismissed.value));

function sectionTitle(section: PreviewSection, fallback: string): string {
  return section.items[0]?.title || fallback;
}

function sectionImage(section: PreviewSection): string {
  const key = section.items[0]?.imageFileKey;
  return key ? imageUrls.value[key] || '' : '';
}

function shortcutImage(block: HomeBlock): string {
  return block.imageFileKey ? imageUrls.value[block.imageFileKey] || '' : '';
}

function shortcutLabel(title: string | null | undefined): string {
  return title?.split('/')[0]?.trim() || '入口';
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
  width: 300px;
  height: 640px;
  margin: 0 auto;
  overflow: hidden;
  border: 8px solid #1f2329;
  border-radius: 28px;
  background: #f5f6f8;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.22);
  font-size: 11px;
}

.phone-preview__screen {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.phone-preview__statusbar {
  display: flex;
  justify-content: space-between;
  padding: 4px 14px;
  color: #fff;
  font-size: 10px;
}

.phone-preview__status-icons {
  letter-spacing: 1px;
}

.phone-preview__header {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
}

.phone-preview__location {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  font-weight: 500;
}

.phone-preview__location-arrow {
  font-size: 9px;
  opacity: 0.8;
}

.phone-preview__search-wrap {
  padding: 8px 12px;
  background: #fff;
}

.phone-preview__search {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid;
  border-radius: 999px;
  background: #fff;
  color: #8a919f;
  font-size: 10px;
}

.phone-preview__body {
  flex: 1;
  overflow-y: auto;
  background: #f5f6f8;
}

.phone-preview__banner {
  position: relative;
  margin: 10px 12px 0;
  overflow: hidden;
  border-radius: 8px;
}

.phone-preview__banner img {
  display: block;
  width: 100%;
  /* 280rpx @375 宽 = 140px，按 300px 机身等比缩到 112px */
  height: 112px;
  object-fit: cover;
}

.phone-preview__banner-fallback {
  display: grid;
  place-content: center;
  gap: 4px;
  height: 112px;
  padding: 8px;
  color: #fff;
  text-align: center;
}

.phone-preview__banner-dots {
  position: absolute;
  right: 0;
  bottom: 6px;
  left: 0;
  display: flex;
  justify-content: center;
  gap: 4px;
}

.phone-preview__banner-dots span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
}

.phone-preview__banner-dots span.is-active {
  background: #fff;
}

.phone-preview__rail {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  overflow: hidden;
  background: #fff;
}

.phone-preview__rail-item {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  width: 48px;
}

.phone-preview__rail-icon {
  display: grid;
  place-items: center;
  width: 35px;
  height: 35px;
  border-radius: 50%;
  font-size: 16px;
  line-height: 1;
}

.phone-preview__rail-text {
  max-width: 48px;
  overflow: hidden;
  color: #4e5969;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.phone-preview__notice {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 12px 0;
  padding: 0 8px;
  min-height: 34px;
  border-radius: 8px;
  background: #fff;
}

.phone-preview__notice-tag {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  color: #fff;
  font-size: 9px;
  line-height: 1.5;
}

.phone-preview__notice-text {
  flex: 1;
  overflow: hidden;
  color: #1f2329;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.phone-preview__shortcuts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin-top: 10px;
  padding: 10px 8px;
  background: #fff;
}

.phone-preview__shortcut {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  font-size: 9px;
  text-align: center;
}

.phone-preview__shortcut-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  object-fit: cover;
  font-size: 11px;
}

.phone-preview__shortcut-text {
  max-width: 100%;
  overflow: hidden;
  color: #4e5969;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.phone-preview__topic {
  margin-top: 10px;
  background: #fff;
}

.phone-preview__topic img {
  display: block;
  width: 100%;
  height: 100px;
  object-fit: cover;
}

.phone-preview__topic-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
}

.phone-preview__topic-copy strong {
  color: #1f2329;
  font-size: 12px;
}

.phone-preview__topic-copy span {
  color: #8a919f;
  font-size: 10px;
}

.phone-preview__recommend {
  margin-top: 10px;
  background: #fff;
}

.phone-preview__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
}

.phone-preview__section-head strong {
  color: #1f2329;
  font-size: 14px;
}

.phone-preview__section-link {
  font-size: 10px;
}

.phone-preview__product {
  display: flex;
  align-items: flex-start;
  padding: 10px 12px;
  border-top: 1px solid #f2f3f5;
}

.phone-preview__product-image {
  flex: 0 0 auto;
  width: 61px;
  height: 61px;
  border-radius: 8px;
}

.phone-preview__product-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  margin-left: 10px;
}

.phone-preview__product-name {
  color: #1f2329;
  font-size: 13px;
  font-weight: 500;
}

.phone-preview__product-spec {
  color: #8a919f;
  font-size: 9px;
}

.phone-preview__product-price {
  color: #8a919f;
  font-size: 9px;
}

/* 商品卡价格与小程序一致，使用固定的危险色，不随主题色变化 */
.phone-preview__product-price em {
  color: #f53f3f;
  font-size: 14px;
  font-style: normal;
  font-weight: 700;
}

.phone-preview__product-price b {
  font-weight: 400;
}

.phone-preview__product-stock {
  color: #8a919f;
  font-size: 9px;
}

.phone-preview__product-action {
  display: inline-flex;
  align-self: flex-end;
  align-items: center;
  flex: 0 0 auto;
  height: 34px;
  margin-left: 8px;
  padding: 0 12px;
  border-radius: 999px;
  color: #fff;
  font-size: 10px;
}

.phone-preview__empty {
  margin-top: 60px;
  color: #8a919f;
  text-align: center;
}

.phone-preview__tabbar {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 5px 0 7px;
  border-top: 1px solid #eef0f3;
  background: #fff;
}

.phone-preview__tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 9px;
}

.phone-preview__tab :deep(.anticon) {
  font-size: 15px;
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
