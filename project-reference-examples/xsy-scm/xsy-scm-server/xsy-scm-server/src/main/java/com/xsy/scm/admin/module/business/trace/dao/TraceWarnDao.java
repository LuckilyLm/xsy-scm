package com.xsy.scm.admin.module.business.trace.dao;

import com.xsy.scm.admin.module.business.trace.domain.vo.TraceWarnVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 溯源预警 Dao（只读）
 *
 * @author xsy-scm
 */
@Mapper
public interface TraceWarnDao {

    /**
     * 查询资质即将到期 / 已过期的厂商（对标蔬东坡 17.4 文件预警查询）
     */
    List<TraceWarnVO> listExpiringManufacturers(@Param("deadline") LocalDate deadline);
}
