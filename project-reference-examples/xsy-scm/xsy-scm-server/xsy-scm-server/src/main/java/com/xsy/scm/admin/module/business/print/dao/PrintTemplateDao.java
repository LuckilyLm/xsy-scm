package com.xsy.scm.admin.module.business.print.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.print.domain.entity.PrintTemplateEntity;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateQueryForm;
import com.xsy.scm.admin.module.business.print.domain.vo.PrintTemplateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 打印模板 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface PrintTemplateDao extends BaseMapper<PrintTemplateEntity> {

    /**
     * 分页查询打印模板
     */
    List<PrintTemplateVO> queryPage(Page page, @Param("queryForm") PrintTemplateQueryForm queryForm);

    /**
     * 按模板编码查询启用中的模板（打印渲染入口）
     */
    PrintTemplateEntity getByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * 清除同业务类型的默认标记
     */
    void clearDefaultByBizType(@Param("bizType") Integer bizType);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
