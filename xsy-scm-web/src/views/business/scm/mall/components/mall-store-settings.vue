<template>
  <a-card :bordered="false" size="small">
    <div class="store-heading">
      <div>
        <h2>店铺</h2>
      </div>
      <a-button v-privilege="'scm:mall:store:edit'" type="primary" :loading="saving" :disabled="!dirty" @click="save">保存店铺信息</a-button>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" class="store-alert">
      <template #action><a-button size="small" @click="load">重新加载</a-button></template>
    </a-alert>

    <a-spin :spinning="loading">
      <div class="store-grid">
        <a-form layout="vertical" class="store-form">
          <a-form-item label="店铺名称" required>
            <a-input v-model:value="storeName" :maxlength="60" show-count placeholder="展示在商城页面" @change="dirty = true" />
          </a-form-item>
          <a-form-item label="店铺链接">
            <a-input v-model:value="storeLink" placeholder="https://" allow-clear @change="dirty = true" />
          </a-form-item>
        </a-form>

        <div class="store-qr">
          <h3>店铺二维码</h3>
          <template v-if="savedLink">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="店铺二维码" class="store-qr__image" />
            <a-spin v-else class="store-qr__loading" />
            <p class="store-qr__link" :title="savedLink">{{ savedLink }}</p>
            <a-space>
              <a-button :disabled="!qrDataUrl" @click="downloadQr">下载二维码</a-button>
              <a-button :disabled="!qrDataUrl" @click="copyLink">复制链接</a-button>
            </a-space>
          </template>
          <a-empty v-else description="暂无二维码" />
        </div>
      </div>
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {message} from 'ant-design-vue';
import QRCode from 'qrcode';
import {mallDecorationApi, type MallStore} from '/@/api/business/scm/mall-decoration-api';

const store = ref<MallStore>({version: 0, storeName: ''});
const storeName = ref('');
const storeLink = ref('');
const saving = ref(false),
  loading = ref(false),
  dirty = ref(false),
  error = ref(''),
  qrDataUrl = ref('');
/** 二维码只对应已保存的链接，避免扫到未保存的地址。 */
const savedLink = ref('');

async function renderQr(link: string | null | undefined): Promise<void> {
  savedLink.value = link || '';
  if (!savedLink.value) {
    qrDataUrl.value = '';
    return;
  }
  try {
    qrDataUrl.value = await QRCode.toDataURL(savedLink.value, {width: 240, margin: 1});
  } catch {
    qrDataUrl.value = '';
    void message.error('二维码生成失败，请检查店铺链接');
  }
}

function apply(data: MallStore): void {
  store.value = data;
  storeName.value = data.storeName || '';
  storeLink.value = data.storeLink || '';
  dirty.value = false;
  void renderQr(data.storeLink);
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    apply((await mallDecorationApi.store()).data);
  } catch (cause) {
    error.value = String((cause as Error)?.message || '店铺信息加载失败');
  } finally {
    loading.value = false;
  }
}

async function save(): Promise<void> {
  if (!storeName.value.trim()) {
    void message.warning('请填写店铺名称');
    return;
  }
  const link = storeLink.value.trim();
  if (link && !/^https:\/\//i.test(link)) {
    void message.warning('店铺链接必须以 https:// 开头');
    return;
  }
  saving.value = true;
  try {
    apply((await mallDecorationApi.saveStore({version: store.value.version, storeName: storeName.value.trim(), storeLink: link || null})).data);
    void message.success('店铺信息已保存');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '店铺信息保存失败'));
  } finally {
    saving.value = false;
  }
}

function downloadQr(): void {
  if (!qrDataUrl.value) return;
  const anchor = document.createElement('a');
  anchor.href = qrDataUrl.value;
  anchor.download = `${storeName.value || '商城'}二维码.png`;
  anchor.click();
}

async function copyLink(): Promise<void> {
  try {
    await navigator.clipboard.writeText(savedLink.value);
    void message.success('链接已复制');
  } catch {
    void message.error('复制失败，请手动选择链接复制');
  }
}

onMounted(load);
</script>

<style scoped>
.store-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.store-heading h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.store-alert {
  margin-bottom: 12px;
}

.store-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 32px;
  align-items: start;
}

.store-form {
  max-width: 560px;
}

.store-qr {
  padding: 20px;
  border: 1px solid var(--scm-line);
  border-radius: 12px;
  text-align: center;
}

.store-qr h3 {
  margin: 0 0 16px;
  font-size: 15px;
}

.store-qr__image {
  display: block;
  width: 200px;
  height: 200px;
  margin: 0 auto 12px;
  border: 1px solid var(--scm-line);
  border-radius: 8px;
}

.store-qr__loading {
  display: block;
  margin: 60px auto;
}

.store-qr__link {
  margin: 0 0 12px;
  overflow: hidden;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .store-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
