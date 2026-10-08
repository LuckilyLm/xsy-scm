import {computed, reactive, ref, type Ref} from 'vue';
import {message} from 'ant-design-vue';
import {useUserStore} from '/@/store/modules/system/user';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {
    SCM_SORTING_PERMISSION,
    SCM_SORTING_RESULT_ENUM,
    SCM_SORTING_WORKING_STATUS,
} from '/@/constants/business/scm/sorting-const';
import {hasPermission} from '../common/scm-permission';
import {useScmErrorToast} from '../common/scm-error-toast';
import {sortingError} from './sorting-types';
import type {
    Id,
    SortingEntryItemPayload,
    SortingEntryPayload,
    SortingLineResult,
    SortingTaskDetail,
    SortingTaskItem,
} from './sorting-types';

interface EntryDraft {
    /** 空串表示未填写，与后端的 "0.0000"（填写了 0）不同。 */
    sortedQuantity: string;
    result: string;
    reason: string;
}

/** 负责分拣任务详情读取、行草稿和批量录入校验。 */
export function useSortingTaskEntry(options: {
    busy: Ref<boolean>;
    refreshTaskList: () => void;
    statusDesc: (status?: string | null) => string;
}) {
    const userStore = useUserStore();
    const detailOpen = ref(false);
    const detailLoading = ref(false);
    const detailError = useScmErrorToast();
    const entryError = useScmErrorToast();
    const detail = ref<SortingTaskDetail>();
    const drafts = reactive<Record<string, EntryDraft>>({});
    const entryErrors = reactive<Record<string, string>>({});
    const detailId = ref<Id>();
    let detailGeneration = 0;

    /** Store 中 employeeId 是字符串，服务端详情返回数字；比较前统一转字符串。 */
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

    const readOnlyReason = computed(() => {
        const task = detail.value?.task;
        if (!task) return '';
        if (!isWorking(task.status)) return `任务当前为「${options.statusDesc(task.status)}」，不再接受录入；已完成的任务需先重开。`;
        if (!isAssignee.value) return '只有受指派人本人可以录入分拣量；需要变更执行人请使用「改派」。';
        return '当前账号没有分拣明细编辑权限（scm:sorting:item:update），只能查看。';
    });

    function isWorking(status: string) {
        return SCM_SORTING_WORKING_STATUS.includes(status);
    }

    /** 赠品权益以只读方式并入任务详情，不作为可录入的销售订单行。 */
    function isGiftRow(record: SortingTaskItem) {
        return record.sourceType === 'PROMOTION_GIFT';
    }

    function canEditRow(record: SortingTaskItem) {
        // 已释放的历史占用行不是待办量；赠品事实也不写回分拣数量与结果。
        if (isGiftRow(record)) return false;
        return canEditItems.value && record.occupationStatus === 'ACTIVE';
    }

    function updateDraft(itemId: Id, field: keyof EntryDraft, value: string) {
        const draft = drafts[String(itemId)];
        if (draft) draft[field] = value;
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

    function resetDrafts(items: SortingTaskItem[]) {
        // 每次服务端刷新都重建草稿，避免把上次编辑的残值误当成新修改提交。
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
        if (detailId.value === undefined) return;
        const current = ++detailGeneration;
        detailLoading.value = true;
        detailError.value = '';
        try {
            const result = await sortingApi.detail(detailId.value);
            if (current === detailGeneration) {
                detail.value = result.data;
                resetDrafts(result.data.items);
                entryError.value = '';
            }
        } catch (error) {
            if (current === detailGeneration) detailError.value = sortingError(error);
        } finally {
            if (current === detailGeneration) detailLoading.value = false;
        }
    }

    function openDetail(id: Id) {
        detailId.value = id;
        detail.value = undefined;
        entryError.value = '';
        resetDrafts([]);
        detailOpen.value = true;
        void reloadDetail();
    }

    /** 只提交已变更行并带回各自版本；任一行失败时服务端会整批回滚。 */
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
                // 行版本独立校验，不能用任务版本代替。
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
        options.busy.value = true;
        try {
            await sortingApi.enter(detail.value.task.id, payload);
            message.success(`分拣录入成功（${payload.items.length} 行）`);
            await reloadDetail();
            options.refreshTaskList();
        } catch (error) {
            entryError.value = sortingError(error);
            // 版本冲突或任务状态被改动时，刷新详情让操作人对照最新数据重录。
            await reloadDetail();
        } finally {
            options.busy.value = false;
        }
    }

    return {
        canEditItems,
        canEditRow,
        detail,
        detailError,
        detailId,
        detailLoading,
        detailOpen,
        dirtyCount,
        drafts,
        entryError,
        entryErrors,
        isGiftRow,
        openDetail,
        readOnlyReason,
        reloadDetail,
        submitEntry,
        updateDraft,
    };
}
