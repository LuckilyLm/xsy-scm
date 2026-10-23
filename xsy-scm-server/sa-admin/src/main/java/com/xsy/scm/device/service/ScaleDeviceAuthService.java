package com.xsy.scm.device.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.device.constant.ScaleDeviceErrorCode;
import com.xsy.scm.device.constant.ScaleDeviceStatusEnum;
import com.xsy.scm.device.dao.ScaleDeviceDao;
import com.xsy.scm.device.dao.ScaleDeviceSessionDao;
import com.xsy.scm.device.domain.entity.ScaleDeviceEntity;
import com.xsy.scm.device.domain.entity.ScaleDeviceSessionEntity;
import com.xsy.scm.device.domain.form.ScaleActivateForm;
import com.xsy.scm.device.domain.form.ScaleDeviceRegisterForm;
import com.xsy.scm.device.domain.form.ScaleHeartbeatForm;
import com.xsy.scm.device.domain.form.ScaleOperatorSessionForm;
import com.xsy.scm.device.domain.vo.ScaleDeviceConfigVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceCredentialVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceRegisterVO;
import com.xsy.scm.device.domain.vo.ScaleOperatorSessionVO;
import com.xsy.scm.device.support.ScaleCredentialCodec;
import com.xsy.scm.device.support.ScaleDeviceContext;
import com.xsy.scm.device.support.ScaleDevicePrincipal;
import com.xsy.scm.warehouse.service.WarehouseQueryService;

import java.time.OffsetDateTime;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.admin.module.system.employee.service.EmployeeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备身份与员工授权的签发、校验与撤销。
 *
 * <p>
 * 设备凭证与员工会话分开校验：设备凭证只回答「哪台秤」，员工会话只回答「谁在操作」。 两者都通过才建立 {@link ScaleDevicePrincipal}，业务层据此写真实员工，不用设备身份代替人工签名。
 *
 * <p>
 * 所有令牌只在签发时返回明文，库里只有摘要；因此即使拿到数据库也无法冒充设备或员工。
 */
@Service
@RequiredArgsConstructor
public class ScaleDeviceAuthService {

    /**
     * 配对码由现场手工输入，熵低于令牌，因此有效期要短。
     */
    private static final int PAIRING_VALID_MINUTES = 30;

    /**
     * 设备凭证有效期；到期前需重新激活，不能靠续期绕过撤销。
     */
    private static final int CREDENTIAL_VALID_DAYS = 30;

    private final ScaleDeviceDao scaleDeviceDao;

    private final ScaleDeviceSessionDao scaleDeviceSessionDao;

    private final WarehouseQueryService warehouseQueryService;

    private final EmployeeService employeeService;

