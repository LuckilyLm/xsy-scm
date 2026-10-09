<!--
  * 菜单 表单弹窗
  *
-->
<template>
  <a-drawer
      :body-style="{ paddingBottom: '80px' }"
      :maskClosable="true"
      :title="form.menuId ? '编辑' : '添加'"
      :open="visible"
      :width="600"
      @close="onClose"
      destroyOnClose
  >
    <a-form ref="formRef" :labelCol="{ span: 5 }" :labelWrap="true" :model="form" :rules="rules" class="app-drawer-form">
      <a-form-item label="菜单类型" name="menuType">
        <a-radio-group v-model:value="form.menuType" button-style="solid">
          <a-radio-button v-for="item in MENU_TYPE_ENUM" :key="item.value" :value="item.value">
            {{ item.desc }}
          </a-radio-button>
        </a-radio-group>
      </a-form-item>
      <a-form-item :label="form.menuType === MENU_TYPE_ENUM.CATALOG.value ? '上级目录' : '上级菜单'">
        <MenuTreeSelect ref="parentMenuTreeSelect" v-model:value="form.parentId"/>
      </a-form-item>
      <!--      目录 菜单 start   -->
      <template v-if="form.menuType === MENU_TYPE_ENUM.CATALOG.value || form.menuType === MENU_TYPE_ENUM.MENU.value">
        <a-form-item label="菜单名称" name="menuName">
          <a-input v-model:value="form.menuName" :maxlength="30" placeholder="请输入菜单名称"/>
        </a-form-item>
        <a-form-item label="菜单图标" name="icon">
          <IconSelect @updateIcon="selectIcon">
            <template #iconSelect>
              <a-input v-model:value="form.icon" :maxlength="100" placeholder="请输入菜单图标" style="width: 200px"/>
              <component :is="$antIcons[form.icon]" class="smart-margin-left15" style="font-size: 20px"/>
            </template>
          </IconSelect>
        </a-form-item>
        <a-form-item v-if="form.menuType === MENU_TYPE_ENUM.MENU.value" label="路由地址" name="path" class="app-drawer-field--wide">
          <a-input v-model:value="form.path" :maxlength="100" placeholder="请输入路由地址"/>
        </a-form-item>
        <template v-if="form.menuType === MENU_TYPE_ENUM.MENU.value">
          <a-form-item v-if="form.frameFlag" label="外链地址" name="frameUrl" class="app-drawer-field--wide">
            <a-input v-model:value="form.frameUrl" :maxlength="500" placeholder="请输入外链地址"/>
          </a-form-item>
          <a-form-item v-else name="component" class="app-drawer-field--wide">
            <template #label>
              组件地址
              <a-tooltip title="例如 /system/employee/index.vue" :trigger="['hover', 'focus']">
                <InfoCircleOutlined class="app-drawer-form__help" tabindex="0" aria-label="组件地址说明"/>
              </a-tooltip>
            </template>
            <a-input v-model:value="form.component" :maxlength="255" placeholder="请输入组件地址 默认带有开头/@/views"/>
          </a-form-item>
        </template>
        <a-form-item v-if="form.menuType === MENU_TYPE_ENUM.MENU.value" label="是否缓存" name="cacheFlag">
          <a-switch v-model:checked="form.cacheFlag" checked-children="开启缓存" un-checked-children="不缓存"/>
        </a-form-item>
        <a-form-item v-if="form.menuType === MENU_TYPE_ENUM.MENU.value" label="是否外链" name="frameFlag">
          <a-switch v-model:checked="form.frameFlag" checked-children="是外链" un-checked-children="不是外链"/>
        </a-form-item>
        <a-form-item label="显示状态" name="visibleFlag">
          <a-switch v-model:checked="form.visibleFlag" checked-children="显示" un-checked-children="不显示"/>
        </a-form-item>
        <a-form-item label="禁用状态" name="disabledFlag">
          <a-switch
              v-model:checked="form.disabledFlag"
              :checkedValue="false"
              :unCheckedValue="true"
              checked-children="启用"
              un-checked-children="禁用"
          />
        </a-form-item>
      </template>
      <!--      目录 菜单 end   -->
      <!--      功能点 start   -->
      <template v-if="form.menuType === MENU_TYPE_ENUM.POINTS.value">
        <a-form-item label="功能点名称" name="menuName">
          <a-input v-model:value="form.menuName" :maxlength="30" placeholder="请输入功能点名称"/>
        </a-form-item>
        <a-form-item label="功能点关联菜单">
          <MenuTreeSelect ref="contextMenuTreeSelect" v-model:value="form.contextMenuId"/>
        </a-form-item>
        <a-form-item label="功能点状态" name="funcDisabledFlag">
          <a-switch
              v-model:checked="form.disabledFlag"
              :checkedValue="false"
              :unCheckedValue="true"
              checked-children="启用"
              un-checked-children="禁用"
          />
        </a-form-item>
        <a-form-item label="权限类型" name="permsType">
          <a-radio-group v-model:value="form.permsType">
            <a-radio v-for="item in MENU_PERMS_TYPE_ENUM" :key="item.value" :value="item.value">
              {{ item.desc }}
            </a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item name="webPerms">
          <template #label>
            前端权限
            <a-tooltip title="控制前端按钮等功能的可见性" :trigger="['hover', 'focus']">
              <InfoCircleOutlined class="app-drawer-form__help" tabindex="0" aria-label="前端权限说明"/>
            </a-tooltip>
          </template>
          <a-input v-model:value="form.webPerms" :maxlength="5000" placeholder="请输入前端权限"/>
        </a-form-item>
        <a-form-item name="apiPerms">
          <template #label>
            后端权限
            <a-tooltip title="多个权限以英文逗号分隔" :trigger="['hover', 'focus']">
              <InfoCircleOutlined class="app-drawer-form__help" tabindex="0" aria-label="后端权限说明"/>
            </a-tooltip>
          </template>
          <a-input v-model:value="form.apiPerms" :maxlength="5000" placeholder="请输入后端权限"/>
        </a-form-item>
      </template>
      <!--      功能点 end   -->
      <a-form-item name="sort">
        <template #label>
          排序
          <a-tooltip title="数值越小，显示越靠前" :trigger="['hover', 'focus']">
            <InfoCircleOutlined class="app-drawer-form__help" tabindex="0" aria-label="排序说明"/>
          </a-tooltip>
        </template>
        <a-input-number v-model:value="form.sort" :min="0" :precision="0" placeholder="请输入排序" style="width: 100px"/>
      </a-form-item>
    </a-form>
    <div class="footer">
      <a-button style="margin-right: 8px" @click="onClose">取消</a-button>
      <a-button style="margin-right: 8px" type="primary" @click="onSubmit(false)">提交</a-button>
      <a-button v-if="!form.menuId" type="primary" @click="onSubmit(true)">提交并添加下一个</a-button>
    </div>
  </a-drawer>
