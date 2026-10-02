package com.xsy.scm.print.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.print.domain.entity.ScmPrintRecordEntity;
import com.xsy.scm.print.domain.form.ScmPrintRecordQueryForm;
import com.xsy.scm.print.domain.vo.ScmPrintRecordVO;
import java.util.List;
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
     * 打印记录分页（不带快照正文，列表只需要「谁在什么时候按哪份模板的哪一版打了哪张单」）。
     */
    List<ScmPrintRecordVO> queryPage(Page<?> page, @Param("query") ScmPrintRecordQueryForm query);

    /**
     * 按 id 取一条记录（含快照），供历史重印。
     */
    ScmPrintRecordEntity selectRecord(@Param("id") Long id);

    /**
     * 新增一条冻结记录。
     */
    int insertRecord(@Param("row") ScmPrintRecordEntity row);
}
