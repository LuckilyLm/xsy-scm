<!--
  * 客户档案列表
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/customer/customer-list.vue
  * （Copy First + Adapt，376 行 → 剪枝 + 类型补全）。
  *
  * 剪枝（W2 范围外）：
  * - 删除「账户余额」列与相关展示（财务域，W2 不做）；
  * - 删除**批量删除**（Target Design Q14：W2 只做单条删除，后端也没有 batchDelete 端点）；
  * - 删除 `:row-selection` / `onSelectChange` / `selectedRowKeyList`（随批量删除一起移除）；
  * - 删除客户等级、区域、二维码、可见性等列。
  *
  * 适配：
  * - 删除全部 `resizable: true` / `@resizeColumn` / `handleResizeColumn` —— V2 没有 `TableHeaderCell`，
  *   全库 0 命中（Target Design §5.2）；
  * - 权限码 `customer:xxx` → `scm:customer:xxx`；
  * - 状态变更从 C 的 `Modal.confirm` 改为 `a-dropdown` + 二次确认，
  *   因为 V2 有 4 个状态（潜在 / 合作中 / 暂停合作 / 黑名单），不是 C 的「启用 / 停用」二态；
  * - `columns` 显式标注 `TableColumnsType<CustomerRow>`（C 是裸数组，V2 严格模式下是 TS2322）；
  * - 补 `requestId` 请求序号，避免快速翻页时旧响应覆盖新响应。
