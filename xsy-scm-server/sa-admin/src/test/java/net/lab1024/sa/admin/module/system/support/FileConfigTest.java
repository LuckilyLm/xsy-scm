package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.base.config.FileConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

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

    @Test void upstreamDefaultsRemainUnchanged() {
        assertThat(new FileConfig().isCloudPathStyleAccessEnabled()).isFalse();
        assertThat(new FileConfig().isCloudSendObjectAcl()).isTrue();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void presignerHonorsPathStyle(boolean pathStyle) {
        FileConfig config = config(); config.setCloudPathStyleAccessEnabled(pathStyle);
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

    @Test void rejectsMissingPublicPrefixAndUnsafeTtl() {
        FileConfig config = config(); config.setCloudPublicUrlPrefix("");
        assertThatThrownBy(config::initS3Client).isInstanceOf(IllegalStateException.class);
        config.setCloudPublicUrlPrefix("http://storage.example.test/xsy-scm-dev/");
        config.setCloudPrivateUrlExpireSeconds(0L);
        assertThatThrownBy(config::initS3Presigner).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"pre", "prod"})
    void rejectsLocalSampleCredentialsInProduction(String profile) {
        FileConfig config = config(); config.setActiveProfiles(profile); config.setCloudAccessKey("xsy_f0_local");
        assertThatThrownBy(config::initS3Client).isInstanceOf(IllegalStateException.class);
    }
}
