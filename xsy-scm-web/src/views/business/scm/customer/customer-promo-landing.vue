<template>
  <div class="promo-page">
    <div class="promo-page__card">
      <div class="promo-page__title">推荐信息</div>
      <a-spin :spinning="loading">
        <a-alert v-if="error" type="error" show-icon :message="error"/>
        <template v-if="landing">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="推荐方">{{ landing.customerName }}</a-descriptions-item>
            <a-descriptions-item label="归属业务员">{{ landing.sellerName || '待分配' }}</a-descriptions-item>
            <a-descriptions-item label="扫码时间">{{ formatDateTime(landing.scannedAt) }}</a-descriptions-item>
          </a-descriptions>
        </template>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
  import {onMounted, ref} from 'vue';
  import {useRoute} from 'vue-router';
  import {getRequest} from '/@/lib/axios';

  interface Landing {
    customerName: string;
    sellerName?: string | null;
    scannedAt: string;
  }

  const route = useRoute();
  const loading = ref(false);
  const error = ref('');
  const landing = ref<Landing>();

  onMounted(async () => {
    loading.value = true;
    try {
      const response = await getRequest(`/scm/mall/promo/${String(route.params.code ?? '')}`, {},
          {suppressGlobalErrorMessage: true});
      landing.value = response.data as Landing;
    } catch (e) {
      error.value = (e as Error)?.message || '推广码不可用，请联系推荐人重新获取';
    } finally {
      loading.value = false;
    }
  });

  function formatDateTime(value?: string | null) {
    return value ? value.replace('T', ' ').slice(0, 16) : '—';
  }
</script>

<style scoped>
  .promo-page {
    min-height: 100vh;
    padding: 16px;
    background: #f5f6f8;
  }

  .promo-page__card {
    max-width: 560px;
    margin: 0 auto;
    padding: 16px;
    background: #fff;
    border-radius: 8px;
  }

  .promo-page__title {
    margin-bottom: 12px;
    font-size: 16px;
    font-weight: 600;
  }
</style>
