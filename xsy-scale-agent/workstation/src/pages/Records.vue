<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {ReloadOutlined, PrinterOutlined, FileTextOutlined} from '@ant-design/icons-vue';
import {api, query, errorText, unknownResult} from '../api';
import {batch, session, quantity, time, printLabels, boot} from '../store';
import type {Scope, Basket, Ticket, Summary} from '../types';
import TraceLabelDialog from '../components/TraceLabelDialog.vue';
const traceDialog = ref<InstanceType<typeof TraceLabelDialog>>();
const syncLabels: Record<string, string> = {
  SENT: '读数已上传',
  WAITING_CONTRACT: '待上传',
  SYNC_RETRY: '等待重试',
  NEEDS_REVIEW: '需人工处理',
};

const options = computed(() =>
  (batch.value?.tasks || []).flatMap((job) =>
    job.items.map((item) => ({
      value: JSON.stringify({
        jobId: job.jobId,
        taskId: job.taskId,
        taskItemId: item.taskItemId,
      }),
      label: `${item.customerName || job.taskNo} / ${item.productName}`,
    }))
  )
);
const selected = ref(''),
  error = ref(''),
  busy = ref(false),
  rows = ref<Basket[]>([]),
  tickets = ref<Ticket[]>([]),
  summary = ref<Summary | null>(null),
  after = ref<number | null>(null),
  reprintOpen = ref(false),
  reason = ref(''),
  target = ref('');
let generation = 0;
const scope = computed<Scope | null>(() => {
  try {
    return selected.value ? JSON.parse(selected.value) : null;
  } catch {
    return null;
  }
});
const selectedRecord = computed(() => rows.value.find((row) => row.eventKey === target.value));
const pendingKey = computed(() => `xsy-reprint:${session.value}:${selected.value}:${target.value}`);
const previewOpen = ref(false),
  preview = ref<Ticket | null>(null);
const columns = [
  {title: '筐次', dataIndex: 'basketNumber', width: 80},
  {title: '净重', key: 'weight', width: 125},
  {title: '采集时间', key: 'time', width: 130},
  {title: '同步状态', key: 'sync', width: 135},
  {title: '打印状态', dataIndex: 'printState', width: 185},
  {title: '操作', key: 'actions', width: 300},
];

/** 记录分页与全量累计分开，翻页不能更改商品总量。 */
async function load(more = false): Promise<void> {
  const current = scope.value;
  if (!current) return;
  const token = ++generation;
  busy.value = true;
  error.value = '';
  try {
    const [page, allTickets, total] = await Promise.all([
      api<{items: Basket[]; nextAfter: number | null}>('/baskets' + query(current, {after: more ? after.value || 0 : 0, limit: 50}), session.value),
      api<Ticket[]>('/local-tickets' + query(current), session.value),
      api<Summary>('/basket-summary' + query(current), session.value),
    ]);
    if (token !== generation) return;
    rows.value = more ? [...rows.value, ...page.items] : page.items;
    after.value = page.nextAfter;
    tickets.value = allTickets;
    summary.value = total;
  } catch (value) {
    if (token === generation) error.value = errorText(value);
  } finally {
    if (token === generation) busy.value = false;
  }
}
function latest(eventKey: string): Ticket | undefined {
  return tickets.value.filter((value) => value.snapshot.eventKey === eventKey).at(-1);
}
function showPreview(eventKey: string): void {
  preview.value = latest(eventKey) || null;
  previewOpen.value = true;
}
function openReprint(eventKey: string): void {
  target.value = eventKey;
  reason.value = '';
  error.value = '';
  reprintOpen.value = true;
}

