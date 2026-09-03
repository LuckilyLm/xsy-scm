package com.xianshuyuan.scm.order.service;
import com.fasterxml.jackson.databind.*;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import com.xianshuyuan.scm.order.mapper.IdempotencyRecordMapper;
import org.springframework.stereotype.Service;
@Service public class IdempotencyService {
 private final IdempotencyRecordMapper mapper; private final ObjectMapper json; private final IdempotencyRequestHasher hasher;
 public IdempotencyService(IdempotencyRecordMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;this.hasher=new IdempotencyRequestHasher(json);}
 public Claim claim(String scope,String key,Object request){if(key==null||key.isBlank())throw new BusinessException(OrderErrorCodes.IDEMPOTENCY_KEY_REQUIRED);String normalized=key.trim(),hash=hasher.hash(request);if(mapper.insertClaim(scope,normalized,hash)==1)return new Claim(mapper.selectActiveByScopeAndKeyForUpdate(scope,normalized),false);var stored=mapper.selectActiveByScopeAndKeyForUpdate(scope,normalized);IdempotencyGuard.requireMatching(stored.getRequestHash(),hash);return new Claim(stored,stored.getResultData()!=null);}
 public void complete(Claim claim,String type,long resultId,Object result){var row=claim.record();row.setResultType(type);row.setResultId(resultId);row.setResultData(json.valueToTree(java.util.Collections.singletonMap("value",result)));row.setUpdatedBy("SYSTEM");mapper.updateById(row);}
 public <T>T replay(Claim claim,Class<T> type){try{var value=claim.record().getResultData().get("value");if(value==null||value.isNull())return null;return json.treeToValue(value,type);}catch(Exception e){throw new IllegalStateException("无法读取幂等结果",e);}}
 public record Claim(IdempotencyRecordEntity record,boolean replay){}
}
