package net.lab1024.sa.admin.module.scm.sorting.controller;

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

import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingAssignForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingCandidateQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingSummaryQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingCandidateLineVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingPrintResultVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingPrintVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingSkuSummaryVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskDetailVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskVO;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingQueryService;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingTaskService;
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

    private final SortingTaskService service;
    private final SortingQueryService query;

    @GetMapping("/tasks")
    @SaCheckPermission("scm:sorting:task:query")
    public ResponseDTO<PageResult<SortingTaskVO>> list(@Valid @ModelAttribute SortingTaskQueryForm form) {
        return ResponseDTO.ok(query.query(form));
    }

    @GetMapping("/tasks/{id}")
    @SaCheckPermission("scm:sorting:task:query")
    public ResponseDTO<SortingTaskDetailVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(query.detail(id));
    }

    @GetMapping("/summary")
    @SaCheckPermission("scm:sorting:summary:query")
    public ResponseDTO<PageResult<SortingSkuSummaryVO>> summary(@Valid @ModelAttribute SortingSummaryQueryForm form) {
        return ResponseDTO.ok(query.summary(form));
    }

    // 候选订单行是建单用的队列视图，只授建单权；不受订单业务员范围约束（裁决补充第 22 条）。
    @GetMapping("/candidate-lines")
    @SaCheckPermission("scm:sorting:task:add")
    public ResponseDTO<PageResult<SortingCandidateLineVO>> candidateLines(
            @Valid @ModelAttribute SortingCandidateQueryForm form) {
        return ResponseDTO.ok(query.candidateLines(form));
    }

    /**
     * 打印预览：内容与正式生成同源，但既不改状态也不计次。
     */
    @GetMapping("/tasks/{id}/print")
    @SaCheckPermission("scm:sorting:task:print")
    public ResponseDTO<SortingPrintVO> printPreview(@PathVariable Long id) {
        return ResponseDTO.ok(query.printPreview(id));
    }

    @PostMapping("/tasks")
    @SaCheckPermission("scm:sorting:task:add")
    @OperateLog
    public ResponseDTO<SortingTaskDetailVO> create(@Valid @RequestBody SortingTaskCreateForm form,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.create(form, key));
    }

    @PostMapping("/tasks/{id}/assign")
    @SaCheckPermission("scm:sorting:task:assign")
    @OperateLog
    public ResponseDTO<String> assign(@PathVariable Long id, @Valid @RequestBody SortingAssignForm form) {
        service.assign(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/entry")
    @SaCheckPermission("scm:sorting:item:update")
    @OperateLog
    public ResponseDTO<String> enter(@PathVariable Long id, @Valid @RequestBody SortingEntryForm form) {
        service.enter(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/complete")
    @SaCheckPermission("scm:sorting:task:complete")
    @OperateLog
    public ResponseDTO<String> complete(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        service.complete(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/cancel")
    @SaCheckPermission("scm:sorting:task:cancel")
    @OperateLog
    public ResponseDTO<String> cancel(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        service.cancel(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/reopen")
    @SaCheckPermission("scm:sorting:task:reopen")
    @OperateLog
    public ResponseDTO<String> reopen(@PathVariable Long id, @Valid @RequestBody SortingActionForm form) {
        service.reopen(id, form);
        return ResponseDTO.ok();
    }

    @PostMapping("/tasks/{id}/print")
    @SaCheckPermission("scm:sorting:task:print")
    @OperateLog
    public ResponseDTO<SortingPrintResultVO> print(@PathVariable Long id, @Valid @RequestBody SortingActionForm form,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(service.print(id, form, key));
    }
}
