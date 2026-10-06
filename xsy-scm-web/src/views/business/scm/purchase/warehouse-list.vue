<!--
  仓库列表。
  - 启用 / 停用走独立命令，基础信息表单不直接修改状态；
  - 「所在地区」的省市区级联与 `address` 自由文本<b>并存</b>：编码供大屏按市聚合，
    地址仍是收货与展示口径。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.warehouseCode" placeholder="仓库编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="仓库名称" class="smart-query-form-item">
        <a-input v-model:value="queryForm.name" placeholder="仓库名称" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_WAREHOUSE_STATUS_ENUM" v-model:value="queryForm.status" width="140px"/>
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
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_WAREHOUSE" :refresh="queryData"/>
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
        :scroll="{ x: 1180 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ SCM_WAREHOUSE_STATUS_ENUM[record.status]?.desc || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'areaText'">
          <!-- 复合单元：区域在上、详细地址在下。地址是库管实际找货的凭据，
               不能因为"省市区能定位"就整列删掉，但也不该再占一列 260px -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ areaText(record) }}</span>
            <span v-if="record.address" class="scm-cell-stack__sub">{{ record.address }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'located'">
          <!-- 未定位的仓库无法参与路线规划，用图标 + Tooltip 表达，不占一整列文字 -->
          <a-tooltip :title="isLocated(record) ? '已定位，可参与路线规划' : '未定位，无法参与路线规划'">
            <CheckCircleOutlined v-if="isLocated(record)" class="located located--ok"/>
            <ExclamationCircleOutlined v-else class="located located--warn"/>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内只留高频的「编辑」与「授权员工」；启用/停用是状态机动作，
               停用还会影响「默认仓库」判定（唯一启用仓库），不与普通动作同排常驻 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:warehouse:update'" @click="open(record)">编辑</a-button>
            <a-button type="link" size="small" v-privilege="'scm:warehouse:scope:query'"
                      @click="scopeModal?.openEmployees(record.id, record.name)">
              授权员工
            </a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)"/>
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

  <a-modal
      :open="visible"
      :title="form.id ? '编辑仓库' : '新建仓库'"
      :confirm-loading="saving"
      @ok="save"
      @cancel="visible = false"
  >
    <a-alert v-if="formError" :message="formError" type="error" show-icon/>
    <a-form :model="form" layout="vertical">
      <a-form-item label="仓库编码" name="warehouseCode" required>
        <a-input v-model:value="form.warehouseCode" maxlength="64" :disabled="!!form.id"/>
      </a-form-item>
      <a-form-item label="仓库名称" name="name" required>
        <a-input v-model:value="form.name" maxlength="150"/>
      </a-form-item>
      <a-form-item label="所在地区">
        <AreaCascader
            type="province_city_district"
            v-model:value="area"
            style="width: 100%"
            placeholder="省 / 市 / 区"
            @change="onAreaChange"
        />
        <div class="ant-form-item-extra">留空则不参与地图分布统计</div>
      </a-form-item>
      <a-form-item label="地址" name="address">
        <a-input v-model:value="form.address" maxlength="255" @change="Object.assign(form, emptyLocation())"/>
      </a-form-item>
      <a-form-item label="地图定位">
        <ScmMapPicker :value="form" :address="form.address" @change="Object.assign(form, $event)"/>
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-input v-model:value="form.remark" maxlength="500"/>
      </a-form-item>
    </a-form>
  </a-modal>

  <WarehouseScopeModal ref="scopeModal"/>
</template>

<script setup lang="ts">
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {emptyLocation, isLocated, locationError} from '/@/components/business/scm/map/types';
import {CheckCircleOutlined, ExclamationCircleOutlined} from '@ant-design/icons-vue';
import {computed, nextTick, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import WarehouseScopeModal from './components/warehouse-scope-modal.vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_TABLE_ID, SCM_WAREHOUSE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {Warehouse, WarehousePayload, WarehouseQuery} from './purchase-types';
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
const form = ref<WarehousePayload>({warehouseCode: '', name: ''});
/** 省 / 市 / 区的选中路径，与 form 的 6 列之间由 scm-area 互转。 */
const area = ref<AreaNode[]>([]);
/** 员工—仓库授权维护（独立权限点，与仓库主数据编辑分开）。 */
const scopeModal = ref<InstanceType<typeof WarehouseScopeModal>>();

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  Object.assign(form.value, areaColumnsOf(nodes), emptyLocation());
}

let requestId = 0;

/**
 * 列表列。仓库是配置类主数据，与普通主数据口径不同：
 * 编码保留成独立列（对账、盘点、接口对接都用它）；创建时间下沉到详情；
 * 「未定位」是唯一要一眼挑出来的信号（未定位无法参与路线规划），用图标 + Tooltip。
 */
const columns = ref<TableColumnsType<Warehouse>>([
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 160},
  {title: '仓库名称', dataIndex: 'name', width: 200},
  {title: '区域 / 地址', dataIndex: 'areaText', width: 280},
  {title: '定位', dataIndex: 'located', align: 'center', width: 80},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
  {title: '备注', dataIndex: 'remark', width: 200},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);

/** 区域摘要：省 / 市 / 区三级名称快照拼一行；未选任何层级时给 `—`（全域空值口径）。 */
function areaText(record: Warehouse): string {
  const nodes = areaNodesOf(record);
  return nodes.length ? nodes.map((node) => node.label).join(' / ') : '—';
}

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canEnable = computed(() => hasPermission('scm:warehouse:enable'));
const canDisable = computed(() => hasPermission('scm:warehouse:disable'));

/** 状态机动作按当前状态二选一，与原行内按钮一一对应。 */
function rowActions(row: Warehouse): ScmActionItem[] {
  const enabled = row.status === 'ENABLED';
  return [
    {key: 'state', label: enabled ? '停用' : '启用', danger: enabled, hidden: enabled ? !canDisable.value : !canEnable.value},
  ];
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
    const r = await warehouseApi.query(queryForm);
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
  // 弹窗内容首次打开才挂载，而 AreaCascader 只用<b>非 immediate</b> 的 watch 同步 value，
  // 因此回填必须排在 nextTick 之后，否则第一次编辑时选择器是空的。
  await nextTick();
  area.value = areaNodesOf(form.value);
}

async function save() {
  formError.value = locationError(form.value) ?? '';
  if (formError.value) return;
  if (!form.value.warehouseCode.trim()) {
    formError.value = '请填写仓库编码';
    return;
  }
  if (!form.value.name.trim()) {
    formError.value = '请填写仓库名称';
    return;
  }
  saving.value = true;
  try {
    if (form.value.id === undefined) {
      await warehouseApi.create(form.value);
      message.success('仓库已创建');
    } else {
      await warehouseApi.update(form.value);
      message.success('仓库已更新');
    }
    visible.value = false;
    await queryData();
  } catch (e) {
    formError.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

async function enable(row: Warehouse) {
  try {
    await warehouseApi.enable({id: row.id, version: row.version!});
    message.success('仓库已启用');
    await queryData();
  } catch (e) {
    error.value = purchaseError(e);
  }
}

/** 停用失败会把后端真实原因（库存余额 / 在途采购单 / 待入库收货单）显示给用户。 */
function disable(row: Warehouse) {
  Modal.confirm({
    title: '停用该仓库？',
    content: '停用后不能再用于新的采购需求、采购单与收货单。',
    okType: 'danger',
    onOk: async () => {
      try {
        await warehouseApi.disable({id: row.id, version: row.version!});
        message.success('仓库已停用');
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
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
