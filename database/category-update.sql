-- PostgreSQL 기존 DB용입니다. 변경된 애플리케이션을 시작하기 전에 실행합니다.
-- 기존 카테고리명 유일성은 유지하고, 제약조건의 이름만 명시적으로 통일합니다.
BEGIN;

DO $$
DECLARE
    category_constraint_name TEXT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'categories'::regclass
          AND conname = 'uk_categories_category'
    ) THEN
        SELECT conname INTO category_constraint_name
        FROM pg_constraint
        WHERE conrelid = 'categories'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[
              (SELECT attnum FROM pg_attribute
               WHERE attrelid = 'categories'::regclass AND attname = 'category')
          ]
        LIMIT 1;

        IF category_constraint_name IS NULL THEN
            ALTER TABLE categories
                ADD CONSTRAINT uk_categories_category UNIQUE (category);
        ELSE
            EXECUTE format('ALTER TABLE categories RENAME CONSTRAINT %I TO uk_categories_category',
                           category_constraint_name);
        END IF;
    END IF;
END $$;

COMMIT;
