package com.project.review.category.repository;

import com.project.review.category.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByCategoryAndIdNot(String category, Long id);
}
