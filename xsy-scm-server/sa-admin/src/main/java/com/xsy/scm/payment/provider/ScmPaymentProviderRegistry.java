package com.xsy.scm.payment.provider;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 渠道注册表：按 {@link ScmPaymentProviderEnum} 取实现。
 *
 * <p>
 * 业务域**只通过这里**拿渠道，因此新增渠道 = 新增一个 {@code ScmPaymentProvider} 实现，
 * 业务代码零改动。没实现的渠道在这里明确报「不支持的渠道」，而不是抛空指针。
 */
@Component
public class ScmPaymentProviderRegistry {

    private final Map<ScmPaymentProviderEnum, ScmPaymentProvider> providers = new EnumMap<>(
            ScmPaymentProviderEnum.class);

    public ScmPaymentProviderRegistry(List<ScmPaymentProvider> implementations) {
        for (ScmPaymentProvider implementation : implementations) {
            providers.put(implementation.provider(), implementation);
        }
    }

    /**
     * @throws ScmBusinessException
     *             渠道未接入（例如 {@code WECHAT} 已冻结契约但尚无实现）
     */
    public ScmPaymentProvider require(String provider) {
        ScmPaymentProviderEnum code = ScmPaymentProviderEnum.of(provider);
        ScmPaymentProvider implementation = code == null ? null : providers.get(code);
        if (implementation == null || code == ScmPaymentProviderEnum.INTERNAL_BALANCE) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
        }
        return implementation;
    }

    /** 已接入的渠道（供页面下拉用，避免让用户选到一个必然失败的渠道）。 */
    public List<String> available() {
        return providers.keySet().stream().map(Enum::name).sorted().toList();
    }
}
