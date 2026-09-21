<!--
  * 客户 新建 / 编辑 抽屉
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/customer/customer-list.vue
  * 第 93–147 行的**内联抽屉**（Copy First + Adapt，从列表里位移到独立组件）。
  *
  * 剪枝（W2 范围外，Target Design §5.4）：
  * - 删除 `customerLevelId`（客户等级，W2 不做）；
  * - 删除 `longitude` / `latitude`（地图坐标，随 M2 的高德选点一起做）；
  * - 「所属区域 / 省市区」级联：W2 只保留 `address` 文本，地图 M0（V40）起恢复级联，
  *   与 `address` 并存 —— 前者是可统计的编码 + 名称快照，后者仍是配送用的自由文本；
  * - 删除 `visibleType` / 二维码相关字段（W3+）。
  *
  * 适配：
  * - 客户类型从 C 的**硬编码下拉**改为 `CustomerTypeSelect`（V2 是可维护字典表）；
  * - `parentCustomerId` → `CustomerSelect`（`type-code="GROUP"` 收窄 + `exclude-id` 排除自己，
  *   后端 `CustomerValidator.validateParent` 只接受集团且拒绝环形，前端提前挡掉必然失败的选项）；
  * - `sellerId` → V2 原生 `EmployeeSelect`（不新写员工下拉）；
  * - `supplierId` → `SupplierSelect`；
  * - 新增 C 完全没有的**账期 6 字段**（额度 / 类型 / 金额阈值 / 账期值 / 单位 / 结算日），
  *   由 `customer-form-model.ts` 的三个纯函数负责「形态互斥清理 + 校验 + 归一化」；
  * - 状态在 C 里是可编辑下拉，V2 的 `CustomerAddForm` / `CustomerUpdateForm` **不含 status**，
  *   新建固定「潜在」、变更走独立的 `updateStatus` 端点，因此这里改成只读展示。
-->
<template>
  <a-drawer v-model:open="visible" :title="title" width="780" @close="close">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="客户编码" name="customerCode"
                         :rules="[{ required: true, whitespace: true, message: '请输入客户编码' }]">
              <a-input v-model:value="form.customerCode" :maxlength="64" placeholder="保存时自动转为大写"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="客户名称" name="name"
                         :rules="[{ required: true, whitespace: true, message: '请输入客户名称' }]">
              <a-input v-model:value="form.name" :maxlength="150"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="客户类型" name="customerTypeId"
                         :rules="[{ required: true, message: '请选择客户类型' }]">
              <CustomerTypeSelect v-model:value="form.customerTypeId"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="客户状态">
              <a-input :value="statusText" disabled/>
              <div class="ant-form-item-extra">新建固定为「潜在」，后续通过「状态变更」维护</div>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="上级集团客户" name="parentCustomerId">
              <CustomerSelect
                  v-model:value="form.parentCustomerId"
                  type-code="GROUP"
                  :exclude-id="form.customerId"
                  placeholder="仅集团客户可作为上级"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结算方式" name="settleMode" :rules="[{ required: true, message: '请选择结算方式' }]">
              <SmartEnumSelect v-model:value="form.settleMode" enum-name="SETTLE_MODE_ENUM" width="100%"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="归属业务员" name="sellerId">
              <EmployeeSelect v-model:value="sellerValue" placeholder="请选择业务员" width="100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="绑定供应商" name="supplierId">
              <SupplierSelect v-model:value="form.supplierId" placeholder="请选择供应商"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-divider orientation="left">联系方式</a-divider>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="联系人" name="contactName">
              <a-input v-model:value="form.contactName" :maxlength="100"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="联系电话" name="contactPhone">
              <a-input v-model:value="form.contactPhone" :maxlength="32"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="8">
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
          </a-col>
          <a-col :span="16">
            <a-form-item label="地址" name="address">
              <a-input v-model:value="form.address" :maxlength="255" @change="Object.assign(form, emptyLocation())"/>
            </a-form-item>
            <a-form-item label="地图定位">
              <ScmMapPicker :value="form" :address="form.address" @change="Object.assign(form, $event)"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-divider orientation="left">授信与账期</a-divider>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="授信额度" name="creditLimit">
              <a-input v-model:value="form.creditLimit" placeholder="例如 10000.0000（最多四位小数）"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="账期类型" name="creditPeriodType">
              <SmartEnumSelect
                  v-model:value="creditPeriodTypeValue"
                  enum-name="CREDIT_PERIOD_TYPE_ENUM"
                  width="100%"
                  placeholder="不设置账期"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row v-if="form.creditPeriodType === 'BY_AMOUNT'" :gutter="16">
          <a-col :span="12">
            <a-form-item label="金额阈值" name="creditAmountThreshold">
              <a-input v-model:value="form.creditAmountThreshold" placeholder="例如 5000.0000"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row v-if="form.creditPeriodType === 'BY_TIME'" :gutter="16">
          <a-col :span="8">
            <a-form-item label="账期值" name="creditPeriodValue">
              <a-input-number v-model:value="form.creditPeriodValue" :min="1" :precision="0" style="width: 100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="账期单位" name="creditPeriodUnit">
              <SmartEnumSelect v-model:value="creditPeriodUnitValue" enum-name="CREDIT_PERIOD_UNIT_ENUM" width="100%"/>
            </a-form-item>
          </a-col>
          <a-col v-if="form.creditPeriodUnit === 'MONTH'" :span="8">
            <a-form-item label="固定结算日" name="settleDay">
              <a-input-number v-model:value="form.settleDay" :min="1" :max="28" :precision="0" style="width: 100%"/>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" show-count/>
        </a-form-item>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {emptyLocation, locationError} from '/@/components/business/scm/map/types';
