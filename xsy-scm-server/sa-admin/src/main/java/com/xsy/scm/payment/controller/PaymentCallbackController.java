package com.xsy.scm.payment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.payment.constant.ScmPaymentPermission;
import com.xsy.scm.payment.domain.form.PaymentCallbackQueryForm;
import com.xsy.scm.payment.domain.vo.PaymentCallbackEventVO;
import com.xsy.scm.payment.service.PaymentCallbackService;
import com.xsy.scm.payment.service.PaymentQueryService;
import com.xsy.scm.payment.support.PaymentVoAssembler;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调：查记录，以及**受控的模拟投递**。
 *
 * <p>
 * <b>当前不开放匿名渠道回调，也不改 {@code MvcConfig}。</b> 模拟入口要求登录态 + 权限， 而且它走的仍是业务侧那条真实路径（验签 → 落事件 → 匹配交易 → 状态机），
 * 只是由后台把报文投进来而已。等接真实渠道时再单独处理公网入口、验签证书、 原始报文白名单、密钥与重放防护。
 *
 * <p>
 * 模拟入口**不新造权限模型**：复用创建支付意图的权限码 —— 能发起这笔支付的人， 才能模拟它的回调。
 */
@RestController
@RequestMapping("/scm/payment/callback")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentCallbackService paymentCallbackService;

    private final PaymentQueryService paymentQueryService;

    @PostMapping("/query")
    @SaCheckPermission(ScmPaymentPermission.CALLBACK_QUERY)
    public ResponseDTO<PageResult<PaymentCallbackEventVO>> query(@Valid @RequestBody PaymentCallbackQueryForm form) {
        return ResponseDTO.ok(paymentQueryService.callbackPage(form));
    }

    /**
     * 模拟渠道投递一次回调（仅本地模拟渠道）。
     *
     * <p>
     * 报文由调用方给出（含模拟渠道的签名头），业务侧照常验签 —— 因此「伪造回调」在这里 也会被如实记成 {@code REJECTED}，而不是被当成一次合法调用。
     */
    @PostMapping("/mock")
    @SaCheckPermission(ScmPaymentPermission.INTENT_CREATE)
    @OperateLog
    public ResponseDTO<PaymentCallbackEventVO> mock(@RequestHeader Map<String, String> headers,
            @RequestBody String rawBody) {
        return ResponseDTO.ok(PaymentVoAssembler.toCallbackEvent(paymentCallbackService
                .handle(com.xsy.scm.payment.constant.ScmPaymentProviderEnum.MOCK.name(), headers, rawBody)));
    }
}
