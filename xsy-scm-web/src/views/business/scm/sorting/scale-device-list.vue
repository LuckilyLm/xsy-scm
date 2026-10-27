<!--
  电子秤设备管理。
  秤用设备凭证证明「是哪台秤」，员工会话证明「谁在操作」，两者分开授权。
  配对码与会话令牌明文只出现一次，弹窗里必须提示离开即不可再查看。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="设备编号 / 名称 / 工位" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" width="180px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select v-model:value="queryForm.status" :options="statusOptions" allow-clear placeholder="全部" style="width: 140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.QUERY" @click="onSearch">查询</a-button>
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
        <a-button type="primary" v-privilege="PERM.BIND" @click="openRegister">登记电子秤</a-button>
      </div>
    </a-row>

    <a-table
      :id="TABLE_ID"
      size="small"
      ref="tableRef"
      :data-source="tableData"
      :columns="displayColumns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scrollX}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="SCM_SCALE_DEVICE_STATUS_ENUM[record.status as ScaleDeviceStatus]?.color">
            {{ SCM_SCALE_DEVICE_STATUS_ENUM[record.status as ScaleDeviceStatus]?.desc || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'online'">
          <div class="scale-online">
            <a-tag :color="record.scaleConnected ? 'green' : 'default'">
              {{ record.scaleConnected ? '秤已连接' : '秤未连接' }}
            </a-tag>
            <a-tag :color="record.printerConnected ? 'green' : 'default'">
              {{ record.printerConnected ? '打印机已连接' : '打印机未连接' }}
            </a-tag>
            <span class="scale-online__time">{{ record.lastSeenAt ? datetime(record.lastSeenAt) : '从未心跳' }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'activeSessionCount'"> {{ record.activeSessionCount ?? 0 }} 人 </template>
        <template v-else-if="column.dataIndex === 'credentialExpiresAt'">
          {{ record.credentialExpiresAt ? datetime(record.credentialExpiresAt) : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="PERM.SESSION_ISSUE" :disabled="record.status !== 'ACTIVE'" @click="openSession(record)">
              签发会话
            </a-button>
            <a-button type="link" size="small" v-privilege="PERM.QUERY" @click="openSessions(record)">会话记录</a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        show-quick-jumper
        v-model:current="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        @change="queryData"
        :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <!-- 登记：配对码一次性，30 分钟内有效 -->
  <a-modal :open="registerOpen" title="登记电子秤" :confirm-loading="registering" @ok="submitRegister" @cancel="closeRegister">
    <a-alert v-if="registerError" :message="registerError" type="error" show-icon class="smart-margin-bottom10" />
    <a-form ref="registerFormRef" :model="registerForm" :rules="registerRules" layout="vertical">
      <a-form-item label="设备编号" name="deviceCode" extra="现场贴在秤上的编号；撤销后也不会被另一台秤复用">
        <a-input v-model:value="registerForm.deviceCode" maxlength="64" />
      </a-form-item>
      <a-form-item label="设备名称" name="deviceName">
        <a-input v-model:value="registerForm.deviceName" maxlength="100" placeholder="如：分拣台 1 号秤" />
      </a-form-item>
      <a-form-item label="设备型号" name="deviceModel">
        <a-input v-model:value="registerForm.deviceModel" maxlength="100" placeholder="如：SHOUHENG TCS-150A" />
      </a-form-item>
      <a-form-item label="绑定仓库" name="warehouseId">
        <WarehouseSelect v-model:value="registerForm.warehouseId" width="100%" />
      </a-form-item>
      <a-form-item label="工位编码" name="stationCode">
        <a-input v-model:value="registerForm.stationCode" maxlength="64" />
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 登记结果：明文配对码只显示这一次 -->
  <a-modal :open="pairingVisible" title="配对码已生成" :footer="null" @cancel="pairingVisible = false">
    <a-result status="success" title="请把配对码输入秤端完成激活">
      <template #subTitle>
        <p>配对码 30 分钟内有效，只显示这一次；关闭后需要重新登记才会再生成。</p>
        <p class="scale-secret">{{ pairingResult?.pairingCode }}</p>
        <p>有效期至 {{ datetime(pairingResult?.pairingExpiresAt) }}</p>
      </template>
      <template #extra>
        <a-button type="primary" @click="copyText(pairingResult?.pairingCode, '配对码已复制')">复制配对码</a-button>
      </template>
    </a-result>
  </a-modal>

  <!-- 签发员工会话 -->
  <a-modal :open="sessionOpen" title="签发员工会话" :confirm-loading="issuing" ok-text="签发" @ok="submitSession" @cancel="sessionOpen = false">
    <a-alert
      message="会话只代表该员工在这台秤上工作，不携带后台菜单权限；业务写入仍按该员工的真实数据范围校验。"
      type="info"
      show-icon
      class="smart-margin-bottom10"
    />
    <a-alert v-if="sessionError" :message="sessionError" type="error" show-icon class="smart-margin-bottom10" />
    <a-form layout="vertical">
      <a-form-item label="操作员工" required>
        <EmployeeSelect v-model:value="sessionForm.employeeId" width="100%" />
      </a-form-item>
      <a-form-item label="有效时长（分钟）" required>
        <a-input-number v-model:value="sessionForm.validMinutes" :min="5" :max="720" :step="5" style="width: 100%" />
        <div class="scale-hint">一个班次通常不超过 12 小时；到期后重新签发，不提供无限续期。</div>
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 会话令牌：离开弹窗即不可查看 -->
  <a-modal :open="tokenVisible" title="会话令牌已签发" :footer="null" @cancel="tokenVisible = false">
    <a-result status="success" title="把令牌配置到秤端工作台">
      <template #subTitle>
        <p>令牌只显示这一次，关闭后无法再次查看；泄露时请在「会话记录」里结束该会话。</p>
        <p class="scale-secret">{{ tokenResult?.sessionToken }}</p>
        <p>有效期至 {{ datetime(tokenResult?.expiresAt) }}</p>
      </template>
      <template #extra>
        <a-button type="primary" @click="copyText(tokenResult?.sessionToken, '令牌已复制')">复制令牌</a-button>
      </template>
    </a-result>
  </a-modal>

  <!-- 会话记录 -->
  <a-drawer :open="sessionsOpen" title="员工会话记录" :width="scmDrawerWidth('m')" @close="sessionsOpen = false">
    <a-alert v-if="sessionsError" :message="sessionsError" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="loadSessions">重试</a-button>
      </template>
    </a-alert>
    <a-table
      size="small"
      :data-source="sessions"
      :columns="sessionColumns"
      row-key="id"
      :loading="sessionsLoading"
      :pagination="false"
      :locale="{emptyText: '这台秤还没有员工登录过'}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'active'">
          <a-tag :color="record.active ? 'green' : 'default'">{{ record.active ? '有效' : '已结束' }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'issuedAt'">{{ datetime(record.issuedAt) }}</template>
        <template v-else-if="column.dataIndex === 'expiresAt'">{{ datetime(record.expiresAt) }}</template>
        <template v-else-if="column.dataIndex === 'endedAt'">
          {{ record.endedAt ? datetime(record.endedAt) : '—' }}
          <div v-if="record.endReason" class="scale-hint">{{ record.endReason }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-if="record.active" type="link" size="small" danger v-privilege="PERM.SESSION_ISSUE" @click="endSession(record)">
            结束
          </a-button>
          <span v-else>—</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-drawer>
</template>

<script setup lang="ts">
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal, type FormInstance, type TableColumnsType} from 'ant-design-vue';
import {scaleDeviceApi} from '/@/api/business/scm/scale-device-api';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {hasPermission} from '../common/scm-permission';
import {datetime} from '../common/scm-display';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import {
  SCM_SCALE_DEVICE_STATUS_ENUM,
  type ScaleDevice,
  type ScaleDeviceQuery,
  type ScaleDeviceRegisterForm,
  type ScaleDeviceRegisterResult,
  type ScaleDeviceSession,
  type ScaleDeviceStatus,
  type ScaleOperatorSessionResult,
} from './scale-device-types';

const PERM = {
  QUERY: 'scm:device:scale:query',
  BIND: 'scm:device:scale:bind',
  SESSION_ISSUE: 'scm:device:scale:session:issue',
} as const;

const TABLE_ID = 'scm-scale-device-list-table';

const statusOptions = Object.entries(SCM_SCALE_DEVICE_STATUS_ENUM).map(([value, meta]) => ({
  value,
  label: meta.desc,
}));

const queryForm = reactive<ScaleDeviceQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<ScaleDevice[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let requestId = 0;

const columns = ref<TableColumnsType<ScaleDevice>>([
  {title: '设备编号', dataIndex: 'deviceCode', width: 140, ellipsis: true},
  {title: '设备名称', dataIndex: 'deviceName', width: 150, ellipsis: true},
  {title: '型号', dataIndex: 'deviceModel', width: 140, ellipsis: true},
  {title: '仓库', dataIndex: 'warehouseName', width: 110, ellipsis: true},
  {title: '工位', dataIndex: 'stationCode', width: 90, ellipsis: true},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
  {title: '在线', dataIndex: 'online', width: 220},
  {title: '有效会话', dataIndex: 'activeSessionCount', width: 85},
  {title: '凭证到期', dataIndex: 'credentialExpiresAt', width: 150},
  {title: '操作', dataIndex: 'action', align: 'center', width: 190, fixed: 'right'},
]);
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns);

const canBind = computed(() => hasPermission(PERM.BIND));

/** 已撤销的设备不能再撤销；暂停/撤销的后端接口目前只有撤销。 */
function rowActions(record: ScaleDevice): ScmActionItem[] {
  return [
    {
      key: 'revoke',
      label: '撤销绑定',
      danger: true,
      disabled: record.status === 'REVOKED',
      hidden: !canBind.value,
    },
  ];
}

function onRowAction(key: string, record: ScaleDevice) {
  if (key === 'revoke') {
    revoke(record);
  }
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await scaleDeviceApi.query(queryForm, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      tableData.value = response.data.list;
      total.value = response.data.total;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = '电子秤列表加载失败，请重试';
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  void queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.warehouseId = undefined;
  queryForm.status = undefined;
  onSearch();
}

// ---------------- 登记 ----------------

const registerOpen = ref(false);
const registering = ref(false);
const registerError = ref('');
const registerFormRef = ref<FormInstance>();
/** 表单里仓库在未选择时是 undefined；提交前由必填校验保证确实选过。 */
type RegisterFormState = Omit<ScaleDeviceRegisterForm, 'warehouseId'> & {warehouseId?: Id};
const registerForm = reactive<RegisterFormState>({deviceCode: ''});
const registerRules = {
  deviceCode: [{required: true, message: '请填写设备编号', trigger: 'blur'}],
  warehouseId: [{required: true, message: '请选择绑定仓库', trigger: 'change'}],
};

function openRegister() {
  registerError.value = '';
  Object.assign(registerForm, {deviceCode: '', deviceName: undefined, deviceModel: undefined, stationCode: undefined});
  registerOpen.value = true;
}

function closeRegister() {
  registerOpen.value = false;
}

const pairingVisible = ref(false);
const pairingResult = ref<ScaleDeviceRegisterResult>();

async function submitRegister() {
  try {
    await registerFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, registerFormRef.value);
    return;
  }
  registering.value = true;
  registerError.value = '';
  try {
    const response = await scaleDeviceApi.register({...registerForm, warehouseId: registerForm.warehouseId!});
    pairingResult.value = response.data;
    registerOpen.value = false;
    pairingVisible.value = true;
    await queryData();
  } catch {
    // 失败原因由全局提示给出；弹窗保持打开以便修改
  } finally {
    registering.value = false;
  }
}

// ---------------- 签发会话 ----------------

const sessionOpen = ref(false);
const issuing = ref(false);
const sessionError = ref('');
const sessionDevice = ref<ScaleDevice>();
const sessionForm = reactive<{employeeId?: number; validMinutes: number}>({validMinutes: 480});

const tokenVisible = ref(false);
const tokenResult = ref<ScaleOperatorSessionResult>();

function openSession(device: ScaleDevice) {
  sessionDevice.value = device;
  sessionError.value = '';
  sessionForm.employeeId = undefined;
  sessionForm.validMinutes = 480;
  sessionOpen.value = true;
}

async function submitSession() {
  if (!sessionDevice.value) return;
  if (sessionForm.employeeId === undefined) {
    sessionError.value = '请选择操作员工';
    return;
  }
  issuing.value = true;
  sessionError.value = '';
  try {
    const response = await scaleDeviceApi.issueSession(sessionDevice.value.id, {
      employeeId: sessionForm.employeeId,
      validMinutes: sessionForm.validMinutes,
    });
    tokenResult.value = response.data;
    sessionOpen.value = false;
    tokenVisible.value = true;
    await queryData();
  } catch {
    // 失败原因由全局提示给出
  } finally {
    issuing.value = false;
  }
}

// ---------------- 会话记录 ----------------

const sessionsOpen = ref(false);
const sessionsLoading = ref(false);
const sessionsError = ref('');
const sessions = ref<ScaleDeviceSession[]>([]);
const sessionDeviceId = ref<Id>();

const sessionColumns: TableColumnsType<ScaleDeviceSession> = [
  {title: '员工', dataIndex: 'employeeName', width: 120},
  {title: '状态', dataIndex: 'active', width: 90},
  {title: '签发时间', dataIndex: 'issuedAt', width: 170},
  {title: '到期时间', dataIndex: 'expiresAt', width: 170},
  {title: '结束', dataIndex: 'endedAt', width: 170},
  {title: '操作', dataIndex: 'action', align: 'center', width: 80},
];

function openSessions(device: ScaleDevice) {
  sessionDeviceId.value = device.id;
  sessionsOpen.value = true;
  void loadSessions();
}

async function loadSessions() {
  if (sessionDeviceId.value === undefined) return;
  sessionsLoading.value = true;
  sessionsError.value = '';
  try {
    const response = await scaleDeviceApi.sessions(sessionDeviceId.value, {suppressGlobalErrorMessage: true});
    sessions.value = response.data;
  } catch {
    sessionsError.value = '会话记录加载失败，请重试';
  } finally {
    sessionsLoading.value = false;
  }
}

async function endSession(record: ScaleDeviceSession) {
  if (sessionDeviceId.value === undefined) return;
  try {
    await scaleDeviceApi.endSession(sessionDeviceId.value, record.id);
    message.success('会话已结束，秤端下次请求即失效');
    await loadSessions();
    await queryData();
  } catch {
    // 失败原因由全局提示给出
  }
}

// ---------------- 撤销绑定 ----------------

function revoke(record: ScaleDevice) {
  Modal.confirm({
    title: `撤销「${record.deviceCode}」的绑定？`,
    content: '撤销后该设备不能再领取任务或上报读数，仍在有效期内的员工会话一起结束；历史读数与打印记录保留，设备编号不会被复用。',
    okText: '撤销绑定',
    okType: 'danger',
    onOk: async () => {
      await scaleDeviceApi.revoke(record.id);
      message.success('设备已撤销');
      await queryData();
    },
  });
}

async function copyText(value: string | null | undefined, successText: string) {
  if (!value) return;
  await navigator.clipboard.writeText(value);
  message.success(successText);
}

onMounted(queryData);
</script>

<style scoped>
.scale-online {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
}

.scale-online__time {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.scale-secret {
  padding: 10px 12px;
  border: 1px dashed var(--scm-line, #d9d9d9);
  border-radius: 6px;
  background: var(--scm-fill, #fafafa);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 15px;
  word-break: break-all;
}

.scale-hint {
  margin-top: 4px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  line-height: 18px;
  white-space: normal;
}
</style>
