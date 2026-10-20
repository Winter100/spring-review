package com.project.review.review.repository;

import com.project.review.review.entities.Review;
import com.project.review.review.dtos.ReviewSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query(value = """
            select new com.project.review.review.dtos.ReviewSummaryResponse(
                r.id, r.title, r.createdAt, r.rating,
                a.id, a.nickname, c.id, c.category, i.imageKey
            )
            from Review r
            join r.author a
            join r.category c
            left join r.images i on i.displayOrder = 0
            """, countQuery = "select count(r) from Review r")
    Page<ReviewSummaryResponse> findAllSummaries(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    List<Review> findAll();

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    Optional<Review> findById(Long id);
}
