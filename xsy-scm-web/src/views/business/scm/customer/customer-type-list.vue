<!--
  客户类型管理（平铺分页表）。

  删除走 `POST /scm/customer/type/delete` 并回传 `version`。
  排序只开放 `typeCode` / `name` / `status` 三列 —— 后端 `CustomerTypeVO` 不返回
  `updatedAt`，开放它会出现「点了排序但后端没有对应可见列」的假象。
-->
<template>
  <section aria-label="客户类型">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="类型编码 / 名称" style="width: 220px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <SmartEnumSelect v-model:value="filters.status" enum-name="CUSTOMER_TYPE_STATUS_ENUM" width="120px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space>
            <a-button type="primary" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button v-privilege="'scm:customer:type:add'" type="primary" @click="modal?.open()">新增客户类型</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER_TYPE" :refresh="load"/>
      </a-row>
      <a-table
          :data-source="rows"
          :columns="columns"
          row-key="typeId"
          :loading="loading"
          :pagination="false"
          size="small"
          bordered
          :scroll="{ x: 660 }"
          @change="sortChanged"
      >
        <template #bodyCell="{ column, record }">
          <a-tag v-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ statusText(record.status) }}
          </a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:customer:type:update'" type="link" size="small" @click="modal?.open(record)">
              编辑
            </a-button>
            <a-popconfirm title="确认删除此客户类型？" @confirm="remove(record)">
              <a-button v-privilege="'scm:customer:type:delete'" type="link" danger size="small">删除</a-button>
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

    <CustomerTypeModal ref="modal" @saved="load"/>
  </section>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {customerTypeApi} from '/@/api/business/scm/customer-type-api';
import type {CustomerType, CustomerTypeQuery} from '/@/types/business/scm/customer';
import {CUSTOMER_TYPE_STATUS_ENUM} from '/@/constants/business/scm/customer-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import CustomerTypeModal from './components/customer-type-form-modal.vue';
import {customerError} from './customer-errors';
import {useScmErrorToast} from '../common/scm-error-toast';

const filters = reactive<CustomerTypeQuery>({pageNum: 1, pageSize: 20});
const rows = ref<CustomerType[]>([]);
const total = ref(0);
const loading = ref(false);
const error = useScmErrorToast();
const modal = ref<InstanceType<typeof CustomerTypeModal>>();

/** 枚举值 → 中文；查不到时退回原值，避免表格出现空白单元格。 */
const statusText = (value: string): string => CUSTOMER_TYPE_STATUS_ENUM[value]?.desc || value;

// 字典表只有四列值得常驻：编码、名称、状态、操作。创建时间不影响判断，不默认摊在列表上。
const columns = ref<TableColumnsType<CustomerType>>([
  {title: '类型编码', dataIndex: 'typeCode', width: 200, sorter: true},
  {title: '类型名称', dataIndex: 'name', width: 220, sorter: true},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center', sorter: true},
  {title: '操作', dataIndex: 'action', width: 140, align: 'center', fixed: 'right'},
]);

// 请求序号：避免快速切页时旧响应覆盖新响应（与 product-list 同策略）。
let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await customerTypeApi.query({...filters});
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
  Object.assign(filters, {pageNum: 1, keyword: undefined, status: undefined, sortItemList: undefined});
  void load();
}

// 排序白名单：只映射后端 CustomerTypeService.SORTABLE 允许的列。
const sortChanged: TableProps<CustomerType>['onChange'] = (_page, _filters, sort) => {
  const item = Array.isArray(sort) ? sort[0] : sort;
  const names: Record<string, string> = {typeCode: 'type_code', name: 'name', status: 'status'};
  const column = names[String(item.field)];
  filters.sortItemList = item.order && column ? [{column, isAsc: item.order === 'ascend'}] : undefined;
  search();
};

async function remove(row: CustomerType) {
  try {
    await customerTypeApi.delete(row.typeId, row.version);
    message.success('客户类型已删除');
    await load();
  } catch (e) {
    error.value = customerError(e);
  }
}

onMounted(load);
</script>
