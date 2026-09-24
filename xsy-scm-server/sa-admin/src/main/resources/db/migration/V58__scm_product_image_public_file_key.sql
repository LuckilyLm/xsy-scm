-- ============================================================================
-- FA-3 存量商品图搬运收口（P0 最后一条未闭合入口）
--
-- 裁决依据：docs/decisions.md「F0-DEBT-01」与
-- docs/plan/attachment-asset-grading-and-file-access-plan.md §5 —— 商品图是公开资产，
-- 历史上传默认值曾把它们落到私有前缀下；读侧只能按前缀 + 关系行放行，于是每张存量私有商品图
-- 都要在 Java 侧留一条「沿用原 key 时放行」的例外（ProductImageSyncManager#requirePublicImageKey）。
-- 本迁移把口径收成一个：product_image 的活行只能引用 public/ 前缀。
--
-- ⚠ 应用前置条件（不属于 SQL 能完成的事，顺序错了会让商品图静默 404）：
--   必须先在对象存储里把下列私有 key **复制**（不是移动）到同名尾段的 public/image/ 前缀下，
--   并确认对象可读，再应用本迁移。复制而非移动的理由：历史 key 可能已被外部引用，
--   且复制失败可以重跑，移动失败会把原图也一起丢掉。
--   下面把每一条被改写的 key 用 RAISE NOTICE 打出来，供运维与桶内容逐条对账。
--
-- 幂等：谓词是 file_key NOT LIKE 'public/%'，第二次执行影响 0 行、不产生任何写入。
-- 冲突即失败：两个旧 key 落成同一个新 key、或新 key 已被其它 t_file 行占用时，
-- 本迁移在改写前就 RAISE EXCEPTION 中止，绝不留下「库里指向新 key、对象却不存在」的中间态。
--
-- 本环境实测：xsy_scm_b0 与全部一次性 IT 库里「活行且非 public 前缀」的 product_image 行数均为 0
-- （见 docs/progress.md 的 FA-3 取证），因此这里的数据改写是一次有据可查的空操作；
-- 迁移的价值在于把它变成数据库层面的不变量，而不是继续靠 Java 侧的过渡例外。
-- ============================================================================

-- FA3 BEGIN 搬运（ScmProductImageKeyMigrationPgIT 按这段标记执行，改标记名要同步改测试）
DO
$$
    DECLARE
        moved bigint;
        bad_key bigint;
        manifest text;
    BEGIN
        SELECT count(*)
        INTO bad_key
        FROM product_image
        WHERE deleted = FALSE
          AND file_key NOT LIKE 'public/%'
          AND length(btrim(file_key)) = 0;
        IF bad_key > 0 THEN
                -- 空 key 无法机械搬运（不知道对象在哪），必须人工定性后再放行本迁移
                RAISE EXCEPTION 'FA-3 中止：product_image 有 % 条活行的 file_key 为空，无法推导公开前缀目标', bad_key;
        END IF;

        -- 先删再建：同一事务里重复执行本段时（测试按标记重放这段），
        -- ON COMMIT DROP 的临时表还没随事务结束，直接 CREATE 会撞「relation already exists」
        DROP TABLE IF EXISTS fa3_moved_key;
        CREATE TEMP TABLE fa3_moved_key ON COMMIT DROP AS
        SELECT legacy.file_key                                   AS old_key,
               'public/image/' || regexp_replace(legacy.file_key, '^[^/]*/[^/]*/', '') AS new_key
        FROM (SELECT DISTINCT file_key
              FROM product_image
              WHERE deleted = FALSE
                AND file_key NOT LIKE 'public/%') legacy;

        SELECT count(*)
        INTO bad_key
        FROM (SELECT new_key FROM fa3_moved_key GROUP BY new_key HAVING count(*) > 1) dup;
        IF bad_key > 0 THEN
                RAISE EXCEPTION 'FA-3 中止：% 个目标 key 由多条历史 key 折叠而来，改写会互相覆盖', bad_key;
        END IF;

        SELECT count(*)
        INTO bad_key
        FROM fa3_moved_key m
        JOIN t_file f ON f.file_key = m.new_key AND f.file_key <> m.old_key;
        IF bad_key > 0 THEN
                RAISE EXCEPTION 'FA-3 中止：% 个目标 key 已被其它文件占用', bad_key;
        END IF;

        SELECT count(*)
        INTO moved
        FROM fa3_moved_key;
        IF moved > 0 THEN
            SELECT string_agg(old_key || ' -> ' || new_key, E'\n' ORDER BY old_key)
            INTO manifest
            FROM fa3_moved_key;
            RAISE NOTICE 'FA-3 搬运清单（对象必须已在下列公开 key 下可读）：%', manifest;

            UPDATE t_file f
            SET file_key    = m.new_key,
                -- 搬运后这个 key 就是公开图片，folder_type 一起改（5 = FileFolderTypeEnum.PUBLIC_IMAGE），
                -- 避免同一行里「键说 public/image、类型说 private/common」被后续交叉校验判成脏数据
                folder_type = 5,
                update_time = CURRENT_TIMESTAMP
            FROM fa3_moved_key m
            WHERE f.file_key = m.old_key;

            UPDATE product_image pi
            SET file_key   = m.new_key,
                updated_at = CURRENT_TIMESTAMP
            FROM fa3_moved_key m
            WHERE pi.file_key = m.old_key
              AND pi.deleted = FALSE;

            -- 关系行只为「私有 key 的读取授权」而存在；搬到公开前缀后读判定不再查关系，
            -- 留着它们只是一批永远不会命中的死键，故一并软删除（不物理删，保留可追溯）。
            UPDATE t_file_relation r
            SET deleted_flag = TRUE,
                update_time  = CURRENT_TIMESTAMP
            FROM fa3_moved_key m
            WHERE r.file_key = m.old_key
              AND r.biz_type = 'PRODUCT'
              AND r.deleted_flag = FALSE;
        END IF;
    END
$$;
-- FA3 END 搬运

-- 搬运之后的目标形态：活着的商品图只能引用公开前缀。
-- 判据带上 deleted = TRUE 分支，是因为 CHECK 约束无法做成部分约束，而历史软删行仍是搬运前的原值。
ALTER TABLE product_image
    ADD CONSTRAINT ck_product_image_public_file_key
        CHECK (file_key LIKE 'public/%' OR deleted = TRUE);

COMMENT ON CONSTRAINT ck_product_image_public_file_key ON product_image IS
    '商品图是公开资产：活行只能引用 public/ 前缀（FA-3 收口）。软删行保留搬运前的原 key 作为历史事实';
