<!--
  营销活动（ADM-12 第一阶段）。

  活动是**版本化的规则**：每次编辑 version 自增，订单优惠冻结时一并记录，因此「这单当时按
  哪一版算的」永远可查。生效中的活动不能改内容（只能先停用）—— 同一时间窗内出现两套规则，
  界面上是看不出来的。

  规则是受控键值：每种活动类型只接受自己的键，未知键会被服务端拒收（41324），
  因此这里的输入项按类型切换，而不是给一个「任意键值」的编辑器。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="关键词" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="活动编码 / 名称" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="类型" class="smart-query-form-item">
        <a-select v-model:value="queryForm.activityType" :options="typeOptions" style="width: 140px"
                  placeholder="全部" allow-clear/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" style="width: 140px"
                  placeholder="全部" allow-clear/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="'scm:promotion:activity:query'" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:promotion:activity:edit'" @click="openCreate">新建活动</a-button>
        <span class="hint">活动价在基础价（协议价 → 客户类型价 → 市场价）之后计算；互斥组决定可否叠加</span>
      </div>
    </a-row>

    <a-table
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'activityType'">
          <a-tag :color="activityTypes[record.activityType as PromotionActivityType]?.color || 'default'">
            {{ activityTypes[record.activityType as PromotionActivityType]?.label || record.activityType }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="promotionStatuses[record.status as PromotionStatus]?.color || 'default'">
            {{ promotionStatuses[record.status as PromotionStatus]?.label || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'ruleText'">{{ ruleText(record.rule) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" v-privilege="'scm:promotion:activity:edit'" @click="openEdit(record)">编辑</a-button>
            <a-button
                v-if="record.status !== 'ACTIVE'"
                type="link"
                v-privilege="'scm:promotion:activity:status'"
                @click="changeStatus(record, 'ACTIVE')"
            >上线
            </a-button>
            <a-button
                v-else
                type="link"
                danger
                v-privilege="'scm:promotion:activity:status'"
                @click="changeStatus(record, 'STOPPED')"
            >停用
            </a-button>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <a-modal
      :open="editOpen"
      :title="form.id ? '编辑活动' : '新建活动'"
      width="720px"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="editOpen = false"
  >
    <a-alert v-if="editError" :message="editError" type="error" show-icon class="banner"/>
    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="活动编码" required>
            <a-input v-model:value="form.activityCode" :disabled="!!form.id"
                     placeholder="字母、数字、下划线或连字符"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="活动名称" required>
            <a-input v-model:value="form.activityName"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="活动类型" required>
            <a-select v-model:value="form.activityType" :options="typeOptions" @change="onTypeChange"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="互斥组">
            <a-input v-model:value="form.exclusiveGroup" placeholder="同组内不可叠加，可留空"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="优先级">
            <a-input-number v-model:value="form.priority" :min="0" :precision="0" style="width: 100%"/>
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider orientation="left">规则</a-divider>
      <a-alert
          message="规则是受控键值：每种类型只接受自己的键，多传未知键会被服务端拒收（41324）。"
          type="info"
          show-icon
          class="banner"
      />
      <a-row :gutter="12">
        <template v-if="form.activityType === 'DISCOUNT'">
          <a-col :span="12">
            <a-form-item label="折扣率（0~1）" required>
              <a-input v-model:value="form.rule.discountRate" placeholder="0.95 表示 95 折"/>
            </a-form-item>
          </a-col>
        </template>
        <template v-else-if="form.activityType === 'SPECIAL_PRICE'">
          <a-col :span="12">
            <a-form-item label="特价商品规格" required>
              <SkuSelect
                  :value="form.rule.skuId ?? null"
                  width="100%"
                  @update:value="onSpecialSkuChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="特价单价" required>
              <a-input v-model:value="form.rule.specialPrice" placeholder="例如 6.8000；只能低于该行单价"/>
            </a-form-item>
          </a-col>
        </template>
        <template v-else>
          <a-col :span="12">
            <a-form-item label="门槛金额" required>
              <a-input v-model:value="form.rule.thresholdAmount" placeholder="达到该金额才生效"/>
            </a-form-item>
          </a-col>
          <a-col v-if="form.activityType === 'FULL_REDUCE'" :span="12">
            <a-form-item label="减免金额" required>
              <a-input v-model:value="form.rule.reduceAmount"/>
            </a-form-item>
          </a-col>
          <template v-if="form.activityType === 'FULL_GIFT'">
            <a-col :span="12">
              <a-form-item label="赠送商品规格" required>
                <a-input v-model:value="form.rule.giftSkuId"/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="赠品数量" required>
                <a-input v-model:value="form.rule.giftQuantity"/>
              </a-form-item>
            </a-col>
          </template>
        </template>
      </a-row>

      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="生效时间" required>
            <a-date-picker v-model:value="form.validFrom" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                           style="width: 100%"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="失效时间" required>
            <a-date-picker v-model:value="form.validTo" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                           style="width: 100%"/>
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="备注">
        <a-input v-model:value="form.remark"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import type {ScmId} from '/@/types/business/scm/customer';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import {
  activityTypes,
  promotionError,
  promotionStatuses,
  type PromotionActivity,
  type PromotionActivityQuery,
  type PromotionActivitySave,
  type PromotionActivityType,
  type PromotionRule,
  type PromotionStatus,
} from './promotion-types';

const queryForm = reactive<PromotionActivityQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<PromotionActivity[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const editOpen = ref(false);
const saving = ref(false);
const editError = ref('');

const typeOptions = Object.entries(activityTypes).map(([value, item]) => ({value, label: item.label}));
const statusOptions = Object.entries(promotionStatuses).map(([value, item]) => ({value, label: item.label}));

const columns: TableColumnsType<PromotionActivity> = [
  {title: '活动编码', dataIndex: 'activityCode', width: 180},
  {title: '活动名称', dataIndex: 'activityName', width: 200},
  {title: '类型', dataIndex: 'activityType', width: 100},
  {title: '规则', dataIndex: 'ruleText', width: 260},
  {title: '互斥组', dataIndex: 'exclusiveGroup', width: 130},
  {title: '优先级', dataIndex: 'priority', align: 'right', width: 90},
  {title: '生效时间', dataIndex: 'validFrom', width: 180},
  {title: '失效时间', dataIndex: 'validTo', width: 180},
  {title: '状态', dataIndex: 'status', width: 100},
  {title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 160},
];

const form = reactive<PromotionActivitySave>({
  activityCode: '',
  activityName: '',
  activityType: 'FULL_REDUCE',
  exclusiveGroup: null,
  priority: 0,
  validFrom: '',
  validTo: '',
  rule: {},
  remark: null,
});

/** 规则的可读描述：受控键值 → 一句话，便于在列表上直接看出「这条活动减多少」。 */
function ruleText(rule?: PromotionRule): string {
  if (!rule) return '—';
  if (rule.discountRate) return `折扣率 ${rule.discountRate}`;
  if (rule.specialPrice) return `商品规格 ${rule.skuId} 限时特价 ${rule.specialPrice}`;
  if (rule.reduceAmount) return `满 ${rule.thresholdAmount} 减 ${rule.reduceAmount}`;
  if (rule.giftSkuId) return `满 ${rule.thresholdAmount} 赠送商品规格 ${rule.giftSkuId} × ${rule.giftQuantity}`;
  return '—';
}

/** SKU 选择器给出的是 id（可能是数字）；规则里的键统一按字符串提交。 */
function onSpecialSkuChange(value: ScmId | ScmId[] | undefined) {
  form.rule.skuId = value === undefined || value === null ? undefined : String(value);
}

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    const r = await promotionApi.activityQuery({...queryForm});
    tableData.value = r.data.list ?? [];
    total.value = r.data.total ?? 0;
  } catch (e) {
    error.value = promotionError(e);
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.activityType = undefined;
  queryForm.status = undefined;
  onSearch();
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    activityCode: '',
    activityName: '',
    activityType: 'FULL_REDUCE',
    exclusiveGroup: null,
    priority: 0,
    validFrom: '',
    validTo: '',
    rule: {} as PromotionRule,
    remark: null,
    version: undefined,
  });
  editError.value = '';
  editOpen.value = true;
}

function openEdit(record: PromotionActivity) {
  Object.assign(form, {
    id: record.id,
    activityCode: record.activityCode,
    activityName: record.activityName,
    activityType: record.activityType,
    exclusiveGroup: record.exclusiveGroup ?? null,
    priority: record.priority ?? 0,
    validFrom: record.validFrom,
    validTo: record.validTo,
    rule: {...(record.rule ?? {})},
    remark: record.remark ?? null,
    version: record.version,
  });
  editError.value = '';
  editOpen.value = true;
}

/** 切换类型时清空规则：残留的键会被服务端当成「未知键」拒收，提前清掉比提交后才报错好。 */
function onTypeChange() {
  form.rule = {};
}

async function submit() {
  editError.value = '';
  if (!form.activityCode || !form.activityName || !form.validFrom || !form.validTo) {
    editError.value = '活动编码、名称与生效时间都必须填写';
    return;
  }
  saving.value = true;
  try {
    await promotionApi.activitySave({...form});
    message.success('活动已保存');
    editOpen.value = false;
    await queryData();
  } catch (e) {
    editError.value = promotionError(e);
  } finally {
    saving.value = false;
  }
}

async function changeStatus(record: PromotionActivity, status: 'ACTIVE' | 'STOPPED') {
  try {
    await promotionApi.activityStatus(record.id, record.version, status);
    message.success(status === 'ACTIVE' ? '活动已上线' : '活动已停用');
    await queryData();
  } catch (e) {
    error.value = promotionError(e);
  }
}

onMounted(queryData);
</script>

<style scoped>
.hint {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
  margin-left: 8px;
}

.banner {
  margin-bottom: 12px;
}
</style>
