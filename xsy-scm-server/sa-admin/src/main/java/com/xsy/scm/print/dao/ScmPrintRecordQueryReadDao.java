package com.xsy.scm.print.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.print.domain.form.ScmPrintRecordQueryForm;
import com.xsy.scm.print.domain.vo.ScmPrintRecordVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 打印记录跨域只读查询，按源单据当前归属与调用者权限收窄。 */
@Mapper
public interface ScmPrintRecordQueryReadDao {

    List<ScmPrintRecordVO> queryPage(Page<?> page, @Param("query") ScmPrintRecordQueryForm query,
            @Param("scope") ScmDataScopeContext scope, @Param("visibleTypes") List<String> visibleTypes,
            @Param("crossAssignee") boolean crossAssignee);
}
