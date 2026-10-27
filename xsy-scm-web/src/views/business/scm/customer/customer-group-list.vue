<!-- 客户分组：组内客户共用分组价；每个分组至少保留一个客户。 -->
<template>
  <section aria-label="客户分组">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="关键字" class="smart-query-form-item">
          <a-input v-model:value="query.keyword" allow-clear placeholder="分组编码 / 分组名称" style="width: 220px" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <a-select v-model:value="query.status" allow-clear placeholder="全部" style="width: 120px" :options="statusOptions" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-space :size="10">
            <a-button type="primary" v-privilege="'scm:customer:group:query'" @click="search">查询</a-button>
            <a-button @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-button type="primary" v-privilege="'scm:customer:group:add'" @click="openForm()">新增分组</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_CUSTOMER_GROUP" :refresh="load" />
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <a-table
        :data-source="rows"
        :columns="columns"
        row-key="groupId"
        size="small"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{x: scmColumnsWidth(columns)}"
      >
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'status'">
            <ScmStatusTag :tone="record.status === 'ENABLED' ? 'success' : 'neutral'" :label="statusText(record.status)" />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <div class="smart-table-operate scm-table-actions">
              <a-button type="link" v-privilege="'scm:customer:group:update'" @click="openForm(record.groupId)">编辑</a-button>
              <a-button type="link" danger v-privilege="'scm:customer:group:delete'" @click="remove(record)">删除</a-button>
            </div>
          </template>
          <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
        </template>
      </a-table>
      <div class="smart-query-table-page">
        <a-pagination
          v-model:current="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          show-size-changer
          :page-size-options="SCM_PAGE_SIZE_OPTIONS"
          :show-total="(n: number) => `共 ${n} 条`"
          @change="load"
        />
      </div>
    </a-card>

    <a-modal
      v-model:open="formVisible"
      :title="editingId ? '编辑客户分组' : '新增客户分组'"
      :width="640"
      :confirm-loading="saving"
      :ok-button-props="{disabled: saving}"
      @ok="submit"
    >
      <a-spin :spinning="formLoading">
        <a-alert v-if="formError" :message="formError" type="error" show-icon class="smart-margin-bottom10" />
        <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
          <a-form-item label="分组编码" name="groupCode">
            <a-input v-model:value="form.groupCode" :maxlength="32" placeholder="例如 G-YUJIN" />
          </a-form-item>
          <a-form-item label="分组名称" name="groupName">
            <a-input v-model:value="form.groupName" :maxlength="64" />
          </a-form-item>
          <a-form-item label="状态" name="status">
            <a-select v-model:value="form.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item name="customerIds">
            <template #label>
              组内客户
              <ScmFieldHelp label="组内客户" text="至少选择一个客户；同一个客户只能属于一个分组，保存后按本名单整体替换" />
            </template>
            <a-select
              v-model:value="form.customerIds"
              mode="multiple"
              allow-clear
              show-search
              option-filter-prop="label"
              placeholder="选择客户"
              :options="customerOptions"
              :max-tag-count="6"
            />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {Modal, message, type FormInstance} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi, customerGroupApi} from '/@/api/business/scm/customer-api';
import type {CustomerGroupForm, CustomerGroupQuery, CustomerGroupRow, CustomerOption, ScmId} from '/@/types/business/scm/customer';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {customerError} from './customer-errors';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const statusOptions = [
  {value: 'ENABLED', label: '启用'},
  {value: 'DISABLED', label: '停用'},
];

const columns = ref<TableColumnsType<CustomerGroupRow>[number][]>([
  {title: '分组名称', dataIndex: 'groupName', width: 220},
  {title: '组内客户数', dataIndex: 'customerCount', width: 120},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '操作', dataIndex: 'action', width: 120, align: 'center', fixed: 'right'},
]);

const query = reactive<CustomerGroupQuery>({pageNum: 1, pageSize: 20, keyword: undefined, status: undefined});
const rows = ref<CustomerGroupRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');

const formVisible = ref(false);
const formLoading = ref(false);
const saving = ref(false);
const formError = ref('');
const formRef = ref<FormInstance>();
const editingId = ref<ScmId>();
const editingVersion = ref<number>();
const form = reactive<CustomerGroupForm>({groupCode: '', groupName: '', status: 'ENABLED', customerIds: []});
const customerOptions = ref<{value: ScmId; label: string}[]>([]);

const formRules = {
  groupCode: [{required: true, whitespace: true, message: '请输入分组编码'}],
  groupName: [{required: true, whitespace: true, message: '请输入分组名称'}],
  customerIds: [
    {
      validator: () => (form.customerIds.length ? Promise.resolve() : Promise.reject(new Error('请至少选择一个客户'))),
      trigger: 'change',
    },
  ],
};

function statusText(status?: string): string {
  return status === 'DISABLED' ? '停用' : '启用';
}

let requestId = 0;
async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await customerGroupApi.query({...query}, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
    }
  } catch (e) {
    if (id === requestId) error.value = customerError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = undefined;
  query.status = undefined;
  search();
}

async function loadCustomerOptions() {
  const response = await customerApi.optionList();
  customerOptions.value = (response.data ?? []).map((item: CustomerOption) => ({
    value: item.customerId,
    label: item.name,
  }));
}

async function openForm(groupId?: ScmId) {
  editingId.value = groupId;
  editingVersion.value = undefined;
  formVisible.value = true;
  formError.value = '';
  Object.assign(form, {groupCode: '', groupName: '', status: 'ENABLED', customerIds: []});
  formRef.value?.clearValidate();
  formLoading.value = true;
  try {
    await loadCustomerOptions();
    if (groupId == null) return;
    const response = await customerGroupApi.detail(groupId, {suppressGlobalErrorMessage: true});
    editingVersion.value = response.data.version;
    Object.assign(form, {
      groupCode: response.data.groupCode,
      groupName: response.data.groupName,
      status: response.data.status,
      customerIds: (response.data.members ?? []).map((member) => member.customerId),
    });
  } catch (e) {
    formError.value = customerError(e);
  } finally {
    formLoading.value = false;
  }
}

async function submit() {
  // 编辑对象未加载完成时禁止提交，避免空表单覆盖原分组。
  if (formError.value) return;
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  saving.value = true;
  try {
    const payload = {
      groupCode: form.groupCode.trim(),
      groupName: form.groupName.trim(),
      status: form.status,
      customerIds: form.customerIds,
    };
    if (editingId.value != null) {
      await customerGroupApi.update({...payload, groupId: editingId.value, version: editingVersion.value});
    } else {
      await customerGroupApi.add(payload);
    }
    message.success('客户分组已保存');
    formVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

function remove(record: CustomerGroupRow) {
  Modal.confirm({
    title: `删除分组「${record.groupName}」？`,
    content: '组内客户会移出分组，分组价记录保留在价格变更历史中。',
    onOk: async () => {
      await customerGroupApi.delete({groupId: record.groupId, version: record.version});
      await load();
    },
  });
}

onMounted(load);
</script>