import {computed, nextTick, reactive, ref} from 'vue';
import type {FormInstance} from 'ant-design-vue';
import {message} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import type {
  CreditPeriodType,
  CreditPeriodUnit,
  CustomerForm,
  CustomerStatus,
  ScmId
} from '/@/types/business/scm/customer';
import {CUSTOMER_STATUS_ENUM} from '/@/constants/business/scm/customer-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf, areaNodesOf} from '../../common/scm-area';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import {
  applyCreditPeriodType,
  applyCreditPeriodUnit,
  emptyCustomer,
  toCustomerPayload,
  validateCustomer,
} from '../customer-form-model';
import {customerError} from '../customer-errors';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const formRef = ref<FormInstance>();

/** 详情里的状态（只读展示用）。新建时后端固定给「潜在」。 */
const status = ref<CustomerStatus>('POTENTIAL');

const form = reactive<CustomerForm>(emptyCustomer());

/** 省 / 市 / 区的选中路径，与 form 的 6 列之间由 scm-area 互转。 */
const area = ref<AreaNode[]>([]);

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  Object.assign(form, areaColumnsOf(nodes), emptyLocation());
}

const title = computed(() => (form.customerId ? '编辑客户' : '新增客户'));
const statusText = computed(() => CUSTOMER_STATUS_ENUM[status.value]?.desc || status.value);

/**
 * 业务员下拉的桥接。
 *
 * V2 原生 `EmployeeSelect` 的 `value` prop 声明是 `[Number, Array]`，
 * 直接绑 `number | null` 会因 `null` 报 TS2322；这里统一把 `null` 折成 `undefined`。
 */
const sellerValue = computed<number | undefined>({
  get: () => form.sellerId ?? undefined,
  set: (value) => {
    form.sellerId = value ?? null;
  },
});

