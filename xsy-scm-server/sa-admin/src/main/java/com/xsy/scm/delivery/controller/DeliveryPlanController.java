package com.xsy.scm.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.delivery.domain.form.DeliveryPlanApplyForm;
import com.xsy.scm.delivery.domain.form.DeliveryPlanDiscardForm;
import com.xsy.scm.delivery.domain.vo.DeliveryPlanProposalVO;
import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.delivery.service.DeliveryPlanProposalService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 辅助排线建议。
 *
 * <p>
 * 「生成」与「应用」分开授权：生成只是算一份建议（不碰线路），应用才写回停靠顺序。
 * 合并成一个权限会让「只是想看看怎么排」的人顺手获得改线路的能力。
 *
 * <p>
 * 建议不自动发车：应用之后线路仍是 {@code DRAFT}，发车要走 {@code /routes/{id}/dispatch}。
 */
@RestController
@RequestMapping("/scm/delivery/plan")
@Tag(name = "SCM 辅助排线")
@RequiredArgsConstructor
public class DeliveryPlanController {

    private final DeliveryPlanProposalService deliveryPlanProposalService;

    /**
     * 生成建议（会作废该线路原有的待确认建议）。
     */
    @PostMapping("/route/{routeId}/propose")
    @SaCheckPermission(value = {DeliveryPermission.PLAN_PROPOSE, DeliveryPermission.ROUTE_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<DeliveryPlanProposalVO> propose(@PathVariable("routeId") Long routeId) {
        return ResponseDTO.ok(deliveryPlanProposalService.propose(routeId));
    }

    /**
     * 建议历史（最新在前），供比较多次生成的结果。
     */
    @GetMapping("/route/{routeId}/history")
    @SaCheckPermission(value = {DeliveryPermission.PLAN_QUERY, DeliveryPermission.ROUTE_QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<DeliveryPlanProposalVO>> history(@PathVariable("routeId") Long routeId) {
        return ResponseDTO.ok(deliveryPlanProposalService.history(routeId));
    }

    /**
     * 应用建议：按建议顺序重排停靠点。{@code version} 是**线路**版本。
     */
    @PostMapping("/proposal/{proposalId}/apply")
    @SaCheckPermission(value = {DeliveryPermission.PLAN_APPLY, DeliveryPermission.ROUTE_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<String> apply(@PathVariable("proposalId") Long proposalId,
            @Valid @RequestBody DeliveryPlanApplyForm form) {
        deliveryPlanProposalService.apply(proposalId, form);
        return ResponseDTO.ok();
    }

    /**
     * 放弃建议：不改线路。
     */
    @PostMapping("/proposal/{proposalId}/discard")
    @SaCheckPermission(value = {DeliveryPermission.PLAN_APPLY, DeliveryPermission.ROUTE_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<String> discard(@PathVariable("proposalId") Long proposalId,
            @Valid @RequestBody DeliveryPlanDiscardForm form) {
        deliveryPlanProposalService.discard(proposalId, form);
        return ResponseDTO.ok();
    }
}
