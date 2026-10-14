-- 리뷰 생성 시 카테고리 조회에 필요한 날짜 컬럼을 기존 DB에 추가합니다.
-- 과거 생성 시각을 알 수 없는 기존 행은 마이그레이션 시각으로 채웁니다.
BEGIN;

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE categories
SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL OR updated_at IS NULL;

ALTER TABLE categories
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

COMMIT;
