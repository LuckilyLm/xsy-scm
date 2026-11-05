package com.xsy.scm.common.dao;

import com.baomidou.mybatisplus.core.batch.BatchSqlSession;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import org.apache.ibatis.executor.BatchResult;

/** 追加记录的Mapper基类：保留标准新增查询，拒绝通用改删及批量覆盖。 */
public interface AppendOnlyMapper<T> extends BaseMapper<T> {
    /**
     * @param id
     *            任意记录主键；即使为空也不允许删除。 @return 不返回，历史记录只能追加。
     */
    @Override
    default int deleteById(Serializable id) {
        throw immutable();
    }

    /**
     * @param id
     *            任意主键或实体，不能删除。 @param useFill 不影响只能新增规则。 @return 不返回。
     */
    @Override
    default int deleteById(Object id, boolean useFill) {
        throw immutable();
    }

    /**
     * @param entity
     *            任意记录实体，不能逻辑删除。 @return 不返回。
     */
    @Override
    default int deleteById(T entity) {
        throw immutable();
    }

    /**
     * @param wrapper
     *            任意删除条件，空条件也不能删除。 @return 不返回。
     */
    @Override
    default int delete(Wrapper<T> wrapper) {
        throw immutable();
    }

    /**
     * @param ids
     *            任意主键集合，空集合也不能删除。 @param useFill 不影响只能新增规则。 @return 不返回。
     */
    @Override
    default int deleteByIds(Collection<?> ids, boolean useFill) {
        throw immutable();
    }

    /**
     * @param entity
     *            任意记录实体，不能覆盖历史字段。 @return 不返回。
     */
    @Override
    default int updateById(T entity) {
        throw immutable();
    }

    /**
     * @param entity
     *            任意新值。 @param wrapper 任意更新条件，不能覆盖历史记录。 @return 不返回。
     */
    @Override
    default int update(T entity, Wrapper<T> wrapper) {
        throw immutable();
    }

    /**
     * @param entities
     *            任意批量更新集合。 @param batchSize 批量大小不影响只能新增规则。 @return 不返回。
     */
    @Override
    default List<BatchResult> updateById(Collection<T> entities, int batchSize) {
        throw immutable();
    }

    /**
     * @param entity
     *            任意记录实体；新增使用insert，不能使用可能覆盖的upsert。 @return 不返回。
     */
    @Override
    default boolean insertOrUpdate(T entity) {
        throw immutable();
    }

    /**
     * @param entities
     *            任意记录集合。 @param batchSize 批量大小不影响只能新增规则。 @return 不返回。
     */
    @Override
    default List<BatchResult> insertOrUpdate(Collection<T> entities, int batchSize) {
        throw immutable();
    }

    /**
     * @param entities
     *            任意记录集合。
     * @param predicate
     *            任意覆盖判定，不能改变只能新增规则。
     * @param batchSize
     *            批量大小不影响只能新增规则。
     * @return 不返回，调用方应明确使用insert追加记录。
     */
    @Override
    default List<BatchResult> insertOrUpdate(Collection<T> entities, BiPredicate<BatchSqlSession, T> predicate,
        int batchSize) {
        throw immutable();
    }

    private static UnsupportedOperationException immutable() {
        return new UnsupportedOperationException("历史记录只能追加；更正请新增反向记录");
    }
}
