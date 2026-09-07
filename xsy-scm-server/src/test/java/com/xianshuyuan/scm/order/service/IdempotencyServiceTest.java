package com.xianshuyuan.scm.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import com.xianshuyuan.scm.order.mapper.IdempotencyRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {
    @Test void normalizesKeyBeforeAtomicClaimAndReplaysLong() {
        var mapper=mock(IdempotencyRecordMapper.class);
        var row=new IdempotencyRecordEntity(); row.setRequestHash(new IdempotencyRequestHasher(new ObjectMapper()).hash(Map.of("a",1)));
        when(mapper.insertClaim(eq("S"),eq("key"),anyString())).thenReturn(0);
        when(mapper.selectActiveByScopeAndKeyForUpdate("S","key")).thenReturn(row);
        var service=new IdempotencyService(mapper,new ObjectMapper());
        var claim=service.claim("S","  key  ",Map.of("a",1));
        service.complete(claim,"LONG",42L,42L);
        row.setResultData(new ObjectMapper().valueToTree(Map.of("value",42L)));
        assertEquals(42L,service.replay(new IdempotencyService.Claim(row,true),Long.class));
    }

    @Test void voidResultCanBeCompletedAndReplayed() {
        var mapper=mock(IdempotencyRecordMapper.class); var row=new IdempotencyRecordEntity();
        var service=new IdempotencyService(mapper,new ObjectMapper());
        var claim=new IdempotencyService.Claim(row,false);
        assertDoesNotThrow(()->service.complete(claim,"VOID",0L,null));
        assertNull(service.replay(new IdempotencyService.Claim(row,true),Void.class));
    }

    @Test void rejectsKeyLongerThanDatabaseLimitBeforeClaiming() {
        var mapper=mock(IdempotencyRecordMapper.class);
        var service=new IdempotencyService(mapper,new ObjectMapper());

        var error=assertThrows(BusinessException.class,
                ()->service.claim("S","x".repeat(201),Map.of("a",1)));

        assertEquals(OrderErrorCodes.IDEMPOTENCY_KEY_INVALID,error.getErrorCode());
        verifyNoInteractions(mapper);
    }
}
