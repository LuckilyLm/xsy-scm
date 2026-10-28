<!--
  *  表格列设置
  *
  *
-->

<template>
  <span>
    <a-tooltip title="全屏" v-if="!fullScreenFlag">
      <a-button type="text" @click="onFullScreen" size="small">
        <template #icon><fullscreen-outlined /></template>
      </a-button>
    </a-tooltip>
    <a-tooltip title="取消全屏" v-if="fullScreenFlag">
      <a-button type="text" @click="onFullScreen" size="small">
        <template #icon><fullscreen-exit-outlined /></template>
      </a-button>
    </a-tooltip>
    <a-tooltip title="刷新">
      <a-button type="text" @click="props.refresh" size="small">
        <template #icon><redo-outlined /></template>
      </a-button>
    </a-tooltip>
    <a-tooltip title="列设置">
      <a-button type="text" @click="showModal" size="small">
        <template #icon><setting-outlined /></template>
      </a-button>
    </a-tooltip>
    <SmartTableColumnModal ref="smartTableColumnModal" @change="updateColumn" />
  </span>
</template>

<script setup lang="ts">
import _ from 'lodash';
import {tableColumnApi} from '/@/api/support/table-column-api';
import {onMounted, ref, watch, reactive} from 'vue';
import SmartTableColumnModal from './smart-table-column-modal.vue';
import {message} from 'ant-design-vue';
import {mergeColumn} from './smart-table-column-merge';
import {smartSentry} from '/@/lib/smart-sentry';
import {useAppConfigStore} from '/@/store/modules/system/app-config';

const props = defineProps({
  // 表格列数组
  modelValue: {
    type: Array,
    default: () => [],
  },
  // 刷新表格函数
  refresh: {
    type: Function,
    required: true,
  },
  // 表格id
  tableId: {
    type: Number,
    require: true,
  },
  // 列设置中不展示的列键（如无权限查看的成本列），不影响本地列数据与保存合并
  excludeColumnKeys: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['update:modelValue']);

// 原始表格列数据（复制一份最原始的columns集合，以供后续各个地方使用）
let originalColumn = reactive(_.cloneDeep(props.modelValue));
// 存储最新的列数据
let newColumn = reactive(_.cloneDeep(props.modelValue));

// 拖拽改宽后的落库请求：打开列设置前先等它写完，
// 否则刚调好的宽度会被服务端旧配置盖回去
let pendingSave: Promise<unknown> = Promise.resolve();

/** 页面列与内部列共有的最小形状：列键、显隐、宽度与拖拽标记。 */
interface ColumnLike {
  dataIndex?: unknown;
  width?: number | string;
  showFlag?: boolean;
  fixed?: unknown;
  columnKey?: string;
  dragAndDropFlag?: boolean;
  [key: string]: unknown;
}

// 用于监听表格拖拽后新的列数据
watch(
  () => props.modelValue,
  (value) => {
    let resized = false;
    const incoming = (value ?? []) as ColumnLike[];
    newColumn.forEach((item: ColumnLike) => {
      incoming.forEach((itemNewColumns) => {
        if (item.dataIndex === itemNewColumns.dataIndex) {
          Object.assign(item, itemNewColumns);
          // 表头拖拽改宽由 useScmTableFill 打标：宽度已经写进列定义，这里负责落库。
          // 标记本身不参与列设置合并，两边都要清掉，否则打开弹窗会被当成待保存
          if (itemNewColumns.dragAndDropFlag) {
            delete itemNewColumns.dragAndDropFlag;
            delete item.dragAndDropFlag;
            resized = true;
          }
        }
      });
    });
    if (resized) {
      pendingSave = persistColumns();
    }
  },
  {
    deep: true,
  }
);
onMounted(buildUserTableColumns);

//构建用户的数据列
async function buildUserTableColumns() {
  if (!props.tableId) {
    return;
  }

  let userTableColumnArray = [];
  try {
    let res = await tableColumnApi.getColumns(props.tableId);
    if (res.data) {
      try {
        userTableColumnArray = JSON.parse(res.data);
      } catch (e1) {
        smartSentry.captureError(e1);
      }
    }
  } catch (e) {
    smartSentry.captureError(e);
  }
  updateColumn(userTableColumnArray);
}

// ----------------- 全屏 -------------------
const fullScreenFlag = ref(false);

function onFullScreen() {
  if (fullScreenFlag.value) {
    // 退出全屏
    handleExitFullScreen();
    exitElementFullscreen(document.body);
  } else {
    fullScreenFlag.value = true;
    useAppConfigStore().startFullScreen();
    launchElementFullScreen(document.body);
  }
}

// 处理退出全屏
function handleExitFullScreen() {
  document.querySelector('#smartAdminHeader').style.display = 'block';
  fullScreenFlag.value = false;
  useAppConfigStore().exitFullScreen();
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  document.removeEventListener('mozfullscreenchange', handleFullscreenChange); // Firefox
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange); // Chrome, Safari and Opera
  document.removeEventListener('MSFullscreenChange', handleFullscreenChange); // Internet Explorer and Edge
}

