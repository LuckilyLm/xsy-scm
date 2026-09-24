package net.lab1024.sa.admin.module.scm.sorting.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.sorting.domain.dto.SortingOrderLineSnapshot;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskEntity;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingCandidateQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingSummaryQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingCandidateLineVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingSkuSummaryVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskItemVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskVO;

/**
 * 分拣读侧与聚合写侧取数。带 {@code scope} 的语句必须由 Service 显式下传数据范围，
 * {@code scope == null} 在 SQL 里渲染成 {@code AND FALSE}：漏传范围的结果是「查不到」，不是「查全部」。
 */
@Mapper
public interface SortingQueryDao {

    /**
     * 任务聚合锁：所有任务级写动作都先锁这一行，明细的占用位与状态迁移才可能一致。
     */
    SortingTaskEntity lockTask(@Param("id") Long id);

    long nextNumber();

    List<SortingTaskVO> tasks(Page<?> page, @Param("q") SortingTaskQueryForm q,
                              @Param("scope") ScmDataScopeContext scope,
                              @Param("crossAssignee") boolean crossAssignee);

    SortingTaskVO task(@Param("id") Long id, @Param("scope") ScmDataScopeContext scope,
                       @Param("crossAssignee") boolean crossAssignee);

    List<SortingTaskItemVO> items(@Param("taskId") Long taskId);

    List<SortingSkuSummaryVO> skuSummary(Page<?> page, @Param("q") SortingSummaryQueryForm q,
                                         @Param("scope") ScmDataScopeContext scope,
                                         @Param("crossAssignee") boolean crossAssignee);

    /**
     * 建单用的候选订单行队列：不受订单业务员维度约束（裁决补充第 22 条），
     * 由「只授建单权 + 只取队列必需列」两头收口。
     */
    List<SortingCandidateLineVO> candidateLines(Page<?> page, @Param("q") SortingCandidateQueryForm q);

    /**
     * 按 id 批量取订单行快照（含已删除标志），一次往返完成校验与冻结。
     */
    List<SortingOrderLineSnapshot> orderLines(@Param("ids") List<Long> ids);

    int markPrinted(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 取消任务时释放该任务全部活动明细的占用位；与任务状态迁移同事务，否则索引会与状态漂移。
     */
    int releaseItems(@Param("taskId") Long taskId, @Param("operator") String operator);
}
