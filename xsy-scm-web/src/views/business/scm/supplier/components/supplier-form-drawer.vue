<!--
  供应商 新建 / 编辑 抽屉。
  - 表单不含 status：新建强制 `ENABLED`，变更走独立的 `updateStatus` 端点；
  - 编辑时先拉详情（列表 VO 不含 `address` / `remark`）；
  - 「所在地区」省市区级联与 `address` 自由文本并存：编码供地图按市聚合，
    地址仍是收货与展示口径。
-->
<template>
  <a-drawer v-model:open="visible" :title="title" :width="scmDrawerWidth('m')" @close="close">
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="form" layout="vertical">
        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">基础信息</h3>
          </div>
          <a-row :gutter="20">
            <a-col :xs="24" :sm="12">
              <a-form-item label="供应商名称" name="name"
                           :rules="[{ required: true, whitespace: true, message: '请输入供应商名称' }]">
                <a-input v-model:value="form.name" :maxlength="150"/>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="供应商编码">
                <span v-if="form.supplierId" class="scm-form-readonly">{{ form.supplierCode || '—' }}</span>
                <span v-else class="scm-form-readonly">保存后由系统自动生成</span>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="联系人" name="contactName">
                <a-input v-model:value="form.contactName" :maxlength="100"/>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="联系电话" name="contactPhone">
                <a-input v-model:value="form.contactPhone" :maxlength="32"/>
              </a-form-item>
            </a-col>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">位置与配送</h3>
          </div>
          <a-form-item label="所在地区">
            <AreaCascader
                type="province_city_district"
                v-model:value="area"
                style="width: 100%"
                placeholder="省 / 市 / 区"
                @change="onAreaChange"
            />
            <div class="scm-form-section__extra">留空则不参与地图分布统计</div>
          </a-form-item>
          <a-form-item label="地址" name="address">
            <a-input v-model:value="form.address" :maxlength="255" @change="Object.assign(form, emptyLocation())"/>
          </a-form-item>
          <a-form-item label="地图定位">
            <ScmMapPicker :value="form" :address="form.address" @change="Object.assign(form, $event)"/>
            <div class="scm-form-section__extra">
              点位用于地图分布与供应商位置查询；经纬度与坐标系必须同时填写或同时清空
            </div>
          </a-form-item>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">结算设置</h3>
          </div>
          <a-row :gutter="20">
            <a-col :xs="24" :sm="12">
              <a-form-item label="付款账期" name="paymentPeriodDays">
                <a-input-number v-model:value="form.paymentPeriodDays" :min="0" :max="3650" :precision="0"
                                addon-after="天" style="width: 100%"/>
                <div class="scm-form-section__extra">应付形成时冻结到期日；0 表示当日到期，修改只影响新应付。</div>
              </a-form-item>
            </a-col>
          </a-row>
        </section>

        <section class="scm-form-section">
          <div class="scm-form-section__head">
            <h3 class="scm-form-section__title">其他</h3>
          </div>
          <a-form-item label="备注" name="remark">
            <a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" show-count/>
          </a-form-item>
        </section>
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
import {computed, nextTick, reactive, ref} from 'vue';
import type {FormInstance} from 'ant-design-vue';
import {message} from 'ant-design-vue';
import {supplierApi} from '/@/api/business/scm/supplier-api';
import type {ScmId, SupplierFormModel} from '/@/types/business/scm/supplier';
import {emptySupplier, toSupplierPayload, validateSupplier} from '../supplier-form-model';
import AreaCascader from '/@/components/framework/area-cascader/index.vue';
import ScmMapPicker from '/@/components/business/scm/map/scm-map-picker.vue';
import {emptyLocation, locationError} from '/@/components/business/scm/map/types';
import type {AreaNode} from '/@/types/business/scm/area';
import {areaColumnsOf, areaNodesOf} from '../../common/scm-area';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {supplierError} from '../supplier-errors';
import {useScmErrorToast} from '../../common/scm-error-toast';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = useScmErrorToast();
const formRef = ref<FormInstance>();

const form = reactive<SupplierFormModel>(emptySupplier());

/** 省 / 市 / 区的选中路径，与 form 的 6 列之间由 scm-area 互转。 */
const area = ref<AreaNode[]>([]);

function onAreaChange(_value: unknown, nodes: AreaNode[]) {
  // 区划变了就作废已选点位：留着旧坐标会让「改了区划但没重新选点」静默指向另一个地方。
  Object.assign(form, areaColumnsOf(nodes), emptyLocation());
}

const title = computed(() => (form.supplierId ? '编辑供应商' : '新增供应商'));

/** 打开抽屉。传 `supplierId` 即编辑模式（先拉详情再填表）。 */
async function open(supplierId?: ScmId) {
  visible.value = true;
  error.value = '';
  Object.assign(form, emptySupplier());
  area.value = [];
  if (supplierId == null) {
    await nextTick();
    formRef.value?.clearValidate();
    return;
  }
  loading.value = true;
  try {
    const response = await supplierApi.detail(supplierId);
    const detail = response.data;
    // 列表 VO 不含 address / remark，编辑必须走详情接口。
    Object.assign(form, {
      supplierId: detail.supplierId,
      version: detail.version,
      supplierCode: detail.supplierCode,
      name: detail.name,
      contactName: detail.contactName ?? '',
      contactPhone: detail.contactPhone ?? '',
      paymentPeriodDays: detail.paymentPeriodDays ?? 0,
      address: detail.address ?? '',
      provinceCode: detail.provinceCode ?? null,
      provinceName: detail.provinceName ?? null,
      cityCode: detail.cityCode ?? null,
      cityName: detail.cityName ?? null,
      districtCode: detail.districtCode ?? null,
      districtName: detail.districtName ?? null,
      longitude: detail.longitude ?? null,
      latitude: detail.latitude ?? null,
      geomCrs: detail.geomCrs ?? null,
      remark: detail.remark ?? '',
    });
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    loading.value = false;
    // 抽屉内容首次打开才挂载，而 AreaCascader 只用非 immediate 的 watch 同步 value，
    // 因此回填必须排在 nextTick 之后，否则第一次编辑时选择器是空的。
    await nextTick();
    area.value = areaNodesOf(form);
    formRef.value?.clearValidate();
  }
}

function close() {
  error.value = '';
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // 点位成组校验先于业务校验：半组坐标在地图上无法解释，后端也会以 VALIDATION_ERROR 拒绝
  const problem = locationError(form) || validateSupplier(form);
  if (problem) {
    error.value = problem;
    return;
  }
  saving.value = true;
  error.value = '';
  const payload = toSupplierPayload({...form});
  try {
    if (form.supplierId) {
      await supplierApi.update(payload);
      message.success('供应商已保存');
    } else {
      await supplierApi.add(payload);
      message.success('供应商已创建');
    }
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
