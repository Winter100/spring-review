-- 기존 content가 TEXT인 PostgreSQL DB에 애플리케이션 배포 전에 1회 적용합니다.
-- 기존 일반 텍스트를 Tiptap 문단으로 감싸 원문을 보존합니다.
-- ddl-auto=update는 기존 텍스트를 Tiptap 문서로 변환하지 않습니다.
BEGIN;

ALTER TABLE reviews
    ALTER COLUMN content TYPE jsonb
    USING jsonb_build_object(
        'type', 'doc',
        'content', jsonb_build_array(
            jsonb_build_object(
                'type', 'paragraph',
                'content', CASE
                    WHEN content = '' THEN '[]'::jsonb
                    ELSE jsonb_build_array(jsonb_build_object('type', 'text', 'text', content))
                END
            )
        )
    );

COMMIT;
