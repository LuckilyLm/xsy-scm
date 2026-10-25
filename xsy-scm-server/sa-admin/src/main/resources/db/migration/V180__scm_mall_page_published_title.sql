-- 富文本单页的标题也随发布冻结：客户端读已发布正文时，必须拿到同一次发布的标题，
-- 不能把发布后保存的草稿标题拼到线上正文上。
ALTER TABLE xsy_mall_page ADD COLUMN IF NOT EXISTS published_title VARCHAR(80);

-- 迁移前没有标题快照，只能以当前标题回填；从本版本起标题不再随草稿变化。
UPDATE xsy_mall_page
SET published_title = title
WHERE published_content IS NOT NULL
  AND published_title IS NULL;
