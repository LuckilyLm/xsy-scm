<template>
  <!-- workspace：称重记录要并排核对读数、已接受分拣量、设备状态与处理操作，属于多面板业务工作台。 -->
  <a-drawer :open="open" title="订单称重工作台" :width="scmDrawerWidth('workspace')" @close="close">
    <a-alert type="info" show-icon class="workbench-alert"
             message="电子秤采集接口待接入"
             description="先选择订单和商品规格；设备接入后，稳定读数将在下方出现。只有人工接受才会写入分拣结果。"/>
    <a-alert v-if="scaleError" type="error" show-icon :message="scaleError" class="workbench-alert"/>
    <a-spin :spinning="scaleLoading">
      <a-row :gutter="[12, 12]" class="select-row">
        <a-col :xs="24" :md="12">
          <div class="field-label">1. 选择客户订单</div>
          <a-select :value="selectedOrderId" :options="orderOptions" show-search
                    option-filter-prop="label" placeholder="请选择当前分拣任务内的订单"
                    style="width:100%" @change="chooseOrder"/>
        </a-col>
        <a-col :xs="24" :md="12">
          <div class="field-label">2. 选择待称商品规格</div>
          <a-select :value="selectedItemId" :options="itemOptions" show-search
                    option-filter-prop="label" placeholder="请选择商品规格"
                    style="width:100%" @change="chooseItem"/>
        </a-col>
      </a-row>
      <a-card size="small" class="reading-card">
        <template #title>
          <a-space>
            <span>当前读数</span>
            <a-tag v-if="selectedItem?.productTypeSnapshot === 'NON_STANDARD'" color="blue">非标品</a-tag>
          </a-space>
        </template>
        <template #extra><a-button :loading="scaleLoading" @click="reload">刷新读数</a-button></template>
        <template v-if="selectedItem">
          <div class="reading-row">
            <div>
              <div class="reading-label">{{ selectedItem.productNameSnapshot }} · {{ selectedItem.specNameSnapshot || '默认规格' }}</div>
              <div class="reading-number">
                {{ latestPending?.rawReading ?? (isDev && demoReading !== '' ? demoReading : '—') }}
                <small>{{ latestPending?.unit ?? selectedItem.saleUnitSnapshot }}</small>
              </div>
              <a-tag v-if="latestPending" :color="latestPending.stableFlag ? 'green' : 'orange'">
                {{ latestPending.stableFlag ? '读数稳定 · 待人工确认' : '读数尚未稳定' }}
              </a-tag>
              <a-tag v-else-if="isDev && demoReading !== ''" color="orange">仅模拟展示 · 不可接受</a-tag>
              <a-tag v-else>暂无设备读数</a-tag>
            </div>
            <a-descriptions :column="1" size="small" class="reading-facts">
              <a-descriptions-item label="订单">{{ selectedItem.orderNoSnapshot }}</a-descriptions-item>
              <a-descriptions-item label="客户">{{ selectedItem.customerNameSnapshot }}</a-descriptions-item>
              <a-descriptions-item label="计划量">{{ quantityText(selectedItem.plannedQuantitySnapshot) }} {{ selectedItem.saleUnitSnapshot }}</a-descriptions-item>
              <a-descriptions-item label="已分拣">{{ quantityText(selectedItem.sortedQuantity) }} {{ selectedItem.saleUnitSnapshot }}</a-descriptions-item>
              <a-descriptions-item label="设备">{{ latestPending?.deviceCode || '等待连接' }}</a-descriptions-item>
            </a-descriptions>
          </div>
          <a-alert v-if="latestPending && latestPending.unit !== selectedItem.saleUnitSnapshot" type="warning" show-icon
                   message="设备单位与商品销售单位不一致。当前不自动换算，禁止直接接受这笔读数。"/>
        </template>
        <a-empty v-else description="当前任务没有可称重的订单明细"/>
      </a-card>

      <a-card v-if="isDev && selectedItem" size="small" class="demo-card">
        <template #title>本地示例（仅展示）</template>
        <a-space wrap>
          <span>模拟重量</span>
          <a-input-number v-model:value="demoReading" string-mode :min="0" :precision="4"
                          :max="99999999999999" placeholder="输入演示读数"/>
          <span>{{ selectedItem.saleUnitSnapshot }}</span>
          <a-tag color="orange">本地模拟</a-tag>
        </a-space>
        <p class="demo-note">模拟值只用于展示交互，不会生成电子秤事件，也不会修改订单或分拣数量。真实上报需等厂家接口文档。</p>
      </a-card>

      <a-card size="small" title="称重记录" class="events-card">
        <a-table :data-source="visibleEvents" :columns="scaleColumns" row-key="id" size="small" bordered
                 :loading="scaleLoading" :pagination="false" :scroll="{ x: scmColumnsWidth(scaleColumns) }"
                 :locale="{emptyText: '当前订单商品暂无设备上报记录'}">
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'status'">
              <a-tag :color="sortingScaleStatuses[record.status as SortingScaleStatus]?.color || 'default'">
                {{ sortingScaleStatuses[record.status as SortingScaleStatus]?.label || record.status }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'stableFlag'">
              <a-tag :color="record.stableFlag ? 'green' : 'orange'">{{ record.stableFlag ? '稳定' : '跳动' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'rawReading'">
              <span class="reading"><strong class="scm-quantity">{{ quantityText(record.rawReading) }}</strong></span>
            </template>
            <template v-else-if="column.dataIndex === 'acceptedQuantity'">
              <span class="accepted"><strong class="scm-quantity">{{ record.acceptedQuantity == null ? '—' : quantityText(record.acceptedQuantity) }}</strong></span>
            </template>
            <template v-else-if="column.dataIndex === 'capturedAt' || column.dataIndex === 'receivedAt'">
              {{ datetime(record[column.dataIndex]) }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-space v-if="record.status === 'PENDING'" :size="0">
                <a-button v-privilege="'scm:sorting:scale:accept'" type="link" size="small"
                          :disabled="!record.stableFlag || scaleBusy" @click="openAccept(record)">接受</a-button>
                <a-button v-privilege="'scm:sorting:scale:accept'" type="link" size="small" danger
                          :disabled="scaleBusy" @click="openReject(record)">驳回</a-button>
              </a-space>
              <span v-else>{{ record.acceptedBy || record.rejectedBy || '—' }}</span>
            </template>
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </a-drawer>

  <a-modal v-model:open="acceptOpen" title="人工确认称重结果" :confirm-loading="scaleBusy"
           :ok-button-props="{disabled: scaleBusy}" @ok="submitAccept">
    <template v-if="acceptTarget">
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="设备">{{ acceptTarget.deviceCode }}</a-descriptions-item>
        <a-descriptions-item label="原始重量">{{ quantityText(acceptTarget.rawReading) }} {{ acceptTarget.unit }}</a-descriptions-item>
        <a-descriptions-item label="商品">{{ selectedItem?.productNameSnapshot || '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-form v-if="selectedItem?.productTypeSnapshot === 'NON_STANDARD'" layout="vertical" class="accept-form">
        <a-form-item label="分拣结果" required>
          <a-select v-model:value="acceptResult" :options="resultOptions" placeholder="由操作员判断是否正常、短少或超出"/>
        </a-form-item>
        <a-form-item label="差异原因" :required="acceptResult !== 'NORMAL'">
          <a-textarea v-model:value="acceptReason" :maxlength="500" :rows="3"
                      placeholder="例如：实重较计划量少，或现场复核说明"/>
        </a-form-item>
      </a-form>
      <a-alert v-else type="info" show-icon class="accept-form"
               message="标准品将按照当前读数登记为正常分拣结果。"/>
      <a-alert v-if="scaleError" :message="scaleError" type="error" show-icon class="accept-form"/>
    </template>
  </a-modal>

  <a-modal v-model:open="rejectOpen" title="驳回电子秤读数" :confirm-loading="scaleBusy" @ok="submitReject">
    <a-form ref="rejectFormRef" :model="{rejectReason}" :rules="rejectRules" layout="vertical">
      <a-form-item label="驳回原因" name="rejectReason">
        <a-textarea v-model:value="rejectReason" :maxlength="200" :rows="3"
                    placeholder="例如：称重物品不匹配、秤未校准、读数跳动"/>
      </a-form-item>
    </a-form>
    <a-alert v-if="scaleError" :message="scaleError" type="error" show-icon/>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {datetime} from '../../common/scm-display';
import {SCM_SORTING_RESULT_ENUM} from '/@/constants/business/scm/sorting-const';
import {quantityText, sortingError, sortingScaleStatuses} from '../sorting-types';
import type {Id, SortingScaleEvent, SortingScaleStatus, SortingTaskDetail, SortingTaskItem} from '../sorting-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const props = defineProps<{open: boolean; taskId?: Id}>();
const emit = defineEmits<{'update:open': [value: boolean]; updated: []}>();
const isDev = import.meta.env.DEV;
const detail = ref<SortingTaskDetail>();
const events = ref<SortingScaleEvent[]>([]);
const selectedOrderId = ref<Id>();
const selectedItemId = ref<Id>();
const scaleLoading = ref(false);
const scaleBusy = ref(false);
const scaleError = ref('');
const demoReading = ref<string>('');
const acceptOpen = ref(false);
const acceptTarget = ref<SortingScaleEvent>();
const acceptResult = ref<string>();
const acceptReason = ref('');
const rejectOpen = ref(false);
const rejectTarget = ref<SortingScaleEvent>();
const rejectReason = ref('');
const rejectFormRef = ref();
const rejectRules = {rejectReason: [{required: true, message: '请填写驳回原因', trigger: 'blur'}]};
const resultOptions = Object.values(SCM_SORTING_RESULT_ENUM).map((x) => ({value: x.value, label: x.desc}));
let requestGeneration = 0;

const activeItems = computed(() => (detail.value?.items ?? []).filter(
    (row) => row.sourceType !== 'PROMOTION_GIFT' && row.occupationStatus === 'ACTIVE'
));
const orderOptions = computed(() => {
    const seen = new Set<string>();
    return activeItems.value.filter((item) => {
        const key = String(item.salesOrderId);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    }).map((item) => ({value: item.salesOrderId, label: `${item.orderNoSnapshot} · ${item.customerNameSnapshot}`}));
});
const itemOptions = computed(() => activeItems.value
    .filter((item) => String(item.salesOrderId) === String(selectedOrderId.value))
    .map((item) => ({
        value: item.id,
        label: `${item.productNameSnapshot} · ${item.specNameSnapshot || '默认规格'} · ${item.saleUnitSnapshot}${item.productTypeSnapshot === 'NON_STANDARD' ? '（非标品）' : ''}`,
    })));
const selectedItem = computed(() => activeItems.value.find((item) => String(item.id) === String(selectedItemId.value)));
const visibleEvents = computed(() => events.value.filter((row) => String(row.taskItemId) === String(selectedItemId.value)));
const latestPending = computed(() => visibleEvents.value.find((row) => row.status === 'PENDING'));

function chooseOrder(value: unknown) {
    if (typeof value !== 'string' && typeof value !== 'number') return;
    selectedOrderId.value = value;
    selectedItemId.value = itemOptions.value[0]?.value;
    demoReading.value = '';
}
function chooseItem(value: unknown) {
    if (typeof value !== 'string' && typeof value !== 'number') return;
    selectedItemId.value = value;
    demoReading.value = '';
}
async function reload() {
    if (!props.open || props.taskId === undefined) return;
    const generation = ++requestGeneration;
    scaleLoading.value = true;
    scaleError.value = '';
    try {
        const [task, scale] = await Promise.all([
            sortingApi.detail(props.taskId),
            sortingApi.scaleEvents(props.taskId),
        ]);
        if (generation !== requestGeneration || !props.open) return;
        detail.value = task.data;
        events.value = scale.data ?? [];
        const currentOrder = activeItems.value.find((item) => String(item.salesOrderId) === String(selectedOrderId.value));
        const preferred = currentOrder ?? activeItems.value.find((item) => item.productTypeSnapshot === 'NON_STANDARD')
            ?? activeItems.value[0];
        if (!preferred) {
            selectedOrderId.value = undefined;
            selectedItemId.value = undefined;
        } else {
            selectedOrderId.value = preferred.salesOrderId;
            if (!activeItems.value.some((item) => String(item.id) === String(selectedItemId.value)
                && String(item.salesOrderId) === String(selectedOrderId.value))) {
                selectedItemId.value = preferred.id;
            }
        }
    } catch (error) {
        if (generation === requestGeneration) scaleError.value = sortingError(error);
    } finally {
        if (generation === requestGeneration) scaleLoading.value = false;
    }
}
watch(() => [props.open, props.taskId] as const, ([open, taskId]) => {
    requestGeneration++;
    if (open && taskId !== undefined) {
        selectedOrderId.value = undefined;
        selectedItemId.value = undefined;
        detail.value = undefined;
        events.value = [];
        void reload();
    }
});

function lineOf(record: SortingScaleEvent): SortingTaskItem | undefined {
    return detail.value?.items.find((item) => String(item.id) === String(record.taskItemId));
}
function openAccept(record: SortingScaleEvent) {
    scaleError.value = '';
    const item = lineOf(record);
    if (!item || item.saleUnitSnapshot !== record.unit) {
        scaleError.value = '读数单位与订单商品规格的销售单位不一致，请先核对设备。';
        return;
    }
    acceptTarget.value = record;
    acceptResult.value = item.productTypeSnapshot === 'NON_STANDARD' ? undefined : 'NORMAL';
    acceptReason.value = '';
    acceptOpen.value = true;
}
async function submitAccept() {
    const event = acceptTarget.value;
    if (!event) return;
    const item = lineOf(event);
    if (!item) return;
    if (item.productTypeSnapshot === 'NON_STANDARD' && !acceptResult.value) {
        scaleError.value = '请先选择分拣结果。';
        return;
    }
    if (acceptResult.value !== 'NORMAL' && !acceptReason.value.trim()) {
        scaleError.value = '短少、缺货或超出必须填写原因。';
        return;
    }
    scaleBusy.value = true;
    scaleError.value = '';
    try {
        await sortingApi.acceptScaleEvent(event.id, event.version, acceptResult.value, acceptReason.value.trim() || undefined);
        message.success('稳定读数已由人工确认并写入分拣结果');
        acceptOpen.value = false;
        emit('updated');
        await reload();
    } catch (error) {
        scaleError.value = sortingError(error);
    } finally {
        scaleBusy.value = false;
    }
}
function openReject(record: SortingScaleEvent) {
    rejectTarget.value = record;
    rejectReason.value = '';
    scaleError.value = '';
    rejectOpen.value = true;
}
async function submitReject() {
    if (!rejectTarget.value) return;
    try {
        await rejectFormRef.value?.validate();
    } catch {
        return;
    }
    scaleBusy.value = true;
    scaleError.value = '';
    try {
        await sortingApi.rejectScaleEvent(rejectTarget.value.id, rejectTarget.value.version, rejectReason.value.trim());
        message.success('读数已驳回，未修改分拣结果');
        rejectOpen.value = false;
        await reload();
    } catch (error) {
        scaleError.value = sortingError(error);
    } finally {
        scaleBusy.value = false;
    }
}
function close() {
    if (scaleBusy.value) return;
    requestGeneration++;
    emit('update:open', false);
}

const scaleColumns: TableColumnsType<SortingScaleEvent> = [
    {title: '状态', dataIndex: 'status', align: 'center', width: 90},
    {title: '原始读数', dataIndex: 'rawReading', width: 120},
    {title: '已接受数量', dataIndex: 'acceptedQuantity', width: 120},
    {title: '单位', dataIndex: 'unit', width: 70},
    {title: '稳定', dataIndex: 'stableFlag', align: 'center', width: 80},
    {title: '设备', dataIndex: 'deviceCode', width: 130},
    {title: '采集时间', dataIndex: 'capturedAt', width: 175},
    {title: '接收时间', dataIndex: 'receivedAt', width: 175},
    {title: '处理', dataIndex: 'action', fixed: 'right', align: 'center', width: 150},
];
</script>

<style scoped>
.workbench-alert { margin-bottom: 16px; }
.select-row { margin-bottom: 16px; }
.field-label { margin-bottom: 8px; font-weight: 600; }
.reading-card, .events-card, .demo-card { margin-bottom: 16px; }
.reading-row { display: flex; gap: 24px; justify-content: space-between; flex-wrap: wrap; padding: 12px; }
.reading-label { color: var(--scm-text-secondary, #667085); }
.reading-number { font-size: 40px; line-height: 1.4; font-variant-numeric: tabular-nums; font-weight: 700; }
.reading-number small { font-size: 18px; font-weight: 400; }
.reading-facts { min-width: 280px; }
.reading { font-weight: 700; color: var(--scm-text); }
.accepted { font-weight: 700; color: var(--scm-success); }
.demo-note { margin: 10px 0 0; color: var(--scm-text-secondary, #667085); }
.accept-form { margin-top: 16px; }
</style>
