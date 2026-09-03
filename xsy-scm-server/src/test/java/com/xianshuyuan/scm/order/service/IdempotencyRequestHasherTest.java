package com.xianshuyuan.scm.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyRequestHasherTest {

    private final IdempotencyRequestHasher hasher = new IdempotencyRequestHasher(new ObjectMapper());

    @Test
    void hashesEquivalentObjectsDeterministicallyRegardlessOfFieldOrder() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode first = mapper.createObjectNode().put("quantity", 2).put("skuId", 10);
        ObjectNode second = mapper.createObjectNode().put("skuId", 10).put("quantity", 2);

        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second)).hasSize(64);
    }

    @Test
    void preservesArrayOrderWhenNormalizing() {
        assertThat(hasher.hash(new int[] {1, 2})).isNotEqualTo(hasher.hash(new int[] {2, 1}));
    }

    @Test
    void guardRecognizesReplayAndRejectsKeyReuseWithDifferentRequest() {
        String original = hasher.hash(new Request("A", 1));

        assertThat(IdempotencyGuard.matches(original, hasher.hash(new Request("A", 1)))).isTrue();
        assertThatThrownBy(() -> IdempotencyGuard.requireMatching(original, hasher.hash(new Request("A", 2))))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("幂等键");
    }

    private record Request(String code, int quantity) { }
}