</template>
<script setup lang="ts">
import {message} from 'ant-design-vue';
import {InfoCircleOutlined} from '@ant-design/icons-vue';
import _ from 'lodash';
import {nextTick, reactive, ref} from 'vue';
import MenuTreeSelect from './menu-tree-select.vue';
import {menuApi} from '/@/api/system/menu-api';
import IconSelect from '/@/components/framework/icon-select/index.vue';
import {MENU_DEFAULT_PARENT_ID, MENU_PERMS_TYPE_ENUM, MENU_TYPE_ENUM} from '/@/constants/system/menu-const';
import {smartSentry} from '/@/lib/smart-sentry';
import {SmartLoading} from '/@/components/framework/smart-loading';

// ----------------------- 以下是字段定义 emits props ------------------------
// emit
const emit = defineEmits(['reloadList']);

// ----------------------- 展开、隐藏编辑窗口 ------------------------

// 是否展示抽屉
const visible = ref(false);

const contextMenuTreeSelect = ref();
const parentMenuTreeSelect = ref();

//展开编辑窗口
async function showDrawer(rowData) {
  Object.assign(form, formDefault);
  if (rowData && !_.isEmpty(rowData)) {
    Object.assign(form, rowData);
    if (form.parentId === MENU_DEFAULT_PARENT_ID) {
      form.parentId = null;
    }
  }
  visible.value = true;
  refreshParentAndContext();
}

