package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.dtos.ReviewResponse;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static com.project.review.review.ReviewContentFixtures.textDocument;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.config.import=",
        "security.jwt.access-token-expires-minutes=15",
        "security.jwt.refresh-token-expires-days=7"
})
class ReviewContentPersistenceTests {

    @Autowired private ReviewRepository reviews;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsAndUpdatesJsonDocumentAndReturnsAnObject() {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode content = mapper.readTree("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},
                   "content":[{"type":"text","text":"사용 후기"}]},
                  {"type":"paragraph","content":[
                    {"type":"text","text":"만족합니다.","marks":[{"type":"bold"}]}]},
                  {"type":"image","attrs":{"imageKey":"reviews/body-image","alt":"제품 사진"}}
                ]}
                """);
        Member author = members.save(Member.builder().email("json@example.com")
                .nickname("json-user").password("unused").build());
        Category category = categories.save(new Category("JSON 테스트"));
        Review review = reviews.saveAndFlush(Review.builder().title("후기").product("Product").content(content)
                .rating(BigDecimal.ONE).author(author).category(category).build());
        Long reviewId = review.getId();
        entityManager.clear();

        Review loaded = reviews.findById(reviewId).orElseThrow();
        assertThat(loaded.getContent()).isEqualTo(content);
        ReviewResponse response = ReviewResponse.from(loaded, null, List.of(), 0L);
        JsonNode responseJson = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(responseJson.path("content").isObject()).isTrue();
        assertThat(responseJson.path("content")).isEqualTo(content);

        JsonNode updated = textDocument("수정한 본문");
        loaded.updateDetails("수정한 제목", updated, BigDecimal.valueOf(5), loaded.getProduct(), loaded.getCategory(), loaded.getDuration(), loaded.getPros(), loaded.getCons(), loaded.getVerdict());
        reviews.flush();
        entityManager.clear();

        assertThat(reviews.findById(reviewId).orElseThrow().getContent()).isEqualTo(updated);
    }
}