/** 补打保留原重量，模糊成功使用同操作号重试，不增加称重记录。 */
async function reprint(): Promise<void> {
  if (!scope.value || busy.value) return;
  let request: {
    operationId: string;
    eventKey: string;
    reason: string;
  } | null = null;
  try {
    request = JSON.parse(localStorage.getItem(pendingKey.value) || 'null');
  } catch {
    error.value = '补打待核对数据无法读取，请先联系维护人员';
    return;
  }
  if (!request && !reason.value.trim()) {
    error.value = '请填写补打原因，并核对是否已经出纸';
    return;
  }
  request ??= {
    operationId: crypto.randomUUID(),
    eventKey: target.value,
    reason: reason.value.trim(),
  };
  try {
    localStorage.setItem(pendingKey.value, JSON.stringify(request));
  } catch {
    error.value = '无法保留补打编号，请检查本地存储';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await api('/local-tickets/reprint' + query(scope.value), session.value, request);
    localStorage.removeItem(pendingKey.value);
    reprintOpen.value = false;
    await load();
  } catch (value) {
    error.value = errorText(value);
    if (!unknownResult(value)) localStorage.removeItem(pendingKey.value);
  } finally {
    busy.value = false;
  }
}
watch(
  options,
  (value) => {
    if (!value.some((option) => option.value === selected.value)) selected.value = value[0]?.value || '';
  },
  {immediate: true}
);
watch(
  () => [selected.value, session.value],
  () => {
    generation++;
    rows.value = [];
    tickets.value = [];
    summary.value = null;
    after.value = null;
    void load();
  },
  {immediate: true}
);
</script>

