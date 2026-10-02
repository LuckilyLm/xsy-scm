package com.xsy.scm.print.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.print.domain.entity.ScmPrintRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 打印记录读写。
 *
 * <p>
 * <b>没有更新与删除</b>：表上的触发器会拒绝它们，接口层也不提供 —— 冻结快照一旦能改，
 * 「历史重印与当初那张一致」就不再成立。
 */
@Mapper
public interface ScmPrintRecordDao extends BaseMapper<ScmPrintRecordEntity> {

    /**
     * 按 id 取一条记录（含快照），供历史重印。
     */
    ScmPrintRecordEntity selectRecord(@Param("id") Long id);

    /**
     * 新增一条冻结记录。
     */
    int insertRecord(@Param("row") ScmPrintRecordEntity row);
}
