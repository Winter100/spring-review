package com.project.review.category.service;

import com.project.review.category.dtos.CategoryRequest;
import com.project.review.category.dtos.CategoryResponse;
import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.common.exception.BusinessException;
import com.project.review.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    // 리뷰 생성 시 연관관계에 사용할 수 있도록 응답 DTO 대신 엔티티를 반환합니다.
    @Transactional(readOnly = true)
    public Category findById(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() ->
                new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest categoryRequest) {
        Category category = new Category(categoryRequest.category());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryRequest categoryRequest) {
        Category category = findById(categoryId);
        String categoryName = categoryRequest.category();

        // 이름이 같으면 중복 조회와 엔티티 변경을 생략하여 수정 시각을 유지합니다.
        if (category.getCategory().equals(categoryName)) {
            return CategoryResponse.from(category);
        }
        // 수정 대상 자신을 제외하고 다른 카테고리가 이미 사용 중인 이름인지 확인합니다.
        // 동시 요청으로 사전 검사를 통과하는 중복은 DB 유니크 제약으로 차단해야 합니다.
        if (categoryRepository.existsByCategoryAndIdNot(categoryName, categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }

        // 같은 트랜잭션에서 조회한 엔티티이므로 별도 save() 없이 변경 감지로 반영됩니다.
        category.changeCategory(categoryName);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void remove(Long categoryId) {
        categoryRepository.deleteById(categoryId);
    }
}