-->
<template>
  <section aria-label="客户档案">
    <a-form class="smart-query-form" layout="inline" @finish="search">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="编码 / 名称 / 联系人 / 电话" style="width: 240px" />
        </a-form-item>
        <a-form-item label="客户类型" class="smart-query-form-item">
          <CustomerTypeSelect v-model:value="filters.customerTypeId" width="180px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="CUSTOMER_STATUS_ENUM" width="130px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" html-type="submit">查询</a-button>
            <a-button @click="reset">重置</a-button>
            <a-button type="link" @click="advanced = !advanced">{{ advanced ? '收起筛选' : '高级筛选' }}</a-button>
          </a-space>
        </a-form-item>
      </a-row>
      <a-row v-if="advanced" class="smart-query-form-row">
        <a-form-item label="结算方式" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.settleMode" enum-name="SETTLE_MODE_ENUM" width="140px" />
        </a-form-item>
        <a-form-item label="上级集团" class="smart-query-form-item">
          <CustomerSelect v-model:value="filters.parentCustomerId" type-code="GROUP" width="220px" placeholder="仅集团客户" />
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:customer:add'" type="primary" @click="drawer?.open()">新增客户</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action><a-button size="small" @click="load">重新加载</a-button></template>
      </a-alert>
      <a-table
        :data-source="rows"
        :columns="columns"
        row-key="customerId"
        :loading="loading"
        :pagination="false"
        size="small"
        bordered
        :scroll="{ x: 1800 }"
        @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" @click="detail(record.customerId)">{{ record.name }}</a-button>
          </template>
          <span v-else-if="column.dataIndex === 'parentCustomerName'">{{ record.parentCustomerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'sellerName'">{{ record.sellerName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactName'">{{ record.contactName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'contactPhone'">{{ record.contactPhone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'settleMode'">{{ settleModeText(record.settleMode) }}</span>
          <span v-else-if="column.dataIndex === 'creditLimit'" class="amount">{{ record.creditLimit ?? '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
            <a-button type="link" size="small" @click="detail(record.customerId)">详情</a-button>
            <a-button v-privilege="'scm:customer:update'" type="link" size="small" @click="drawer?.open(record.customerId)">编辑</a-button>
            <a-dropdown>
              <a-button v-privilege="'scm:customer:status'" type="link" size="small">状态</a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item
                    v-for="item in statusOptions"
                    :key="item.value"
                    :disabled="item.value === record.status"
                    @click="changeStatus(record, item.value)"
                  >
                    {{ item.desc }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-popconfirm title="确认删除此客户？" @confirm="remove(record)">
              <a-button v-privilege="'scm:customer:delete'" type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="filters.pageNum"
          v-model:page-size="filters.pageSize"
          :total="total"
          show-size-changer
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
        />
      </div>
    </a-card>

    <CustomerDrawer ref="drawer" @saved="load" />
  </section>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { message } from 'ant-design-vue';
  import type { TableColumnsType, TableProps } from 'ant-design-vue';
  import { customerApi } from '/@/api/business/scm/customer-api';
  import type { CustomerQuery, CustomerRow, CustomerStatus, ScmId } from '/@/types/business/scm/customer';
  import { CUSTOMER_STATUS_ENUM, SETTLE_MODE_ENUM } from '/@/constants/business/scm/customer-const';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
  import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
  import CustomerDrawer from './components/customer-form-drawer.vue';
  import { customerError } from './customer-errors';
import { datetime } from '../common/scm-display';

  const router = useRouter();
  const filters = reactive<CustomerQuery>({ pageNum: 1, pageSize: 20 });
  const rows = ref<CustomerRow[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref('');
  const advanced = ref(false);
  const drawer = ref<InstanceType<typeof CustomerDrawer>>();

  /** 状态下拉的可选项：直接展开 SmartEnum，避免手写一份会和后端漂移的文案表。 */
  const statusOptions = Object.values(CUSTOMER_STATUS_ENUM);

  const statusText = (value: CustomerStatus): string => CUSTOMER_STATUS_ENUM[value]?.desc || value;
  const settleModeText = (value: string): string => SETTLE_MODE_ENUM[value]?.desc || value;
  const statusColor = (value: CustomerStatus): string => {
    if (value === 'COOPERATING') return 'green';
    if (value === 'SUSPENDED') return 'orange';
    if (value === 'BLACKLIST') return 'red';
    return 'default';
  };

  const columns = ref<TableColumnsType<CustomerRow>>([
    { title: '客户编码', dataIndex: 'customerCode', width: 150, sorter: true },
    { title: '客户名称', dataIndex: 'name', width: 200, sorter: true },
    { title: '客户类型', dataIndex: 'customerTypeName', width: 110 },
    { title: '上级集团', dataIndex: 'parentCustomerName', width: 160 },
    { title: '业务员', dataIndex: 'sellerName', width: 110 },
    { title: '联系人', dataIndex: 'contactName', width: 110 },
    { title: '联系电话', dataIndex: 'contactPhone', width: 140 },
    { title: '结算方式', dataIndex: 'settleMode', width: 110, align: 'center' },
    { title: '授信额度', dataIndex: 'creditLimit', width: 140, align: 'right' },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true },
    { title: '更新时间', dataIndex: 'updatedAt', width: 190, sorter: true, customRender: ({ text }) => datetime(text) },
    { title: '操作', dataIndex: 'action', width: 220, align: 'right', fixed: 'right' },
  ]);

  let requestId = 0;

  async function load() {
    const request = ++requestId;
    loading.value = true;
    error.value = '';
    try {
      const response = await customerApi.query({ ...filters });
      if (request === requestId) {
        rows.value = response.data.list;
        total.value = Number(response.data.total);
      }
    } catch (e) {
      if (request === requestId) error.value = customerError(e);
    } finally {
      if (request === requestId) loading.value = false;
    }
  }

  function search() {
    filters.pageNum = 1;
    void load();
  }

  function reset() {
    Object.assign(filters, {
      pageNum: 1,
      keyword: undefined,
      customerTypeId: undefined,
      status: undefined,
      settleMode: undefined,
      parentCustomerId: undefined,
      sortItemList: undefined,
    });
    void load();
  }

  // 排序白名单：只映射后端 CustomerQueryService.SORTABLE 允许的四列。
  const sortChanged: TableProps<CustomerRow>['onChange'] = (_page, _filters, sort) => {
    const item = Array.isArray(sort) ? sort[0] : sort;
    const names: Record<string, string> = {
      customerCode: 'customer_code',
      name: 'name',
      status: 'status',
      updatedAt: 'updated_at',
    };
    const column = names[String(item.field)];
    filters.sortItemList = item.order && column ? [{ column, isAsc: item.order === 'ascend' }] : undefined;
    search();
  };

  function detail(id: ScmId) {
    void router.push({ path: '/customer/customer-detail', query: { customerId: String(id) } });
  }

  /** `item.value` 来自 SmartEnum 展开，运行时必然是后端枚举码之一。 */
  async function changeStatus(row: CustomerRow, status: string) {
    try {
      await customerApi.updateStatus({ customerId: row.customerId, version: row.version, status: status as CustomerStatus });
      message.success('客户状态已更新');
      await load();
    } catch (e) {
      error.value = customerError(e);
    }
  }

  async function remove(row: CustomerRow) {
    try {
      await customerApi.delete({ customerId: row.customerId, version: row.version });
      message.success('客户已删除');
      await load();
    } catch (e) {
      error.value = customerError(e);
    }
  }

  onMounted(load);
</script>

<style scoped>
  .amount {
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
</style>
