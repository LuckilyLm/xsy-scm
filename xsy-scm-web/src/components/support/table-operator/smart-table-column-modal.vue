<!--
  *  表格列设置
  *
  *
-->
<template>
  <a-modal :width="800" :open="visible" title="设置列" :destroyOnClose="true" :closable="false">
    <div v-if="!tableId">
      <a-alert type="error" show-icon class="smart-margin-bottom10">
        <template #message> 您尚未设置 TableOperator 组件的 tableId</template>
      </a-alert>
      <a-alert type="error" class="smart-margin-bottom10">
        <template #message>
          1. 请在 src\constants\support\table-id-const.js 中配置 tableId 常量
          <br />
          <br />
          2. 在自己的业务表格页面组件中使用如下：
          <br />
          导入: import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
          <br />
          使用: {{ '<TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.XXX" :refresh="queryData" />' }}
          <br />
          <br />
          3. 具体用法可参考员工管理
        </template>
      </a-alert>
    </div>
    <div v-else>
      <FormNote
        :lines="[
          '勾选需要显示的列；拖动左侧手柄可调整顺序。带图钉标记的固定列不可拖动或取消。',
          '右侧数字是这一列的宽度（像素），清空后跟随页面默认宽度并在宽屏上自动铺满。',
        ]"
      />
      <!-- 网格勾选而不是表格：一屏能看完全部列，不必为了勾一项反复滚动。 -->
      <div ref="gridRef" class="scm-column-grid">
        <div
          v-for="item in tableData"
          :key="item.columnKey"
          class="scm-column-grid__cell"
          :class="{
            'is-fixed': item.fixed,
            'is-off': !isColumnShown(item) && !item.fixed,
          }"
        >
          <!-- 勾选与拖拽放在同一个可聚焦区域；列宽输入框是并列控件，不能触发显隐切换 -->
          <span
            class="scm-column-grid__toggle"
            role="checkbox"
            :aria-checked="isColumnShown(item)"
            :aria-disabled="item.fixed ? true : undefined"
            :tabindex="item.fixed ? -1 : 0"
            @click="toggleColumn(item)"
            @keydown.space.prevent="toggleColumn(item)"
            @keydown.enter.prevent="toggleColumn(item)"
          >
            <span class="scm-column-grid__handle" :class="{'is-locked': item.fixed}">
              <pushpin-outlined v-if="item.fixed" />
              <span v-else class="scm-column-grid__grip" aria-hidden="true"></span>
            </span>
            <span
              class="scm-column-grid__check"
              :class="{
                'is-on': isColumnShown(item) && !item.fixed,
                'is-off': !isColumnShown(item) && !item.fixed,
                'is-locked': item.fixed,
              }"
              aria-hidden="true"
            >
              <check-outlined v-if="isColumnShown(item) && !item.fixed" />
            </span>
            <span class="scm-column-grid__label" :title="item.title">{{ item.title }}</span>
          </span>
          <a-input-number
            class="scm-column-grid__width"
            :value="widthOf(item)"
            :min="COLUMN_WIDTH_MIN"
            :max="COLUMN_WIDTH_MAX"
            :step="10"
            size="small"
            placeholder="默认"
            :aria-label="'列宽（像素）：' + (item.title ?? '')"
            @update:value="(value: number | string | null) => changeWidth(item, value)"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <!-- 破坏性操作放最左，确认类按钮靠右：避免「恢复默认」和「保存」挨着被误点 -->
      <div class="smart-table-column-footer">
        <a-button v-if="tableId" key="reset" class="smart-table-column-footer__reset" :loading="submitLoading" @click="reset" danger
          >恢复默认
        </a-button>
        <a-button key="back" @click="hide">取消</a-button>
        <a-button v-if="tableId" key="submit" type="primary" :loading="submitLoading" @click="save">保存</a-button>
      </div>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {SmartLoading} from '/@/components/framework/smart-loading';
import {tableColumnApi} from '/@/api/support/table-column-api';
import {nextTick, ref} from 'vue';
import _ from 'lodash';
import Sortable from 'sortablejs';
import {message, Modal} from 'ant-design-vue';
import {mergeColumn} from './smart-table-column-merge';
import {smartSentry} from '/@/lib/smart-sentry';
import FormNote from '/@/components/framework/form-note/index.vue';

