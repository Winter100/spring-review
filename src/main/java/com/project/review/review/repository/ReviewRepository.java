package com.project.review.review.repository;

import com.project.review.review.entities.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    List<Review> findAll();

    @Override
    @EntityGraph(attributePaths = {"author", "category", "images"})
    Optional<Review> findById(Long id);
}
