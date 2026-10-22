package com.xsy.scm.trace.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 溯源附件挂载前的对象存在性校验。
 *
 * <p>
 * 只回答「这个商品/批次还在不在」：附件关系由通用文件层维护，溯源域不重复实现一遍绑定逻辑。 SQL 见 mapper/scm/trace/TraceAttachmentDao.xml。
 */
@Mapper
public interface TraceAttachmentDao {

    /** 商品 SPU 是否存在且未删除。 */
    int countLiveSpu(@Param("spuId") Long spuId);

    /** 追溯批次是否存在；批次事实只追加，没有软删除列。 */
    int countLot(@Param("lotId") Long lotId);
}
