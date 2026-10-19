<template>
  <div class="share-page">
    <div class="share-page__card">
      <div class="share-page__title">采购任务</div>

      <a-spin :spinning="loading">
        <a-alert v-if="error" type="error" show-icon :message="error" class="share-page__alert"/>

        <template v-if="view">
          <a-descriptions :column="2" size="small" bordered class="share-page__info">
            <a-descriptions-item label="采购单">{{ view.orderNo }}</a-descriptions-item>
            <a-descriptions-item label="供应商">{{ view.supplierName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="采购员">{{ view.purchaserName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="预计到货">{{ view.plannedArrivalDate || '—' }}</a-descriptions-item>
          </a-descriptions>

          <div v-for="line in view.lines" :key="line.itemId" class="share-page__line">
            <div class="share-page__name">{{ line.productName }} · {{ line.skuName }}</div>
            <div class="share-page__planned">
              计划 {{ line.plannedQuantity }}{{ line.purchaseUnit || '' }}，已收
              {{ line.receivedQuantity || '0' }}{{ line.purchaseUnit || '' }}
            </div>
            <div class="share-page__fields">
              <label class="share-page__field">
                <span>单价</span>
                <a-input-number
                    v-model:value="draft[line.itemId].unitPrice"
                    string-mode
                    :min="'0'"
                    :precision="4"
                    placeholder="0.0000"
                    :aria-label="`${line.productName} 单价`"/>
              </label>
              <label class="share-page__field">
                <span>实收重量</span>
                <a-input-number
                    v-model:value="draft[line.itemId].receivedQuantity"
                    string-mode
                    :min="'0'"
                    :precision="4"
                    placeholder="0.0000"
                    :aria-label="`${line.productName} 实收重量`"/>
              </label>
            </div>
            <a-input v-model:value="draft[line.itemId].remark" :maxlength="200" placeholder="备注（可空）"/>
            <div v-if="line.lastSubmittedAt" class="share-page__last">
              上次回填：单价 {{ line.lastUnitPrice ?? '—' }}，实收 {{ line.lastReceivedQuantity ?? '—' }}
            </div>
          </div>

          <a-button type="primary" block :loading="submitting" @click="submit">提交回填</a-button>
        </template>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
  import {onMounted, reactive, ref} from 'vue';
  import {useRoute} from 'vue-router';
  import {message} from 'ant-design-vue';
  import {getRequest, postRequest} from '/@/lib/axios';

  interface ShareLine {
    itemId: number;
    productName: string;
    skuName: string;
    purchaseUnit?: string | null;
    plannedQuantity: string;
    receivedQuantity?: string | null;
    purchasePrice?: string | null;
    lastUnitPrice?: string | null;
    lastReceivedQuantity?: string | null;
    lastRemark?: string | null;
    lastSubmittedAt?: string | null;
  }

  interface ShareView {
    orderNo: string;
    supplierName?: string | null;
    purchaserName?: string | null;
    warehouseName?: string | null;
    plannedArrivalDate?: string | null;
    status: string;
    expiresAt: string;
    lines: ShareLine[];
  }

  interface DraftLine {
    unitPrice?: string;
    receivedQuantity?: string;
    remark?: string;
  }

  const route = useRoute();
  const token = String(route.params.token ?? '');
  const loading = ref(false);
  const submitting = ref(false);
  const error = ref('');
  const view = ref<ShareView>();
  const draft = reactive<Record<number, DraftLine>>({});

  onMounted(load);

  async function load() {
    loading.value = true;
    error.value = '';
    try {
      const response = await getRequest(`/scm/purchase/share/public/${token}`, {}, {suppressGlobalErrorMessage: true});
      view.value = response.data as ShareView;
      (view.value.lines ?? []).forEach((line) => {
        draft[line.itemId] = {
          unitPrice: line.lastUnitPrice ?? undefined,
          receivedQuantity: line.lastReceivedQuantity ?? undefined,
          remark: undefined,
        };
      });
    } catch (e) {
      error.value = (e as Error)?.message || '链接不可用，请联系采购员重新分享';
    } finally {
      loading.value = false;
    }
  }

  async function submit() {
    const items = Object.entries(draft)
        .map(([itemId, line]) => ({
          purchaseOrderItemId: Number(itemId),
          unitPrice: line.unitPrice || undefined,
          receivedQuantity: line.receivedQuantity || undefined,
          remark: line.remark || undefined,
        }))
        .filter((line) => line.unitPrice !== undefined || line.receivedQuantity !== undefined);

    if (!items.length) {
      message.warning('请至少填写一行的单价或实收重量');
      return;
    }

    submitting.value = true;
    try {
      await postRequest(`/scm/purchase/share/public/${token}/feedback`, {items}, {suppressGlobalErrorMessage: true});
      message.success('已提交');
      await load();
    } catch (e) {
      message.error((e as Error)?.message || '提交失败');
    } finally {
      submitting.value = false;
    }
  }
</script>

<style scoped>
  .share-page {
    min-height: 100vh;
    padding: 16px;
    background: #f5f6f8;
  }

  .share-page__card {
    max-width: 720px;
    margin: 0 auto;
    padding: 16px;
    background: #fff;
    border-radius: 8px;
  }

  .share-page__title {
    margin-bottom: 12px;
    font-size: 16px;
    font-weight: 600;
  }

  .share-page__alert {
    margin-bottom: 12px;
  }

  .share-page__info {
    margin-bottom: 16px;
  }

  .share-page__line {
    padding: 12px 0;
    border-top: 1px solid #f0f0f0;
  }

  .share-page__name {
    font-weight: 600;
  }

  .share-page__planned {
    margin: 4px 0 8px;
    color: #8c8c8c;
    font-size: 12px;
  }

  .share-page__fields {
    display: flex;
    gap: 12px;
    margin-bottom: 8px;
  }

  .share-page__field {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 4px;
    font-size: 12px;
    color: #8c8c8c;
  }

  .share-page__last {
    margin-top: 6px;
    color: #8c8c8c;
    font-size: 12px;
  }
</style>
