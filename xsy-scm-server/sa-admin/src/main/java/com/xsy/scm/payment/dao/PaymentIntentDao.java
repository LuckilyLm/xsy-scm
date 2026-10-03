package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentQueryForm;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentIntentDao extends BaseMapper<PaymentIntentEntity> {

    List<PaymentIntentEntity> queryPage(Page<?> page, @Param("query") PaymentIntentQueryForm query);

    PaymentIntentEntity selectByIntentNo(@Param("intentNo") String intentNo);

    /** 行锁：状态转换必须在锁内读-判-写，否则并发回调会各自基于旧状态做决定。 */
    PaymentIntentEntity lockById(@Param("id") Long id);

    /**
     * 条件更新状态：把「当前状态」一起放进 WHERE。
     *
     * <p>
     * 受影响行数 != 1 就是「有人先改了」——状态机判定与写入之间不留窗口。
     */
    int updateStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to,
            @Param("operator") String operator);

    /** 回填渠道意图号（发起成功后）。 */
    int bindExternalIntent(@Param("id") Long id, @Param("externalIntentId") String externalIntentId,
            @Param("operator") String operator);

    /** 该来源下已成功收款的合计（判断「这单还欠多少」用，不用于自动推断应付金额）。 */
    java.math.BigDecimal sumSucceededBySource(@Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId);
}
