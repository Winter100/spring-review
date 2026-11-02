package com.project.review.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.project.review.review.entities.ReviewImageDelete;
import com.project.review.review.entities.ImageDeleteStatus;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

@Repository
public interface ReviewImageDeleteRepository extends JpaRepository<ReviewImageDelete,Long> {
    List<ReviewImageDelete> findByStatusAndNextAttemptAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
            ImageDeleteStatus status, Instant cutoff, Long afterId, Pageable pageable);
}
