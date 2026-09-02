package com.xianshuyuan.scm.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void createsStandardSuccessEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response).isEqualTo(new ApiResponse<>(0, "success", "ok"));
    }
}
