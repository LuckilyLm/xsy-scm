package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.config.FileConfig;
import net.lab1024.sa.base.module.support.file.controller.FileController;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileUploadVO;
import net.lab1024.sa.base.module.support.file.service.FileService;
import net.lab1024.sa.base.module.support.file.service.FileStorageCloudServiceImpl;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Real MinIO + PostgreSQL + Redis. Set cloud ENV and XSY_FILE_PRIVATE_URL_EXPIRE=2.
 * No mocked storage and no new Maven/resources profile. Data rows roll back; objects are removed.
 */
@SpringBootTest(classes = AdminApplication.class, properties = "logging.level.root=WARN")
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
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void cleanup() {
        try {
            for (String key : objects) {
                storage.delete(key);
            }
        } finally {
            SmartRequestUtil.remove();
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
        // Infrastructure-only path; F0 intentionally adds no PUBLIC business folder enum.
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
