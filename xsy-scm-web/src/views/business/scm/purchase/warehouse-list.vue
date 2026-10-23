<!-- 仓库列表。启用/停用是独立操作；省市区编码用于统计，地址用于收货和展示。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.warehouseCode" placeholder="仓库编码" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库名称" class="smart-query-form-item">
        <a-input v-model:value="queryForm.name" placeholder="仓库名称" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_WAREHOUSE_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:warehouse:query'">查询</a-button>
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
        <a-button type="primary" v-privilege="'scm:warehouse:add'" @click="open()">新建仓库</a-button>
        <a-button v-privilege="'scm:warehouse:scope:update'" @click="scopeModal?.open()">授权维护</a-button>
        <a-button v-privilege="'scm:warehouse:query'" @click="districtModal?.open()">商城配送区县</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_WAREHOUSE" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_PURCHASE_TABLE_ID.WAREHOUSE"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ SCM_WAREHOUSE_STATUS_ENUM[record.status]?.desc || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'areaText'">{{ areaText(record) }}</template>
        <template v-else-if="column.dataIndex === 'located'">
          <!-- 未定位的仓库无法参与路线规划，用图标 + Tooltip 表达，不占一整列文字 -->
          <a-tooltip :title="isLocated(record) ? '已定位，可参与路线规划' : '未定位，无法参与路线规划'">
            <CheckCircleOutlined v-if="isLocated(record)" class="located located--ok" />
            <ExclamationCircleOutlined v-else class="located located--warn" />
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 编辑和授权员工常驻，启用/停用收进“更多” -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:warehouse:update'" @click="open(record)">编辑</a-button>
            <a-button type="link" size="small" v-privilege="'scm:warehouse:scope:query'" @click="scopeModal?.openEmployees(record.id, record.name)">
              授权员工
            </a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
          </a-space>
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

  <a-modal :open="visible" :title="form.id ? '编辑仓库' : '新建仓库'" :confirm-loading="saving" @ok="save" @cancel="visible = false">
    <a-alert v-if="formError" :message="formError" type="error" show-icon />
    <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
      <a-form-item label="仓库名称" name="name">
        <a-input v-model:value="form.name" maxlength="150" />
      </a-form-item>
      <a-form-item label="所在地区">
        <AreaCascader type="province_city_district" v-model:value="area" style="width: 100%" placeholder="省 / 市 / 区" @change="onAreaChange" />
      </a-form-item>
      <a-form-item label="地址" name="address">
        <a-input v-model:value="form.address" maxlength="255" @change="Object.assign(form, emptyLocation())" />
      </a-form-item>
      <a-form-item label="地图定位">
        <ScmMapPicker :value="form" :address="form.address" @change="Object.assign(form, $event)" />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-input v-model:value="form.remark" maxlength="500" />
      </a-form-item>
      <!-- 批次追踪只在新建空仓时决定：开启后不可关闭，因此编辑态只读回显 -->
      <a-form-item label="批次追踪">
        <a-switch v-model:checked="form.lotTrackingEnabled" :disabled="form.id !== undefined" />
      </a-form-item>
    </a-form>
  </a-modal>

  <WarehouseScopeModal ref="scopeModal" />
  <MallDistrictWarehouseModal ref="districtModal" />
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {emptyLocation, isLocated, locationError} from '/@/components/business/scm/map/types';
import {CheckCircleOutlined, ExclamationCircleOutlined} from '@ant-design/icons-vue';
import {computed, nextTick, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import WarehouseScopeModal from './components/warehouse-scope-modal.vue';
import MallDistrictWarehouseModal from './components/mall-district-warehouse-modal.vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_TABLE_ID, SCM_WAREHOUSE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {Warehouse, WarehouseFormModel, WarehouseQuery} from './purchase-types';
import {toWarehousePayload} from './warehouse-form-model';
import {purchaseError} from './purchase-errors';
import {hasPermission} from '../common/scm-permission';
import {areaColumnsOf, areaNodesOf} from '../common/scm-area';

const queryForm = reactive<WarehouseQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<Warehouse[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const saving = ref(false);
const formError = ref('');
const form = ref<WarehouseFormModel>({warehouseCode: '', name: ''});
const formRef = ref<FormInstance>();

/** 逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  name: [{required: true, message: '请填写仓库名称', trigger: 'blur'}],
};
/** 省 / 市 / 区的选中路径，与 form 的 6 列之间由 scm-area 互转。 */
const area = ref<AreaNode[]>([]);
/** 员工—仓库授权维护（独立权限点，与仓库主数据编辑分开）。 */
const scopeModal = ref<InstanceType<typeof WarehouseScopeModal>>();
const districtModal = ref<InstanceType<typeof MallDistrictWarehouseModal>>();

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  Object.assign(form.value, areaColumnsOf(nodes), emptyLocation());
}

let requestId = 0;

/** 列表列。编码单独一列；“未定位”用图标提示，未定位不能参与路线规划。 */
const columns = ref<TableColumnsType<Warehouse>>([
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 160},
  {title: '仓库名称', dataIndex: 'name', width: 200},
  {title: '所在地区', dataIndex: 'areaText', width: 190},
  {title: '详细地址', dataIndex: 'address', width: 240},
  {title: '定位', dataIndex: 'located', width: 80},
  {title: '状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '备注', dataIndex: 'remark', width: 200},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);

/** 省市区名称拼成一行；未选择时显示“—”。 */
function areaText(record: Warehouse): string {
  const nodes = areaNodesOf(record);
  return nodes.length ? nodes.map((node) => node.label).join(' / ') : '—';
}

/** 更多菜单无法使用 `v-privilege`，改用 `hasPermission` 判断。 */
const canEnable = computed(() => hasPermission('scm:warehouse:enable'));
const canDisable = computed(() => hasPermission('scm:warehouse:disable'));

/** 按当前状态显示启用或停用。 */
function rowActions(row: Warehouse): ScmActionItem[] {
  const enabled = row.status === 'ENABLED';
  return [{key: 'state', label: enabled ? '停用' : '启用', danger: enabled, hidden: enabled ? !canDisable.value : !canEnable.value}];
}

function onRowAction(key: string, row: Warehouse) {
  if (key !== 'state') {
    return;
  }
  if (row.status === 'ENABLED') {
    disable(row);
  } else {
    enable(row);
  }
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 列表加载失败由页头的 Alert 承担（带重试），不再让全局 toast 重复说一遍
    const r = await warehouseApi.query(queryForm, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.warehouseCode = undefined;
  queryForm.name = undefined;
  queryForm.status = undefined;
  onSearch();
}

async function open(row?: Warehouse) {
  formError.value = '';
  form.value = row
    ? {
        id: row.id,
        version: row.version,
        warehouseCode: row.warehouseCode ?? '',
        name: row.name ?? '',
        address: row.address ?? null,
        longitude: row.longitude ?? null,
        latitude: row.latitude ?? null,
        geomCrs: row.geomCrs ?? null,
        remark: row.remark ?? null,
        lotTrackingEnabled: row.lotTrackingEnabled === true,
        provinceCode: row.provinceCode ?? null,
        provinceName: row.provinceName ?? null,
        cityCode: row.cityCode ?? null,
        cityName: row.cityName ?? null,
        districtCode: row.districtCode ?? null,
        districtName: row.districtName ?? null,
      }
    : {warehouseCode: '', name: ''};
  area.value = [];
  visible.value = true;
  // 弹窗首次打开才挂载，需等 nextTick 后再回填地区选择器
  await nextTick();
  area.value = areaNodesOf(form.value);
}

async function save() {
  // 必填项走表单校验（错误显示在字段下方）；定位是跨字段的业务前置条件，仍用顶部提示
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  formError.value = locationError(form.value) ?? '';
  if (formError.value) return;
  saving.value = true;
  try {
    const payload = toWarehousePayload(form.value);
    if (form.value.id === undefined) {
      await warehouseApi.create(payload);
      message.success('仓库已创建');
    } else {
      await warehouseApi.update(payload);
      message.success('仓库已更新');
    }
    visible.value = false;
    await queryData();
  } catch {
    // 保存失败由全局提示给出
  } finally {
    saving.value = false;
  }
}

async function enable(row: Warehouse) {
  try {
    await warehouseApi.enable({id: row.id, version: row.version!});
    message.success('仓库已启用');
    await queryData();
  } catch {
    // 状态操作失败由全局提示给出
  }
}

/** 停用是严格模式命令，后端会挡下仍有库存余额 / 在途采购单 / 待入库收货单的仓库。 */
function disable(row: Warehouse) {
  Modal.confirm({
    title: '停用该仓库？',
    content: '停用后不能再用于新的采购需求、采购单与收货单。',
    okType: 'danger',
    onOk: async () => {
      // 失败原因由全局提示给出
      await warehouseApi.disable({id: row.id, version: row.version!});
      message.success('仓库已停用');
      await queryData();
    },
  });
}

onMounted(queryData);
</script>

<style scoped>
/* 定位状态：语义色与配送线路页同一档位（绿=已定位、橙=未定位） */
.located--ok {
  color: var(--scm-success, #52c41a);
}

.located--warn {
  color: var(--scm-warning, #faad14);
}
</style>
