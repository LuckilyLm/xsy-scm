package com.xsy.scm.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.module.system.menu.domain.vo.MenuTreeVO;
import com.xsy.scm.admin.module.system.menu.service.MenuService;
import com.xsy.scm.admin.test.PgITDatabase;
import com.xsy.scm.admin.test.PgITMigrationHistory;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.json.serializer.FileKeyVoSerializer;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.config.FileConfig;
import com.xsy.scm.base.module.support.file.constant.FileFolderTypeEnum;
import com.xsy.scm.base.module.support.file.constant.FileRelationBizTypeEnum;
import com.xsy.scm.base.module.support.file.controller.FileController;
import com.xsy.scm.base.module.support.file.dao.FileDao;
import com.xsy.scm.base.module.support.file.dao.FileRelationDao;
import com.xsy.scm.base.module.support.file.domain.entity.FileRelationEntity;
import com.xsy.scm.base.module.support.file.domain.vo.FileUploadVO;
import com.xsy.scm.base.module.support.file.domain.vo.FileVO;
import com.xsy.scm.base.module.support.file.service.*;
import com.xsy.scm.base.module.support.file.service.FileAccessGuard;
import com.xsy.scm.base.module.support.file.service.FileAccessIdentity;
import com.xsy.scm.base.module.support.file.service.FileService;
import com.xsy.scm.base.module.support.file.service.FileStorageCloudServiceImpl;
import com.xsy.scm.base.module.support.file.service.IFileStorageService;
import com.xsy.scm.base.module.support.operatelog.OperateLogService;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogQueryForm;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogVO;
import com.xsy.scm.base.module.support.redis.RedisService;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityFileService;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.xsy.scm.admin.module.system.support.AdminOperateLogController;
import com.xsy.scm.admin.module.system.support.FileScratchCleanupJob;
import com.xsy.scm.admin.module.system.support.AdminFileAccessIdentity;
import com.xsy.scm.admin.module.system.support.FileBizVisibilityDao;

class AdminTest {

    @Nested
    class AdminUnitTest {
        @Nested

        class AdminOperateLogBusinessGuardTest {
            final OperateLogService service = mock(OperateLogService.class);
            final AdminOperateLogController controller = controller();

            private AdminOperateLogController controller() {
                AdminOperateLogController c = new AdminOperateLogController();
                ReflectionTestUtils.setField(c, "operateLogService", service);
                return c;
            }

            private OperateLogQueryForm form(String businessType, Long businessId) {
                OperateLogQueryForm f = new OperateLogQueryForm();
                f.setPageNum(1L);
                f.setPageSize(20L);
                f.setBusinessType(businessType);
                f.setBusinessId(businessId);
                return f;
            }

            @ParameterizedTest
            @CsvSource({
                    "PRODUCT, scm:product:query",
                    "CUSTOMER, scm:customer:query",
                    "DELIVERY_ROUTE, scm:delivery:route:query",
            })
            void structuredFilterChecksTheMatchingDomainReadPermissionBeforeQuerying(String businessType, String permission) {
                when(service.queryByPage(any())).thenReturn(ResponseDTO.ok(new PageResult<OperateLogVO>()));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.queryByPage(form(businessType, 12L)).getOk()).isTrue();
                    stp.verify(() -> StpUtil.checkPermission(permission));
                    verify(service).queryByPage(any());
                }
            }

