package com.xsy.scm.admin.module.business.oa.enterprise.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.oa.enterprise.domain.entity.EnterpriseEntity;
import com.xsy.scm.admin.module.business.oa.enterprise.domain.form.EnterpriseQueryForm;
import com.xsy.scm.admin.module.business.oa.enterprise.domain.vo.EnterpriseExcelVO;
import com.xsy.scm.admin.module.business.oa.enterprise.domain.vo.EnterpriseListVO;
import com.xsy.scm.admin.module.business.oa.enterprise.domain.vo.EnterpriseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 企业
 *
 */
@Mapper
public interface EnterpriseDao extends BaseMapper<EnterpriseEntity> {

    /**
     * 根据企业名称查询
     *
     */
    EnterpriseEntity queryByEnterpriseName(@Param("enterpriseName") String enterpriseName,
        @Param("excludeEnterpriseId") Long excludeEnterpriseId, @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 删除企业
     */
    void deleteEnterprise(@Param("enterpriseId") Long enterpriseId, @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 企业分页查询
     *
     */
    List<EnterpriseVO> queryPage(Page page, @Param("queryForm") EnterpriseQueryForm queryForm);

    /**
     * 查询导出的数据
     *
     */
    List<EnterpriseExcelVO> selectExcelExportData(@Param("queryForm") EnterpriseQueryForm queryForm);

    /**
     * 查询企业详情
     *
     */
    EnterpriseVO getDetail(@Param("enterpriseId") Long enterpriseId, @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 查询列表
     *
     */
    List<EnterpriseListVO> queryList(@Param("type") Integer type, @Param("disabledFlag") Boolean disabledFlag,
        @Param("deletedFlag") Boolean deletedFlag);

}
