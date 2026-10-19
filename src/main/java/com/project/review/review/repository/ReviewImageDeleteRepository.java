package com.project.review.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewImageDeleteRepository extends JpaRepository<com.project.review.review.entities.ReviewImageDelete,Long> {
}