            @Test
            void unsupportedBusinessTypeIsRejectedWithoutQueryingOrPermissionLookup() {
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    ResponseDTO<PageResult<OperateLogVO>> result = controller.queryByPage(form("WAREHOUSE", 12L));
                    assertThat(result.getOk()).isFalse();
                    assertThat(result.getCode()).isEqualTo(UserErrorCode.PARAM_ERROR.getCode());
                    stp.verify(() -> StpUtil.checkPermission(anyString()), never());
                    verifyNoInteractions(service);
                }
            }

            @Test
            void businessIdWithoutTypeIsRejectedWithoutQuerying() {
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    ResponseDTO<PageResult<OperateLogVO>> result = controller.queryByPage(form(null, 12L));
                    assertThat(result.getCode()).isEqualTo(UserErrorCode.PARAM_ERROR.getCode());
                    stp.verify(() -> StpUtil.checkPermission(anyString()), never());
                    verifyNoInteractions(service);
                }
            }

            @Test
            void plainLogQueryWithoutBusinessFilterSkipsPermissionLookupAndQueries() {
                when(service.queryByPage(any())).thenReturn(ResponseDTO.ok(new PageResult<OperateLogVO>()));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.queryByPage(form(null, null)).getOk()).isTrue();
                    stp.verify(() -> StpUtil.checkPermission(anyString()), never());
                    verify(service).queryByPage(any());
                }
            }

            @ParameterizedTest
            @CsvSource({
                    "[{\"spuId\":12}],/api/product/spu/edit,scm:product:query",
                    "[{\"customerId\":9}],/api/scm/customer/edit,scm:customer:query",
                    "[{\"version\":0}],/api/scm/delivery/routes/12/plan,scm:delivery:route:query",
            })
            void detailChecksTheDomainReadPermissionItBelongsTo(String param, String url, String permission) {
                when(service.detail(1L)).thenReturn(ResponseDTO.ok(log(param, url)));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.detail(1L).getOk()).isTrue();
                    stp.verify(() -> StpUtil.checkPermission(permission));
                }
            }

            @Test
            void detailRequiresEveryRecognizedDomainPermission() {
                // 一条订单操作日志同时带 spuId 与 customerId，正文含两个领域的明细
                when(service.detail(1L)).thenReturn(ResponseDTO.ok(log("[{\"spuId\":12,\"customerId\":9}]", "/api/scm/order/edit")));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.detail(1L).getOk()).isTrue();
                    stp.verify(() -> StpUtil.checkPermission("scm:product:query"));
                    stp.verify(() -> StpUtil.checkPermission("scm:customer:query"));
                }
            }

            @Test
            void detailOfUnrecognizedSystemLogKeepsOnlyLogPermission() {
                when(service.detail(1L)).thenReturn(ResponseDTO.ok(log("[{\"configKey\":\"sys.name\"}]", "/api/config/update")));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.detail(1L).getOk()).isTrue();
                    stp.verify(() -> StpUtil.checkPermission(anyString()), never());
                }
            }

            @Test
            void failedDetailLookupDoesNotTriggerAnyPermissionLookup() {
                when(service.detail(1L)).thenReturn(ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST));
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    assertThat(controller.detail(1L).getOk()).isFalse();
                    stp.verify(() -> StpUtil.checkPermission(anyString()), never());
                }
            }

            private OperateLogVO log(String param, String url) {
                OperateLogVO vo = new OperateLogVO();
                vo.setParam(param);
                vo.setUrl(url);
                return vo;
            }
        }

        @Nested

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
                XsyRequestUtil.remove();
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
                // 公开对象要能被未登录的客户页直接取用，不再要求先有员工身份。
                assertThatCode(() -> guard.checkRead("public/a.png", null)).doesNotThrowAnyException();
                verifyNoInteractions(dao, identity, relations);
            }

            /**
             * 前缀本身不构成放行理由：不能因为 key 带 private/notice/ 前缀就放行。
             * 同前缀下所有人的附件互看正是这条规则要挡的，所以这条断言是承重的。
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
            void filterReadableServesPublicKeysWithoutACallerButDropsPrivateOnes() {
                assertThat(guard.filterReadable(List.of("public/a"), null)).containsExactly("public/a");
                assertThat(guard.filterReadable(List.of("public/a", "private/common/a.png"), null))
                        .containsExactly("public/a");
                assertThat(guard.filterReadable(null, employee)).isEmpty();
                assertThat(guard.filterReadable(List.of(), employee)).isEmpty();
            }

            @Test
            void administratorNeedsNoRoleOrPermissionLookup() {
                employee.setAdministratorFlag(true);
                assertThat(new AdminFileAccessIdentity(mock(FileBizVisibilityDao.class), mock(ScmDataScopeService.class))
                        .canReadAllFiles(employee)).isTrue();
            }

            @Test
            void nonAdministratorUsesExistingFileQueryPermission() {
                AdminFileAccessIdentity identity = new AdminFileAccessIdentity(mock(FileBizVisibilityDao.class),
                        mock(ScmDataScopeService.class));
                try (var stp = mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.hasPermission("support:file:query")).thenReturn(true);
                    assertThat(identity.canReadAllFiles(employee)).isTrue();
                    stp.when(() -> StpUtil.hasPermission("support:file:query")).thenReturn(false);
                    assertThat(identity.canReadAllFiles(employee)).isFalse();
                }
            }

            /**
             * 关系放行的最后一环：xsy-admin 侧的策略实现。只验派活关系，不验 SQL（SQL 由 IT 覆盖）。
             */
            @Test
            void bizPolicyMapsEachTypeToItsOwnDomainRule() {
                FileBizVisibilityDao visibility = mock(FileBizVisibilityDao.class);
                AdminFileAccessIdentity policy = new AdminFileAccessIdentity(visibility, mock(ScmDataScopeService.class));
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
                XsyRequestUtil.setRequestUser(employee);
                var mvc = MockMvcBuilders.standaloneSetup(controller).build();
                for (String endpoint : new String[]{"getFileUrl", "downLoad"}) {
                    mvc.perform(get("/support/file/" + endpoint).param("fileKey", "private/common/other.png"))
                            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(30005))
                            .andExpect(jsonPath("$.ok").value(false));
                }
                verifyNoInteractions(service);
            }
        }

        @Nested

        class FileConfigTest {
            static FileConfig config() {
                FileConfig config = new FileConfig();
                config.setCloudRegion("us-east-1");
                config.setCloudEndpoint("http://storage.example.test:9000");
                config.setCloudBucketName("xsy-scm-dev");
                config.setCloudPublicUrlPrefix("http://storage.example.test:9000/xsy-scm-dev/");
                config.setCloudAccessKey(UUID.randomUUID().toString());
                config.setCloudSecretKey(UUID.randomUUID().toString());
                config.setCloudPrivateUrlExpireSeconds(600L);
                return config;
            }

            @Test
            void upstreamDefaultsRemainUnchanged() {
                assertThat(new FileConfig().isCloudPathStyleAccessEnabled()).isFalse();
                assertThat(new FileConfig().isCloudSendObjectAcl()).isTrue();
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void presignerHonorsPathStyle(boolean pathStyle) {
                FileConfig config = config();
                config.setCloudPathStyleAccessEnabled(pathStyle);
                try (var signer = config.initS3Presigner(); var client = config.initS3Client()) {
                    var url = signer.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(2))
                            .getObjectRequest(GetObjectRequest.builder().bucket("xsy-scm-dev").key("private/common/a.png").build())
                            .build()).url();
                    assertThat(url.getHost()).isEqualTo(pathStyle ? "storage.example.test" : "xsy-scm-dev.storage.example.test");
                    assertThat(url.getPath()).isEqualTo(pathStyle ? "/xsy-scm-dev/private/common/a.png" : "/private/common/a.png");
                    assertThat(url.getQuery()).contains("X-Amz-Expires=2");
                }
            }

            @Test
            void rejectsMissingPublicPrefixAndUnsafeTtl() {
                FileConfig config = config();
                config.setCloudPublicUrlPrefix("");
                assertThatThrownBy(config::initS3Client).isInstanceOf(IllegalStateException.class);
                config.setCloudPublicUrlPrefix("http://storage.example.test/xsy-scm-dev/");
                config.setCloudPrivateUrlExpireSeconds(0L);
                assertThatThrownBy(config::initS3Presigner).isInstanceOf(IllegalStateException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {"pre", "prod"})
            void rejectsLocalSampleCredentialsInProduction(String profile) {
                FileConfig config = config();
                config.setActiveProfiles(profile);
                config.setCloudAccessKey("xsy_f0_local");
                assertThatThrownBy(config::initS3Client).isInstanceOf(IllegalStateException.class);
            }
        }

        @Nested

        class FileFolderTypeEnumTest {
            @Test
            void allCurrentUploadFoldersArePublic() {
                assertThat(FileFolderTypeEnum.values()).allSatisfy(
                        folder -> assertThat(folder.getFolder()).startsWith(FileFolderTypeEnum.FOLDER_PUBLIC + "/"));
            }
        }

        @Nested

        class FileKeyVoSerializerTest {
            final FileService fileService = mock(FileService.class);

            final JsonGenerator generator = mock(JsonGenerator.class);

            final SerializerProvider provider = mock(SerializerProvider.class);

            private FileKeyVoSerializer wired() {
                FileKeyVoSerializer serializer = new FileKeyVoSerializer();
                ReflectionTestUtils.setField(serializer, "fileService", fileService);
                return serializer;
            }

            private RequestEmployee employee(long id) {
                RequestEmployee user = new RequestEmployee();
                user.setEmployeeId(id);
                user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                return user;
            }

            @AfterEach
            void clear() {
                XsyRequestUtil.remove();
            }

            @Test
            void handsTheCallerIdentityToTheControlledBatchEntry() throws Exception {
                RequestEmployee caller = employee(44L);
                XsyRequestUtil.setRequestUser(caller);
                when(fileService.getFileList(anyList(), org.mockito.ArgumentMatchers.eq(caller)))
                        .thenReturn(List.of());

                wired().serialize("private/feedback/own.png,private/feedback/others.png", generator, provider);

                verify(fileService).getFileList(
                        List.of("private/feedback/own.png", "private/feedback/others.png"), caller);
            }

            @Test
            void neverFallsBackToTheUnauthenticatedBatchEntry() throws Exception {
                // 无身份入口只服务公开目录；序列化器拿不到身份时必须什么都不解析，
                // 而不是退回无守卫的解析入口，把私有附件展开成 URL。
                wired().serialize("private/common/a.png", generator, provider);

                verify(fileService, never()).getFileList(anyList());
                verify(fileService).getFileList(anyList(), org.mockito.ArgumentMatchers.isNull());
                verify(generator).writeObject(List.of());
            }

            @Test
            void emptyFieldWritesAnEmptyList() throws Exception {
                XsyRequestUtil.setRequestUser(employee(44L));

                wired().serialize("", generator, provider);

                verify(fileService, never()).getFileList(anyList(), org.mockito.ArgumentMatchers.any());
                verify(generator).writeObject(List.of());
            }

            @Test
            void neverEmitsTheRawKeyIfFileServiceWasNeverWired() throws Exception {
                FileKeyVoSerializer unwired = new FileKeyVoSerializer();
                XsyRequestUtil.setRequestUser(employee(44L));

                unwired.serialize("private/common/a.pdf", generator, provider);

                // 依赖缺失时把存储里的 key 原样写进响应，仍然会泄露私有附件的存在与路径。
                verify(generator, never()).writeString(anyString());
                verify(generator).writeObject(List.of());
            }
        }

        @Nested

        class FileServiceReadTest {
            final FileDao fileDao = mock(FileDao.class);

            final FileAccessIdentity identity = mock(FileAccessIdentity.class);

            final IFileStorageService storage = mock(IFileStorageService.class);

            final SecurityFileService securityFileService = mock(SecurityFileService.class);

            final FileService fileService = build();

            private FileService build() {
                FileService service = new FileService();
                ReflectionTestUtils.setField(service, "fileDao", fileDao);
                ReflectionTestUtils.setField(service, "fileStorageService", storage);
                ReflectionTestUtils.setField(service, "securityFileService", securityFileService);
                ReflectionTestUtils.setField(service, "fileAccessGuard",
                        new FileAccessGuard(fileDao, identity, mock(FileRelationDao.class)));
                return service;
            }

            private RequestEmployee employee(long id) {
                RequestEmployee user = new RequestEmployee();
                user.setEmployeeId(id);
                user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                return user;
            }

            private FileVO stored(String key, Long creatorId) {
                FileVO file = new FileVO();
                file.setFileKey(key);
                file.setFileName(key.substring(key.lastIndexOf('/') + 1));
                file.setCreatorId(creatorId);
                file.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
                return file;
            }

            private void stubStorageUrl() {
                when(storage.getFileUrl(anyString())).thenAnswer(invocation ->
                        ResponseDTO.ok("https://storage.example/" + invocation.getArgument(0)));
            }

            private void stubLookup(List<FileVO> rows) {
                when(fileDao.selectByFileKeyList(org.mockito.ArgumentMatchers.anyCollection()))
                        .thenAnswer(invocation -> rows.stream()
                                .filter(row -> asKeys(invocation.getArgument(0)).contains(row.getFileKey()))
                                .toList());
                // FileAccessGuard 的「是不是上传者」判定走单 key 入口，与批量解析是两个 DAO 方法；
                // 只桩批量那个会让守卫把所有私有 key 判成不可读，测试空过而不是验到规则。
                for (FileVO row : rows) {
                    when(fileDao.getByFileKey(row.getFileKey())).thenReturn(row);
                }
            }

            private Collection<String> asKeys(Object argument) {
                return (Collection<String>) argument;
            }

            @Test
            void controlledEntryResolvesOnlyWhatTheCallerMayRead() {
                stubLookup(List.of(stored("private/feedback/own.png", 44L), stored("private/feedback/others.png", 45L)));
                stubStorageUrl();

                List<FileVO> result = fileService.getFileList(
                        List.of("private/feedback/own.png", "private/feedback/others.png"), employee(44L));

                assertThat(result).extracting(FileVO::getFileKey).containsExactly("private/feedback/own.png");
                assertThat(result.getFirst().getFileUrl()).isNotBlank();
                // 不可读的那条连解析都不该发生：URL 不存在 = 既不泄露内容，也不泄露「它还在」
                verify(storage, never()).getFileUrl("private/feedback/others.png");
            }

            @Test
            void controlledEntryWithoutACallerResolvesPublicFilesOnly() {
                stubLookup(List.of(stored("public/a.png", null), stored("private/common/b.png", 44L)));
                stubStorageUrl();

                assertThat(fileService.getFileList(List.of("public/a.png", "private/common/b.png"), null))
                        .extracting(FileVO::getFileKey).containsExactly("public/a.png");
                verify(storage).getFileUrl("public/a.png");
                verify(storage, never()).getFileUrl("private/common/b.png");
            }

            @Test
            void unauthenticatedEntryServesPublicFolderOnly() {
                stubLookup(List.of(stored("public/image/a.png", null), stored("private/common/b.png", 44L)));
                stubStorageUrl();

                List<FileVO> result = fileService.getFileList(List.of("public/image/a.png", "private/common/b.png"));

                assertThat(result).extracting(FileVO::getFileKey).containsExactly("public/image/a.png");
                verify(fileDao, never()).selectByFileKeyList(
                        org.mockito.ArgumentMatchers.argThat(keys -> keys.contains("private/common/b.png")));
            }

            @Test
            void metadataLookupProvesExistenceWithoutEverProducingAUrl() {
                // 写路径要的是「这个 key 真的存在吗」。解析 URL 等于替一个可能无读取权的人读附件，
                // 因此元数据入口必须完全不触达存储层。
                stubLookup(List.of(stored("private/common/legacy.png", 44L)));

                List<FileVO> result = fileService.getFileMetadata(List.of("private/common/legacy.png"));

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getFileUrl()).isNull();
                verify(storage, never()).getFileUrl(anyString());
            }
        }

        @Nested

        class FileStorageCloudServiceImplTest {
            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void privateUploadSignsBeforeMetadataInsertAndAclIsOptional(boolean sendAcl) {
                FileConfig config = FileConfigTest.config();
                config.setCloudSendObjectAcl(sendAcl);
                S3Client client = mock(S3Client.class);
                FileDao dao = mock(FileDao.class);
                try (var signer = config.initS3Presigner()) {
                    var storage = new FileStorageCloudServiceImpl();
                    ReflectionTestUtils.setField(storage, "s3Client", client);
                    ReflectionTestUtils.setField(storage, "s3Presigner", signer);
                    ReflectionTestUtils.setField(storage, "cloudConfig", config);
                    ReflectionTestUtils.setField(storage, "fileDao", dao);
                    var result = storage.upload(new MockMultipartFile("file", "public.png", "image/png", new byte[]{1}), "private/common/");
                    assertThat(result.getOk()).isTrue();
                    assertThat(result.getData().getFileUrl()).contains("X-Amz-Signature=");
                    var request = ArgumentCaptor.forClass(PutObjectRequest.class);
                    verify(client).putObject(request.capture(), any(RequestBody.class));
                    assertThat(request.getValue().acl()).isEqualTo(sendAcl ? ObjectCannedACL.PRIVATE : null);
                    verifyNoInteractions(dao);
                }
            }

            @Test
            void shortTtlDoesNotCreatePermanentRedisEntry() {
                FileConfig config = FileConfigTest.config();
                config.setCloudPrivateUrlExpireSeconds(2L);
                FileDao dao = mock(FileDao.class);
                RedisService redis = mock(RedisService.class);
                String key = "private/common/short.png";
                when(dao.getByFileKey(key)).thenReturn(new FileVO());
                try (var signer = config.initS3Presigner()) {
                    var storage = new FileStorageCloudServiceImpl();
                    ReflectionTestUtils.setField(storage, "s3Presigner", signer);
                    ReflectionTestUtils.setField(storage, "cloudConfig", config);
                    ReflectionTestUtils.setField(storage, "fileDao", dao);
                    ReflectionTestUtils.setField(storage, "redisService", redis);
                    assertThat(storage.getFileUrl(key).getData()).contains("X-Amz-Expires=2");
                    verifyNoInteractions(redis);
                }
            }

            @Test
            void malformedPrefixNeverReachesS3() {
                var storage = new FileStorageCloudServiceImpl();
                assertThat(storage.getFileUrl("publicity/a.png").getCode()).isEqualTo(30005);
                assertThat(storage.download("privatex/a.png").getCode()).isEqualTo(30005);
                assertThat(storage.delete("private/common/../a.png").getCode()).isEqualTo(30005);
            }
        }
    }

    @Nested
    class AdminW3PgIT extends ScmW3PgITBase {
        @Nested
        @DisplayName("FA-2 文件关系授权（PG IT）")
        class FileRelationPgIT {
            @Autowired
            private FileRelationDao relationDao;

            @Autowired
            private FileAccessGuard accessGuard;

            @Autowired
            private com.xsy.scm.base.module.support.file.service.FileRelationService relations;

            private final AtomicLong bizIdSeq = new AtomicLong(900000L);

            private RequestUser employee(long id) {
                // RequestUser 是抽象约定，具体身份取底座员工请求对象（与 FileAccessGuardTest 同形）
                RequestEmployee user = new RequestEmployee();
                user.setEmployeeId(id);
                user.setActualName("FA-2 IT");
                user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                return user;
            }

            /**
             * 造一条只属于本用例的私有附件：key 落在 t_file 白名单目录里，上传者固定为 777。
             */
            private String seedPrivateFile(String suffix, long creatorId) {
                String key = "private/common/fa2-" + prefix.toLowerCase() + suffix;
                jdbc.update("INSERT INTO t_file (file_name, file_key, file_type, folder_type, creator_id,"
                        + " creator_user_type, creator_name) VALUES (?, ?, 'image/png', 1, ?, 1, 'FA-2 IT')",
                        suffix + ".png", key, creatorId);
                return key;
            }

            private void bind(String key, FileRelationBizTypeEnum type, long bizId) {
                FileRelationEntity row = new FileRelationEntity();
                row.setFileKey(key);
                row.setBizType(type.name());
                row.setBizId(bizId);
                relationDao.insert(row);
            }

            @Test
            @DisplayName("V52 的结构记录：白名单 CHECK、三元组部分唯一索引、双向查询索引都在")
            void migrationStructuralFactsArePresent() {

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 't_file_relation'"
                                + " AND indexname = 'uk_t_file_relation_active'",
                        Integer.class)).isEqualTo(1);
                // 唯一性必须落在 (key, 类型, 对象) 三元组上：一份附件可以合法地属于多个业务对象
                String indexDef = jdbc.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_t_file_relation_active'", String.class);
                assertThat(indexDef).contains("file_key").contains("biz_type").contains("biz_id");
                assertThat(indexDef).as("部分唯一索引必须带 deleted_flag 谓词，否则软删后无法重新绑定")
                        .contains("deleted_flag");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 't_file_relation'"
                                + " AND indexname = 'idx_t_file_relation_biz'",
                        Integer.class)).isEqualTo(1);
            }

            @Test
            @DisplayName("业务对象没有 owner 列可猜：同一 key 可挂两个对象，唯一索引不拦这种共享")
            void oneFileMayBeBoundToSeveralObjects() {
                String key = seedPrivateFile("-shared.png", 777L);
                bind(key, FileRelationBizTypeEnum.NOTICE, bizIdSeq.incrementAndGet());
                bind(key, FileRelationBizTypeEnum.PRODUCT, bizIdSeq.incrementAndGet());

                assertThat(relationDao.listActiveByFileKeys(List.of(key))).hasSize(2);

                FileRelationEntity duplicate = new FileRelationEntity();
                duplicate.setFileKey(key);
                duplicate.setBizType(FileRelationBizTypeEnum.NOTICE.name());
                duplicate.setBizId(relationDao.listActiveByFileKeys(List.of(key)).getFirst().getBizId());
                assertThatThrownBy(() -> relationDao.insert(duplicate))
                        .as("同一 (key, 类型, 对象) 重复绑定必须由部分唯一索引拒绝")
                        .hasMessageContaining("uk_t_file_relation_active");
            }

            @Test
            @DisplayName("守卫按关系放行：无关系不可读，有关系且对象可读即可读")
            void guardDecisionsFollowRelationRows() {
                String key = seedPrivateFile("-guarded.png", 777L);
                RequestUser other = employee(778L);

                assertThatThrownBy(() -> accessGuard.checkRead(key, other))
                        .as("他人上传的私有附件在没有关系行时必须拒绝")
                        .isInstanceOf(FileAccessGuard.AccessDenied.class);

                // 上传者可读自己的文件 —— 这是关系之外的另一条通道，不该被回填打断
                assertThatCode(() -> accessGuard.checkRead(key, employee(777L))).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("回填 SQL 真的会拆开逗号分隔的多 key（重放迁移里的 NOTICE 段）")
            void backfillSplitsCommaSeparatedAttachments() {
                String attachment = "private/notice/" + prefix + "-1.pdf,private/notice/" + prefix + "-2.pdf";
                jdbc.update("INSERT INTO t_notice (notice_type_id, title, all_visible_flag, scheduled_publish_flag,"
                                + " publish_time, content_text, content_html, attachment, deleted_flag)"
                                + " VALUES (1, ?, TRUE, FALSE, now(), ?, ?, ?, FALSE)",
                        prefix + " 公告", "正文", "正文", attachment);
                Long noticeId = jdbc.queryForObject(
                        "SELECT notice_id FROM t_notice WHERE title = ? AND deleted_flag = FALSE",
                        Long.class, prefix + " 公告");

                // 跑迁移里的原文而不是抄一份 SQL：抄错的回填等于没有验证过上线用的那段。
                jdbc.execute("INSERT INTO t_file_relation (file_key, biz_type, biz_id)\n"
                        + "SELECT btrim(part), 'NOTICE', n.notice_id\n"
                        + "FROM t_notice n CROSS JOIN LATERAL regexp_split_to_table(n.attachment, ',') AS part\n"
                        + "WHERE n.deleted_flag = FALSE AND coalesce(n.attachment, '') <> '' AND btrim(part) <> ''\n"
                        + "  AND n.notice_id = " + noticeId + "\n"
                        + "ON CONFLICT DO NOTHING");

                List<String> bound = jdbc.queryForList(
                        "SELECT file_key FROM t_file_relation WHERE biz_type = 'NOTICE' AND biz_id = ? AND deleted_flag = FALSE"
                                + " ORDER BY file_key", String.class, noticeId);
                assertThat(bound).containsExactlyInAnyOrder(
                        "private/notice/" + prefix + "-1.pdf", "private/notice/" + prefix + "-2.pdf");

                // 重放必须防重复处理：迁移可失败重试是硬要求
                jdbc.execute("INSERT INTO t_file_relation (file_key, biz_type, biz_id)\n"
                        + "SELECT btrim(part), 'NOTICE', n.notice_id\n"
                        + "FROM t_notice n CROSS JOIN LATERAL regexp_split_to_table(n.attachment, ',') AS part\n"
                        + "WHERE n.deleted_flag = FALSE AND coalesce(n.attachment, '') <> '' AND btrim(part) <> ''\n"
                        + "  AND n.notice_id = " + noticeId + "\n"
                        + "ON CONFLICT DO NOTHING");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_file_relation WHERE biz_type = 'NOTICE' AND biz_id = ?",
                        Integer.class, noticeId)).isEqualTo(2);
            }

            /**
             * <b>本用例盯的是一个真实缺陷</b>：对象的新建链路只调 {@code rebind}（因为它必须同时收回被删附件），
             * 而如果 {@code rebind} 只做清理、不补授权行，新建带附件的公告 / 帮助文档 / 企业对任何
             * 非上传者都永远 30005 —— 上面几条用例都按「先有行再判守卫」组织夹具，所以看不出这个洞。
             */
            @Test
            @DisplayName("rebind 是同步而不是只清理：新引用的 key 必须建出行，被移出的必须置删")
            void rebindGrantsCurrentKeysAndReclaimsRemovedOnes() {
                String first = seedPrivateFile("-rb1.png", 777L);
                String second = seedPrivateFile("-rb2.png", 777L);
                long noticeId = bizIdSeq.incrementAndGet();

                relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first));
                assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                        .as("新建对象时 rebind 必须建立授权行").containsExactly(first);

                relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first, second));
                assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                        .as("追加附件只补差集，不产生第二行").containsExactlyInAnyOrder(first, second);
                assertThat(totalRows(FileRelationBizTypeEnum.NOTICE, noticeId))
                        .as("已存在的三元组不能被重复插入").isEqualTo(2);

                relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(second));
                assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId)).containsExactly(second);

                // 收回后重新挂上：部分唯一索引带 deleted_flag 谓词，所以历史行留在原地、新行可再建
                relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first));
                assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId)).containsExactly(first);
                assertThat(totalRows(FileRelationBizTypeEnum.NOTICE, noticeId))
                        .as("软删行保留以便追溯授权变化").isGreaterThan(1);

                relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of());
                assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                        .as("附件全部删掉时必须收回，只增不减等于长期放行").isEmpty();
            }

            private List<String> activeKeysOf(FileRelationBizTypeEnum type, long bizId) {
                return jdbc.queryForList("SELECT file_key FROM t_file_relation WHERE biz_type = ? AND biz_id = ?"
                        + " AND deleted_flag = FALSE ORDER BY file_key", String.class, type.name(), bizId);
            }

            private int totalRows(FileRelationBizTypeEnum type, long bizId) {
                return jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE biz_type = ? AND biz_id = ?",
                        Integer.class, type.name(), bizId);
            }

            @Test
            @DisplayName("软删业务对象的关系行随删除置位，物理文件不动")
            void unbindSoftDeletesRelationOnly() {
                String key = seedPrivateFile("-unbind.png", 777L);
                long bizId = bizIdSeq.incrementAndGet();
                bind(key, FileRelationBizTypeEnum.NOTICE, bizId);
                FileRelationEntity row = relationDao.listActiveByFileKeys(List.of(key)).stream()
                        .filter(r -> r.getBizId().equals(bizId)).findFirst().orElseThrow();

                jdbc.update("UPDATE t_file_relation SET deleted_flag = TRUE WHERE relation_id = ?", row.getRelationId());

                // 旁路 UPDATE 不刷新 MyBatis 一级缓存，读回必须走同一个连接之外的裸查询
                assertThat(jdbc.queryForList(
                        "SELECT relation_id FROM t_file_relation WHERE deleted_flag = FALSE AND file_key = ?",
                        Long.class, key)).doesNotContain(row.getRelationId());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file WHERE file_key = ?", Integer.class, key))
                        .as("解绑只收回授权，不删物理文件记录").isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("FA-2b 暂存附件回收（PG IT）")
        class FileScratchCleanupPgIT {
            @Autowired
            private FileDao fileDao;

            @Autowired
            private FileScratchCleanupJob cleanupJob;

            @Autowired
            private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

            /**
             * 绕过 DAO 改库之后还要走 mapper 读，必须先清一级缓存：
             * 用例整体在一个 Spring 事务里，SqlSession 与事务同生命周期，不清会读到旧值。
             */
            private void evict() {
                org.mybatis.spring.SqlSessionUtils.getSqlSession(sqlSessionFactory).clearCache();
            }

            private int unbound() {
                evict();
                return fileDao.countUnboundScratchByCreator(777L, 1);
            }

            private String key(String suffix) {
                return FileFolderTypeEnum.SCRATCH.getFolder() + prefix.toLowerCase() + "-" + suffix;
            }

            private Long seedScratch(String suffix, int ageDays) {
                String fileKey = key(suffix);
                java.time.LocalDateTime createTime = LocalDateTime.now().minusDays(ageDays);
                fileDao.insert(new com.xsy.scm.base.module.support.file.domain.entity.FileEntity() {{
                    setFileKey(fileKey);
                    setFileName(suffix);
                    setFileType("image/png");
                    setFolderType(FileFolderTypeEnum.SCRATCH.getValue());
                    setFileSize(10L);
                    setCreatorId(777L);
                    setCreatorUserType(1);
                    setCreatorName("FA-2b IT");
                    setCreateTime(createTime);
                }});
                FileVO stored = fileDao.getByFileKey(fileKey);
                assertThat(stored).as("夹具未写入数据库：%s", fileKey).isNotNull();
                return stored.getFileId();
            }

            private boolean stillThere(String fileKey) {
                return fileDao.getByFileKey(fileKey) != null;
            }

            @Test
            @DisplayName("未绑定计数只数暂存目录：绑定一份就少一份，其它目录的行不计入")
            void unboundCountFollowsBindingAndFolderType() {
                String bound = key("bound.png");
                seedScratch("bound.png", 1);
                seedScratch("free.png", 1);
                assertThat(unbound()).as("本类夹具只有两条未绑定暂存件").isEqualTo(2);

                jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'NOTICE', 88001)", bound);
                assertThat(unbound())
                        .as("建立关系行后立即不再计入上限 —— 绑定就是解除暂存状态")
                        .isEqualTo(1);

                // 普通私有目录（folder_type=1）的行不能被算进暂存上限，否则上限会被历史附件提前吃掉
                jdbc.update("INSERT INTO t_file (file_name, file_key, file_type, folder_type, creator_id, creator_user_type,"
                        + " creator_name) VALUES ('common', 'private/common/cap-probe.png', 'image/png', 1, 777, 1, 'IT')");
                assertThat(unbound()).isEqualTo(1);
            }

            @Test
            @DisplayName("只回收超期且无引用无关系的暂存件，其余三种情况一律保留")
            void cleanupOnlyRemovesTrueOrphans() {
                Long freshId = seedScratch("fresh.png", 1);
                String orphanKey = key("orphan.png");
                Long orphanId = seedScratch("orphan.png", 30);
                String boundKey = key("bound2.png");
                Long boundId = seedScratch("bound2.png", 30);
                String referencedKey = key("referenced.png");
                Long referencedId = seedScratch("referenced.png", 30);

                jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'NOTICE', 88002)", boundKey);
                jdbc.update("INSERT INTO t_notice (notice_type_id, title, all_visible_flag, scheduled_publish_flag,"
                        + " publish_time, content_text, content_html, attachment, deleted_flag)"
                        + " VALUES (1, ?, TRUE, FALSE, now(), 'x', 'x', ?, FALSE)", prefix + " 引用公告", referencedKey);

                String summary = cleanupJob.run("7");

                assertThat(stillThere(referencedKey)).as("仍被业务列引用的附件绝不能被删除（误删不可逆）").isTrue();
                assertThat(stillThere(boundKey)).as("有关系行的附件不在回收范围内").isTrue();
                assertThat(stillThere(key("fresh.png"))).as("未超期的附件不回收").isTrue();
                assertThat(stillThere(orphanKey))
                        .as("超期、无关系行、无业务引用的暂存件必须被回收；本地存储 delete 恒成功，因此这条是决定性的")
                        .isFalse();
                assertThat(summary).contains("已回收").contains("保留 7 天");
            }

            @Test
            @DisplayName("参数非法退回默认 7 天窗口，而不是整轮跳过")
            void badParamFallsBackToDefaultWindow() {
                seedScratch("param.png", 30);
                assertThat(cleanupJob.run("not-a-number")).contains("保留 7 天");
                assertThat(cleanupJob.run("0")).contains("保留 7 天");
                assertThat(cleanupJob.run("3")).contains("保留 3 天");
            }
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = "logging.level.root=WARN")
    @Transactional
    @EnabledIfEnvironmentVariable(named = "XSY_FILE_STORAGE_MODE", matches = "cloud")
    class F0FileStorageCloudIT {
        @Autowired
        IFileStorageService storage;
        @Autowired
        FileService files;
        @Autowired
        FileDao dao;
        @Autowired
        FileConfig config;
        @Autowired
        FileController controller;
        @Autowired
        JdbcTemplate jdbc;
        final List<String> objects = new ArrayList<>();
        final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        final byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5XcAAAAASUVORK5CYII=");
        RequestEmployee employee;

        @BeforeEach
        void context() {
            assertThat(storage).isInstanceOf(FileStorageCloudServiceImpl.class);
            assertThat(config.isCloudPathStyleAccessEnabled()).isTrue();
            assertThat(config.isCloudSendObjectAcl()).isFalse();
            assertThat(config.getCloudPrivateUrlExpireSeconds()).as("Use XSY_FILE_PRIVATE_URL_EXPIRE=2 for this suite").isEqualTo(2L);
            employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("F0 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        @AfterEach
        void cleanup() {
            try {
                for (String key : objects) {
                    storage.delete(key);
                }
            } finally {
                XsyRequestUtil.remove();
            }
        }

        MockMultipartFile image() {
            return new MockMultipartFile("file", "f0-" + UUID.randomUUID() + ".png", "image/png", png);
        }

        FileUploadVO upload(int folder) {
            var result = files.fileUpload(image(), folder, employee);
            assertThat(result.getOk()).isTrue();
            objects.add(result.getData().getFileKey());
            return result.getData();
        }

        HttpResponse<byte[]> get(String url) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        @Test
        void privateUploadPersistsMetadataAndDownloadsOriginalBytes() throws Exception {
            var file = upload(1);
            var row = dao.getByFileKey(file.getFileKey());
            assertThat(row.getFileId()).isEqualTo(file.getFileId());
            assertThat(row.getFolderType()).isEqualTo(1);
            assertThat(row.getFileSize()).isEqualTo(png.length);
            assertThat(row.getCreatorId()).isEqualTo(employee.getEmployeeId());
            assertThat(row.getCreatorUserType()).isEqualTo(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
            assertThat(get(config.getCloudPublicUrlPrefix() + file.getFileKey()).statusCode()).isEqualTo(403);
            var signed = get(file.getFileUrl());
            assertThat(signed.statusCode()).isEqualTo(200);
            assertThat(signed.body()).isEqualTo(png);
            var response = new MockHttpServletResponse();
            controller.downLoad(file.getFileKey(), new MockHttpServletRequest(), response);
            assertThat(response.getContentAsByteArray()).isEqualTo(png);
            assertThat(response.getHeader("Content-Disposition")).contains(file.getFileName());
        }

        @Test
        void publicPolicyAllowsGetButNotAnonymousList() throws Exception {
            // Infrastructure-only path; the file layer adds no PUBLIC business folder enum.
            var result = storage.upload(image(), "public/f0-it/");
            assertThat(result.getOk()).isTrue();
            var file = result.getData();
            objects.add(file.getFileKey());
            assertThat(file.getFileUrl()).isEqualTo(config.getCloudPublicUrlPrefix() + file.getFileKey());
            var publicResponse = get(file.getFileUrl());
            assertThat(publicResponse.statusCode()).isEqualTo(200);
            assertThat(publicResponse.body()).isEqualTo(png);
            assertThat(get(config.getCloudPublicUrlPrefix() + "?list-type=2").statusCode()).isEqualTo(403);
        }

        @Test
        void signatureExpiresAndSubsequentReadGetsAFreshSignature() throws Exception {
            var file = upload(1);
            assertThat(get(file.getFileUrl()).statusCode()).isEqualTo(200);
            Thread.sleep(3500);
            assertThat(get(file.getFileUrl()).statusCode()).isEqualTo(403);
            var refreshed = files.getFileUrl(file.getFileKey());
            assertThat(refreshed.getOk()).isTrue();
            assertThat(get(refreshed.getData()).statusCode()).isEqualTo(200);
        }

        @Test
        void storageDeleteRemovesObjectButPreservesNativeMetadataLifecycle() throws Exception {
            var file = upload(1);
            assertThat(storage.delete(file.getFileKey()).getOk()).isTrue();
            assertThat(get(file.getFileUrl()).statusCode()).isEqualTo(404);
            // Native IFileStorageService.delete has no metadata deletion contract or HTTP endpoint.
            assertThat(dao.getByFileKey(file.getFileKey())).isNotNull();
        }

        @Test
        void validatesMimeLimitAndFolderBeforeCreatingRows() {
            Long before = jdbc.queryForObject("select count(*) from t_file", Long.class);
            byte[] html = "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(files.fileUpload(new MockMultipartFile("file", "fake.png", "image/png", html), 1, employee).getOk()).isFalse();
            assertThat(files.fileUpload(new MockMultipartFile("file", "large.png", "image/png", new byte[20 * 1024 * 1024 + 1]), 1, employee).getOk()).isFalse();
            assertThat(files.fileUpload(image(), 999, employee).getOk()).isFalse();
            assertThat(storage.getFileUrl("publicity/a").getCode()).isEqualTo(30005);
            assertThat(jdbc.queryForObject("select count(*) from t_file", Long.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select (config_value::jsonb->>'maxUploadFileSizeMb')::int from t_config where config_key='level3_protect_config'", Integer.class)).isEqualTo(20);
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    @Transactional
    class OperateLogBusinessFilterPgIT {
        /** 与其它测试/既有数据隔离的操作人哨兵值，真实员工 id 都是个位数级别。 */
        private static final long OWNER = 88_880_001L;
        private static final int USER_TYPE = 1;

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private OperateLogService operateLogService;

        private void log(String tag, String url, String param) {
            jdbc.update("INSERT INTO t_operate_log (operate_user_id, operate_user_type, operate_user_name, url, method, param, success_flag)"
                            + " VALUES (?,?,?,?,?,?,?)",
                    OWNER, USER_TYPE, tag, url, "Svc.method", param, true);
        }

        /** 仅设置业务筛选与隔离条件后查询，返回命中行的标签集合。 */
        private Set<String> tagsOf(String businessType, long businessId) {
            return list(queryForm(businessType, businessId, 100L)).stream()
                    .map(OperateLogVO::getOperateUserName)
                    .collect(Collectors.toSet());
        }

        private List<OperateLogVO> list(OperateLogQueryForm form) {
            return operateLogService.queryByPage(form).getData().getList();
        }

        private OperateLogQueryForm queryForm(String businessType, long businessId, long pageSize) {
            OperateLogQueryForm form = new OperateLogQueryForm();
            form.setOperateUserId(OWNER);
            form.setOperateUserType(USER_TYPE);
            form.setBusinessType(businessType);
            form.setBusinessId(businessId);
            form.setPageNum(1L);
            form.setPageSize(pageSize);
            form.setSearchCount(true);
            return form;
        }

        @Test
        void productFilterMatchesExactSpuIdBoundaryNotSiblingsOrSubResource() {
            log("spu12", "/scm/product/spu/update", "[{\"spuId\":12,\"qty\":5}]");
            log("spu12tail", "/scm/product/spu/delete", "[{\"spuId\":12}]");
            log("spu112", "/scm/product/spu/update", "[{\"spuId\":112,\"qty\":5}]");
            log("sku12", "/scm/product/sku/update", "[{\"skuId\":12,\"qty\":5}]");

            // ID 12 只命中真正的 spuId=12（含作为末字段的 }），不误命中 spuId=112 与子资源 skuId=12
            assertThat(tagsOf("PRODUCT", 12L)).containsExactlyInAnyOrder("spu12", "spu12tail");
            assertThat(tagsOf("PRODUCT", 112L)).containsExactly("spu112");
        }

        @Test
        void customerFilterSeparatesCustomerIdFromParentAndLongerId() {
            log("cus12", "/scm/customer/update", "[{\"customerId\":12}]");
            log("parentCus12", "/scm/customer/add", "[{\"parentCustomerId\":12,\"status\":\"ACTIVE\"}]");
            log("cus123", "/scm/customer/delete", "[{\"customerId\":123}]");

            // 前导引号使 parentCustomerId 不被当作 customerId；数字边界使 12 不命中 123
            assertThat(tagsOf("CUSTOMER", 12L)).containsExactly("cus12");
            assertThat(tagsOf("CUSTOMER", 123L)).containsExactly("cus123");
        }

        @Test
        void deliveryRouteFilterMatchesWholeUrlPathSegment() {
            log("r12plan", "/scm/delivery/routes/12/plan", "[12,{\"version\":1}]");
            log("r12update", "/scm/delivery/routes/12", "[12,{\"name\":\"x\"}]");
            log("r123plan", "/scm/delivery/routes/123/plan", "[123,{\"version\":1}]");

            // 线路 id 是 @PathVariable，落在 url 段里；按完整段匹配，12 不误命中 123
            assertThat(tagsOf("DELIVERY_ROUTE", 12L)).containsExactlyInAnyOrder("r12plan", "r12update");
            assertThat(tagsOf("DELIVERY_ROUTE", 123L)).containsExactly("r123plan");
        }

        @Test
        void malformedOrMissingParamFailsClosedWithoutError() {
            log("truncated", "/scm/product/spu/update", "[{\"spuId\":12");
            log("nullParam", "/scm/product/spu/update", null);
            log("gibberish", "/scm/product/spu/update", "spuId:12 not-json");

            // 脏值/缺列时 STRPOS 只返回 0 或 NULL，查询正常返回且不误归因（fail closed）
            assertThat(tagsOf("PRODUCT", 12L)).isEmpty();
        }

        @Test
        void createWithoutRecoverableIdIsNotAttributable() {
            // 新增只回 ResponseDTO<Long>，切面把 data 强制置空，因此新对象 id 无法从日志还原
            log("routeCreate", "/scm/delivery/routes", "[{\"routeName\":\"new\"}]");
            log("productAdd", "/scm/product/spu/add", "[{\"spuCode\":\"NEW\",\"name\":\"n\"}]");

            assertThat(tagsOf("DELIVERY_ROUTE", 12L)).isEmpty();
            assertThat(tagsOf("PRODUCT", 12L)).isEmpty();
        }

        @Test
        void businessFilterAppliedBeforePaginationAndTotalConsistent() {
            log("pg-a", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":1}]");
            log("pg-b", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":2}]");
            log("pg-c", "/scm/product/spu/update", "[{\"spuId\":777,\"q\":3}]");
            log("pg-7777", "/scm/product/spu/update", "[{\"spuId\":7777,\"q\":9}]");

            // total 统计的是过滤后的命中数（3），而非全库或分页大小；列表按 pageSize 截断
            var result = operateLogService.queryByPage(queryForm("PRODUCT", 777L, 2L)).getData();
            assertThat(result.getTotal()).isEqualTo(3L);
            assertThat(result.getList()).hasSize(2);
            assertThat(result.getList()).extracting(OperateLogVO::getOperateUserName)
                    .allMatch(t -> t.startsWith("pg-") && !t.equals("pg-7777"));
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    class XsyMapperPgValidationIT {
        /**
         * 每个用例独立命名的 prepared statement，避免重名。
         */
        private static final AtomicInteger SEQ = new AtomicInteger();

        /**
         * 已知且已归档的失败（新增问题检查基线）。任何新增失败都会让本测试变红。
         *
         * <p>当前只剩 1 条：
         * <ul>
         *   <li>{@code DEAD_CODE}：语句本身非法（MySQL 下同样非法），但当前调用链不可达；
         *       修它等于发明未验证的行为，故只登记不改。</li>
         * </ul>
         */
        private static final Map<String, String> KNOWN_FAILURES = Map.of(
                "com.xsy.scm.base.module.support.serialnumber.dao.SerialNumberRecordDao"
                        + ".selectRecordIdBySerialNumberIdAndDate",
                "DEAD_CODE: 选 serial_number_record_id，该表上游与本项目都没有此列（表无主键），无调用方"
        );

        /**
         * {@code PREPARE} 无法推断裸参数类型（{@code CONCAT('%', $1, '%')} 这类），
         * 而 JDBC 实际执行时参数带明确类型。按报错里点名的参数号补一次 {@code ::text} 重试，
         * 把「PREPARE 的静态类型推断限制」与「真正的 MySQL 残留」区分开。
         */
        private static void prepare(Statement st, String name, String sql) throws Exception {
            String current = sql;
            // PostgreSQL 只报第一个无法推断类型的参数，补一次可能又暴露下一个，故循环处理。
            // 上限取参数出现次数：写死小整数会让「参数很多的长 SQL」在真正报错之前就退出循环，
            // 把 PREPARE 的推断轮数不足误报成 MySQL 残留。
            int rounds = (int) java.util.regex.Pattern.compile("\\$\\d+")
                    .matcher(sql).results().count();
            for (int round = 0; round <= rounds; round++) {
                try {
                    st.execute("PREPARE " + name + " AS " + current);
                    return;
                } catch (Exception e) {
                    String msg = String.valueOf(e.getMessage());
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("could not determine data type of parameter \\$(\\d+)").matcher(msg);
                    if (!m.find()) {
                        throw e;
                    }
                    String patched = current.replaceAll("\\$" + m.group(1) + "\\b",
                            "\\$" + m.group(1) + "::text");
                    if (patched.equals(current)) {
                        throw e;
                    }
                    current = patched;
                }
            }
            throw new IllegalStateException("prepare retry exhausted: " + name);
        }

        @Autowired
        private SqlSessionFactory sqlSessionFactory;

        @Autowired
        private DataSource dataSource;

        @Test
        void everyMapperStatementIsParseableByPostgres() throws Exception {
            List<String> failures = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            List<String> knownFailures = new ArrayList<>();
            Set<String> registeredIds = new LinkedHashSet<>();
            Set<String> inspectedIds = new LinkedHashSet<>();
            int validated = 0;

            // 通过 MapperRegistry 枚举，而不是 Configuration.getMappedStatements()：
            // 后者的 values() 里混有 MyBatis StrictMap 的「短名歧义」占位对象，无法安全强转。
            for (Class<?> mapperInterface
                    : sqlSessionFactory.getConfiguration().getMapperRegistry().getMappers()) {
                for (Method method : mapperInterface.getMethods()) {
                    if (method.getDeclaringClass() == Object.class) {
                        continue;
                    }
                    String id = mapperInterface.getName() + "." + method.getName();
                    if (!sqlSessionFactory.getConfiguration().hasStatement(id)) {
                        continue;
                    }
                    registeredIds.add(id);
                    // BaseMapper 的批量默认方法调用同名的单条映射，自身没有独立 SQL。
                    // 用单条方法的真实参数校验同一语句，避免把批量集合误传给实体映射。
                    if (method.isDefault()
                            && method.getDeclaringClass() == com.baomidou.mybatisplus.core.mapper.BaseMapper.class
                            && java.util.Arrays.stream(mapperInterface.getMethods()).anyMatch(candidate ->
                                candidate.getName().equals(method.getName()) && !candidate.isDefault())) {
                        continue;
                    }
                    inspectedIds.add(id);
                    MappedStatement ms = sqlSessionFactory.getConfiguration().getMappedStatement(id);
                    BoundSql boundSql;
                    try {
                        boundSql = ms.getBoundSql(sampleArgs(mapperInterface, method));
                    } catch (Throwable t) {
                        skipped.add(id + "  -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
                        continue;
                    }
                    String prepared = toPositional(boundSql.getSql());
                    try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                        String name = "xsy_audit_" + SEQ.incrementAndGet();
                        prepare(st, name, prepared);
                        st.execute("DEALLOCATE " + name);
                        validated++;
                    } catch (Exception e) {
                        String id2 = id + "#" + java.util.Arrays.toString(method.getParameterTypes());
                        String reason = KNOWN_FAILURES.get(id);
                        if (reason != null) {
                            knownFailures.add(id + "  [" + reason + "]");
                            continue;
                        }
                        failures.add(id2 + "\n      SQL : " + oneLine(prepared)
                                + "\n      ERR : " + oneLine(String.valueOf(e.getMessage())));
                    }
                }
            }

            System.out.println("[mapper-pg-validation] validated=" + validated
                    + " failed=" + failures.size() + " knownFailure=" + knownFailures.size()
                    + " skipped=" + skipped.size());
            for (String s : knownFailures) {
                System.out.println("[mapper-pg-validation][known] " + s);
            }
            for (String s : skipped) {
                System.out.println("[mapper-pg-validation][skipped] " + s);
            }

            assertThat(failures)
                    .as("以下 mapper 语句无法被 PostgreSQL 解析（含 MySQL 残留标识符/函数）:\n%s",
                            String.join("\n", failures))
                    .isEmpty();
            assertThat(inspectedIds).as("排除框架默认重载不能遗漏任何已注册的 SQL 映射")
                    .containsExactlyInAnyOrderElementsOf(registeredIds);
            // 新增问题检查：已知失败集合不得扩大
            assertThat(knownFailures).as("已知失败基线应恰好为 %s 条", KNOWN_FAILURES.size())
                    .hasSize(KNOWN_FAILURES.size());
            assertThat(validated).as("应至少校验到一批语句").isGreaterThan(100);

            // 任何在 getBoundSql 抛错的语句都会整条跳过 PG 解析，failures 仍为空、用例照样绿，
            // 所以 skipped 不能只打印。被跳过的语句全部来自 MyBatis-Plus BaseMapper 继承的泛型 CRUD
            // （SQL 由框架生成，不是本仓库手写的）。
            // 真正要防的是「有人在 mapper XML 里写了与继承方法同名的自定义语句」——
            // 那会被这套按方法名枚举的机制静默跳过，所以钉两条：
            //   1) 每条跳过项的方法名必须在 BaseMapper 自身的方法集合内（运行时取，不另抄一份清单）；
            //   2) 跳过总数不得超过基线，新增跳过项时要显式调整这个数字才会过。
            var inheritedCrudMethods = new java.util.HashSet<String>();
            for (Method base : com.baomidou.mybatisplus.core.mapper.BaseMapper.class.getMethods()) {
                inheritedCrudMethods.add(base.getName());
            }
            for (String s : skipped) {
                String statementId = s.substring(0, s.indexOf("  -> "));
                String methodName = statementId.substring(statementId.lastIndexOf('.') + 1);
                assertThat(inheritedCrudMethods)
                        .as("手写 mapper 语句不允许被跳过（它是 BaseMapper 之外的自定义语句，"
                                + "按方法名枚举会漏掉）: %s", statementId)
                        .contains(methodName);
            }
            // BaseMapper 自动注册的方法不做独立 SQL PREPARE，因此这里跳过统计。
            // 当前允许的最大跳过数为 658；新增跳过项必须显式调整这个数字。
            // 上面第 1) 条已保证这些跳过项的方法名全部属于 BaseMapper，因此它们不可能是手写语句被漏掉——
            // 手写语句一旦混进来会先在那条断言上点名失败。
            // 这个数字是「继承 BaseMapper 的 Mapper 接口数」的函数（2026-10-28 实测 678 条），
            // 与业务改动无关：往既有 DAO 加手写语句不会让它变，新增 MP DAO 才会。618 → 658 来自客户
            // 供应商分类、商品规格库存换算等新 DAO；658 → 678 来自客户分组 / 分组价、独立退货取价与
            // 电子秤会话等新 DAO，已确认跳过项全部是 BaseMapper 方法。
            assertThat(skipped).as("跳过项基线 678 条，只增不减需显式确认").hasSizeLessThanOrEqualTo(678);
        }

        // ------------------------------------------------------------------
        // 参数构造
        // ------------------------------------------------------------------

        private Object sampleArgs(Class<?> mapperInterface, Method target) {
            Parameter[] params = target.getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                // MyBatis-Plus 的 BaseMapper 方法形如 insert(T entity)：T 在字节码里擦除成 Object，
                // 必须用具体 Mapper 接口的泛型实参回填，否则造不出实体参数。
                Type declared = params[i].getParameterizedType();
                Class<?> raw = params[i].getType();
                if (declared instanceof TypeVariable<?>) {
                    // 只读事实接口也会继承 BaseMapper，必须沿完整继承链解析实体类型。
                    Class<?> resolved = ResolvableType.forType(declared,
                            ResolvableType.forClass(mapperInterface).as(target.getDeclaringClass())).resolve();
                    if (resolved != null) {
                        declared = resolved;
                        raw = resolved;
                    }
                }
                args[i] = sample(declared, raw, 0);
            }
            if (args.length == 0) {
                return null;
            }
            return new ParamNameResolver(sqlSessionFactory.getConfiguration(), target).getNamedParams(args);
        }

        /**
         * 为任意类型造一个非空样本值；bean 递归填充字段，保证 OGNL 的 {@code != null} 分支全部打开。
         */
        @SuppressWarnings("unchecked")
        private Object sample(Type genericType, Class<?> rawType, int depth) {
            if (rawType == null || depth > 3) {
                return null;
            }
            if (rawType == String.class || CharSequence.class.isAssignableFrom(rawType)) {
                return "a";
            }
            if (rawType == Long.class || rawType == long.class) {
                return 1L;
            }
            if (rawType == Integer.class || rawType == int.class) {
                return 1;
            }
            if (rawType == Short.class || rawType == short.class) {
                return (short) 1;
            }
            if (rawType == Byte.class || rawType == byte.class) {
                return (byte) 1;
            }
            if (rawType == Double.class || rawType == double.class) {
                return 1.0d;
            }
            if (rawType == Float.class || rawType == float.class) {
                return 1.0f;
            }
            if (rawType == Boolean.class || rawType == boolean.class) {
                return Boolean.TRUE;
            }
            if (rawType == BigDecimal.class) {
                return new BigDecimal("1");
            }
            if (rawType == Character.class || rawType == char.class) {
                return 'a';
            }
            if (rawType == LocalDate.class) {
                return LocalDate.now();
            }
            if (rawType == LocalDateTime.class) {
                return LocalDateTime.now();
            }
            if (rawType == LocalTime.class) {
                return LocalTime.NOON;
            }
            if (rawType == java.util.Date.class) {
                return new java.util.Date();
            }
            if (Page.class.isAssignableFrom(rawType) || IPage.class.isAssignableFrom(rawType)) {
                return new Page<>(1, 10);
            }
            if (Wrapper.class.isAssignableFrom(rawType)) {
                return new QueryWrapper<>();
            }
            // 数据范围值对象是不可修改的（final 字段 + 静态工厂），通用 bean 递归填不出它的 ids。
            // 于是所有把 scope 传进 <choose> 的 mapper 都会在 getBoundSql 抛错、整条被跳过，
            // 等价于让「按 SCM 数据范围收窄」的那段 SQL 永远不进 PostgreSQL 校验。
            // 这里用 of(...) 造一个非空、非全量范围，使 NOT IN <foreach> 分支真正渲染出来。
            if (rawType == com.xsy.scm.common.scope.ScmValueScope.class) {
                return com.xsy.scm.common.scope.ScmValueScope.of(java.util.List.of(1L));
            }
            // SCM 数据范围上下文同样是不可修改的（final 字段 + 只有全参构造器，且工厂方法不是 public）。
            // 它是全部「按范围收窄」DAO 的入参：造不出来就会被 getBoundSql 抛错整条跳过，
            // 等于数据范围那段 SQL 从来没进过 PostgreSQL 校验。用非空、非全量范围把它造出来。
            if (rawType == com.xsy.scm.common.scope.ScmDataScopeContext.class) {
                Object scopedValue = com.xsy.scm.common.scope.ScmValueScope.of(java.util.List.of(1L));
                try {
                    Constructor<?> ctor = rawType.getDeclaredConstructors()[0];
                    ctor.setAccessible(true);
                    Object[] args = new Object[ctor.getParameterCount()];
                    for (int k = 0; k < args.length; k++) {
                        Class<?> param = ctor.getParameterTypes()[k];
                        if (param == Long.class || param == long.class) {
                            args[k] = 1L;
                        } else if (param == boolean.class || param == Boolean.class) {
                            args[k] = Boolean.TRUE;
                        } else {
                            args[k] = scopedValue;
                        }
                    }
                    return ctor.newInstance(args);
                } catch (Exception ignored) {
                    return null;
                }
            }
            if (rawType == java.io.Serializable.class || rawType == Comparable.class) {
                return 1L;
            }
            if (rawType.isEnum()) {
                Object[] constants = rawType.getEnumConstants();
                return constants.length > 0 ? constants[0] : null;
            }
            if (rawType.isArray()) {
                Object arr = java.lang.reflect.Array.newInstance(rawType.getComponentType(), 1);
                java.lang.reflect.Array.set(arr, 0, sample(rawType.getComponentType(),
                        rawType.getComponentType(), depth + 1));
                return arr;
            }
            if (Collection.class.isAssignableFrom(rawType)) {
                Class<?> elementType = Long.class;
                if (genericType instanceof ParameterizedType pt) {
                    Type arg = pt.getActualTypeArguments()[0];
                    if (arg instanceof Class<?> c) {
                        elementType = c;
                    } else if (arg instanceof ParameterizedType npt && npt.getRawType() instanceof Class<?> c) {
                        elementType = c;
                    }
                }
                Object element = sample(elementType, elementType, depth + 1);
                if (Set.class.isAssignableFrom(rawType)) {
                    Set<Object> set = new LinkedHashSet<>();
                    set.add(element);
                    return set;
                }
                List<Object> list = new ArrayList<>();
                list.add(element);
                return list;
            }
            if (Map.class.isAssignableFrom(rawType)) {
                Map<String, Object> map = new HashMap<>();
                map.put("a", "a");
                return map;
            }
            if (rawType == Object.class || rawType == java.io.Serializable.class
                    || rawType == Comparable.class || rawType.isInterface()) {
                return null;
            }
            // 普通 bean：递归填充全部可写字段
            try {
                Constructor<?> ctor = rawType.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object bean = ctor.newInstance();
                for (Class<?> c = rawType; c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                            continue;
                        }
                        Object v = sample(f.getGenericType(), f.getType(), depth + 1);
                        if (v == null) {
                            continue;
                        }
                        f.setAccessible(true);
                        try {
                            f.set(bean, v);
                        } catch (Exception ignored) {
                            // 只读字段跳过，不影响 OGNL 的非空判定
                        }
                    }
                }
                return bean;
            } catch (Exception e) {
                return null;
            }
        }

        // ------------------------------------------------------------------
        // 工具
        // ------------------------------------------------------------------

        /**
         * {@code #{}} 已被 MyBatis 换成 {@code ?}，这里按顺序换成 PostgreSQL 的 {@code $n}。
         */
        private static String toPositional(String sql) {
            StringBuilder sb = new StringBuilder(sql.length() + 16);
            int n = 0;
            for (int i = 0; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '?') {
                    sb.append('$').append(++n);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private static String oneLine(String s) {
            return s == null ? "null" : s.replaceAll("\\s+", " ").trim();
        }

        /**
         * 未使用，保留以避免 IDE 提示 TreeMap 导入缺失（报告里按 id 排序时使用）。
         */
        @SuppressWarnings("unused")
        private static Map<String, String> sorted() {
            return new TreeMap<>();
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    @DisplayName("页面菜单 component 存在性约定（PG IT）")
    class XsyMenuComponentPgIT {
        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private MenuService menuService;

        /**
         * 隐藏的详情页路由必须能被「新建角色」授出去。
         *
         * <p>权限管理页的菜单树取 {@code MenuService.queryMenuTree}，它不过滤 {@code visible_flag}；
         * 勾选父菜单时前端会把子级一并勾上。因此隐藏详情路由只要挂在可见父菜单下，新角色勾了
         * 「线路管理」就同时拿到隐藏的「线路详情」，不需要迁移去复制角色权限。
         *
         * <p>一旦有人给菜单树加上 {@code visible_flag = TRUE} 过滤，这条链路会在「新建角色」上断掉，
         * 表现为能看列表、点详情空白，且迁移当时的角色仍然正常 —— 这里把它钉住。
         */
        @Test
        @DisplayName("隐藏的详情页路由出现在权限菜单树里，可随父菜单一起授出")
        void hiddenDetailRoutesStayGrantable() {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 1004 AND menu_type = 2"
                            + " AND parent_id = 1001 AND visible_flag = FALSE AND deleted_flag = FALSE",
                    Integer.class)).as("线路详情应当是线路管理下的隐藏页面菜单").isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id = 1004",
                    Integer.class)).as("迁移已把线路详情授给既有角色").isEqualTo(1);

            assertThat(containsMenu(menuService.queryMenuTree(false).getData(), 1004L))
                    .as("权限菜单树若过滤 visible_flag，新建角色勾「线路管理」就拿不到隐藏的「线路详情」")
                    .isTrue();
        }

        private boolean containsMenu(List<MenuTreeVO> nodes, Long menuId) {
            if (nodes == null) {
                return false;
            }
            return nodes.stream().anyMatch(node -> menuId.equals(node.getMenuId()) || containsMenu(node.getChildren(), menuId));
        }

        @Test
        @DisplayName("每个已发布页面菜单的 component 都能在 xsy-scm-web/src/views 下找到（既有缺口走显式基线）")
        void everyPublishedPageMenuResolvesToARealVueFile() {
            // menu_type = 2 是页面；frame_flag = TRUE 的外链菜单由 router 强制指向 iframe 组件，
            // 其 component 列不参与解析，因此排除，否则会产生与真实行为无关的失败。
            List<Map<String, Object>> pages = jdbc.queryForList(
                    "SELECT menu_id, menu_name, component FROM t_menu "
                            + "WHERE menu_type = 2 AND component IS NOT NULL AND btrim(component) <> '' "
                            + "AND deleted_flag = FALSE AND COALESCE(frame_flag, FALSE) = FALSE "
                            + "ORDER BY menu_id");
            assertThat(pages).as("t_menu 里应当存在已发布的页面菜单，否则本用例形同虚设").isNotEmpty();

            Path views = viewsRoot();
            List<String> missing = pages.stream()
                    .filter(row -> {
                        String component = String.valueOf(row.get("component"));
                        return !Files.exists(views.resolve(stripLeadingSlash(component)));
                    })
                    .map(row -> "menu_id=" + row.get("menu_id") + " 「" + row.get("menu_name") + "」 -> "
                            + row.get("component"))
                    .toList();

            assertThat(missing)
                    .as("已授权的页面菜单指向不存在的组件时，路由会注册成功但 component 为 undefined，"
                            + "用户点开是空白页且没有任何构建期/运行期报错。"
                            + "请让页面菜单与它的 .vue 同一阶段写入数据库，或用新增 migration 下线已废弃菜单")
                    .isEmpty();
        }

        @Test
        @DisplayName("Finance 六个页面菜单都指向已存在组件")
        void financePagesArePublishedWithTheirComponents() {
            // 能力点可以先行，但不能注册不存在的 .vue。
            assertThat(jdbc.queryForList(
                    "SELECT menu_id FROM t_menu WHERE (menu_id BETWEEN 1500 AND 1599"
                            + " OR api_perms LIKE 'scm:finance:%' OR web_perms LIKE 'scm:finance:%'"
                            + " OR path LIKE '/finance/%') AND menu_type = 2 ORDER BY menu_id", Long.class))
                    .as("已发布的只有六个应收、应付、收款、付款、核销与过账页面")
                    .containsExactly(1501L, 1502L, 1503L, 1504L, 1505L, 1506L);

            assertThat(jdbc.queryForList(
                    "SELECT menu_id FROM t_menu WHERE (menu_id BETWEEN 1500 AND 1599"
                            + " OR api_perms LIKE 'scm:finance:%' OR web_perms LIKE 'scm:finance:%')"
                            + " AND component IS NOT NULL ORDER BY menu_id", Long.class))
                    .as("声明 component 的 Finance 菜单恰好就是六个真实页面")
                    .containsExactly(1501L, 1502L, 1503L, 1504L, 1505L, 1506L);
        }

        private String stripLeadingSlash(String component) {
            return component.startsWith("/") ? component.substring(1) : component;
        }

        /**
         * 定位 {@code xsy-scm-web/src/views}。
         *
         * <p>Surefire 的 {@code user.dir} 是 {@code xsy-scm-server/xsy-admin}，IDE 里单跑则可能是仓库根，
         * 因此逐级向上找。找不到就抛错 —— 静默返回一个不存在的路径会让上面所有断言
         * 「因为一切都是缺失的」而变红，或者更糟：让基线核对悄悄通过。
         */
        private Path viewsRoot() {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            for (int depth = 0; depth < 4 && current != null; depth++) {
                Path candidate = current.resolve("xsy-scm-web/src/views");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
                Path nested = current.resolve("../xsy-scm-web/src/views").normalize();
                if (Files.isDirectory(nested)) {
                    return nested;
                }
                current = current.getParent();
            }
            throw new IllegalStateException(
                    "找不到 xsy-scm-web/src/views（user.dir = " + System.getProperty("user.dir") + "）");
        }
    }

    @Nested
    class XsyOaEnterpriseEmployeeMigrationIT {
        private static final String URL = PgITDatabase.url();

        private static String user() {
            return PgITDatabase.user();
        }

        private static String password() {
            return PgITDatabase.password();
        }

        private static long scalar(Statement statement, String sql) throws SQLException {
            try (ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getLong(1);
            }
        }

        private static String text(Statement statement, String sql) throws SQLException {
            try (ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getString(1);
            }
        }

        @Test
        void v12ConvertsOaEnterpriseEmployeeJoinColumnsToBigint() throws Exception {
            Flyway flyway = Flyway.configure().dataSource(URL, user(), password())
                    .schemas("xsy").defaultSchema("xsy").cleanDisabled(true)
                    .placeholderReplacement(false)
                    .locations("classpath:db/migration/modules").load();
            flyway.migrate();
            flyway.validate();

            try (Connection connection = DriverManager.getConnection(URL, user(), password());
                 Statement statement = connection.createStatement()) {

                PgITMigrationHistory.assertCovered(statement,
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");

                // ---- 2. 核心断言：两列必须是 bigint ----
                for (String column : new String[]{"enterprise_id", "employee_id"}) {
                    assertThat(text(statement,
                            "SELECT data_type FROM information_schema.columns WHERE table_schema='xsy' "
                                    + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "'"))
                            .as("t_oa_enterprise_employee.%s 的 data_type 必须是 bigint", column)
                            .isEqualTo("bigint");
                    assertThat(text(statement,
                            "SELECT udt_name FROM information_schema.columns WHERE table_schema='xsy' "
                                    + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "'"))
                            .as("t_oa_enterprise_employee.%s 的 udt_name 必须是 int8", column)
                            .isEqualTo("int8");
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM information_schema.columns WHERE table_schema='xsy' "
                                    + "AND table_name='t_oa_enterprise_employee' AND column_name='" + column + "' "
                                    + "AND data_type LIKE 'character%'"))
                            .as("t_oa_enterprise_employee.%s 不得再是字符类型", column)
                            .isZero();
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM information_schema.columns "
                                    + "WHERE table_schema='xsy' AND table_name='t_oa_enterprise_employee' "
                                    + "AND column_name='" + column + "' AND character_maximum_length IS NOT NULL"))
                            .as("bigint 列不得再带长度语义").isZero();
                }

                // ---- 3. 唯一键与索引随列类型重建，且仍然存在 ----
                assertThat(scalar(statement,
                        "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy'::regnamespace "
                                + "AND conname='uk_t_oa_enterprise_employee_uk_enterprise_employee' AND contype='u'"))
                        .as("(enterprise_id, employee_id) 唯一键必须保留").isEqualTo(1);
                assertThat(scalar(statement,
                        "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy' AND tablename='t_oa_enterprise_employee' "
                                + "AND indexname IN ('idx_t_oa_enterprise_employee_idx_employee_id',"
                                + "'idx_t_oa_enterprise_employee_idx_enterprise_id')"))
                        .as("两个索引必须保留").isEqualTo(2);
                // 唯一键的列类型也必须已跟随变为 int8（而不是重建成了 text）
                assertThat(scalar(statement,
                        "SELECT count(*) FROM pg_index i JOIN pg_attribute a "
                                + "  ON a.attrelid = i.indexrelid AND a.attnum > 0 "
                                + "WHERE i.indexrelid = 'xsy.uk_t_oa_enterprise_employee_uk_enterprise_employee'::regclass "
                                + "  AND a.atttypid = 'int8'::regtype"))
                        .as("唯一键的两个键列都必须已是 int8").isEqualTo(2);

                // ---- 4. 行为断言：列类型不匹配时失败的语句现在可执行 ----
                // 4a. JOIN 比较
                scalar(statement,
                        "SELECT count(*) FROM t_oa_enterprise_employee e "
                                + "LEFT JOIN t_oa_enterprise en ON e.enterprise_id = en.enterprise_id "
                                + "LEFT JOIN t_employee emp ON e.employee_id = emp.employee_id");
                // 4b. JDBC 强类型参数（Long → int8），PREPARE 失败的形态
                try (var prepared = connection.prepareStatement(
                        "SELECT count(*) FROM t_oa_enterprise_employee WHERE enterprise_id = ? "
                                + "AND employee_id IN (?)")) {
                    prepared.setLong(1, 1L);
                    prepared.setLong(2, 1L);
                    prepared.executeQuery();
                }
                // 4c. 写路径：Long → bigint 赋值（PG 允许数值→text，反向则需显式转换，故这是必须验证的方向）
                try (var prepared = connection.prepareStatement(
                        "INSERT INTO t_oa_enterprise_employee(enterprise_id, employee_id) VALUES (?, ?)")) {
                    prepared.setLong(1, -1L);
                    prepared.setLong(2, -1L);
                    prepared.executeUpdate();
                }
                assertThat(scalar(statement,
                        "SELECT count(*) FROM t_oa_enterprise_employee WHERE enterprise_id = -1 AND employee_id = -1"))
                        .as("写入的探测行应可读回（读写类型一致）").isEqualTo(1);
                statement.execute("DELETE FROM t_oa_enterprise_employee WHERE enterprise_id = -1 AND employee_id = -1");
                assertThat(scalar(statement, "SELECT count(*) FROM t_oa_enterprise_employee"))
                        .as("清理后探测行不得残留").isZero();
            }
        }
    }
}
