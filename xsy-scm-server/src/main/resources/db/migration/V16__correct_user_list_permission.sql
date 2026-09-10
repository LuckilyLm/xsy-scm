-- Rename the V15 permission in place so existing role grants retain their permission ID.
UPDATE sys_permission
SET code       = 'system:user:list',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'system:user:read'
  AND deleted = FALSE;
