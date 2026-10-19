package com.project.review.storage.repository;

import com.project.review.storage.entities.ImageUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.project.review.storage.entities.UploadStatus;

import java.time.Instant;
import java.util.List;

public interface ImageUploadRepository extends JpaRepository<ImageUploadStatus, Long> {

    List<ImageUploadStatus> findAllByImageKeyIn(List<String> imageKeys);

    @Modifying
    @Query("""
            update ImageUploadStatus upload
            set upload.status = :attached
            where upload.imageKey = :key
              and upload.member.id = :memberId
              and upload.status = :pending
              and upload.attachExpiresAt > :now
            """)
    int attachIfPending(@Param("key") String key,
                        @Param("memberId") Long memberId,
                        @Param("now") Instant now,
                        @Param("pending") UploadStatus pending,
                        @Param("attached") UploadStatus attached);
}
