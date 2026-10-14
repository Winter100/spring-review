-- 기존에 id만 있던 빈 reviews_images 테이블을 확장하는 1회용 마이그레이션입니다.
-- 스키마 변경은 하나의 트랜잭션으로 적용하며, 기존 데이터를 삭제하지 않습니다.
BEGIN;

ALTER TABLE reviews_images
    ADD COLUMN review_id BIGINT NOT NULL,
    ADD COLUMN image_key VARCHAR(1024) NOT NULL,
    ADD COLUMN display_order INTEGER NOT NULL,
    ADD CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    ADD CONSTRAINT uk_review_images_review_order UNIQUE (review_id, display_order),
    ADD CONSTRAINT ck_review_images_display_order CHECK (display_order BETWEEN 0 AND 4);

COMMIT;
