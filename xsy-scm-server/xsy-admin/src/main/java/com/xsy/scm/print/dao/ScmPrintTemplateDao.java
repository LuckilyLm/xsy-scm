package com.xsy.scm.print.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.print.domain.entity.ScmPrintTemplateEntity;
import com.xsy.scm.print.domain.form.ScmPrintTemplateQueryForm;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 打印模板读写。编辑与设默认都走手写 SQL：{@code model} 是 JSONB，必须显式带 typeHandler； 设默认要先清掉同类型的旧默认，两步需在同一事务里完成。
 */
@Mapper
public interface ScmPrintTemplateDao extends BaseMapper<ScmPrintTemplateEntity> {

    List<ScmPrintTemplateEntity> enabledOptions(@Param("documentType") String documentType);

    /** 模板分页；单据类型展示名由服务端补。 */
    List<ScmPrintTemplateEntity> queryPage(Page<?> page, @Param("query") ScmPrintTemplateQueryForm query);

    /** 加锁读一行，供编辑使用。 */
    ScmPrintTemplateEntity lock(@Param("id") Long id);

    /** 按编码查一行，同类型下防重，与部分唯一索引一致。 */
    ScmPrintTemplateEntity selectByCode(@Param("documentType") String documentType,
        @Param("templateCode") String templateCode);

    /** 该单据类型的默认模板；没有时返回 {@code null}，由调用方提示「没有可用模板」。 */
    ScmPrintTemplateEntity selectDefault(@Param("documentType") String documentType);

    /** 新建模板。 */
    int insertTemplate(@Param("row") ScmPrintTemplateEntity row);

    /**
     * 编辑，带乐观锁和软删条件。
     *
     * @return 影响行数，必须为 1
     */
    int updateTemplate(@Param("id") Long id, @Param("templateCode") String templateCode,
        @Param("templateName") String templateName, @Param("defaultFlag") Boolean defaultFlag,
        @Param("enabledFlag") Boolean enabledFlag, @Param("model") Map<String, Object> model,
        @Param("remark") String remark, @Param("version") Integer version, @Param("operator") String operator);

    /** 清掉同类型的其它默认标记；{@code keepId} 是要设为默认的那一行，新建时可为 null。 */
    int clearDefault(@Param("documentType") String documentType, @Param("keepId") Long keepId,
        @Param("operator") String operator);

    /** 只翻转默认标记，供列表上的「设为默认」使用。 */
    int setDefaultFlag(@Param("id") Long id, @Param("defaultFlag") Boolean defaultFlag,
        @Param("operator") String operator);

    /** 软删，带乐观锁和软删条件。 */
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);
}
