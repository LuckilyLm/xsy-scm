package com.xsy.scm.sorting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xsy.scm.sorting.domain.form.SortingActionForm;
import com.xsy.scm.sorting.domain.form.SortingAssignForm;
import com.xsy.scm.sorting.domain.form.SortingCandidateQueryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingSummaryQueryForm;
import com.xsy.scm.sorting.domain.form.SortingTaskCreateForm;
import com.xsy.scm.sorting.domain.form.SortingTaskQueryForm;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.domain.vo.SortingCandidateLineVO;
import com.xsy.scm.sorting.domain.vo.SortingPrintResultVO;
import com.xsy.scm.sorting.domain.vo.SortingPrintVO;
import com.xsy.scm.sorting.domain.vo.SortingSkuSummaryVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskDetailVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskVO;
import com.xsy.scm.sorting.service.SortingQueryService;
import com.xsy.scm.sorting.service.SortingTaskService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

/**
 * 分拣管理。写侧全部经服务端权限 + 数据范围（授权仓 ∩ 可见指派人）双重判定；
 * 生成入口带 {@code Idempotency-Key}，预览类入口只读、不计次。
 */
@RestController
@RequestMapping("/scm/sorting")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "SCM分拣管理")
public class SortingTaskController {

    private final SortingTaskService sortingTaskService;
    private final SortingQueryService sortingQueryService;

    @GetMapping("/tasks")
    @SaCheckPermission(SortingPermission.TASK_QUERY)
    public ResponseDTO<PageResult<SortingTaskVO>> list(@Valid @ModelAttribute SortingTaskQueryForm form) {
        return ResponseDTO.ok(sortingQueryService.query(form));
    }

    @GetMapping("/tasks/{id}")
    @SaCheckPermission(SortingPermission.TASK_QUERY)
    public ResponseDTO<SortingTaskDetailVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(sortingQueryService.detail(id));
    }

    @GetMapping("/summary")
    @SaCheckPermission(SortingPermission.SUMMARY_QUERY)
    public ResponseDTO<PageResult<SortingSkuSummaryVO>> summary(@Valid @ModelAttribute SortingSummaryQueryForm form) {
        return ResponseDTO.ok(sortingQueryService.summary(form));
    }

    // 候选订单行是建单队列，只授建单权限；返回列不含价格或金额。
    @GetMapping("/candidate-lines")
    @SaCheckPermission(SortingPermission.TASK_ADD)
    public ResponseDTO<PageResult<SortingCandidateLineVO>> candidateLines(
            @Valid @ModelAttribute SortingCandidateQueryForm form) {
        return ResponseDTO.ok(sortingQueryService.candidateLines(form));
    }

    /**
     * 打印预览：内容与正式生成同源，但既不改状态也不计次。
     */
    @GetMapping("/tasks/{id}/print")
    @SaCheckPermission(SortingPermission.TASK_PRINT)
    public ResponseDTO<SortingPrintVO> printPreview(@PathVariable Long id) {
        return ResponseDTO.ok(sortingQueryService.printPreview(id));
    }

    @PostMapping("/tasks")
    @SaCheckPermission(SortingPermission.TASK_ADD)
    @OperateLog
    public ResponseDTO<SortingTaskDetailVO> create(@Valid @RequestBody SortingTaskCreateForm form,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(sortingTaskService.create(form, key));
    }

    @PostMapping("/tasks/{id}/assign")
    @SaCheckPermission(SortingPermission.TASK_ASSIGN)
    @OperateLog
    public ResponseDTO<String> assign(@PathVariable Long id, @Valid @RequestBody SortingAssignForm form) {
        sortingTaskService.assign(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/entry")
    @SaCheckPermission(SortingPermission.ITEM_UPDATE)
    @OperateLog
    public ResponseDTO<String> enter(@PathVariable Long id, @Valid @RequestBody SortingEntryForm form) {
        sortingTaskService.enter(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/complete")
    @SaCheckPermission(SortingPermission.TASK_COMPLETE)
    @OperateLog
    public ResponseDTO<String> complete(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        sortingTaskService.complete(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/cancel")
    @SaCheckPermission(SortingPermission.TASK_CANCEL)
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        sortingTaskService.cancel(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/reopen")
    @SaCheckPermission(SortingPermission.TASK_REOPEN)
    @OperateLog
    public ResponseDTO<String> reopen(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        sortingTaskService.reopen(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/print")
    @SaCheckPermission(SortingPermission.TASK_PRINT)
    @OperateLog
    public ResponseDTO<SortingPrintResultVO> print(@PathVariable Long id, @Valid @RequestBody SortingActionForm form,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(sortingTaskService.print(id, form, key));
    }
}
