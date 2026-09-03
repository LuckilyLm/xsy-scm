package com.xianshuyuan.scm.order.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class IdempotencyRequestHasherTest {
 @Test void normalizesEquivalentDecimalStrings(){var h=new IdempotencyRequestHasher(new ObjectMapper());assertThat(h.hash(Map.of("quantity","1.0"))).isEqualTo(h.hash(Map.of("quantity","1.0000")));}
}
