<template>
  <div class="scm-inquiry">
    <a-card title="采购询价" :bordered="false" size="small">
      <p class="scm-note">从净需求批次发起询价，登记并比较供应商报价。</p>
      <a-form layout="inline" class="scm-inquiry__toolbar" @submit.prevent="loadBatch(1)">
        <a-form-item label="净需求批次">
          <a-input v-model:value="batchInput" placeholder="输入已冻结的批次编号" style="width: 220px" />
        </a-form-item>
        <a-button type="primary" :loading="loading" v-privilege="'scm:purchase:inquiry:query'" @click="loadBatch(1)">查询</a-button>
      </a-form>
      <a-alert v-if="error" :message="error" type="error" show-icon class="scm-inquiry__alert" />
      <a-space v-if="loadedBatch" wrap class="scm-inquiry__toolbar">
        <a-select
          v-model:value="candidateKey"
          :options="candidateOptions"
          placeholder="选择未建立询价的商品规格"
          style="width: 310px"
          show-search
          option-filter-prop="label"
        />
        <a-date-picker v-model:value="deadlineAt" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="报价截止日期时间" />
        <a-button
          type="primary"
          :loading="busy"
          :disabled="!candidateKey || !deadlineAt"
          v-privilege="'scm:purchase:inquiry:create'"
          @click="createInquiry"
          >建立询价</a-button
        >
      </a-space>
      <a-table
        :data-source="inquiries"
        :columns="columns"
        row-key="id"
        bordered
        size="small"
        :loading="loading"
        :pagination="{current: currentPage, pageSize: 20, total: total, showSizeChanger: false}"
        :scroll="{x: scmColumnsWidth(columns)}"
        @change="onPageChange"
      >
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'requiredQuantity'">
            <span class="scm-quantity">{{ record.requiredQuantity }}</span> {{ record.demandUnit }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'ACCEPTED' ? 'green' : 'blue'">
              {{ record.status === 'ACCEPTED' ? '已确认报价' : expired(record.deadlineAt) ? '已截止' : '询价中' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-button type="link" v-privilege="'scm:purchase:inquiry:query'" @click="openDetail(record.id)">查看/比价</a-button>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loadedBatch && !loading" description="输入净需求批次编号，查看该批次的采购询价" />
    </a-card>

    <a-drawer :open="detailOpen" :width="scmDrawerWidth('xl')" title="询价与报价对比" @close="detailOpen = false">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions :column="3" bordered size="small">
            <a-descriptions-item label="商品规格"
              >{{ detail.inquiry.productNameSnapshot }}（{{ detail.inquiry.skuCodeSnapshot }}）</a-descriptions-item
            >
            <a-descriptions-item label="需求量">{{ detail.inquiry.requiredQuantity }} {{ detail.inquiry.demandUnit }}</a-descriptions-item>
            <a-descriptions-item label="报价截止">{{ detail.inquiry.deadlineAt }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ detail.inquiry.status === 'ACCEPTED' ? '已确认报价' : '未确认' }}</a-descriptions-item>
            <a-descriptions-item label="确认报价">{{ detail.inquiry.acceptedQuoteId || '—' }}</a-descriptions-item>
            <a-descriptions-item label="确认人">{{ detail.inquiry.acceptedBy || '—' }}</a-descriptions-item>
          </a-descriptions>
          <a-space class="scm-inquiry__toolbar">
            <a-button type="primary" v-if="canQuote" v-privilege="'scm:purchase:inquiry:quote'" @click="quoteOpen = true">登记供应商报价</a-button>
            <a-button @click="openDetail(detail.inquiry.id)">刷新报价</a-button>
          </a-space>
          <a-alert
            type="info"
            show-icon
            message="报价接收截止后仍可确认有效报价；报价由采购员代为登记，不代表供应商在线签署。"
            class="scm-inquiry__alert"
          />
          <a-divider orientation="left">当前有效报价（单价从低到高）</a-divider>
          <a-table
            :data-source="detail.currentOffers"
            :columns="quoteColumns"
            row-key="id"
            size="small"
            bordered
            :pagination="{pageSize: 10}"
            :scroll="{x: scmColumnsWidth(quoteColumns)}"
          >
            <template #bodyCell="{record, column}">
              <template v-if="column.dataIndex === 'unitPrice'">
                <span class="scm-amount">{{ record.unitPrice }}</span> / {{ record.purchaseUnit }}
              </template>
              <template v-else-if="column.dataIndex === 'revision'">第 {{ record.revision }} 版</template>
              <template v-else-if="column.dataIndex === 'validUntil'">
                {{ record.validUntil }}
                <a-tag v-if="expired(record.validUntil)" color="default">已失效</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-tag v-if="String(record.id) === String(detail.inquiry.acceptedQuoteId)" color="green">已选用</a-tag>
                <a-button
                  v-else-if="canAccept && !expired(record.validUntil)"
                  type="link"
                  v-privilege="'scm:purchase:inquiry:accept'"
                  @click="confirmQuote(record)"
                  >确认选用</a-button
                >
                <span v-else>—</span>
              </template>
            </template>
          </a-table>
          <a-divider orientation="left">历次报价修订记录</a-divider>
          <a-table
            :data-source="quoteHistory"
            :columns="historyColumns"
            row-key="id"
            bordered
            size="small"
            :pagination="{pageSize: 10}"
            :scroll="{x: scmColumnsWidth(historyColumns)}"
          />
          <a-button v-if="quoteHistoryMore" :loading="historyLoading" @click="loadMoreHistory"> 加载更多历史报价 </a-button>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal :open="quoteOpen" title="人工登记供应商报价" :confirm-loading="busy" @ok="submitQuote" @cancel="quoteOpen = false">
      <a-form layout="vertical">
        <a-form-item label="供应商" required>
          <SupplierSelect v-model:value="quoteSupplierId" width="100%" />
        </a-form-item>
        <a-form-item label="采购单价（与需求单位一致，最多四位小数）" required>
          <a-input-number v-model:value="quotePrice" string-mode :min="'0'" :precision="4" style="width: 100%" />
        </a-form-item>
        <a-form-item label="报价有效期" required>
          <a-date-picker v-model:value="quoteValidUntil" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="quoteRemark" :maxlength="500" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {purchaseInquiryApi} from '/@/api/business/scm/purchase-inquiry-api';
import type {InquiryCandidate, PurchaseInquiry, PurchaseInquiryDetail, PurchaseInquiryQuote} from '/@/api/business/scm/purchase-inquiry-api';
import type {ScmId} from '/@/types/business/scm/customer';

const route = useRoute();
const batchInput = ref('');
const loadedBatch = ref<ScmId>();
const inquiries = ref<PurchaseInquiry[]>([]);
const currentPage = ref(1);
const total = ref(0);
const quoteHistory = ref<PurchaseInquiryQuote[]>([]);
const quoteHistoryMore = ref(false);
const quoteHistoryBefore = ref<ScmId | null>(null);
const historyLoading = ref(false);
const candidates = ref<InquiryCandidate[]>([]);
const candidateKey = ref<string>();
const deadlineAt = ref<string>();
const loading = ref(false);
const detailLoading = ref(false);
const busy = ref(false);
const error = ref('');
const detailOpen = ref(false);
const detail = ref<PurchaseInquiryDetail>();
const quoteOpen = ref(false);
const quoteSupplierId = ref<ScmId>();
const quotePrice = ref<string>();
const quoteValidUntil = ref<string>();
const quoteRemark = ref('');

const columns: TableColumnsType<PurchaseInquiry> = [
  {title: '询价编号', dataIndex: 'id', width: 115},
  {title: '商品', dataIndex: 'productNameSnapshot', width: 210},
  {title: '规格编码', dataIndex: 'skuCodeSnapshot', width: 165},
  {title: '需求量', dataIndex: 'requiredQuantity', width: 150},
  {title: '截止时间', dataIndex: 'deadlineAt', width: 180},
  {title: '状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '操作', dataIndex: 'action', align: 'center', width: 125},
];
const quoteColumns: TableColumnsType<PurchaseInquiryQuote> = [
  {title: '供应商', dataIndex: 'supplierNameSnapshot', width: 185},
  {title: '版本', dataIndex: 'revision', width: 85},
  {title: '单价', dataIndex: 'unitPrice', width: 148},
  {title: '有效期', dataIndex: 'validUntil', width: 180},
  {title: '登记人', dataIndex: 'recordedBy', width: 130},
  {title: '备注', dataIndex: 'remark', width: 155},
  {title: '操作', dataIndex: 'action', align: 'center', width: 120},
];

const historyColumns: TableColumnsType<PurchaseInquiryQuote> = [
  {title: '供应商', dataIndex: 'supplierNameSnapshot', width: 180},
  {title: '版本', dataIndex: 'revision', width: 80},
  {title: '单价', dataIndex: 'unitPrice', width: 130},
  {title: '单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '有效期', dataIndex: 'validUntil', width: 180},
  {title: '登记人', dataIndex: 'recordedBy', width: 120},
  {title: '备注', dataIndex: 'remark', width: 150},
];
const candidateOptions = computed(() =>
  candidates.value.map((c) => ({
    value: candidateIdentifier(c),
    label: c.productName + ' · ' + c.skuCode + ' · ' + c.requiredQuantity + ' ' + c.demandUnit + (c.existingInquiryId ? '（已建询价）' : ''),
    disabled: !!c.existingInquiryId,
  }))
);
const canAccept = computed(() => !!detail.value && detail.value.inquiry.status === 'OPEN');
const canQuote = computed(() => canAccept.value && !!detail.value && !expired(detail.value.inquiry.deadlineAt));
const expired = (value: string) => new Date(value).getTime() <= Date.now();
const candidateIdentifier = (c: InquiryCandidate) => c.skuId + '|' + c.demandUnit;

async function loadBatch(page = 1): Promise<void> {
  const id = batchInput.value.trim();
  if (!/^[1-9]\d*$/.test(id)) {
    void message.warning('请输入正确的净需求批次编号');
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const [rows, options] = await Promise.all([purchaseInquiryApi.byBatch(id, page), purchaseInquiryApi.candidates(id)]);
    loadedBatch.value = id;
    inquiries.value = rows.data.records;
    total.value = rows.data.total;
    currentPage.value = page;
    candidates.value = options.data;
    candidateKey.value = undefined;
  } catch (cause) {
    loadedBatch.value = undefined;
    inquiries.value = [];
    total.value = 0;
    candidates.value = [];
    error.value = String((cause as Error)?.message || '询价加载失败');
  } finally {
    loading.value = false;
  }
}
function onPageChange(pagination: {current?: number}): void {
  void loadBatch(pagination.current ?? 1);
}

function applyDetail(next: PurchaseInquiryDetail): void {
  detail.value = next;
  quoteHistory.value = next.quotes;
  quoteHistoryMore.value = next.hasMoreQuotes;
  quoteHistoryBefore.value = next.nextQuoteBeforeId;
}

async function loadMoreHistory(): Promise<void> {
  if (!detail.value || !quoteHistoryMore.value || !quoteHistoryBefore.value) return;
  const id = detail.value.inquiry.id;
  historyLoading.value = true;
  try {
    const page = (await purchaseInquiryApi.history(id, quoteHistoryBefore.value)).data;
    if (String(detail.value?.inquiry.id) !== String(id)) return;
    quoteHistory.value = [...quoteHistory.value, ...page.records];
    quoteHistoryMore.value = page.hasMore;
    quoteHistoryBefore.value = page.nextBeforeId;
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '加载报价历史失败'));
  } finally {
    historyLoading.value = false;
  }
}
async function createInquiry(): Promise<void> {
  const candidate = candidates.value.find((c) => candidateIdentifier(c) === candidateKey.value);
  if (!loadedBatch.value || !candidate || candidate.existingInquiryId || !deadlineAt.value || expired(deadlineAt.value)) {
    void message.warning('请选择商品规格和未来的报价截止时间');
    return;
  }
  busy.value = true;
  try {
    const created = await purchaseInquiryApi.create({
      batchId: loadedBatch.value,
      skuId: candidate.skuId,
      demandUnit: candidate.demandUnit,
      deadlineAt: deadlineAt.value,
    });
    void message.success('询价已建立');
    await loadBatch();
    await openDetail(created.data.inquiry.id);
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '建立询价失败'));
  } finally {
    busy.value = false;
  }
}
async function openDetail(id: ScmId): Promise<void> {
  detailOpen.value = true;
  detailLoading.value = true;
  try {
    applyDetail((await purchaseInquiryApi.detail(id)).data);
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '读取报价失败'));
  } finally {
    detailLoading.value = false;
  }
}
async function submitQuote(): Promise<void> {
  if (
    !detail.value ||
    !quoteSupplierId.value ||
    !quotePrice.value ||
    !/^\d{1,14}(?:\.\d{1,4})?$/.test(quotePrice.value) ||
    !quoteValidUntil.value ||
    expired(quoteValidUntil.value)
  ) {
    void message.warning('请选择供应商并填写有效的价格和有效期');
    return;
  }
  busy.value = true;
  try {
    applyDetail(
      (
        await purchaseInquiryApi.quote({
          inquiryId: detail.value.inquiry.id,
          version: detail.value.inquiry.version,
          supplierId: quoteSupplierId.value,
          unitPrice: quotePrice.value,
          validUntil: quoteValidUntil.value,
          remark: quoteRemark.value.trim() || undefined,
        })
      ).data
    );
    quoteOpen.value = false;
    quotePrice.value = undefined;
    quoteSupplierId.value = undefined;
    quoteValidUntil.value = undefined;
    quoteRemark.value = '';
    void message.success('已登记报价新版本');
    await loadBatch();
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '登记报价失败'));
  } finally {
    busy.value = false;
  }
}
function confirmQuote(quote: PurchaseInquiryQuote): void {
  if (!detail.value) return;
  const current = detail.value.inquiry;
  Modal.confirm({
    title: '确认选用这份报价？',
    content: '确认后锁定本次询价的选择，采购单仍需单独创建和提交。',
    onOk: async () => {
      try {
        applyDetail(
          (
            await purchaseInquiryApi.accept({
              inquiryId: current.id,
              quoteId: quote.id,
              version: current.version,
            })
          ).data
        );
        void message.success('报价已确认');
        await loadBatch();
      } catch (cause) {
        void message.error(String((cause as Error)?.message || '确认报价失败'));
        throw cause;
      }
    },
  });
}
onMounted(() => {
  const fromBatch = route.query.batchId;
  if (typeof fromBatch === 'string' && /^[1-9]\d*$/.test(fromBatch)) {
    batchInput.value = fromBatch;
    void loadBatch();
  }
});
</script>
<style scoped>
.scm-inquiry {
  display: grid;
  gap: 16px;
}
.scm-inquiry__toolbar {
  margin: 12px 0;
}
.scm-inquiry__alert {
  margin-bottom: 12px;
}
</style>
