package com.xsy.scm.print.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.print.domain.entity.ScmPrintRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 打印记录读写。没有更新与删除：表上的触发器会拒绝，接口层也不提供。 保存的打印内容一旦能改，历史重印就和当初那张对不上了。
 */
@Mapper
public interface ScmPrintRecordDao extends BaseMapper<ScmPrintRecordEntity> {

    /** 按 id 取一条记录（含当时保存的内容），供历史重印。 */
    ScmPrintRecordEntity selectRecord(@Param("id") Long id);

    /** 新增一条打印记录。 */
    int insertRecord(@Param("row") ScmPrintRecordEntity row);
}
