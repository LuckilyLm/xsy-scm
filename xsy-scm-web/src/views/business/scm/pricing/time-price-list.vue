<template>
  <a-form class="smart-query-form" layout="inline">
    <a-row class="smart-query-form-row">
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="query.keyword" placeholder="商品名称或规格编码" allow-clear />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-space>
          <a-button type="primary" @click="search" v-privilege="'scm:pricing:time-price:manage'">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon closable @close="error = ''" />

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:pricing:time-price:manage'" @click="openDrawer()">新增时价</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_TIME_PRICE" :refresh="load" />
      </div>
    </a-row>

    <a-table
      id="scm-pricing-time-price-table"
      :data-source="rows"
      :columns="columns"
      row-key="id"
      size="small"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'productName'">{{ record.productName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'skuName'">{{ record.skuName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'unitPrice'">
          <span class="scm-money">{{ formatAmount(record.unitPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'startAt'">{{ formatDateTime(record.startAt) }}</template>
        <template v-else-if="column.dataIndex === 'endAt'">{{ formatDateTime(record.endAt) }}</template>
        <template v-else-if="column.dataIndex === 'effectiveState'">
          <ScmStatusTag :tone="record.effectiveNow ? 'success' : 'neutral'" :text="record.effectiveNow ? '生效中' : '未生效'" />
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate scm-table-actions">
            <a-button type="link" v-privilege="'scm:pricing:time-price:manage'" @click="openDrawer(record)">编辑</a-button>
            <a-button type="link" danger v-privilege="'scm:pricing:time-price:manage'" @click="remove(record)">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        v-model:current="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        show-size-changer
        show-quick-jumper
        :show-total="(n: number) => `共 ${n} 条`"
        @change="load"
      />
    </div>
  </a-card>

  <a-modal v-model:open="drawerOpen" :title="form.id ? '编辑时价' : '新增时价'" :confirm-loading="saving" ok-text="保存" @ok="save">
    <a-alert v-if="formError" class="form-error" type="error" show-icon :message="formError" />
    <a-form layout="vertical">
      <a-form-item label="商品规格" required>
        <SkuSelect v-model:value="form.skuId" width="100%" :disabled="form.id != null" :disabled-statuses="[]" />
      </a-form-item>
      <a-form-item label="时价" required>
        <a-input-number v-model:value="form.unitPrice" string-mode :min="'0'" :precision="4" style="width: 100%" aria-label="时价" />
      </a-form-item>
      <a-form-item label="生效时间" required>
        <a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" aria-label="生效时间" />
      </a-form-item>
      <a-form-item label="备注">
        <a-input v-model:value="form.remark" :maxlength="200" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import {Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {timePriceApi, type TimePrice} from '/@/api/business/scm/time-price-api';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';

const columns = ref<TableColumnsType>([
  {title: '商品', dataIndex: 'productName', width: 180, ellipsis: true},
  {title: '规格', dataIndex: 'skuName', width: 140, ellipsis: true},
  {title: '时价', dataIndex: 'unitPrice', width: 110},
  {title: '生效开始', dataIndex: 'startAt', width: 150},
  {title: '生效结束', dataIndex: 'endAt', width: 150},
  {title: '状态', dataIndex: 'effectiveState', width: 90, align: 'center'},
  {title: '操作', dataIndex: 'action', width: 110, align: 'center'},
]);

const query = reactive({pageNum: 1, pageSize: 20, keyword: ''});
const rows = ref<TimePrice[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');

const drawerOpen = ref(false);
const saving = ref(false);
const formError = ref('');
const form = reactive<{id?: Id; version?: number; skuId?: Id; unitPrice?: string; remark?: string}>({});
const range = ref<[string, string] | null>(null);

onMounted(load);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const response = await timePriceApi.query({...query, keyword: query.keyword || undefined});
    rows.value = response.data?.list ?? [];
    total.value = Number(response.data?.total ?? 0);
  } catch (e) {
    error.value = (e as Error)?.message || '时价加载失败';
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = '';
  search();
}

function openDrawer(record?: TimePrice) {
  formError.value = '';
  form.id = record?.id;
  form.version = record?.version;
  form.skuId = record?.skuId;
  form.unitPrice = record?.unitPrice;
  form.remark = record?.remark ?? '';
  range.value = record ? [toInput(record.startAt), toInput(record.endAt)] : null;
  drawerOpen.value = true;
}

async function save() {
  formError.value = '';
  if (form.skuId == null || !form.unitPrice || !range.value) {
    formError.value = '请选择商品规格、填写时价与生效时间';
    return;
  }
  saving.value = true;
  try {
    const payload = {
      id: form.id,
      version: form.version,
      skuId: form.skuId,
      unitPrice: form.unitPrice,
      // 后端要带时区偏移的时间，不能只传日期时间
      startAt: dayjs(range.value[0]).format('YYYY-MM-DDTHH:mm:ssZ'),
      endAt: dayjs(range.value[1]).format('YYYY-MM-DDTHH:mm:ssZ'),
      remark: form.remark || null,
    };
    if (form.id == null) {
      await timePriceApi.add(payload);
    } else {
      await timePriceApi.update(payload);
    }
    drawerOpen.value = false;
    await load();
  } catch (e) {
    formError.value = (e as Error)?.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

function remove(record: TimePrice) {
  Modal.confirm({
    title: '删除时价',
    content: `确认删除「${record.productName || ''} ${record.skuName || ''}」这条时价？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => timePriceApi.remove(record.id, record.version).then(load),
  });
}

function formatAmount(value?: string | null) {
  return value == null ? '—' : String(value);
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}

/** 服务端下发带偏移的时间，range-picker 只认 yyyy-MM-dd HH:mm:ss。 */
function toInput(value: string) {
  return value.replace('T', ' ').slice(0, 19);
}
</script>
