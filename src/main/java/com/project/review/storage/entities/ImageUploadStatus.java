package com.project.review.storage.entities;

import com.project.review.member.entities.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;


/**
 * 이미지 업로드 관리 엔티티
 */

@Entity
@Table(name = "images_upload_status")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ImageUploadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "image_key", nullable = false, unique = true)
    private String imageKey;

    @Column(name = "expected_content_type", length = 100, nullable = false)
    private String expectedContentType;

    // 예상되는 파일 크기
    @Column(name = "expected_size", nullable = false)
    private Long expectedSize;

    // 업로드 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UploadStatus status;

    // 업로드 URL 만료 시각
    @Column(name = "upload_expires_at", nullable = false)
    private Instant uploadExpiresAt;

    // 리뷰 연결 기한
    @Column(name = "attach_expires_at", nullable = false)
    private Instant attachExpiresAt;

    public ImageUploadStatus(Member member, String imageKey, String expectedContentType, Long expectedSize,
                             UploadStatus status, Instant uploadExpiresAt, Instant attachExpiresAt) {
        this.member = member;
        this.imageKey = imageKey;
        this.expectedContentType = expectedContentType;
        this.expectedSize = expectedSize;
        this.status = status;
        this.uploadExpiresAt = uploadExpiresAt;
        this.attachExpiresAt = attachExpiresAt;
    }
}
