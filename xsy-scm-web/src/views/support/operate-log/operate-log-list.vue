<!--
  * 操作记录 列表
  *
-->
<template>
  <a-form class="smart-query-form" v-privilege="'support:operateLog:query'">
    <a-row class="smart-query-form-row">
      <a-form-item label="操作关键字" class="smart-query-form-item">
        <a-input style="width: 150px" v-model:value="queryForm.keywords" placeholder="模块/操作内容"/>
      </a-form-item>
      <a-form-item label="请求关键字" class="smart-query-form-item">
        <a-input style="width: 270px" v-model:value="queryForm.requestKeywords"
                 placeholder="请求地址/请求方法/请求参数/返回结果"/>
      </a-form-item>
      <a-form-item label="用户名称" class="smart-query-form-item">
        <a-input style="width: 100px" v-model:value="queryForm.userName" placeholder="用户名称"/>
      </a-form-item>

      <a-form-item label="请求时间" class="smart-query-form-item">
        <a-range-picker @change="changeCreateDate" v-model:value="createDateRange" :presets="defaultChooseTimeRange"
                        style="width: 240px"/>
      </a-form-item>

      <a-form-item label="状态：" class="smart-query-form-item">
        <a-radio-group v-model:value="queryForm.successFlag" @change="onSearch">
          <a-radio-button :value="undefined">全部</a-radio-button>
          <a-radio-button :value="true">成功</a-radio-button>
          <a-radio-button :value="false">失败</a-radio-button>
        </a-radio-group>
      </a-form-item>

      <a-form-item class="smart-query-form-item smart-margin-left10">
        <a-button-group>
          <a-button type="primary" @click="ajaxQuery">
            <template #icon>
              <SearchOutlined/>
            </template>
            查询
          </a-button>
          <a-button @click="resetQuery">
            <template #icon>
              <ReloadOutlined/>
            </template>
            重置
          </a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert
      v-if="businessContextActive"
      class="smart-margin-bottom10"
      type="info"
      show-icon
      message="正在按当前业务对象查看操作记录"
      description="该视图按业务类型与 ID 精确匹配；操作时未写入该 ID 的记录（如新建类操作或更早的历史数据）无法归属到本对象，因此此处不保证是该对象的全部历史。"
  />

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row justify="end">
      <TableOperator class="smart-margin-bottom5" v-model="columns" :tableId="TABLE_ID_CONST.SUPPORT.CONFIG"
                     :refresh="ajaxQuery"/>
    </a-row>
    <a-table size="small" :loading="tableLoading" :dataSource="tableData" :columns="columns" bordered
             rowKey="operateLogId" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'response'">
          <a-typography-text v-if="text && text.ok">{{ text ? text.msg : '-' }}</a-typography-text>
          <a-typography-text v-else type="warning">{{ text ? text.msg : '-' }}</a-typography-text>
        </template>

        <template v-if="column.dataIndex === 'successFlag'">
          <a-tag :color="text ? 'success' : 'error'">{{ text ? '成功' : '报错' }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'userAgent'">
          <div>{{ record.os }} / {{ record.browser }} {{ record.device ? '/' + record.device : record.device }}</div>
        </template>

        <template v-if="column.dataIndex === 'operateUserType'">
          <div>{{ $smartEnumPlugin.getDescByValue('USER_TYPE_ENUM', text) }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showDetail(record.operateLogId)" type="link" v-privilege="'support:operateLog:detail'">
              详情
            </a-button>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          showSizeChanger
          showQuickJumper
          show-less-items
          :pageSizeOptions="PAGE_SIZE_OPTIONS"
          :defaultPageSize="queryForm.pageSize"
          v-model:current="queryForm.pageNum"
          v-model:pageSize="queryForm.pageSize"
          :total="total"
          @change="ajaxQuery"
          :show-total="(total) => `共${total}条`"
      />
    </div>

    <OperateLogDetailModal ref="detailModal"/>
  </a-card>
</template>
<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import OperateLogDetailModal from './operate-log-detail-modal.vue';
import {operateLogApi} from '/@/api/support/operate-log-api';
import {PAGE_SIZE_OPTIONS} from '/@/constants/common-const';
import {defaultTimeRanges} from '/@/lib/default-time-ranges';
import uaparser from 'ua-parser-js';
import {smartSentry} from '/@/lib/smart-sentry';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';

const columns = ref([
  {
    title: '用户',
    dataIndex: 'operateUserName',
    width: 70,
  },
  {
    title: '类型',
    dataIndex: 'operateUserType',
    width: 50,
    ellipsis: true,
  },
  {
    title: '操作模块',
    dataIndex: 'module',
    ellipsis: true,
  },
  {
    title: '操作内容',
    dataIndex: 'content',
    ellipsis: true,
  },
  {
    title: '请求路径',
    dataIndex: 'url',
    ellipsis: true,
  },
  {
    title: '返回结果',
    dataIndex: 'response',
    ellipsis: true,
  },
  {
    title: 'IP地区',
    dataIndex: 'ipRegion',
    ellipsis: true,
    width: 150,
  },
  {
    title: '客户端',
    dataIndex: 'userAgent',
    ellipsis: true,
  },
  {
    title: '操作时间',
    dataIndex: 'createTime',
    width: 150,
  },
  {
    title: '状态',
    dataIndex: 'successFlag',
    width: 60,
  },
  {
    title: '操作',
    dataIndex: 'action',
    fixed: 'right',
    width: 60,
  },
]);

const queryFormState = {
  userName: '',
  requestKeywords: '',
  keywords: '',
  successFlag: undefined,
  startDate: undefined,
  endDate: undefined,
  businessType: undefined as string | undefined,
  businessId: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10,
};
const queryForm = reactive({...queryFormState});
const createDateRange = ref([]);
const defaultChooseTimeRange = defaultTimeRanges;

const route = useRoute();

// 与后端 AdminOperateLogController 支持的业务类型白名单保持一致。
const BUSINESS_TYPES = ['PRODUCT', 'CUSTOMER', 'DELIVERY_ROUTE'];

// 从路由解析业务上下文：类型须在白名单内、ID 须为正整数，否则视为无上下文。
function routeBusinessContext(): {businessType?: string; businessId?: number} {
  const businessType = route.query.businessType;
  const businessId = Number(route.query.businessId);
  if (typeof businessType === 'string' && BUSINESS_TYPES.includes(businessType) && Number.isInteger(businessId) && businessId > 0) {
    return {businessType, businessId};
  }
  return {};
}

// 写入业务上下文：始终按当前路由重算，缺失时清空，避免旧的本地筛选覆盖业务视图。
function applyBusinessContext() {
  const context = routeBusinessContext();
  queryForm.businessType = context.businessType;
  queryForm.businessId = context.businessId;
}

const businessContextActive = computed(() => Boolean(queryForm.businessType) && Boolean(queryForm.businessId));

// 时间变动
function changeCreateDate(dates, dateStrings) {
  queryForm.startDate = dateStrings[0];
  queryForm.endDate = dateStrings[1];
}

const tableLoading = ref(false);
const tableData = ref([]);
const total = ref(0);

function resetQuery() {
  Object.assign(queryForm, queryFormState);
  createDateRange.value = [];
  applyBusinessContext();
  ajaxQuery();
}

function onSearch() {
  queryForm.pageNum = 1;
  ajaxQuery();
}

// 逐行规范化：脏 response 退化为 null、脏 userAgent 忽略，单行异常不影响整页。
function normalizeRow(row: Record<string, any>) {
  try {
    if (row.response) {
      row.response = JSON.parse(row.response);
    }
  } catch (e) {
    row.response = null;
  }
  try {
    if (row.userAgent) {
      let ua = uaparser(row.userAgent);
      row.browser = ua.browser && ua.browser.name;
      row.os = ua.os && ua.os.name;
      row.device = ua.device && ua.device.vendor ? ua.device.vendor + ua.device.model : '';
    }
  } catch (e) {
    // 客户端信息解析失败不影响该行其余字段展示
  }
}

async function ajaxQuery() {
  try {
    tableLoading.value = true;
    let responseModel = await operateLogApi.queryList(queryForm);
    const list = responseModel.data.list;
    for (const e of list) {
      normalizeRow(e);
    }
    total.value = responseModel.data.total;
    tableData.value = list;
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    tableLoading.value = false;
  }
}

onMounted(() => {
  applyBusinessContext();
  ajaxQuery();
});

// 刷新或切换业务对象时重新套用路由上下文，并从第一页查询。
watch(
  () => [route.query.businessType, route.query.businessId],
  () => {
    applyBusinessContext();
    queryForm.pageNum = 1;
    ajaxQuery();
  }
);

// ---------------------- 详情 ----------------------
const detailModal = ref();

function showDetail(operateLogId) {
  detailModal.value.show(operateLogId);
}
</script>