function refreshParentAndContext() {
  nextTick(() => {
    if (contextMenuTreeSelect.value) {
      contextMenuTreeSelect.value.queryMenuTree();
    }
    if (parentMenuTreeSelect.value) {
      parentMenuTreeSelect.value.queryMenuTree();
    }
  });
}

// 隐藏窗口
function onClose() {
  Object.assign(form, formDefault);
  formRef.value.resetFields();
  visible.value = false;
}

// ----------------------- form表单相关操作 ------------------------

const formRef = ref();
const formDefault = {
  menuId: undefined,
  menuName: undefined,
  menuType: MENU_TYPE_ENUM.CATALOG.value,
  icon: undefined,
  parentId: undefined,
  path: undefined,
  permsType: MENU_PERMS_TYPE_ENUM.SA_TOKEN.value,
  webPerms: undefined,
  apiPerms: undefined,
  sort: undefined,
  visibleFlag: true,
  cacheFlag: false,
  component: undefined,
  contextMenuId: undefined,
  disabledFlag: false,
  frameFlag: false,
  frameUrl: undefined,
};
let form = reactive({...formDefault});

function continueResetForm() {
  refreshParentAndContext();
  const menuType = form.menuType;
  const parentId = form.parentId;
  const webPerms = form.webPerms;
  Object.assign(form, formDefault);
  formRef.value.resetFields();
  form.menuType = menuType;
  form.parentId = parentId;
  if (form.menuType === MENU_TYPE_ENUM.POINTS.value) {
    form.contextMenuId = parentId;
  }
  // 移除最后一个：后面的内容
  if (webPerms && webPerms.lastIndexOf(':')) {
    form.webPerms = webPerms.substring(0, webPerms.lastIndexOf(':') + 1);
  }
}

const rules = {
  menuType: [{required: true, message: '菜单类型不能为空'}],
  permsType: [{required: true, message: '权限类型不能为空'}],
  menuName: [
    {required: true, message: '菜单名称不能为空'},
    {max: 30, message: '菜单名称不能大于30个字符', trigger: 'blur'},
  ],
  frameUrl: [
    {required: true, message: '外链地址不能为空'},
    {max: 500, message: '外链地址不能大于500个字符', trigger: 'blur'},
  ],
  path: [
    {required: true, message: '路由地址不能为空'},
    {max: 100, message: '路由地址不能大于100个字符', trigger: 'blur'},
  ],
  icon: [{max: 100, message: '菜单图标最多100个字符'}],
  component: [{max: 255, message: '组件地址最多255个字符'}],
  webPerms: [{max: 5000, message: '前端权限最多5000个字符'}],
  apiPerms: [{max: 5000, message: '后端权限最多5000个字符'}],
};

function validateForm(formRef) {
  return new Promise((resolve) => {
    formRef
        .validate()
        .then(() => {
          resolve(true);
        })
        .catch(() => {
          resolve(false);
        });
  });
}

const onSubmit = async (continueFlag) => {
  let validateFormRes = await validateForm(formRef.value);
  if (!validateFormRes) {
    message.error('参数验证错误，请仔细填写表单数据!');
    return;
  }
  SmartLoading.show();
  try {
    let params = _.cloneDeep(form);
    // 若无父级ID 默认设置为0
    if (!params.parentId) {
      params.parentId = 0;
    }
    if (params.menuId) {
      await menuApi.updateMenu(params);
    } else {
      await menuApi.addMenu(params);
    }
    message.success(`${params.menuId ? '修改' : '添加'}成功`);
    if (continueFlag) {
      continueResetForm();
    } else {
      onClose();
    }
    emit('reloadList');
  } catch (error) {
    smartSentry.captureError(error);
  } finally {
    SmartLoading.hide();
  }
};

function selectIcon(icon) {
  form.icon = icon;
}

// ----------------------- 以下是暴露的方法内容 ------------------------
defineExpose({
  showDrawer,
});
</script>
<style lang="less" scoped>
.footer {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 100%;
  border-top: 1px solid #e9e9e9;
  padding: 10px 16px;
  background: #fff;
  text-align: left;
  z-index: 1;
}
</style>
