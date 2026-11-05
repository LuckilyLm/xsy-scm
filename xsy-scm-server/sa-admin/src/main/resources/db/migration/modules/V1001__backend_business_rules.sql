-- 业务规则由Java表单、领域服务与只追加Mapper执行；保留存储类型、非空、主键、唯一及索引。
-- 不改写任何业务行，不重置序列；已发布的模块初始化SQL保持冻结。
DO $$
DECLARE
    guard RECORD;
BEGIN
    FOR guard IN
        SELECT c.relname AS table_name, t.tgname AS guard_name
        FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
        WHERE c.relnamespace = 'xsy_v2'::regnamespace AND NOT t.tgisinternal
        ORDER BY c.relname, t.tgname
    LOOP
        EXECUTE format('DROP TRIGGER %I ON xsy_v2.%I', guard.guard_name, guard.table_name);
    END LOOP;

    FOR guard IN
        SELECT c.relname AS table_name, k.conname AS guard_name
        FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid
        WHERE k.connamespace = 'xsy_v2'::regnamespace AND k.contype = 'c'
        ORDER BY c.relname, k.conname
    LOOP
        EXECUTE format('ALTER TABLE xsy_v2.%I DROP CONSTRAINT %I', guard.table_name, guard.guard_name);
    END LOOP;

    -- 只移除已失去触发器引用的专用函数；不使用CASCADE误删其它对象。
    FOR guard IN
        SELECT p.proname AS function_name, pg_get_function_identity_arguments(p.oid) AS arguments
        FROM pg_proc p
        WHERE p.pronamespace = 'xsy_v2'::regnamespace AND p.prorettype = 'trigger'::regtype
        ORDER BY p.proname
    LOOP
        EXECUTE format('DROP FUNCTION xsy_v2.%I(%s)', guard.function_name, guard.arguments);
    END LOOP;
END $$;
