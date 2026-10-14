package com.project.review.category.dtos;

import com.project.review.category.entities.Category;

public record CategoryResponse(
        Long id,
        String category
) {
    public static CategoryResponse from(Category category){
        return new CategoryResponse(
                category.getId(),
                category.getCategory()
        );
    }
}
