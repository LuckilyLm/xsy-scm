package net.lab1024.sa.admin.module.system.support;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.controller.FileController;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 私有附件的读取判定：管理员 / 业务对象授权（关系表）/ 上传者本人，三条之外一律拒绝。
 */
class FileAccessGuardTest {
    final FileDao dao = mock(FileDao.class);
    final FileAccessIdentity identity = mock(FileAccessIdentity.class);
    final FileRelationDao relations = mock(FileRelationDao.class);
    final FileAccessGuard guard = new FileAccessGuard(dao, identity, relations);
    final RequestEmployee employee = employee();

    private RequestEmployee employee() {
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(44L);
        user.setDepartmentId(7L);
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        return user;
    }

    private void boundTo(String key, FileRelationBizTypeEnum bizType, long bizId) {
        FileRelationEntity relation = new FileRelationEntity();
        relation.setFileKey(key);
        relation.setBizType(bizType.name());
        relation.setBizId(bizId);
        when(relations.listActiveByFileKeys(anyCollection())).thenReturn(List.of(relation));
    }

    @AfterEach
    void clear() {
        SmartRequestUtil.remove();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"publicity/a", "privatex/a", "PUBLIC/a", "Public/a", "private/other/a",
            "private/notice/../common/a", "private/notice/%2e%2e/common/a", "private/notice//a",
            "private/notice/a?x", "private/notice/a#x", "private/notice/a\\x", "private/notice/", "public/a,"})
    void rejectsInvalidKeysEvenForPrivilegedUser(String key) {
        when(identity.canReadAllFiles(employee)).thenReturn(true);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void publicFolderNeedsNoAuthorizationLookup() {
        assertThatCode(() -> guard.checkRead("public/a.png", employee)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkRead("public/a.png", null)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        verifyNoInteractions(dao, identity, relations);
    }

    /**
     * FA-2 的核心变化：以前「是 private/notice/ 前缀就放行」，现在前缀本身不再构成理由。
     * 同前缀下所有人的附件互看正是这笔债的成因，所以这条断言是承重的。
     */
    @ParameterizedTest
    @ValueSource(strings = {"private/notice/a.pdf", "private/help-doc/a.pdf"})
    void sharedFoldersNoLongerPassOnPrefixAlone(String key) {
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void relationGrantOpensAPrivateAttachmentForAnyoneWhoCanReadTheObject() {
        boundTo("private/notice/a.pdf", FileRelationBizTypeEnum.NOTICE, 900L);
        when(identity.canReadBizObject(FileRelationBizTypeEnum.NOTICE, 900L, employee)).thenReturn(true);

        assertThatCode(() -> guard.checkRead("private/notice/a.pdf", employee)).doesNotThrowAnyException();
    }

    @Test
    void relationRowDoesNotOutweighTheObjectItself() {
        boundTo("private/notice/a.pdf", FileRelationBizTypeEnum.NOTICE, 900L);
        when(identity.canReadBizObject(FileRelationBizTypeEnum.NOTICE, 900L, employee)).thenReturn(false);

        assertThatThrownBy(() -> guard.checkRead("private/notice/a.pdf", employee))
                .as("关系行只把「能否读对象」传进来，对象不可读时不能单独放行")
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void anyReadableObjectAmongSeveralGrantsTheSharedAttachment() {
        // 一份附件同时挂两个对象：用户对其中一个有权即可读，要求「全部有权」会让共享方看不见它。
        FileRelationEntity mine = new FileRelationEntity();
        mine.setFileKey("private/common/shared.pdf");
        mine.setBizType(FileRelationBizTypeEnum.PRODUCT.name());
        mine.setBizId(1L);
        FileRelationEntity theirs = new FileRelationEntity();
        theirs.setFileKey("private/common/shared.pdf");
        theirs.setBizType(FileRelationBizTypeEnum.ENTERPRISE.name());
        theirs.setBizId(2L);
        when(relations.listActiveByFileKeys(anyCollection())).thenReturn(List.of(theirs, mine));
        when(identity.canReadBizObject(FileRelationBizTypeEnum.ENTERPRISE, 2L, employee)).thenReturn(false);
        when(identity.canReadBizObject(FileRelationBizTypeEnum.PRODUCT, 1L, employee)).thenReturn(true);

        assertThatCode(() -> guard.checkRead("private/common/shared.pdf", employee)).doesNotThrowAnyException();
    }

    @Test
    void unknownBizTypeInDatabaseNeverBecomesAGrant() {
        FileRelationEntity stale = new FileRelationEntity();
        stale.setFileKey("private/common/a.pdf");
        stale.setBizType("SOMETHING_REMOVED");
        stale.setBizId(3L);
        when(relations.listActiveByFileKeys(anyCollection())).thenReturn(List.of(stale));

        assertThatThrownBy(() -> guard.checkRead("private/common/a.pdf", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
        verify(identity, never()).canReadBizObject(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"private/common/a.png", "private/feedback/a.png"})
    void strictFoldersAllowCreatorOrPrivilegeAndRejectOtherEmployees(String key) {
        FileVO file = new FileVO();
        file.setCreatorId(44L);
        file.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey(key)).thenReturn(file);
        assertThatCode(() -> guard.checkRead(key, employee)).doesNotThrowAnyException();
        file.setCreatorId(45L);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        file.setCreatorId(44L);
        file.setCreatorUserType(-1);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        when(identity.canReadAllFiles(employee)).thenReturn(true);
        assertThatCode(() -> guard.checkRead(key, employee)).doesNotThrowAnyException();
    }

    @Test
    void everyKeyInBatchMustBeAuthorized() {
        assertThatThrownBy(() -> guard.checkRead("private/notice/a,private/common/other", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void filterReadableKeepsOnlyKeysTheCallerMayRead() {
        FileVO ownFile = new FileVO();
        ownFile.setCreatorId(44L);
        ownFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/common/own")).thenReturn(ownFile);

        FileVO othersFile = new FileVO();
        othersFile.setCreatorId(45L);
        othersFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/common/others")).thenReturn(othersFile);

        // Mirrors the exact key set FileKeyVoSerializer would pass through: a mix of shared,
        // own, someone else's, and a malformed key, all in one VO field.
        List<String> readable = guard.filterReadable(
                List.of("public/a", "private/common/own", "private/common/others",
                        "private/notice/../common/escape", "private/notice/granted"),
                employee);

        assertThat(readable).containsExactly("public/a", "private/common/own");
        // Same per-key outcome as the throwing path, just collected instead of failing fast.
        assertThatCode(() -> guard.checkRead("public/a,private/common/own", employee)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkRead("public/a,private/common/others", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void filterReadableAndCheckReadAgreeOnRelationGrants() {
        boundTo("private/notice/granted", FileRelationBizTypeEnum.NOTICE, 900L);
        when(identity.canReadBizObject(FileRelationBizTypeEnum.NOTICE, 900L, employee)).thenReturn(true);

        assertThat(guard.filterReadable(List.of("private/notice/granted", "private/notice/denied"), employee))
                .containsExactly("private/notice/granted");
        assertThatThrownBy(() -> guard.checkRead("private/notice/granted,private/notice/denied", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void filterReadableReturnsEmptyRatherThanThrowingWithNoAuthenticatedCaller() {
        assertThat(guard.filterReadable(List.of("public/a"), null)).isEmpty();
        assertThat(guard.filterReadable(null, employee)).isEmpty();
        assertThat(guard.filterReadable(List.of(), employee)).isEmpty();
    }

    @Test
    void administratorNeedsNoRoleOrPermissionLookup() {
        employee.setAdministratorFlag(true);
        assertThat(new AdminFileAccessIdentity(mock(FileBizVisibilityDao.class)).canReadAllFiles(employee)).isTrue();
    }

    @Test
    void nonAdministratorUsesExistingFileQueryPermission() {
        AdminFileAccessIdentity identity = new AdminFileAccessIdentity(mock(FileBizVisibilityDao.class));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("support:file:query")).thenReturn(true);
            assertThat(identity.canReadAllFiles(employee)).isTrue();
            stp.when(() -> StpUtil.hasPermission("support:file:query")).thenReturn(false);
            assertThat(identity.canReadAllFiles(employee)).isFalse();
        }
    }

    /**
     * 关系放行的最后一环：sa-admin 侧的策略实现。只验派活关系，不验 SQL（SQL 由 IT 覆盖）。
     */
    @Test
    void bizPolicyMapsEachTypeToItsOwnDomainRule() {
        FileBizVisibilityDao visibility = mock(FileBizVisibilityDao.class);
        AdminFileAccessIdentity policy = new AdminFileAccessIdentity(visibility);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("scm:product:query")).thenReturn(true);
            when(visibility.countLiveSpu(5L)).thenReturn(1);
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.PRODUCT, 5L, employee)).isTrue();
            // 商品已删除：权限还在也不能继续读它的私有图（关系行不会被删除动作回收）
            when(visibility.countLiveSpu(5L)).thenReturn(0);
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.PRODUCT, 5L, employee)).isFalse();
            when(visibility.countLiveSpu(5L)).thenReturn(1);

            when(visibility.countVisibleNotice(9L, 44L, 7L)).thenReturn(1);
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.NOTICE, 9L, employee)).isTrue();
            when(visibility.countVisibleNotice(9L, 44L, 7L)).thenReturn(0);
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.NOTICE, 9L, employee)).isFalse();

            // 反馈与企业档案不在关系里额外开放：前者只有提交人与管理员可读，
            // 后者的查看权等正式业务角色一起落地。
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.FEEDBACK, 9L, employee)).isFalse();
            assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.ENTERPRISE, 9L, employee)).isFalse();
        }
        assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.PRODUCT, 5L, null)).isFalse();
        assertThat(policy.canReadBizObject(FileRelationBizTypeEnum.PRODUCT, null, employee)).isFalse();
    }

    @Test
    void forbiddenEndpointsReturnHttp403AndNativeEnvelopeWithoutStorageCalls() throws Exception {
        FileController controller = new FileController();
        FileService service = mock(FileService.class);
        ReflectionTestUtils.setField(controller, "fileService", service);
        ReflectionTestUtils.setField(controller, "fileAccessGuard", guard);
        SmartRequestUtil.setRequestUser(employee);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String endpoint : new String[]{"getFileUrl", "downLoad"}) {
            mvc.perform(get("/support/file/" + endpoint).param("fileKey", "private/common/other.png"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(30005))
                    .andExpect(jsonPath("$.ok").value(false));
        }
        verifyNoInteractions(service);
    }
}
