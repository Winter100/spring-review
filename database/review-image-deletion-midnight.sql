-- PostgreSQL: 스케줄러가 활성화된 애플리케이션을 기동하기 전에 한 번 적용합니다.
-- 기존 완료 이력은 유지하며, 기존 대기 작업만 등록 시각의 다음 한국 자정으로 보정합니다.
BEGIN;
UPDATE reviews_images_delete
SET next_attempt_at = ((created_at AT TIME ZONE 'Asia/Seoul')::date + 1)::timestamp
                      AT TIME ZONE 'Asia/Seoul'
WHERE status = 'PENDING';
COMMIT;
