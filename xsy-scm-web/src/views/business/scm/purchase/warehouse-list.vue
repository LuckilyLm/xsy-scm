<!-- C 无仓库页（新增文件）。仿 W2 `supplier-list.vue` 的列表骨架。
适配：`/scm/warehouse/**`、`version`（A8）、`scm:warehouse:*`（A22）、
      `scm-warehouse-table`（A23）、`purchase-errors`（A24）、loading/empty/error/retry（A27）、
      `v-privilege`（A30）。B1 使用独立命令启用或停用仓库，基础信息表单不直接修改状态。
验收：W5 单测、TS 棘轮与 Playwright。
地图 M0（V40）：弹窗的「所在地区」省市区级联与 `address` 并存，编码列供大屏按市聚合，名称为同一次选择的快照。 -->
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
        :scroll="{ x: 1200 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'default'">
            {{ SCM_WAREHOUSE_STATUS_ENUM[record.status]?.desc || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'address'">{{ record.address || '—' }}</template>
        <template v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" v-privilege="'scm:warehouse:update'" @click="open(record)">编辑</a-button>
            <a-button
                v-if="record.status === 'ENABLED'"
                danger
                type="link"
                v-privilege="'scm:warehouse:disable'"
                @click="disable(record)"
            >
              停用
            </a-button>
            <a-button
                v-if="record.status === 'DISABLED'"
                type="link"
                v-privilege="'scm:warehouse:enable'"
                @click="enable(record)"
            >
              启用
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
</template>

<script setup lang="ts">
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {emptyLocation, locationError} from '/@/components/business/scm/map/types';
import {nextTick, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_TABLE_ID, SCM_WAREHOUSE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {Warehouse, WarehousePayload, WarehouseQuery} from './purchase-types';
import {purchaseError} from './purchase-errors';
import {areaColumnsOf, areaNodesOf} from '../common/scm-area';
import {datetime} from '../common/scm-display';

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

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  Object.assign(form.value, areaColumnsOf(nodes), emptyLocation());
}

let requestId = 0;

const columns = ref<TableColumnsType<Warehouse>>([
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 160},
  {title: '仓库名称', dataIndex: 'name', width: 200},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
  {title: '地址', dataIndex: 'address', width: 260},
  {title: '备注', dataIndex: 'remark', width: 200},
  {title: '创建时间', dataIndex: 'createdAt', width: 190, customRender: ({text}) => datetime(text)},
  {title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 100},
]);

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
  // 弹窗内容首次打开才挂载，而 AreaCascader 只用**非 immediate** 的 watch 同步 value，
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
