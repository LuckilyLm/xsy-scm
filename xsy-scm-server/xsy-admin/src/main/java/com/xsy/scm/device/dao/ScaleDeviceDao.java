package com.xsy.scm.device.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.device.domain.entity.ScaleDeviceEntity;
import com.xsy.scm.device.domain.form.ScaleDeviceQueryForm;
import com.xsy.scm.device.domain.vo.ScaleDeviceVO;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备绑定读写。
 *
 * <p>
 * 凭证与配对码只按摘要查询：明文不保存，也不回显。撤销绑定是状态迁移而不是删除，因为历史读数必须继续能指回当时的设备。
 */
@Mapper
public interface ScaleDeviceDao extends BaseMapper<ScaleDeviceEntity> {

    ScaleDeviceEntity selectByDeviceCode(@Param("deviceCode") String deviceCode);

    ScaleDeviceEntity selectByCredentialHash(@Param("credentialHash") String credentialHash);

    /** 取一行并加锁，用于激活、轮换凭证、撤销，避免并发重复激活同一设备。 */
    ScaleDeviceEntity lockById(@Param("id") Long id);

    /** 激活：写入设备凭证并清空配对码。返回 0 表示配对码已被并发使用或已过期。 */
    int activate(@Param("id") Long id, @Param("pairingCodeHash") String pairingCodeHash,
        @Param("credentialHash") String credentialHash,
        @Param("credentialExpiresAt") OffsetDateTime credentialExpiresAt, @Param("protocolType") String protocolType,
        @Param("agentVersion") String agentVersion);

    /** 心跳：只更新观测字段，不改变绑定与凭证状态。 */
    int heartbeat(@Param("id") Long id, @Param("agentVersion") String agentVersion,
        @Param("protocolType") String protocolType, @Param("scaleConnected") Boolean scaleConnected,
        @Param("printerConnected") Boolean printerConnected, @Param("queueDepth") Integer queueDepth,
        @Param("lastReadingAt") OffsetDateTime lastReadingAt);

    int markRevoked(@Param("id") Long id, @Param("operator") String operator);

    /** 设备台账；scope 为仓库数据范围，null 按拒绝处理。 */
    List<ScaleDeviceVO> queryPage(Page<?> page, @Param("query") ScaleDeviceQueryForm query,
        @Param("scope") ScmValueScope scope);
}
