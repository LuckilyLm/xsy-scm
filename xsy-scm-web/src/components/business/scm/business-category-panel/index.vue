<!-- 业务分类表格：按 scope 维护一棵独立小树，客户端/供应商页面各自挂载一份。 -->
<template>
  <a-card size="small" :bordered="false">
    <div class="category-panel__header">
      <h3 class="category-panel__title">{{ title }}</h3>
      <a-button type="primary" v-privilege="'scm:business-category:add'" @click="openCreate">新建{{ title }}</a-button>
    </div>

    <a-alert v-if="error" :message="error" type="error" show-icon class="banner">
      <template #action>
        <a-button size="small" @click="load">重试</a-button>
      </template>
    </a-alert>

    <a-table
      size="small"
      bordered
      :columns="columns"
      :data-source="pagedTree"
      :loading="loading"
      :pagination="false"
      row-key="categoryId"
      default-expand-all-rows
      :scroll="{x: 760}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'name'">
          <span class="category-name">{{ record.name }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="record.status === 'ENABLED' ? 'success' : 'neutral'" :label="enumLabel(ENABLE_STATUS_ENUM, record.status)" />
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0">
            <a-button v-privilege="'scm:business-category:update'" type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-privilege="'scm:business-category:delete'" type="link" size="small" danger @click="remove(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="xsy-query-table-page">
      <a-pagination
        show-quick-jumper
        show-less-items
        v-model:current="pageNum"
        v-model:page-size="pageSize"
        :total="tree.length"
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        :show-total="(n: number) => `共 ${n} 条`"
      />
    </div>

    <a-modal v-model:open="formOpen" :title="formTitle" :confirm-loading="saving" :width="520" destroy-on-close @ok="save">
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-form-item label="分类名称" name="name">
          <a-input v-model:value="form.name" :maxlength="64" placeholder="例如：蔬菜" />
        </a-form-item>
        <a-form-item label="分类编码" name="categoryCode">
          <a-input v-model:value="form.categoryCode" :maxlength="64" placeholder="同一作用域内唯一，例如：VEG" />
        </a-form-item>
        <a-form-item label="上级分类" name="parentId">
          <BusinessCategoryTreeSelect
            v-model:value="form.parentId"
            :scope="form.scope"
            mode="select"
            :exclude-id="form.categoryId"
            placeholder="不选表示一级分类"
          />
        </a-form-item>
        <a-form-item label="排序" name="sortOrder">
          <a-input-number v-model:value="form.sortOrder" :min="0" :max="99999" :precision="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-select v-model:value="form.status" :options="ENABLE_STATUS_ENUM" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal, type FormInstance, type TableColumnsType} from 'ant-design-vue';
import {businessCategoryApi} from '/@/api/business/scm/business-category-api';
import type {BusinessCategory, BusinessCategoryForm, BusinessCategoryScope} from '/@/types/business/scm/business-category';
import BusinessCategoryTreeSelect from '/@/components/business/scm/business-category-tree-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {ENABLE_STATUS_ENUM, enumLabel} from '/@/constants/business/scm/product-const';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const props = defineProps<{scope: BusinessCategoryScope; title: string}>();

const tree = ref<BusinessCategory[]>([]);
const loading = ref(false);
const error = ref('');
const pageNum = ref(1);
const pageSize = ref(20);
const requestId = ref(0);

// 接口一次返回整棵树，按一级分类分页；子分类跟随父节点展示，不单独拆页。
const pagedTree = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value;
  return tree.value.slice(start, start + pageSize.value);
});

const formOpen = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();

const emptyForm = (): BusinessCategoryForm => ({
  categoryId: undefined,
  version: undefined,
  scope: props.scope,
  parentId: null,
  categoryCode: '',
  name: '',
  sortOrder: 0,
  status: 'ENABLED',
});
const form = reactive<BusinessCategoryForm>(emptyForm());
const formTitle = computed(() => `${form.categoryId == null ? '新建' : '编辑'}${props.title}`);

const rules = {
  name: [{required: true, message: '请输入分类名称', trigger: 'blur'}],
  categoryCode: [{required: true, message: '请输入分类编码', trigger: 'blur'}],
  status: [{required: true, message: '请选择状态', trigger: 'change'}],
};

const columns: TableColumnsType<BusinessCategory> = [
  {title: '分类名称', dataIndex: 'name', width: 320},
  {title: '层级', dataIndex: 'level', width: 80, align: 'center'},
  {title: '排序', dataIndex: 'sortOrder', width: 80},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '操作', dataIndex: 'action', width: 140, align: 'center', fixed: 'right'},
];

async function load() {
  const request = ++requestId.value;
  loading.value = true;
  error.value = '';
  try {
    const response = await businessCategoryApi.tree(props.scope, {suppressGlobalErrorMessage: true});
    if (request === requestId.value) {
      tree.value = response.data ?? [];
      const maxPage = Math.max(1, Math.ceil(tree.value.length / pageSize.value));
      if (pageNum.value > maxPage) pageNum.value = maxPage;
    }
  } catch (e) {
    if (request === requestId.value) {
      tree.value = [];
      error.value = e instanceof Error ? e.message : '分类加载失败，请重试';
    }
  } finally {
    if (request === requestId.value) loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, emptyForm());
  formOpen.value = true;
}

function openEdit(row: BusinessCategory) {
  Object.assign(form, {
    categoryId: row.categoryId,
    version: row.version,
    scope: row.scope,
    parentId: row.parentId ?? null,
    categoryCode: row.categoryCode,
    name: row.name,
    sortOrder: row.sortOrder,
    status: row.status,
  });
  formOpen.value = true;
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (form.categoryId == null) {
      await businessCategoryApi.add({...form});
      message.success('分类已创建');
    } else {
      await businessCategoryApi.update({...form});
      message.success('分类已保存');
    }
    formOpen.value = false;
    pageNum.value = 1;
    await load();
  } catch {
    // 失败信息由请求层统一提示
  } finally {
    saving.value = false;
  }
}

function remove(row: BusinessCategory) {
  Modal.confirm({
    title: '删除分类',
    content: `确定删除「${row.name}」吗？有子分类或已被客户/供应商使用时不能删除。`,
    okType: 'danger',
    onOk: async () => {
      await businessCategoryApi.delete(row.categoryId, row.version ?? 0);
      message.success('分类已删除');
      await load();
    },
  });
}

onMounted(load);
</script>

<style scoped lang="less">
.category-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.category-panel__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.banner {
  margin-bottom: 12px;
}
.category-name {
  font-weight: 500;
}
</style>