const emit = defineEmits(['change']);

defineExpose({show});

// ---------------- 显示 / 隐藏 --------------------
let tableId: number | string | null = null;
const visible = ref(false);
const gridRef = ref<HTMLElement>();

//显示
function show(columns: unknown[], showTableId?: number | string) {
  tableId = showTableId ?? null;
  visible.value = true;
  selectedRowKeyList.value = [];
  getUserTableColumns(tableId, _.cloneDeep(columns));
}

//隐藏
function hide() {
  visible.value = false;
}

const saveFlag = ref(false);

//获取用户的列数据
async function getUserTableColumns(tableId: number | string | null, columns: unknown[]) {
  if (!tableId) {
    return;
  }
  SmartLoading.show();
  let userTableColumnArray = [];
  try {
    let res = await tableColumnApi.getColumns(tableId);
    if (res.data) {
      try {
        userTableColumnArray = JSON.parse(res.data);
      } catch (e1) {
        smartSentry.captureError(e1);
      }
    }
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    SmartLoading.hide();
  }

  //根据前端列和后端列构建新的列数据
  let obj = mergeColumn(columns, userTableColumnArray, tableId);
  tableData.value = obj.newColumns;
  saveFlag.value = obj.saveFlag;
  //将已经显示的展示出来
  for (const item of tableData.value) {
    if (item.showFlag) {
      selectedRowKeyList.value.push(item.columnKey);
    }
  }
  if (saveFlag.value) {
    save(false);
  }

  nextTick(() => {
    initDrag();
  });
}

// --------------------- 网格渲染 --------------------------------
interface ColumnGridItem {
  columnKey: string;
  title?: string;
  width?: number | string;
  showFlag?: boolean;
  fixed?: boolean | 'left' | 'right';
  [key: string]: unknown;
}

const tableData = ref<ColumnGridItem[]>([]);

/** 列宽范围：小于 60px 文字没法读，大于 800px 一屏放不下其他列。 */
const COLUMN_WIDTH_MIN = 60;
const COLUMN_WIDTH_MAX = 800;

function isColumnShown(item: ColumnGridItem) {
  return selectedRowKeyList.value.indexOf(item.columnKey) > -1;
}

/** 输入框只接受数字；列宽可能是历史保存的字符串，统一转成数字再绑定。 */
function widthOf(item: ColumnGridItem): number | undefined {
  const width = Number(item.width);
  return Number.isFinite(width) && width > 0 ? width : undefined;
}

/** 清空表示「跟随页面默认宽度」，保存时不写 width，由页面默认值与自动铺满接管。 */
function changeWidth(item: ColumnGridItem, value: number | string | null) {
  const width = Number(value);
  if (value == null || value === '' || !Number.isFinite(width) || width <= 0) {
    delete item.width;
    return;
  }
  item.width = Math.min(COLUMN_WIDTH_MAX, Math.max(COLUMN_WIDTH_MIN, Math.round(width)));
}

/**
 * 切换单列的显示状态。固定列始终显示，点它不产生任何变化。
 */
function toggleColumn(item: ColumnGridItem) {
  if (item.fixed) {
    return;
  }
  const index = selectedRowKeyList.value.indexOf(item.columnKey);
  if (index > -1) {
    selectedRowKeyList.value.splice(index, 1);
  } else {
    selectedRowKeyList.value.push(item.columnKey);
  }
}

// --------------------- 拖拽排序 --------------------------------
function initDrag() {
  if (!gridRef.value) {
    return;
  }
  Sortable.create(gridRef.value, {
    animation: 150,
    handle: '.scm-column-grid__handle:not(.is-locked)',
    ghostClass: 'scm-column-grid__cell--ghost',
    chosenClass: 'scm-column-grid__cell--chosen',
    // 固定列是表格尾部的能力列，不允许被普通列挤到中间。
    onMove(event: {related: Element}) {
      return !event.related.classList.contains('is-fixed');
    },
    onEnd({newIndex, oldIndex}) {
      if (newIndex === oldIndex) {
        return;
      }
      moveTableData(oldIndex, newIndex);
    },
  });
}

