package com.xianshuyuan.scm.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class IdempotencyRequestHasher {
    private final ObjectMapper objectMapper;

    public IdempotencyRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String hash(Object request) {
        try {
            JsonNode tree = objectMapper.valueToTree(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(tree).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("无法计算幂等请求哈希", exception);
        }
    }

    private String canonical(JsonNode node) throws JsonProcessingException {
        if (node.isObject()) {
            var sorted = objectMapper.createObjectNode();
            node.properties().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> sorted.set(e.getKey(), canonicalNode(e.getValue())));
            return objectMapper.writeValueAsString(sorted);
        }
        return objectMapper.writeValueAsString(canonicalNode(node));
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            var sorted = objectMapper.createObjectNode();
            node.properties().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> sorted.set(e.getKey(), canonicalNode(e.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(v -> result.add(canonicalNode(v)));
            return result;
        }
        if (node.isNumber()) return objectMapper.getNodeFactory().numberNode(node.decimalValue().stripTrailingZeros());
        if (node.isTextual() && node.textValue().matches("-?\\d+(\\.\\d+)?")) {
            try {
                return objectMapper.getNodeFactory().textNode(new java.math.BigDecimal(node.textValue()).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException ignored) {
            }
        }
        return node;
    }
}
