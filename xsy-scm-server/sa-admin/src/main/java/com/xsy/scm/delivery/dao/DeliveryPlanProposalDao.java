package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.delivery.domain.entity.DeliveryPlanProposalEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 排线建议读写。
 *
 * <p>
 * 没有「更新快照」的方法：表上的触发器会拒绝它，接口层也不提供 —— 冻结快照一旦能改，
 * 「建议里看到的距离」与「应用时依据的距离」就不再是同一份。
 */
@Mapper
public interface DeliveryPlanProposalDao extends BaseMapper<DeliveryPlanProposalEntity> {

    DeliveryPlanProposalEntity findById(@Param("id") Long id);

    /**
     * 取该线路当前待确认的建议并加行锁；没有则返回 {@code null}。
     */
    DeliveryPlanProposalEntity lockActive(@Param("routeId") Long routeId);

    /**
     * 按 id 取一行并加行锁。
     */
    DeliveryPlanProposalEntity lockById(@Param("id") Long id);

    /**
     * 某线路的建议历史（最新在前），供界面比较多次生成的结果。
     */
    List<DeliveryPlanProposalEntity> listByRoute(@Param("routeId") Long routeId, @Param("limit") int limit);

    int insertProposal(@Param("row") DeliveryPlanProposalEntity row);

    /**
     * 把待确认建议转为「已应用」。
     */
    int markApplied(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /**
     * 把待确认建议转为「已放弃」。
     */
    int markDiscarded(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /**
     * 把该线路所有待确认建议转为「已放弃」（生成新建议前调用）。
     */
    int discardActive(@Param("routeId") Long routeId, @Param("operator") String operator);
}
