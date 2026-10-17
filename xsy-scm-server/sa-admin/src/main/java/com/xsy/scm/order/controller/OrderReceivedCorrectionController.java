package com.xsy.scm.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.domain.form.OrderReceivedCorrectionForm;
import com.xsy.scm.order.domain.vo.OrderReceivedCorrectionCandidateVO;
import com.xsy.scm.order.domain.vo.OrderReceivedCorrectionVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderReceivedCorrectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/order/received-correction")
@RequiredArgsConstructor
public class OrderReceivedCorrectionController {
    private final OrderReceivedCorrectionService correctionService;

    @GetMapping("/list/{orderId}")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<List<OrderReceivedCorrectionVO>> list(@PathVariable Long orderId) {
        return ResponseDTO.ok(correctionService.list(orderId));
    }

    @GetMapping("/candidate/{orderId}/{orderItemId}")
    @SaCheckPermission(OrderPermission.QUERY)
    public ResponseDTO<OrderReceivedCorrectionCandidateVO> candidate(@PathVariable Long orderId,
            @PathVariable Long orderItemId) {
        return ResponseDTO.ok(correctionService.candidate(orderId, orderItemId));
    }

    @PostMapping("/record")
    @SaCheckPermission(value = {OrderPermission.QUERY, OrderPermission.UPDATE}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<OrderReceivedCorrectionVO> record(@Valid @RequestBody OrderReceivedCorrectionForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(correctionService.record(form, key));
    }
}