    /**
     * 后台登记设备并生成一次性配对码。仓库经正式查询服务校验，管理员只能登记自己有数据范围的仓库。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScaleDeviceRegisterVO register(ScaleDeviceRegisterForm form) {
        warehouseQueryService.detail(form.getWarehouseId());
        String deviceCode = form.getDeviceCode().trim();
        if (scaleDeviceDao.selectByDeviceCode(deviceCode) != null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_ALREADY_REGISTERED);
        }
        String pairingCode = ScaleCredentialCodec.newPairingCode();
        OffsetDateTime now = OffsetDateTime.now();
        ScaleDeviceEntity device = new ScaleDeviceEntity();
        device.setDeviceCode(deviceCode);
        device.setDeviceName(trimToNull(form.getDeviceName()));
        device.setDeviceModel(trimToNull(form.getDeviceModel()));
        device.setWarehouseId(form.getWarehouseId());
        device.setStationCode(trimToNull(form.getStationCode()));
        device.setStatus(ScaleDeviceStatusEnum.PENDING_ACTIVATION.name());
        device.setPairingCodeHash(ScaleCredentialCodec.digest(pairingCode));
        device.setPairingExpiresAt(now.plusMinutes(PAIRING_VALID_MINUTES));
        device.setCreatedBy(ScmOperator.current());
        device.setUpdatedBy(ScmOperator.current());
        scaleDeviceDao.insert(device);
        return new ScaleDeviceRegisterVO(device.getId(), deviceCode, pairingCode, device.getPairingExpiresAt());
    }

    /**
     * 为设备上的员工签发操作会话。同一设备可以有多位员工先后登录，各自一条会话记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScaleOperatorSessionVO issueOperatorSession(Long deviceId, ScaleOperatorSessionForm form) {
        ScaleDeviceEntity device = scaleDeviceDao.selectById(deviceId);
        if (device == null || ScaleDeviceStatusEnum.REVOKED.name().equals(device.getStatus())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_FOUND);
        }
        EmployeeEntity employee = employeeService.getById(form.getEmployeeId());
        if (employee == null || Boolean.TRUE.equals(employee.getDisabledFlag())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_INVALID);
        }
        String sessionToken = ScaleCredentialCodec.newToken();
        OffsetDateTime now = OffsetDateTime.now();
        ScaleDeviceSessionEntity session = new ScaleDeviceSessionEntity();
        session.setDeviceId(deviceId);
        session.setEmployeeId(form.getEmployeeId());
        session.setSessionTokenHash(ScaleCredentialCodec.digest(sessionToken));
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusMinutes(form.getValidMinutes()));
        session.setCreatedBy(ScmOperator.current());
        scaleDeviceSessionDao.insert(session);
        return new ScaleOperatorSessionVO(sessionToken, session.getExpiresAt(), form.getEmployeeId());
    }

    /**
     * 撤销绑定：设备不能再领取任务或上报读数，该设备上仍在有效期内的员工会话同时结束。 历史读数与打印记录保留，仍按原设备编号追溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long deviceId) {
        ScaleDeviceEntity device = scaleDeviceDao.lockById(deviceId);
        if (device == null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_FOUND);
        }
        if (ScaleDeviceStatusEnum.REVOKED.name().equals(device.getStatus())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_ACTIVE);
        }
        if (scaleDeviceDao.markRevoked(deviceId, ScmOperator.current()) != 1) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_ACTIVE);
        }
        scaleDeviceSessionDao.endActiveSessions(deviceId, "设备已撤销", OffsetDateTime.now());
    }

    /**
     * 用配对码换取设备凭证。配对码一次性：并发或重放激活只能成功一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScaleDeviceCredentialVO activate(ScaleActivateForm form) {
        ScaleDeviceEntity device = scaleDeviceDao.selectByDeviceCode(form.getDeviceCode().trim());
        if (device == null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_FOUND);
        }
        if (!ScaleDeviceStatusEnum.PENDING_ACTIVATION.name().equals(device.getStatus())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_ACTIVE);
        }
        String accessToken = ScaleCredentialCodec.newToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(CREDENTIAL_VALID_DAYS);
        int affected = scaleDeviceDao.activate(device.getId(), ScaleCredentialCodec.digest(form.getPairingCode()),
            ScaleCredentialCodec.digest(accessToken), expiresAt, trimToNull(form.getProtocolVersion()),
            trimToNull(form.getAgentVersion()));
        if (affected != 1) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.PAIRING_CODE_INVALID);
        }
        return new ScaleDeviceCredentialVO(accessToken, expiresAt);
    }

    /**
     * 心跳只更新观测字段。它证明设备在线，但不证明云端没有遗漏事件，因此不能作为对账依据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(ScaleHeartbeatForm form) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        scaleDeviceDao.heartbeat(principal.deviceId(), trimToNull(form.getAgentVersion()),
            trimToNull(form.getProtocolType()), form.getScaleConnected(), form.getPrinterConnected(),
            form.getQueueDepth(), form.getLastWeightAt());
    }

    /**
     * 读取设备自身配置。只返回本设备的绑定信息，不含其他设备或其他仓库。
     */
    @Transactional(readOnly = true)
    public ScaleDeviceConfigVO config() {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        ScaleDeviceEntity device = scaleDeviceDao.selectById(principal.deviceId());
        if (device == null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_FOUND);
        }
        ScaleDeviceConfigVO vo = new ScaleDeviceConfigVO();
        vo.setDeviceCode(device.getDeviceCode());
        vo.setWarehouseId(device.getWarehouseId());
        vo.setStationCode(device.getStationCode());
        vo.setStatus(device.getStatus());
        vo.setProtocolType(device.getProtocolType());
        vo.setAgentVersion(device.getAgentVersion());
        vo.setCredentialExpiresAt(device.getCredentialExpiresAt());
        return vo;
    }

    /**
     * 解析一次设备请求的双重身份，供设备拦截器调用。
     *
     * <p>
     * 员工会话必须由本设备签发：同一员工在另一台秤上登录不能直接拿到本机权限， 否则一台被借用的设备就能继承别处签发的授权。
     */
    @Transactional(readOnly = true)
    public ScaleDevicePrincipal authenticate(String credentialToken, String operatorSessionToken) {
        if (isBlank(credentialToken)) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_UNAUTHORIZED);
        }
        ScaleDeviceEntity device = scaleDeviceDao.selectByCredentialHash(ScaleCredentialCodec.digest(credentialToken));
        if (device == null) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_UNAUTHORIZED);
        }
        if (!ScaleDeviceStatusEnum.ACTIVE.name().equals(device.getStatus())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_NOT_ACTIVE);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (device.getCredentialExpiresAt() == null || !device.getCredentialExpiresAt().isAfter(now)) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.DEVICE_UNAUTHORIZED);
        }
        if (isBlank(operatorSessionToken)) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_SESSION_INVALID);
        }
        ScaleDeviceSessionEntity session = scaleDeviceSessionDao
            .selectByTokenHash(ScaleCredentialCodec.digest(operatorSessionToken));
        if (session == null || session.getEndedAt() != null || !session.getExpiresAt().isAfter(now)) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_SESSION_INVALID);
        }
        if (!Objects.equals(session.getDeviceId(), device.getId())) {
            throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_SESSION_DEVICE_MISMATCH);
        }
        return new ScaleDevicePrincipal(device.getId(), device.getDeviceCode(), device.getWarehouseId(),
            session.getId(), session.getEmployeeId(), session.getExpiresAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