//移动表格数据
function moveTableData(oldIndex: number, newIndex: number) {
  const currRow = tableData.value.splice(oldIndex, 1)[0];
  tableData.value.splice(newIndex, 0, currRow);
}

// ------------------------- 选中集合 ------------------------
const selectedRowKeyList = ref<string[]>([]);

// -------------------------提交表单【恢复默认、保存、取消】 ------------------------
const submitLoading = ref(false);

//重置
function reset() {
  Modal.confirm({
    title: '确定要恢复默认吗？',
    content: '确定恢复默认后，该信息将不可恢复',
    okText: '确定恢复',
    okType: 'danger',
    onOk() {
      (async () => {
        submitLoading.value = true;
        try {
          await tableColumnApi.deleteColumns(tableId);
          message.success('恢复默认成功');
          emit('change', []);
          hide();
        } catch (e) {
          smartSentry.captureError(e);
        } finally {
          submitLoading.value = false;
        }
      })();
    },
    cancelText: '取消',
    onCancel() {},
  });
}

//保存
async function save(closeFlag = true) {
  submitLoading.value = true;
  try {
    let columnList = [];
    for (let index = 0; index < tableData.value.length; index++) {
      let item = tableData.value[index];
      const column: {columnKey: string; sort: number; width?: number | string; showFlag?: boolean} = {
        columnKey: item.columnKey,
        sort: index + 1,
      };
      if (item.width) {
        column.width = item.width;
      }
      // 固定列永远可见，不跟随勾选集合。
      column.showFlag = item.fixed ? true : isColumnShown(item);
      columnList.push(column);
    }

    columnList = _.sortBy(columnList, (e) => e.sort);

    await tableColumnApi.updateTableColumn({
      tableId,
      columnList,
    });

    emit('change', columnList);
    if (closeFlag) {
      message.success('保存成功');
      hide();
    }
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    submitLoading.value = false;
  }
}
</script>
<style scoped lang="less">
.smart-table-column-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

/* 恢复默认推到最左；没有它时（无 tableId）其余按钮自然靠右 */
.smart-table-column-footer__reset {
  margin-right: auto;
}

.scm-column-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px 12px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.scm-column-grid__cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 5px 8px;
  border-radius: 6px;
  transition: background-color 0.2s;
}

/* 勾选区：手柄 + 勾选框 + 列名，整块可点击 */
.scm-column-grid__toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  height: 28px;
  cursor: pointer;
  border-radius: 4px;

  &:hover {
    background-color: #f0f9eb;
  }
}

.scm-column-grid__cell.is-fixed .scm-column-grid__toggle {
  cursor: default;

  &:hover {
    background-color: transparent;
  }
}

/* 列宽输入框：不参与勾选，清空即跟随默认宽度 */
.scm-column-grid__width {
  flex: none;
  width: 76px;
}

.scm-column-grid__cell--ghost {
  opacity: 0.4;
}

.scm-column-grid__cell--chosen {
  background-color: #f0f9eb;
}

.scm-column-grid__handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 12px;
  height: 16px;
  color: #bfbfbf;
  cursor: grab;

  &.is-locked {
    cursor: default;
    color: #8c8c8c;
  }
}

/* 两列点阵，和拖拽手柄的视觉重量一致，不抢字段名 */
.scm-column-grid__grip {
  width: 8px;
  height: 12px;
  background-image: radial-gradient(circle, #bfbfbf 1px, transparent 1px);
  background-size: 4px 4px;
}

.scm-column-grid__check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  font-size: 11px;
  color: #ffffff;

  &.is-on {
    background-color: #52c41a;
  }

  &.is-off {
    background-color: #d9d9d9;
  }

  /* 固定列画成空心方框：表示「一定显示、不可取消」 */
  &.is-locked {
    background-color: transparent;
    border: 1px solid #d9d9d9;
  }
}

.scm-column-grid__label {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.88);
}

.scm-column-grid__cell.is-off .scm-column-grid__label {
  color: rgba(0, 0, 0, 0.45);
}

.scm-column-grid__cell.is-fixed .scm-column-grid__label {
  color: rgba(0, 0, 0, 0.45);
}
</style>
