<!--
  分拣任务列表 + 详情录入（P1 分拣管理）。

  ## 这一页只生产「实发事实」，不生产订单事实

  分拣量写在 `sorting_task_item` 上，**不回写** `sales_order_item` 的实发量与结算金额，
  **不写**库存余额与流水，也不创建出库单（裁决第 1、3 条）。因此页面上的「计划量」是建单时
  冻结的订单行实发量快照，之后订单侧怎么改都不追溯已生成的任务 —— 这也是详情区必须常驻
  那条提示的原因：操作人做完分拣后如果回订单页看到原值没变，第一反应会是「系统没保存」。

  ## 状态机决定了每个按钮的出现条件

  ```text
  PENDING ──首次录入──→ SORTING ──每行都有结果──→ COMPLETED ──重开──→ SORTING
     │                     │
     └────────取消─────────┴──→ CANCELLED（同事务释放全部明细占用位）
  ```

  - 取消 / 重开的 `reason` 必填：取消会释放占用让订单行能被重新分拣，重开会推翻「已完成」
    这一事实，两者都需要留下判断依据（只进通用操作日志，不另建分拣审计表）。
  - 已完成只能走重开、不能直接取消，所以两个按钮的 `v-if` 条件不同，不是漏写。
  - `scm:sorting:task:assign` 隐含跨指派人可见：持者才看得到未指派队列与按人筛选，
    分拣员只看到派给自己的任务。

  ## 编辑权判定在服务端，前端只决定输入框长不长出来

  录入要求「调用者是受指派人本人 + 任务还在干活 + 持明细编辑权」。三者缺一就渲染成只读文本，
  而不是留一个提交后必然被 30005 拒掉的输入框。判定读的是 user store 里的 `employeeId`
  与详情返回的 `assigneeEmployeeId`，服务端仍是权威。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="onSearch">
    <a-form-item label="关键词" class="smart-query-form-item">
      <a-input
          v-model:value="queryForm.keyword"
          placeholder="任务号 / 仓库 / 分拣员 / 订单号 / 商品 / 客户"
          allow-clear
          :maxlength="100"
          style="width: 260px"
          @pressEnter="onSearch"
      />
    </a-form-item>
    <a-form-item label="状态" class="smart-query-form-item">
      <a-select v-model:value="queryForm.status" :options="statusOptions" placeholder="全部" allow-clear style="width: 130px"/>
    </a-form-item>
    <a-form-item label="仓库" class="smart-query-form-item">
      <WarehouseSelect v-model:value="queryForm.warehouseId" placeholder="全部仓库" width="200px"/>
    </a-form-item>
    <!-- 未指派队列与按人筛选只对持指派权（即跨指派人可见）的人有意义：
         分拣员的数据范围里根本不存在别人的任务，把筛选摆出来只会让人以为「别人也有任务被藏起来了」。 -->
    <template v-if="isQueueManager">
      <a-form-item label="只看未指派" class="smart-query-form-item">
        <a-switch v-model:checked="queryForm.unassignedOnly"/>
      </a-form-item>
      <a-form-item label="受指派人" class="smart-query-form-item">
        <EmployeeSelect v-model:value="assigneeFilter" placeholder="全部" width="180px"/>
      </a-form-item>
    </template>
    <a-form-item class="smart-query-form-item">
      <a-button-group>
        <a-button type="primary" v-privilege="'scm:sorting:task:query'" @click="onSearch">查询</a-button>
        <a-button @click="resetQuery">重置</a-button>
      </a-button-group>
    </a-form-item>
  </a-form>

  <a-alert v-if="listError" :message="listError" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <div class="smart-table-btn-block">
      <a-button type="primary" v-privilege="'scm:sorting:task:add'" @click="openCreate">新建分拣任务</a-button>
      <a-typography-text type="secondary" class="toolbar-hint">
        一个订单行同一时刻只属于一个活动任务；建单时按仓库与授权范围取候选订单行。
      </a-typography-text>
    </div>
    <div class="smart-table-setting-block">
      <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_SORTING_TASK"
          :refresh="queryData"
      />
    </div>

    <a-table
        :id="SCM_SORTING_TABLE_ID.TASK"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1520 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="SCM_SORTING_TASK_STATUS_COLOR[record.status as SortingTaskStatus]">
            {{ statusDesc(record.status) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'assigneeName'">
          {{ record.assigneeName || '未指派' }}
        </template>
        <template v-else-if="column.dataIndex === 'processedCount'">
          <span class="num">{{ record.processedCount }} / {{ record.itemCount }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'printCount'">
          <span class="num">{{ record.printCount ? record.printCount : '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record.id)">详情</a-button>
            <a-button
                v-if="SCM_SORTING_PRINTABLE_STATUS.includes(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:print'"
                @click="openPrint(record)"
            >打印
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:assign'"
                @click="openAction('assign', record)"
            >{{ record.assigneeEmployeeId == null ? '指派' : '改派' }}
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:complete'"
                @click="confirmComplete(record)"
            >完成
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                danger
                v-privilege="'scm:sorting:task:cancel'"
                @click="openAction('cancel', record)"
            >取消
            </a-button>
            <a-button
                v-if="record.status === 'COMPLETED'"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:reopen'"
                @click="openAction('reopen', record)"
            >重开
            </a-button>
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :page-size-options="['10', '20', '50', '100']"
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共 ${n} 个分拣任务`"
      />
    </div>
  </a-card>

  <!-- 新建任务：仓库必须显式选择（不从订单 / 客户 / 线路猜），候选订单行由服务端按建单权开放 -->
  <a-modal
      v-model:open="createOpen"
      title="新建分拣任务"
      :width="1100"
      :confirm-loading="creating"
      :ok-button-props="{ disabled: !candidateSelected.length || createLoading || candidateRows.length === 0 }"
      :ok-text="`创建任务（${candidateSelected.length} 行）`"
      @ok="submitCreate"
  >
    <a-alert
        type="info"
        show-icon
        message="候选行是「已确认订单上尚未被任何活动任务占用」的明细；计划量取该行的实发量并在此刻冻结。"
    />
    <a-alert v-if="createError" type="error" show-icon :message="createError"/>
    <a-form layout="inline" class="create-form" @submit.prevent="searchCandidates">
      <a-form-item label="仓库" required>
        <WarehouseSelect v-model:value="createForm.warehouseId" width="220px"/>
      </a-form-item>
      <a-form-item label="受指派人">
        <EmployeeSelect v-model:value="createForm.assigneeEmployeeId" placeholder="暂不指派" width="200px"/>
      </a-form-item>
      <a-form-item label="备注">
        <a-input v-model:value="createForm.remark" :maxlength="500" style="width: 220px" placeholder="可选"/>
      </a-form-item>
      <a-form-item label="订单 / 客户 / 商品">
        <a-input
            v-model:value="candidateQuery.keyword"
            placeholder="订单 / 客户 / 商品"
            allow-clear
            :maxlength="100"
            @pressEnter="searchCandidates"
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" @click="searchCandidates">查询候选行</a-button>
          <a-button @click="resetCandidates">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
    <a-table
        :id="SCM_SORTING_TABLE_ID.CANDIDATE_LINE"
        size="small"
        :data-source="candidateRows"
        :columns="candidateColumns"
        row-key="salesOrderItemId"
        :loading="createLoading"
        :pagination="false"
        :scroll="{ x: 940, y: 320 }"
        :row-selection="{ selectedRowKeys: candidateSelected, onChange: onCandidateSelect, preserveSelectedRowKeys: true }"
        :locale="{ emptyText: '暂无可分拣的订单行' }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'actualQuantity'">
          <span class="num">{{ quantityText(record.actualQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'orderedQuantity'">
          <span class="num">{{ quantityText(record.orderedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'productTypeSnapshot'">
          {{ productTypeDesc(record.productTypeSnapshot) }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          v-model:current="candidateQuery.pageNum"
          v-model:page-size="candidateQuery.pageSize"
          :total="candidateTotal"
          show-size-changer
          :page-size-options="['10', '20', '50', '100']"
          @change="loadCandidates"
      />
    </div>
  </a-modal>

  <!-- 详情：头部 + 明细录入 -->
  <a-drawer v-model:open="detailOpen" title="分拣任务详情" width="min(1500px, 96vw)" :destroy-on-close="true">
    <a-alert v-if="detailError" :message="detailError" type="error" show-icon>
      <template #action>
        <a-button @click="reloadDetail">刷新任务</a-button>
      </template>
    </a-alert>
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <div class="task-heading">
          <div>
            <h2>{{ detail.task.taskNo }}</h2>
            <span>{{ detail.task.warehouseNameSnapshot }} · 创建于 {{ datetime(detail.task.createdAt) }}</span>
          </div>
          <a-space wrap>
            <a-tag :color="SCM_SORTING_TASK_STATUS_COLOR[detail.task.status]">{{ statusDesc(detail.task.status) }}</a-tag>
            <a-button
                v-if="SCM_SORTING_PRINTABLE_STATUS.includes(detail.task.status)"
                v-privilege="'scm:sorting:task:print'"
                @click="openPrint(detail.task)"
            >打印
            </a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                v-privilege="'scm:sorting:task:assign'"
                :disabled="busy"
                @click="openAction('assign', detail.task)"
            >{{ detail.task.assigneeEmployeeId == null ? '指派' : '改派' }}
            </a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                type="primary"
                v-privilege="'scm:sorting:task:complete'"
                :disabled="busy"
                @click="confirmComplete(detail.task)"
            >完成任务
            </a-button>
            <a-button
                v-if="isWorking(detail.task.status)"
                danger
                v-privilege="'scm:sorting:task:cancel'"
                :disabled="busy"
                @click="openAction('cancel', detail.task)"
            >取消任务
            </a-button>
            <a-button
                v-if="detail.task.status === 'COMPLETED'"
                v-privilege="'scm:sorting:task:reopen'"
                :disabled="busy"
                @click="openAction('reopen', detail.task)"
            >重开任务
            </a-button>
          </a-space>
        </div>

        <a-descriptions bordered size="small" :column="3" class="task-desc">
          <a-descriptions-item label="受指派人">{{ detail.task.assigneeName || '未指派' }}</a-descriptions-item>
          <a-descriptions-item label="明细行数">{{ detail.task.itemCount }}</a-descriptions-item>
          <a-descriptions-item label="已处理行数">{{ detail.task.processedCount }} / {{ detail.task.itemCount }}</a-descriptions-item>
          <a-descriptions-item label="开始分拣">{{ datetime(detail.task.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="完成时间">{{ datetime(detail.task.completedAt) }}</a-descriptions-item>
          <a-descriptions-item label="取消时间">{{ datetime(detail.task.cancelledAt) }}</a-descriptions-item>
          <a-descriptions-item label="打印次数">{{ detail.task.printCount ? detail.task.printCount : '—' }}</a-descriptions-item>
          <a-descriptions-item label="最近打印">{{ datetime(detail.task.lastPrintedAt) }}</a-descriptions-item>
          <a-descriptions-item label="版本">{{ detail.task.version }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="3">{{ detail.task.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <a-alert
            type="info"
            show-icon
            class="entry-hint"
            message="分拣不回写订单、不改库存"
            description="这里录入的数量只写在本任务明细上，作为后续出库与结算的实发依据；订单行的实发量与结算金额、库存余额与流水都不会因此变化。计划量是建单时冻结的快照，之后订单侧修改不追溯。"
        />
        <a-alert v-if="!canEditItems" type="warning" show-icon :message="readOnlyReason"/>
        <a-alert v-if="entryError" type="error" show-icon :message="entryError"/>

        <a-table
            :id="SCM_SORTING_TABLE_ID.TASK_ITEM"
            size="small"
            :data-source="detail.items"
            :columns="itemColumns"
            row-key="id"
            bordered
            :pagination="false"
            :scroll="{ x: 1420 }"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'product'">
              <div>{{ record.productNameSnapshot }}</div>
              <a-typography-text type="secondary">
                {{ record.specNameSnapshot || '—' }} · {{ productTypeDesc(record.productTypeSnapshot) }}
                <template v-if="record.occupationStatus === 'RELEASED'"> · 占用已释放</template>
              </a-typography-text>
            </template>
            <template v-else-if="column.dataIndex === 'plannedQuantitySnapshot'">
              <span class="num">{{ quantityText(record.plannedQuantitySnapshot) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'sortedQuantity'">
              <a-input-number
                  v-if="canEditRow(record)"
                  v-model:value="drafts[String(record.id)].sortedQuantity"
                  string-mode
                  :min="'0'"
                  :precision="4"
                  :disabled="busy"
                  style="width: 130px"
                  :aria-label="`分拣量 ${record.orderNoSnapshot} ${record.productNameSnapshot}`"
              />
              <span v-else class="num">{{ quantityText(record.sortedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'result'">
              <a-select
                  v-if="canEditRow(record)"
                  v-model:value="drafts[String(record.id)].result"
                  :options="resultOptions"
                  placeholder="未录入"
                  allow-clear
                  :disabled="busy"
                  style="width: 130px"
                  :aria-label="`分拣结果 ${record.productNameSnapshot}`"
              />
              <a-tag v-else :color="resultColor(record.result)">{{ resultDesc(record.result) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'reason'">
              <template v-if="canEditRow(record)">
                <a-input
                    v-model:value="drafts[String(record.id)].reason"
                    :maxlength="500"
                    :status="entryErrors[String(record.id)] ? 'error' : undefined"
                    :disabled="busy"
                    placeholder="非正常结果必填"
                    :aria-label="`分拣原因 ${record.productNameSnapshot}`"
                />
                <a-typography-text v-if="entryErrors[String(record.id)]" type="danger" class="cell-error">
                  {{ entryErrors[String(record.id)] }}
                </a-typography-text>
              </template>
              <span v-else>{{ record.reason || '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'sortedAt'">{{ datetime(record.sortedAt) }}</template>
            <template v-else-if="column.dataIndex === 'occupationStatus'">
              <a-tag :color="SCM_SORTING_OCCUPATION_COLOR[record.occupationStatus]">
                {{ occupationDesc(record.occupationStatus) }}
              </a-tag>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>

        <div class="entry-footer">
          <a-typography-text type="secondary">
            只提交改动过的行，每行带它自己读到的版本；若某行在录入期间被别人改过，本次提交会被服务端拒绝并要求刷新。
          </a-typography-text>
          <a-button
              type="primary"
              v-privilege="'scm:sorting:item:update'"
              :disabled="!canEditItems || busy || !dirtyCount"
              :loading="busy"
              @click="submitEntry"
          >提交分拣录入（{{ dirtyCount }} 行）
          </a-button>
        </div>
      </template>
      <a-empty v-else-if="!detailLoading" description="任务尚未加载"/>
    </a-spin>
  </a-drawer>

  <!-- 指派 / 取消 / 重开：三个动作共用一个弹窗，因为它们的入参形状相同（version + 可选原因） -->
  <a-modal
      v-model:open="actionOpen"
      :title="actionTitle"
      :confirm-loading="busy"
      :ok-type="actionMode === 'cancel' ? 'danger' : 'primary'"
      :ok-text="actionMode === 'assign' ? '确认指派' : '确认'"
      @ok="submitAction"
  >
    <a-alert v-if="actionTip" type="info" show-icon :message="actionTip"/>
    <a-alert v-if="actionError" type="error" show-icon :message="actionError"/>
    <a-form layout="vertical">
      <a-form-item v-if="actionMode === 'assign'" label="受指派人" required>
        <EmployeeSelect v-model:value="actionAssignee" placeholder="选择分拣员"/>
        <a-typography-text type="secondary">
          改派只换人，已录入的分拣量与原因全部保留 —— 上一位称过的重量不会因为换人而重称。
        </a-typography-text>
      </a-form-item>
      <a-form-item v-if="actionMode !== 'assign'" label="原因" required>
        <a-textarea v-model:value="actionReason" :rows="3" :maxlength="500" show-count :placeholder="actionReasonHint"/>
      </a-form-item>
      <a-form-item v-if="actionMode === 'assign'" label="原因">
        <a-input v-model:value="actionReason" :maxlength="500" placeholder="可选，例如：原分拣员请假"/>
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 打印：预览走只读 GET，「登记打印」才是计次的 POST 命令；两者绝不混用同一个入口 -->
  <a-modal v-model:open="printOpen" title="分拣单打印预览" :width="1000" :footer="null">
    <a-alert v-if="printError" type="error" show-icon :message="printError"/>
    <div class="print-toolbar">
      <a-typography-text type="secondary">
        预览不累加打印次数；确认已实际出单后再登记，登记只代表出单动作发生，不代表库存或状态变化。
      </a-typography-text>
      <a-space>
        <a-button :disabled="printLoading || !print" @click="loadPrintPreview">重新预览</a-button>
        <a-button
            type="primary"
            v-privilege="'scm:sorting:task:print'"
            :disabled="!print || printing || printVersion == null"
            :loading="printing"
            @click="recordPrint"
        >登记打印
        </a-button>
      </a-space>
    </div>
    <a-spin :spinning="printLoading">
      <div v-if="print" ref="printPaper" class="sorting-print">
        <section class="print-ticket">
          <h1>分拣单</h1>
          <p>单号：{{ print.taskNo }}</p>
          <p>仓库：{{ print.warehouseNameSnapshot }}　分拣员：{{ print.assigneeName || '未指派' }}</p>
          <p>状态：{{ statusDesc(print.status) }}　行数：{{ print.items.length }}　已打印：{{ print.printCount || '—' }}</p>
          <p>生成时间：{{ datetime(print.generatedAt) }}</p>
          <table>
            <thead>
            <tr>
              <th>订单号</th>
              <th>客户</th>
              <th>商品 / 规格</th>
              <th>单位</th>
              <th class="numeric">计划量</th>
              <th class="numeric">分拣量</th>
              <th>结果</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="item in print.items" :key="item.id">
              <td>{{ item.orderNoSnapshot }}</td>
              <td>{{ item.customerNameSnapshot }}</td>
              <td>{{ item.productNameSnapshot }} {{ item.specNameSnapshot }}</td>
              <td>{{ item.saleUnitSnapshot }}</td>
              <td class="numeric">{{ quantityText(item.plannedQuantitySnapshot) }}</td>
              <td class="numeric">{{ quantityText(item.sortedQuantity) }}</td>
              <td>{{ resultDesc(item.result) }}</td>
            </tr>
            </tbody>
          </table>
          <p class="print-note">本单为分拣作业凭据；未录入的分拣量显示“—”。分拣不回写订单、不改库存。</p>
        </section>
        <section class="print-labels">
          <h2>商品标签（逐行）</h2>
          <div v-for="item in print.items" :key="`label-${item.id}`" class="label">
            <strong>{{ item.productNameSnapshot }}</strong>
            <p>{{ item.specNameSnapshot || '—' }} / {{ item.saleUnitSnapshot }}</p>
            <p class="label-qty">
              计划 {{ quantityText(item.plannedQuantitySnapshot) }}　实分 {{ quantityText(item.sortedQuantity) }}
            </p>
            <p>{{ item.customerNameSnapshot }}</p>
            <p class="label-no">{{ item.orderNoSnapshot }} · {{ print.taskNo }}</p>
          </div>
        </section>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import {useUserStore} from '/@/store/modules/system/user';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {
    SCM_SORTING_OCCUPATION_COLOR,
    SCM_SORTING_OCCUPATION_ENUM,
    SCM_SORTING_PERMISSION,
    SCM_SORTING_PRINTABLE_STATUS,
    SCM_SORTING_RESULT_COLOR,
    SCM_SORTING_RESULT_ENUM,
    SCM_SORTING_TABLE_ID,
    SCM_SORTING_TASK_STATUS_COLOR,
    SCM_SORTING_TASK_STATUS_ENUM,
    SCM_SORTING_WORKING_STATUS,
    SCM_SORTING_PRODUCT_TYPE_ENUM,
} from '/@/constants/business/scm/sorting-const';
import {hasPermission} from '../common/scm-permission';
import {datetime} from '../common/scm-display';
import type {
    Id,
    SortingActionPayload,
    SortingCandidateLine,
    SortingEntryItemPayload,
    SortingEntryPayload,
    SortingLineResult,
    SortingPrint,
    SortingTask,
    SortingTaskDetail,
    SortingTaskItem,
    SortingTaskQuery,
    SortingTaskStatus,
} from './sorting-types';
import {quantityText, sortingError} from './sorting-types';

type ActionMode = 'assign' | 'cancel' | 'reopen';

/** 一行明细的编辑草稿；空串表示「没填」，与后端 `"0.0000"`（填了且为 0）是两回事。 */
interface EntryDraft {
    sortedQuantity: string;
    result: string;
    reason: string;
}

const userStore = useUserStore();

// ------------------------------------------------------------------ 列表

const queryForm = reactive<SortingTaskQuery>({pageNum: 1, pageSize: 20, unassignedOnly: false});
const assigneeFilter = ref<number | undefined>(undefined);
const rows = ref<SortingTask[]>([]);
const total = ref(0);
const loading = ref(false);
const listError = ref('');
let listGeneration = 0;

/** 持指派权即队列管理者（后端 `SortingAccess.crossAssignee()` 同一口径）。 */
const isQueueManager = computed(() => hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN));

// 范围收窄后空表有两种成因（没授权 vs 真没数据），文案必须能区分，否则配置缺口被当成业务空档。
const emptyText = computed(() =>
    isQueueManager.value ? '暂无分拣任务' : '当前仅显示派给您本人的任务；若无数据，可能是任务尚未指派或本仓未授权给您，请联系分拣主管确认。'
);

const columns = ref<TableColumnsType<SortingTask>>([
  {title: '任务号', dataIndex: 'taskNo', width: 180},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 160},
  {title: '受指派人', dataIndex: 'assigneeName', width: 120},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '明细行数', dataIndex: 'itemCount', align: 'right', width: 90},
  {title: '已处理', dataIndex: 'processedCount', align: 'right', width: 100},
  {title: '打印次数', dataIndex: 'printCount', align: 'right', width: 90},
  {title: '创建时间', dataIndex: 'createdAt', width: 170},
  {title: '操作', dataIndex: 'action', fixed: 'right', align: 'right', width: 320},
]);

const statusOptions = Object.values(SCM_SORTING_TASK_STATUS_ENUM).map((item) => ({value: item.value, label: item.desc}));
const resultOptions = Object.values(SCM_SORTING_RESULT_ENUM).map((item) => ({value: item.value, label: item.desc}));

function statusDesc(status?: string | null) {
    return status ? SCM_SORTING_TASK_STATUS_ENUM[status]?.desc ?? status : '—';
}

function resultDesc(result?: string | null) {
    return result ? SCM_SORTING_RESULT_ENUM[result]?.desc ?? result : '未录入';
}

function resultColor(result?: string | null) {
    return (result && SCM_SORTING_RESULT_COLOR[result]) || 'default';
}

function productTypeDesc(type?: string | null) {
    return type ? SCM_SORTING_PRODUCT_TYPE_ENUM[type]?.desc ?? type : '—';
}

function occupationDesc(occupation?: string | null) {
    return occupation ? SCM_SORTING_OCCUPATION_ENUM[occupation]?.desc ?? occupation : '—';
}

function isWorking(status: string) {
    return SCM_SORTING_WORKING_STATUS.includes(status);
}

async function queryData() {
    const current = ++listGeneration;
    loading.value = true;
    listError.value = '';
    try {
        const result = await sortingApi.tasks({...queryForm, assigneeEmployeeId: assigneeFilter.value});
        if (current === listGeneration) {
            rows.value = result.data.list;
            total.value = result.data.total;
        }
    } catch (e) {
        if (current === listGeneration) listError.value = sortingError(e);
    } finally {
        if (current === listGeneration) loading.value = false;
    }
}

function onSearch() {
    queryForm.pageNum = 1;
    queryData();
}

function resetQuery() {
    queryForm.keyword = undefined;
    queryForm.status = undefined;
    queryForm.warehouseId = undefined;
    queryForm.unassignedOnly = false;
    assigneeFilter.value = undefined;
    onSearch();
}

// ------------------------------------------------------------------ 详情与录入

const detailOpen = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const entryError = ref('');
const busy = ref(false);
const detail = ref<SortingTaskDetail>();
const drafts = reactive<Record<string, EntryDraft>>({});
const entryErrors = reactive<Record<string, string>>({});
let detailId: Id | undefined;
let detailGeneration = 0;

const itemColumns = ref<TableColumnsType<SortingTaskItem>>([
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 170},
  {title: '客户', dataIndex: 'customerNameSnapshot', width: 150},
  {title: '商品 / 规格', dataIndex: 'product', width: 220},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 80},
  {title: '计划量', dataIndex: 'plannedQuantitySnapshot', align: 'right', width: 110},
  {title: '分拣量', dataIndex: 'sortedQuantity', align: 'right', width: 150},
  {title: '结果', dataIndex: 'result', align: 'center', width: 140},
  {title: '原因', dataIndex: 'reason', width: 220},
  {title: '录入人', dataIndex: 'sortedBy', width: 120},
  {title: '录入时间', dataIndex: 'sortedAt', width: 170},
  {title: '占用', dataIndex: 'occupationStatus', align: 'center', width: 100},
]);

/**
 * 当前登录者是否本任务受指派人。
 *
 * `employeeId` 在 store 里是字符串，后端回的是 JSON 数字，因此两边都转成字符串再比 ——
 * 直接 `===` 会让「本人也编辑不了」，而且看起来像权限没配。
 */
const isAssignee = computed(() => {
    const assignee = detail.value?.task.assigneeEmployeeId;
    if (assignee === null || assignee === undefined) return false;
    return String(assignee) === String(userStore.employeeId);
});

const canEditItems = computed(
    () =>
        !!detail.value &&
        isWorking(detail.value.task.status) &&
        isAssignee.value &&
        hasPermission(SCM_SORTING_PERMISSION.ITEM_UPDATE)
);

/** 只读时必须说清为什么只读：三种成因（状态已终结 / 不是派给我的 / 没有编辑权）处置方式完全不同。 */
const readOnlyReason = computed(() => {
    const task = detail.value?.task;
    if (!task) return '';
    if (!isWorking(task.status)) return `任务当前为「${statusDesc(task.status)}」，不再接受录入；已完成的任务需先重开。`;
    if (!isAssignee.value) return '只有受指派人本人可以录入分拣量；需要变更执行人请使用「改派」。';
    return '当前账号没有分拣明细编辑权限（scm:sorting:item:update），只能查看。';
});

/** 释放占用的历史行不再代表待办量，即使任务还能干活也不能编辑。 */
function canEditRow(record: SortingTaskItem) {
    return canEditItems.value && record.occupationStatus === 'ACTIVE';
}

function draftKey(record: SortingTaskItem) {
    return String(record.id);
}

function isDirty(record: SortingTaskItem) {
    const draft = drafts[draftKey(record)];
    if (!draft) return false;
    return (
        draft.sortedQuantity !== (record.sortedQuantity ?? '') ||
        draft.result !== (record.result ?? '') ||
        draft.reason.trim() !== (record.reason ?? '').trim()
    );
}

const dirtyCount = computed(() => (detail.value?.items ?? []).filter(isDirty).length);

/** 每次读到详情都按后端返回值重建草稿，避免上一次编辑的残值被当成新的改动提交出去。 */
function resetDrafts(items: SortingTaskItem[]) {
    Object.keys(drafts).forEach((key) => delete drafts[key]);
    Object.keys(entryErrors).forEach((key) => delete entryErrors[key]);
    items.forEach((item) => {
        drafts[String(item.id)] = {
            sortedQuantity: item.sortedQuantity ?? '',
            result: item.result ?? '',
            reason: item.reason ?? '',
        };
    });
}

async function reloadDetail() {
    if (detailId === undefined) return;
    const current = ++detailGeneration;
    detailLoading.value = true;
    detailError.value = '';
    try {
        const result = await sortingApi.detail(detailId);
        if (current === detailGeneration) {
            detail.value = result.data;
            resetDrafts(result.data.items);
            entryError.value = '';
        }
    } catch (e) {
        if (current === detailGeneration) detailError.value = sortingError(e);
    } finally {
        if (current === detailGeneration) detailLoading.value = false;
    }
}

function openDetail(id: Id) {
    detailId = id;
    detail.value = undefined;
    entryError.value = '';
    resetDrafts([]);
    detailOpen.value = true;
    reloadDetail();
}

/**
 * 装配录入载荷：只收改动过的行，每行带自己读到的 `version`。
 *
 * 校验失败时返回 `null` 并把逐行原因落在对应单元格上 —— 不发请求，
 * 因为后端会整批回滚（同一事务），部分发出去只会让人以为改动已保存。
 */
function buildEntryPayload(): SortingEntryPayload | null {
    const items: SortingEntryItemPayload[] = [];
    let invalid = false;
    Object.keys(entryErrors).forEach((key) => delete entryErrors[key]);
    (detail.value?.items ?? []).forEach((record) => {
        if (!canEditRow(record) || !isDirty(record)) return;
        const draft = drafts[draftKey(record)];
        const key = draftKey(record);
        if (!draft.result) {
            entryErrors[key] = '请选择分拣结果';
            invalid = true;
            return;
        }
        const quantity = draft.sortedQuantity.trim();
        if (!quantity) {
            entryErrors[key] = '请填写分拣量；整行缺货也请填 0 并把结果选成「缺货」';
            invalid = true;
            return;
        }
        const reason = draft.reason.trim();
        if (draft.result !== SCM_SORTING_RESULT_ENUM.NORMAL.value && !reason) {
            entryErrors[key] = '非正常结果必须填写原因，这是后续核对唯一的依据';
            invalid = true;
            return;
        }
        items.push({
            id: record.id,
            // 行版本原样回传：后端逐行比对，冲突时整批回滚，不覆盖别人刚录入的量
            version: record.version,
            sortedQuantity: quantity,
            result: draft.result as SortingLineResult,
            reason: reason || undefined,
        });
    });
    if (invalid) {
        entryError.value = '有明细行未通过校验，本次未提交任何改动。';
        return null;
    }
    if (!items.length) {
        entryError.value = '没有需要提交的改动。';
        return null;
    }
    entryError.value = '';
    return {items};
}

async function submitEntry() {
    if (!detail.value) return;
    const payload = buildEntryPayload();
    if (!payload) return;
    busy.value = true;
    try {
        await sortingApi.enter(detail.value.task.id, payload);
        message.success(`分拣录入成功（${payload.items.length} 行）`);
        await reloadDetail();
        queryData();
    } catch (e) {
        entryError.value = sortingError(e);
        // 版本冲突或状态被他人改过：拉最新数据，让操作人重新对着自己看到的那一版录
        await reloadDetail();
    } finally {
        busy.value = false;
    }
}

// ------------------------------------------------------------------ 任务级动作

const actionOpen = ref(false);
const actionMode = ref<ActionMode>('assign');
const actionAssignee = ref<number | undefined>(undefined);
const actionReason = ref('');
const actionError = ref('');
const actionRecord = ref<SortingTask>();

const actionTitle = computed(
    () => ({assign: '指派 / 改派', cancel: '取消分拣任务', reopen: '重开分拣任务'})[actionMode.value]
);

const actionTip = computed(() => {
    switch (actionMode.value) {
        case 'assign':
            return '已完成与已取消的任务不再改派；改派保留已录入的分拣量。';
        case 'cancel':
            return '取消会在同一事务里释放本任务全部明细的占用位，被释放的订单行才能重新进入新任务。此操作不可撤销。';
        default:
            return '重开后任务回到「分拣中」，已录入的量与原因保留，可以继续修改。';
    }
});

const actionReasonHint = computed(() =>
    actionMode.value === 'cancel' ? '例如：订单行下错、客户临时取消' : '例如：完成后发现一行称重有误'
);

function openAction(mode: ActionMode, record: SortingTask) {
    actionMode.value = mode;
    actionRecord.value = record;
    // `EmployeeSelect` 的 value 声明为 Number，这里只做类型层面的收窄（后端对 Long 主键回的是 JSON 数字）。
    // 不用 Number() 转：id 一旦走数值转换，超长 id 会静默丢精度而页面看起来完全正常。
    const employeeId = record.assigneeEmployeeId;
    actionAssignee.value = employeeId === null || employeeId === undefined ? undefined : (employeeId as number);
    actionReason.value = '';
    actionError.value = '';
    actionOpen.value = true;
}

async function submitAction() {
    const record = actionRecord.value;
    if (!record) return;
    actionError.value = '';
    const reason = actionReason.value.trim();
    if (actionMode.value !== 'assign' && !reason) {
        actionError.value = '请填写原因。';
        return;
    }
    if (actionMode.value === 'assign' && actionAssignee.value == null) {
        actionError.value = '请选择受指派人。';
        return;
    }
    busy.value = true;
    try {
        if (actionMode.value === 'assign') {
            await sortingApi.assign(record.id, {
                assigneeEmployeeId: actionAssignee.value as number,
                version: record.version,
                reason: reason || null,
            });
            message.success('指派成功');
        } else {
            const payload: SortingActionPayload = {version: record.version, reason};
            await (actionMode.value === 'cancel' ? sortingApi.cancel(record.id, payload) : sortingApi.reopen(record.id, payload));
            message.success(actionMode.value === 'cancel' ? '取消成功' : '重开成功');
        }
        actionOpen.value = false;
        await afterTaskChanged(record.id);
    } catch (e) {
        // 后端消息原样显示：30005（无权 / 看不到）与 41121（状态不允许）是两种处置
        actionError.value = sortingError(e);
    } finally {
        busy.value = false;
    }
}

function confirmComplete(record: SortingTask) {
    Modal.confirm({
        title: `完成分拣任务 ${record.taskNo}？`,
        content: `硬前置是任务内每条活动明细都已有结果（当前已处理 ${record.processedCount} / ${record.itemCount}）。`
            + '完成只锁定任务与实发事实，不扣减库存、不生成出库单，也不回写订单。',
        okText: '确认完成',
        cancelText: '返回',
        onOk: async () => {
            busy.value = true;
            try {
                await sortingApi.complete(record.id, {version: record.version});
                message.success('任务已完成');
                await afterTaskChanged(record.id);
            } catch (e) {
                // 41125（仍有未处理明细）由服务端给出，前端不预先算一遍：
                // 行是否活动、是否算已处理都由服务端按占用位判定，前端再判一次就是第二份真相
                message.error(sortingError(e));
                await afterTaskChanged(record.id);
            } finally {
                busy.value = false;
            }
        },
    });
}

/** 动作之后刷新列表；详情抽屉开着时一并刷新，保证版本号与状态是刚读到的那一版。 */
async function afterTaskChanged(id: Id) {
    await queryData();
    if (detailOpen.value && String(detailId) === String(id)) await reloadDetail();
}

// ------------------------------------------------------------------ 新建任务

const createOpen = ref(false);
const creating = ref(false);
const createLoading = ref(false);
const createError = ref('');
// `EmployeeSelect` 的 value prop 声明为 `[Number, Array]`，所以这里不收 `null`：
// 「不指派」用 undefined 表达，提交时再落成后端要的 `null`（载荷类型见 sorting-types）。
const createForm = reactive<{ warehouseId?: Id; assigneeEmployeeId?: number; remark?: string }>({});
const candidateQuery = reactive<{ pageNum: number; pageSize: number; keyword?: string }>({pageNum: 1, pageSize: 20});
const candidateRows = ref<SortingCandidateLine[]>([]);
const candidateSelected = ref<Id[]>([]);
const candidateTotal = ref(0);
let candidateGeneration = 0;

const candidateColumns = ref<TableColumnsType<SortingCandidateLine>>([
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 160},
  {title: '商品', dataIndex: 'productNameSnapshot', width: 170},
  {title: '规格', dataIndex: 'specNameSnapshot', width: 130},
  {title: '类型', dataIndex: 'productTypeSnapshot', align: 'center', width: 90},
  {title: '单位', dataIndex: 'saleUnitSnapshot', align: 'center', width: 80},
  {title: '订购量', dataIndex: 'orderedQuantity', align: 'right', width: 110},
  {title: '实发量', dataIndex: 'actualQuantity', align: 'right', width: 110},
]);

function onCandidateSelect(keys: (string | number)[]) {
    candidateSelected.value = keys;
}

async function loadCandidates() {
    const current = ++candidateGeneration;
    createLoading.value = true;
    try {
        const result = await sortingApi.candidateLines({...candidateQuery});
        if (current === candidateGeneration) {
            candidateRows.value = result.data.list;
            candidateTotal.value = result.data.total;
        }
    } catch (e) {
        if (current === candidateGeneration) createError.value = sortingError(e);
    } finally {
        if (current === candidateGeneration) createLoading.value = false;
    }
}

function searchCandidates() {
    candidateQuery.pageNum = 1;
    loadCandidates();
}

function resetCandidates() {
    candidateQuery.keyword = undefined;
    candidateSelected.value = [];
    searchCandidates();
}

function openCreate() {
    createError.value = '';
    createForm.warehouseId = queryForm.warehouseId;
    createForm.assigneeEmployeeId = undefined;
    createForm.remark = undefined;
    candidateSelected.value = [];
    candidateRows.value = [];
    candidateTotal.value = 0;
    candidateQuery.keyword = undefined;
    candidateQuery.pageNum = 1;
    createOpen.value = true;
    loadCandidates();
}

async function submitCreate() {
    if (createForm.warehouseId === undefined || createForm.warehouseId === null || createForm.warehouseId === '') {
        createError.value = '请选择分拣仓库。';
        return;
    }
    if (!candidateSelected.value.length) {
        createError.value = '请至少勾选一条订单行。';
        return;
    }
    creating.value = true;
    createError.value = '';
    try {
        const result = await sortingApi.create({
            warehouseId: createForm.warehouseId,
            assigneeEmployeeId: createForm.assigneeEmployeeId ?? null,
            remark: createForm.remark?.trim() || null,
            // 勾选行即订单行 id；重复提交由幂等键挡住，抢同一行由服务端唯一索引挡住
            salesOrderItemIds: [...candidateSelected.value],
        });
        message.success(`分拣任务创建成功：${result.data.task.taskNo}`);
        createOpen.value = false;
        await queryData();
        openDetail(result.data.task.id);
    } catch (e) {
        createError.value = sortingError(e);
    } finally {
        creating.value = false;
    }
}

// ------------------------------------------------------------------ 打印

const printOpen = ref(false);
const printLoading = ref(false);
const printing = ref(false);
const printError = ref('');
const print = ref<SortingPrint>();
/**
 * 登记命令要的 `version` 不在预览载荷里（预览 VO 刻意不带任务版本，防止被误当成可提交版本），
 * 因此打开打印时把当时读到的任务版本记下来 —— 与录入同理，登记的应当是自己看到的那一版。
 */
const printVersion = ref<number>();
let printTaskId: Id | undefined;

function openPrint(record: SortingTask) {
    printTaskId = record.id;
    printVersion.value = record.version;
    print.value = undefined;
    printError.value = '';
    printOpen.value = true;
    loadPrintPreview();
}

async function loadPrintPreview() {
    if (printTaskId === undefined) return;
    printLoading.value = true;
    printError.value = '';
    try {
        // 只读预览：GET，不累加打印次数
        print.value = (await sortingApi.printPreview(printTaskId)).data;
    } catch (e) {
        printError.value = sortingError(e);
    } finally {
        printLoading.value = false;
    }
}

async function recordPrint() {
    if (printTaskId === undefined || printVersion.value == null) return;
    printing.value = true;
    printError.value = '';
    try {
        const result = await sortingApi.print(printTaskId, {version: printVersion.value});
        message.success(`已登记打印，累计 ${result.data.printCount} 次`);
        print.value = {...print.value, printCount: result.data.printCount} as SortingPrint;
        await queryData();
        if (detailOpen.value && String(detailId) === String(printTaskId)) await reloadDetail();
    } catch (e) {
        // 计次不 bump 任务版本，所以版本冲突只可能是任务真被改过：刷新后重新登记
        printError.value = sortingError(e);
        await queryData();
        if (detailOpen.value && String(detailId) === String(printTaskId)) await reloadDetail();
    } finally {
        printing.value = false;
    }
}

onMounted(queryData);
</script>

<style scoped>
.num {
  font-variant-numeric: tabular-nums;
}

.toolbar-hint {
  margin-left: 12px;
}

.create-form {
  gap: 12px 0;
  margin: 16px 0;
}

.task-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.task-heading h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.task-desc {
  margin-bottom: 12px;
}

.entry-hint {
  margin-bottom: 12px;
}

.cell-error {
  display: block;
  font-size: 12px;
}

.entry-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.print-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin: 12px 0;
}

.sorting-print {
  background: #fff;
  color: #1f2329;
  padding: 24px;
}

.sorting-print h1 {
  font-size: 24px;
  margin: 0 0 12px;
}

.sorting-print h2 {
  font-size: 18px;
  margin: 24px 0 12px;
}

.sorting-print table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
}

.sorting-print th,
.sorting-print td {
  border: 1px solid #e5e6eb;
  padding: 8px;
  text-align: left;
}

.sorting-print .numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.print-note {
  margin-top: 16px;
}

.print-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.print-labels h2 {
  width: 100%;
}

.label {
  width: 220px;
  border: 1px dashed #86909c;
  padding: 12px;
  break-inside: avoid;
}

.label strong {
  font-size: 15px;
}

.label p {
  margin: 4px 0;
}

.label-qty {
  font-variant-numeric: tabular-nums;
}

.label-no {
  font-size: 11px;
  color: #4e5969;
}
</style>
