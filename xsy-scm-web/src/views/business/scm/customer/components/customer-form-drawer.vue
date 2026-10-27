<!-- 客户新建/编辑抽屉：状态只读，变更在列表里操作；上级客户只选集团。 -->
<template>
  <a-drawer v-model:open="visible" :title="title" :width="scmDrawerWidth('m')" @close="close">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10" />
      <a-form ref="formRef" :model="form" layout="vertical" class="app-drawer-form">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">基础信息</h3>
          </div>
          <a-row :gutter="20">
            <a-col :xs="24" :sm="12">
              <a-form-item label="客户名称" name="name" :rules="[{required: true, whitespace: true, message: '请输入客户名称'}]">
                <a-input v-model:value="form.name" :maxlength="150" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="客户类型" name="customerTypeId" :rules="[{required: true, message: '请选择客户类型'}]">
                <CustomerTypeSelect v-model:value="form.customerTypeId" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="客户分类" name="businessCategoryId">
                <BusinessCategoryTreeSelect v-model:value="form.businessCategoryId" scope="CUSTOMER" mode="select" />
              </a-form-item>
            </a-col>
            <a-col v-if="isEdit" :xs="24" :sm="12">
              <a-form-item>
                <template #label>
                  客户状态
                  <ScmFieldHelp label="客户状态" text="请在客户列表中变更状态" />
                </template>
                <div class="scm-form-readonly">{{ statusText }}</div>
              </a-form-item>
            </a-col>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">归属与结算</h3>
          </div>
          <a-row :gutter="20">
            <a-col v-if="isEdit || canAssign" :xs="24" :sm="12">
              <a-form-item>
                <template #label>
                  归属业务员
                  <ScmFieldHelp label="归属业务员" :text="isEdit ? '请在客户列表中改派业务员' : '可留空，保存后暂不分配'" />
                </template>
                <span v-if="isEdit" class="scm-form-readonly">{{ sellerName || '未分配' }}</span>
                <EmployeeSelect v-else v-model:value="sellerValue" placeholder="请选择业务员" width="100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="上级集团客户" name="parentCustomerId">
                <CustomerSelect
                  v-model:value="form.parentCustomerId"
                  type-code="GROUP"
                  :exclude-id="form.customerId"
                  placeholder="仅集团客户可作为上级"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item name="settlementCustomerId">
                <template #label>
                  统一结算方
                  <ScmFieldHelp label="统一结算方" text="留空则独立结算；集团客户请选择结算主体" />
                </template>
                <CustomerSelect v-model:value="form.settlementCustomerId" placeholder="请选择结算方" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="结算方式" name="settleMode" :rules="[{required: true, message: '请选择结算方式'}]">
                <SmartEnumSelect v-model:value="form.settleMode" enum-name="SETTLE_MODE_ENUM" width="100%" />
              </a-form-item>
            </a-col>
            <a-col v-if="form.settleMode === 'GROUP'" :xs="24" :sm="12">
              <a-form-item name="sharedSettlementEnabled">
                <template #label>
                  允许子客户使用集团钱包
                  <ScmFieldHelp label="允许子客户使用集团钱包" text="默认关闭；打开后子客户在商城可直接扣集团钱包余额，看不到集团总额" />
                </template>
                <a-switch v-model:checked="form.sharedSettlementEnabled" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="绑定供应商" name="supplierId">
                <SupplierSelect v-model:value="form.supplierId" placeholder="请选择供应商" />
              </a-form-item>
            </a-col>
            <a-col v-if="canWarehouseUpdate" :xs="24" :sm="12">
              <a-form-item>
                <template #label>
                  商城默认配送仓
                  <ScmFieldHelp label="商城默认配送仓" text="优先使用此仓；未绑定时按收货地址区县映射。" />
                </template>
                <WarehouseSelect v-model:value="mallWarehouseValue" placeholder="按收货地址区县自动匹配" width="100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">联系与配送</h3>
          </div>
          <a-row :gutter="20">
            <a-col :xs="24" :sm="12">
              <a-form-item label="联系人" name="contactName">
                <a-input v-model:value="form.contactName" :maxlength="100" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="联系电话" name="contactPhone" class="app-drawer-field--compact">
                <a-input v-model:value="form.contactPhone" :maxlength="32" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="所在地区">
                <AreaCascader
                  type="province_city_district"
                  v-model:value="area"
                  style="width: 100%"
                  placeholder="省 / 市 / 区"
                  @change="onAreaChange"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="地址" name="address" class="app-drawer-field--wide">
                <a-input v-model:value="form.address" :maxlength="255" @change="Object.assign(form, emptyLocation())" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="地图定位">
                <ScmMapPicker :value="form" :address="form.address" @change="Object.assign(form, $event)" />
              </a-form-item>
            </a-col>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">授信与账期</h3>
          </div>
          <a-row :gutter="20">
            <!-- 销售侧金额按元展示两位；字符串模型避免浮点误差 -->
            <a-col :xs="24" :sm="12">
              <a-form-item name="creditLimit">
                <template #label>
                  授信额度
                  <ScmFieldHelp label="授信额度" text="0 表示不设置额度上限；逾期仍可能阻断订单" />
                </template>
                <a-input-number
                  v-model:value="form.creditLimit"
                  string-mode
                  :min="0"
                  :max="'99999999999999.99'"
                  :precision="2"
                  :step="'0.01'"
                  addon-before="¥"
                  placeholder="0.00"
                  style="width: 100%"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="账期类型" name="creditPeriodType">
                <SmartEnumSelect v-model:value="creditPeriodTypeValue" enum-name="CREDIT_PERIOD_TYPE_ENUM" width="100%" placeholder="不设置账期" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item name="codAllowed">
                <template #label>
                  允许商城货到付款
                  <ScmFieldHelp label="允许商城货到付款" text="默认关闭；开通后仍会检查客户交易状态与逾期风险，收款以交付时实际到账为准" />
                </template>
                <a-switch v-model:checked="form.codAllowed" />
              </a-form-item>
            </a-col>
            <a-col v-if="form.creditPeriodType === 'BY_AMOUNT'" :xs="24" :sm="12">
              <a-form-item label="金额阈值" name="creditAmountThreshold">
                <a-input-number
                  v-model:value="form.creditAmountThreshold"
                  string-mode
                  :min="0"
                  :max="'99999999999999.99'"
                  :precision="2"
                  :step="'0.01'"
                  addon-before="¥"
                  style="width: 100%"
                  placeholder="例如 5000.00"
                />
              </a-form-item>
            </a-col>
            <template v-if="form.creditPeriodType === 'BY_TIME'">
              <a-col :xs="24" :sm="12">
                <a-form-item label="账期值" name="creditPeriodValue">
                  <a-input-number v-model:value="form.creditPeriodValue" :min="1" :precision="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="账期单位" name="creditPeriodUnit">
                  <SmartEnumSelect v-model:value="creditPeriodUnitValue" enum-name="CREDIT_PERIOD_UNIT_ENUM" width="100%" />
                </a-form-item>
              </a-col>
              <a-col v-if="form.creditPeriodUnit === 'MONTH'" :xs="24" :sm="12">
                <a-form-item label="固定结算日" name="settleDay">
                  <a-input-number v-model:value="form.settleDay" :min="1" :max="28" :precision="0" addon-after="日" style="width: 100%" />
                </a-form-item>
              </a-col>
            </template>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">其他</h3>
          </div>
          <a-row :gutter="20">
            <a-col :span="24">
              <a-form-item label="备注" name="remark">
                <a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" show-count />
              </a-form-item>
            </a-col>
          </a-row>
        </section>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space :size="12">
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
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {customerPromotionApi} from '/@/api/business/scm/customer-promotion-api';
import type {CreditPeriodType, CreditPeriodUnit, CustomerFormModel, CustomerStatus, ScmId} from '/@/types/business/scm/customer';
import {CUSTOMER_STATUS_ENUM} from '/@/constants/business/scm/customer-const';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf, areaNodesOf} from '../../common/scm-area';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import BusinessCategoryTreeSelect from '/@/components/business/scm/business-category-tree-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {normalizeMoneyInput} from '/@/utils/scm-amount';
import {applyCreditPeriodType, applyCreditPeriodUnit, emptyCustomer, toCustomerPayload, validateCustomer} from '../customer-form-model';
import {customerError} from '../customer-errors';
import {hasPermission} from '../../common/scm-permission';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const emit = defineEmits<{saved: []}>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const formRef = ref<FormInstance>();