/** 打开抽屉。传 `customerId` 即编辑模式（先拉详情再填表）。 */
async function open(customerId?: ScmId) {
  visible.value = true;
  error.value = '';
  Object.assign(form, emptyCustomer());
  area.value = [];
  status.value = 'POTENTIAL';
  if (customerId == null) {
    await nextTick();
    formRef.value?.clearValidate();
    return;
  }
  loading.value = true;
  try {
    const response = await customerApi.detail(customerId);
    const detail = response.data;
    // 列表 VO 不含地址 / 账期 / 备注，因此编辑必须先取详情，不能直接复用列表行。
    Object.assign(form, {
      customerId: detail.customerId,
      version: detail.version,
      customerCode: detail.customerCode,
      name: detail.name,
      customerTypeId: detail.customerTypeId,
      parentCustomerId: detail.parentCustomerId ?? null,
      sellerId: detail.sellerId ?? null,
      supplierId: detail.supplierId ?? null,
      contactName: detail.contactName ?? '',
      contactPhone: detail.contactPhone ?? '',
      address: detail.address ?? '',
      longitude: detail.longitude ?? null,
      latitude: detail.latitude ?? null,
      geomCrs: detail.geomCrs ?? null,
      provinceCode: detail.provinceCode ?? null,
      provinceName: detail.provinceName ?? null,
      cityCode: detail.cityCode ?? null,
      cityName: detail.cityName ?? null,
      districtCode: detail.districtCode ?? null,
      districtName: detail.districtName ?? null,
      settleMode: detail.settleMode,
      creditLimit: detail.creditLimit ?? '0.0000',
      creditPeriodType: detail.creditPeriodType ?? undefined,
      creditAmountThreshold: detail.creditAmountThreshold ?? undefined,
      creditPeriodValue: detail.creditPeriodValue ?? undefined,
      creditPeriodUnit: detail.creditPeriodUnit ?? undefined,
      settleDay: detail.settleDay ?? undefined,
      remark: detail.remark ?? '',
    });
    status.value = detail.status;
  } catch (e) {
    error.value = customerError(e);
  } finally {
    loading.value = false;
    // 抽屉内容首次打开才挂载，而 AreaCascader 只用**非 immediate** 的 watch 同步 value，
    // 因此回填必须排在 nextTick 之后，否则第一次编辑时选择器是空的。
    await nextTick();
    area.value = areaNodesOf(form);
    formRef.value?.clearValidate();
  }
}

/**
 * 账期类型的桥接。
 *
 * `SmartEnumSelect` 的 `value` prop 声明是 `[Number, String]`，**不接受 `null`**；
 * 而 `CustomerForm.creditPeriodType` 的可空语义正是 `null`（表示「不设置账期」），
 * 直接绑会报 TS2322。这里用 computed 把 `null` 折成 `undefined`。
 *
 * 顺带把「切换形态时清掉不属于该形态的字段」放进 setter：无论用户选择还是程序回填，
 * 都走同一套清理，不会残留「按金额」的阈值 + 「按时间」的账期值这种非法组合（后端 40000）。
 */
const creditPeriodTypeValue = computed<string | undefined>({
  get: () => form.creditPeriodType ?? undefined,
  set: (value) => {
    Object.assign(form, applyCreditPeriodType({...form}, (value ?? undefined) as CreditPeriodType | undefined));
  },
});

/** 账期单位桥接；setter 在切到「天」时清掉结算日（按天账期没有结算日概念）。 */
const creditPeriodUnitValue = computed<string | undefined>({
  get: () => form.creditPeriodUnit ?? undefined,
  set: (value) => {
    Object.assign(form, applyCreditPeriodUnit({...form}, (value ?? undefined) as CreditPeriodUnit | undefined));
  },
});

function close() {
  error.value = '';
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // 表单规则覆盖不到的跨字段约束（账期三形态互斥、金额格式）由纯函数兜底。
  const problem = locationError(form) || validateCustomer(form);
  if (problem) {
    error.value = problem;
    return;
  }
  saving.value = true;
  error.value = '';
  const payload = toCustomerPayload({...form});
  try {
    if (form.customerId) {
      await customerApi.update(payload);
      message.success('客户已保存');
    } else {
      await customerApi.add(payload);
      message.success('客户已创建');
    }
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = customerError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