//判断各种浏览器 -全屏
function launchElementFullScreen(element) {
  document.querySelector('#smartAdminHeader').style.display = 'none';
  if (element.requestFullscreen) {
    element.requestFullscreen();
  } else if (element.mozRequestFullScreen) {
    element.mozRequestFullScreen();
  } else if (element.webkitRequestFullScreen) {
    element.webkitRequestFullScreen();
  } else if (element.msRequestFullscreen) {
    element.msRequestFullscreen();
  } else {
    message.error('当前浏览器不支持部分全屏！');
  }
  document.addEventListener('fullscreenchange', handleFullscreenChange);
  document.addEventListener('mozfullscreenchange', handleFullscreenChange); // Firefox
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange); // Chrome, Safari and Opera
  document.addEventListener('MSFullscreenChange', handleFullscreenChange); // Internet Explorer and Edge
}

function handleFullscreenChange() {
  if (document.fullscreenElement || document.mozFullScreenElement || document.webkitFullscreenElement || document.msFullscreenElement) {
    console.log('进入全屏模式');
  } else {
    console.log('退出全屏模式');
    handleExitFullScreen();
  }
}

//判断各种浏览器 -退出全屏
function exitElementFullscreen(element) {
  if (document.exitFullscreen) {
    document.exitFullscreen();
  } else if (document.mozCancelFullScreen) {
    document.mozCancelFullScreen();
  } else if (document.webkitExitFullscreen) {
    document.webkitExitFullscreen();
  } else if (document.msExitFullscreen) {
    document.msExitFullscreen();
  }
}

// ----------------- 弹窗 修改表格列 -------------------

const smartTableColumnModal = ref<{show: (columns: unknown[], tableId?: number | string) => void}>();

/** 拖拽列宽后立即保存：刷新页面与列设置弹窗读到同一份宽度。 */
async function persistColumns() {
  if (!props.tableId) {
    return;
  }
  const columnList = newColumn.map((item: any, index: number) => {
    const column: {columnKey: string; sort: number; width?: number | string; showFlag?: boolean} = {
      columnKey: item.columnKey,
      sort: index + 1,
    };
    if (item.width) {
      column.width = item.width;
    }
    column.showFlag = item.fixed ? true : item.showFlag !== false;
    return column;
  });
  try {
    await tableColumnApi.updateTableColumn({tableId: props.tableId, columnList});
  } catch (e) {
    smartSentry.captureError(e);
  }
}

async function showModal() {
  await pendingSave;
  const excluded = new Set(props.excludeColumnKeys);
  const visibleColumns = excluded.size
    ? newColumn.filter((item: any) => {
        const key = item.columnKey ?? item.dataIndex;
        return !excluded.has(key);
      })
    : newColumn;
  smartTableColumnModal.value?.show(visibleColumns, props.tableId);
}

// 将弹窗修改的列数据，赋值给原表格 列数组
function updateColumn(changeColumnArray) {
  let obj = {};
  // 如果为空数组代表恢复默认，使用原始表格数据
  //合并列
  obj = mergeColumn(_.cloneDeep(originalColumn), changeColumnArray, props.tableId);
  const newColumns = obj.newColumns;
  newColumn.forEach((item) => {
    obj.newColumns.forEach((itemNewColumns) => {
      if (item.dataIndex === itemNewColumns.dataIndex) {
        Object.assign(item, itemNewColumns);
      }
    });
  });
  emit(
    'update:modelValue',
    newColumns.filter((e) => e.showFlag)
  );
}

// ========= 定义 watch 监听 ===============
watch(
  () => props.tableId,
  (e) => {
    if (e) {
      originalColumn = _.cloneDeep(props.modelValue);
      newColumn = _.cloneDeep(props.modelValue);
      buildUserTableColumns();
    }
  },
  {immediate: false}
);
</script>