/** 状态只读展示；新建时固定为「潜在」。 */
const status = ref<CustomerStatus>('POTENTIAL');
/** 负责人姓名只读；改派在列表里操作。 */
const sellerName = ref('');

/** 没有分配权限时，新建客户默认归自己。 */
const canAssign = computed(() => hasPermission('scm:customer:assign'));
const canWarehouseUpdate = computed(
  () => hasPermission('scm:customer:update') && hasPermission('scm:warehouse:update') && hasPermission('scm:warehouse:query')
);
const isEdit = computed(() => form.customerId != null);

const form = reactive<CustomerFormModel>(emptyCustomer());

/** 省市区选择路径，与表单的地区列互转。 */
const area = ref<AreaNode[]>([]);

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  Object.assign(form, areaColumnsOf(nodes), emptyLocation());
}

const title = computed(() => (form.customerId ? '编辑客户' : '新增客户'));
const statusText = computed(() => CUSTOMER_STATUS_ENUM[status.value]?.desc || status.value);

/** 业务员下拉桥接：null 折成 undefined，避免类型报错。 */
const sellerValue = computed<number | undefined>({
  get: () => form.sellerId ?? undefined,
  set: (value) => {
    form.sellerId = value ?? null;
  },
});
const mallWarehouseValue = computed<string | number | undefined>({
  get: () => form.mallDeliveryWarehouseId ?? undefined,
  set: (value) => {
    form.mallDeliveryWarehouseId = value ?? null;
    form.clearMallDeliveryWarehouse = value == null;
  },
});

