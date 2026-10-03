package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.payment.domain.form.PaymentCallbackQueryForm;
import java.util.List;
import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 回调事件读写。
 *
 * <p>
 * <b>幂等靠唯一索引，不靠先查后写</b>：并发回调下「先 SELECT 再 INSERT」两边都会查到「不存在」，
 * 于是都去执行副作用。这里用 {@code ON CONFLICT DO NOTHING} + 受影响行数判定：
 * 返回 0 就是「这个事件已经有人落过了」，业务侧据此静默返回。
 */
@Mapper
public interface PaymentCallbackEventDao extends BaseMapper<PaymentCallbackEventEntity> {

    /**
     * 落事件；同一 {@code (provider, provider_event_id)} 已存在时**不报错、返回 0**。
     *
     * @return 1 = 本次新落；0 = 重复事件
     */
    int insertIgnoreDuplicate(@Param("row") PaymentCallbackEventEntity row);

    List<PaymentCallbackEventEntity> queryPage(Page<?> page, @Param("query") PaymentCallbackQueryForm query);

    PaymentCallbackEventEntity selectByProviderEventId(@Param("provider") String provider,
            @Param("providerEventId") String providerEventId);

    /** 处理完成后回填结论（只改处理结果，不改事实本身）。 */
    int markProcessed(@Param("id") Long id, @Param("processStatus") String processStatus,
            @Param("transactionId") Long transactionId, @Param("rejectReason") String rejectReason);
}
