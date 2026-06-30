-- M2:给可移动的发布渠道指针加审计列(谁、何时移动)。两列可空以兼容 M1 既有 latest 行。
ALTER TABLE dist_tag
    ADD COLUMN updated_by VARCHAR(255) NULL,
    ADD COLUMN updated_at DATETIME(6)  NULL;
