package net.lab1024.sa.admin.module.system.support;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 附件授权用的最小业务可见性查询：只回答「这个对象是否存在 / 这个员工能否看到」。
 *
 * <p>不复用各业务模块的分页查询，是因为那会把展示层字段与权限判定一起拖进文件读路径；
 * 守卫需要的只是一个布尔判定，SQL 在这里保持单表、单条件。
 */
@Mapper
public interface FileBizVisibilityDao {

    /**
     * 公告对该员工是否可见：全部可见标记，或可见范围按员工/其部门命中。
     */
    int countVisibleNotice(@Param("noticeId") Long noticeId, @Param("employeeId") Long employeeId,
                           @Param("departmentId") Long departmentId);

    /**
     * 帮助文档是否存在且未删除。
     */
    int countHelpDoc(@Param("helpDocId") Long helpDocId);

    /**
     * 商品 SPU 是否存在且未删除。
     *
     * <p>删除商品时业务侧只软删 {@code product_image} 行，不回收 {@code t_file_relation}；
     * 因此「有商品查看权」必须同时要求商品本身还活着，否则已删商品的私有图会对全体
     * 有查看权的人长期可读。
     */
    int countLiveSpu(@Param("spuId") Long spuId);
}