<template>
  <section class="page-heading">
    <div>
      <div class="eyebrow">记录与追溯</div>
      <h1>称重记录</h1>
      <p>核对每筐重量、同步进度和打印尝试。</p>
    </div>
    <a-button :loading="busy" :disabled="!scope" @click="load()"><reload-outlined class="workstation-icon" />刷新记录</a-button>
  </section>
  <a-alert v-if="error" :message="error" type="error" show-icon class="section-gap" />
  <section class="panel records-panel">
    <div class="panel-toolbar">
      <h2>按商品查看</h2>
      <a-select v-model:value="selected" :options="options" placeholder="选择客户与商品" class="scope-select" />
    </div>
    <div v-if="summary" class="record-stats">
      <div>
        <span>已保存</span><strong>{{ summary.basketCount }} 筐</strong>
      </div>
      <div>
        <span>本地累计</span><strong>{{ quantity(summary.totalQuantity) }} {{ summary.unit }}</strong>
      </div>
      <div>
        <span>商品状态</span><strong>{{ summary.locallyCompleted ? '本地已完成' : '继续称重中' }}</strong>
      </div>
      <div>
        <span>同步状态</span
        ><strong class="warning-text">{{ summary.queueState === 'SENT' ? '平台已确认' : syncLabels[summary.queueState] || '待确认' }}</strong>
      </div>
    </div>
    <a-table
      :columns="columns"
      :data-source="rows"
      row-key="eventKey"
      :pagination="false"
      :loading="busy"
      :scroll="{x: 950}"
      :locale="{emptyText: '当前商品暂无本机称重记录'}"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.key === 'weight'"
          ><strong class="numeric">{{ quantity(record.measurement.netWeight) }} {{ record.measurement.businessUnit }}</strong></template
        >
        <template v-else-if="column.key === 'time'">{{ time(record.measurement.capturedAt, true) }}</template>
        <template v-else-if="column.key === 'sync'"
          ><a-tag :color="record.queueState === 'SENT' ? 'green' : 'orange'">{{ syncLabels[record.queueState] || '待确认' }}</a-tag
          ><small v-if="record.lastError" class="block warning-text">{{ record.lastError }}</small></template
        >
        <template v-else-if="column.dataIndex === 'printState'"
          ><span
            :class="{
              'warning-text': ['FAILED', 'UNKNOWN'].includes(record.printState),
            }"
            >{{ printLabels[record.printState] || '未知状态' }}</span
          ><small v-if="latest(record.eventKey)?.errorCode" class="block muted">需核查打印机与原凭条</small></template
        >
        <template v-else-if="column.key === 'actions'"
          ><div class="table-actions">
            <a-button
              size="small"
              type="text"
              :disabled="
                busy ||
                boot?.localTest ||
                boot?.hardwareTest ||
                summary?.locallyCompleted ||
                latest(record.eventKey)?.snapshot.templateVersion === 'PLATFORM_TRACE_V1'
              "
              @click="traceDialog?.open(record.eventKey)"
              >发码打印</a-button
            ><a-button size="small" type="text" :disabled="!latest(record.eventKey)" @click="showPreview(record.eventKey)"
              ><file-text-outlined class="workstation-icon" />凭条</a-button
            ><a-button
              size="small"
              type="text"
              :disabled="
                (!boot?.localTicketsEnabled && latest(record.eventKey)?.snapshot.templateVersion !== 'PLATFORM_TRACE_V1') ||
                !latest(record.eventKey) ||
                ['QUEUED', 'SUBMITTING'].includes(record.printState)
              "
              @click="openReprint(record.eventKey)"
              ><printer-outlined class="workstation-icon" />补打</a-button
            >
          </div></template
        >
      </template>
    </a-table>
    <div v-if="after !== null" class="load-more">
      <a-button :loading="busy" @click="load(true)">加载更多记录</a-button>
    </div>
  </section>
  <a-alert v-if="summary?.lastError" type="error" show-icon :message="summary.lastError" /><TraceLabelDialog
    ref="traceDialog"
    :scope="scope"
    @saved="load()"
  />
  <p class="page-note">提交Windows打印队列不等于已物理出纸。打印结果未知时，请先核对纸票，再决定是否补打。</p>
  <a-modal v-model:open="reprintOpen" title="补打原筐凭条" ok-text="确认补打" :confirm-loading="busy" @ok="reprint"
    ><a-alert type="warning" show-icon message="请先核对是否已经出纸。补打复用原重量，不新增筐记录。" />
    <p class="completion-facts">
      第 {{ selectedRecord?.basketNumber }} 筐 ·
      {{ quantity(selectedRecord?.measurement.netWeight) }}
      {{ selectedRecord?.measurement.businessUnit }}
    </p>
    <a-form layout="vertical"
      ><a-form-item label="补打原因" required
        ><a-textarea v-model:value="reason" :maxlength="500" :rows="3" placeholder="例如：凭条破损，经核对后补打" /></a-form-item></a-form
    ><a-alert v-if="error" :message="error" type="error" show-icon
  /></a-modal>
  <a-modal v-model:open="previewOpen" title="本地凭条内容" :footer="null"
    ><div v-if="preview" class="ticket-preview">
      <h2>鲜蔬园 · 称重凭条</h2>
      <p class="ticket-warning">
        {{
          preview.snapshot.templateVersion === 'PLATFORM_TRACE_V1'
            ? '包装码标签 · 不代表出库'
            : preview.snapshot.templateVersion.startsWith('HARDWARE_TEST_6040_')
            ? '设备联动测试 · 非业务记录'
            : preview.snapshot.testOnly
            ? '演练样张 · 不出纸'
            : '待同步 · 非平台正式单据'
        }}
      </p>
      <p v-if="preview.reprint">补打凭条</p>
      <hr />
      <p>客户：{{ preview.snapshot.customerName || '未提供' }}</p>
      <p>订单：{{ preview.snapshot.orderNo || '未提供' }}</p>
      <h3>{{ preview.snapshot.productName }}</h3>
      <div class="ticket-weight">{{ quantity(preview.snapshot.netWeight) }} {{ preview.snapshot.unit }}</div>
      <p>第 {{ preview.snapshot.basketNumber }} 筐</p>
      <p>{{ time(preview.snapshot.capturedAt, true) }}</p>
      <hr />
      <small>{{ preview.snapshot.eventKey }}</small>
      <div v-if="preview.snapshot.traceReference" class="ticket-trace">
        <strong>溯源查询</strong>
        <p>
          {{ preview.snapshot.traceUrl ? '已冻结查询链接，二维码随标签打印' : '查询入口待配置' }}
        </p>
        <small v-if="preview.snapshot.traceUrl">{{ preview.snapshot.traceUrl }}</small>
        <p class="muted">
          {{
            preview.snapshot.templateVersion === 'PLATFORM_TRACE_V1'
              ? '包装码：' + preview.snapshot.traceReference + ' · 批次：' + (preview.snapshot.lotNo || '—')
              : '本地记录关联，不代表平台包装码。'
          }}
        </p>
      </div>
      <p>{{ printLabels[preview.state] || '未知打印状态' }}</p>
    </div></a-modal
  >
</template>