/** 传 customerId 进入编辑，否则新建。 */
async function open(customerId?: ScmId) {
  visible.value = true;
  error.value = '';
  Object.assign(form, emptyCustomer());
  area.value = [];
  status.value = 'POTENTIAL';
  sellerName.value = '';
  if (customerId == null) {
    await nextTick();
    formRef.value?.clearValidate();
    // 新建时按最近一次扫码预填业务员，管理员仍可改
    try {
      const attribution = await customerPromotionApi.attribution();
      if (attribution.data?.sellerId != null) {
        form.sellerId = Number(attribution.data.sellerId);
      }
    } catch {
      // 预填失败不影响新建
    }
    return;
  }
  loading.value = true;
  try {
    // 加载失败由抽屉内 Alert 提示，不再弹全局 toast
    const response = await customerApi.detail(customerId, {suppressGlobalErrorMessage: true});
    const detail = response.data;
    // 列表数据不含地址、账期等字段，编辑要先取详情。
    Object.assign(form, {
      customerId: detail.customerId,
      version: detail.version,
      customerCode: detail.customerCode,
      name: detail.name,
      customerTypeId: detail.customerTypeId,
      businessCategoryId: detail.businessCategoryId ?? null,
      parentCustomerId: detail.parentCustomerId ?? null,
      settlementCustomerId: detail.settlementCustomerId ?? null,
      mallDeliveryWarehouseId: detail.mallDeliveryWarehouseId ?? null,
      clearMallDeliveryWarehouse: false,
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
      sharedSettlementEnabled: detail.sharedSettlementEnabled === true,
      codAllowed: detail.codAllowed === true,
      creditLimit: detail.creditLimit == null ? '0.0000' : normalizeMoneyInput(detail.creditLimit),
      creditPeriodType: detail.creditPeriodType ?? undefined,
      creditAmountThreshold: detail.creditAmountThreshold == null ? undefined : normalizeMoneyInput(detail.creditAmountThreshold),
      creditPeriodValue: detail.creditPeriodValue ?? undefined,
      creditPeriodUnit: detail.creditPeriodUnit ?? undefined,
      settleDay: detail.settleDay ?? undefined,
      remark: detail.remark ?? '',
    });
    status.value = detail.status;
    sellerName.value = detail.sellerName ?? '';
  } catch (e) {
    error.value = customerError(e);
  } finally {
    loading.value = false;
    // 区划选择器挂载后才生效，回填要等 nextTick。
    await nextTick();
    area.value = areaNodesOf(form);
    formRef.value?.clearValidate();
  }
}

/** 账期类型可不填；切换时清掉另一形态的值。 */
const creditPeriodTypeValue = computed<string | undefined>({
  get: () => form.creditPeriodType ?? undefined,
  set: (value) => {
    Object.assign(form, applyCreditPeriodType({...form}, (value ?? undefined) as CreditPeriodType | undefined));
  },
});

/** 账期单位桥接；切到「天」时清掉结算日。 */
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
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  // 跨字段规则（账期互斥、金额格式）由纯函数校验。
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
  } catch {
    // 保存失败由全局 toast 提示；error 只用于本地校验和加载失败
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
