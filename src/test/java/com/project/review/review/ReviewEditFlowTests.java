package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewImageDeleteRepository;
import com.project.review.storage.entities.ImageUploadStatus;
import com.project.review.storage.entities.UploadStatus;
import com.project.review.storage.repository.ImageUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:edit-flow;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
    "storage.endpoint=https://example.com", "storage.region=auto", "storage.bucket=test-bucket",
    "storage.access-key=test-access-key", "storage.secret-key=test-secret-key",
    "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class ReviewEditFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewImageDeleteRepository deletions;
    @Autowired ImageUploadRepository uploads;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired TransactionTemplate transactions;
    @MockitoBean S3Client s3;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private Member owner;
    private Member other;
    private Category category;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        deletions.deleteAll(); reviews.deleteAll(); uploads.deleteAll();
        members.deleteAll(); categories.deleteAll();
        other = members.save(Member.builder().email("other@example.com").nickname("other").password("unused").build());
        owner = members.save(Member.builder().email("owner@example.com").nickname("owner").password("unused").build());
        category = categories.save(new Category("test"));
        Review review = Review.builder().title("original").product("Old product")
                .content(mapper.readTree("{\"type\":\"doc\",\"content\":[]}"))
                .rating(BigDecimal.ONE).author(owner).category(category).build();
        review.addImage("reviews/A", 0); review.addImage("reviews/B", 1);
        reviewId = reviews.saveAndFlush(review).getId();
    }

    private String request(String main, String sub) {
        var subKeys = mapper.readTree(sub);
        String body = subKeys.isArray() && !subKeys.isEmpty()
                ? "[{\"type\":\"image\",\"attrs\":{\"src\":" + mapper.writeValueAsString(subKeys.get(0)) + ",\"width\":240,\"height\":360}}]"
                : "[]";
        return """
                {"title":"updated","product":"New product","content":{"type":"doc","content":%s},
                 "rating":4.5,"categoryId":%d,"mainImageKey":%s,"subImageKeys":%s,
                 "duration":"One month","pros":["Good"],"cons":["Bad"],"verdict":"Recommended"}
                """.formatted(body, category.getId(), main, sub);
    }

    @Test
    void editReadRequiresOwnerAndDisablesCaching() throws Exception {
        mvc.perform(get("/api/v1/reviews/" + reviewId + "/edit"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reviews/" + reviewId + "/edit")
                        .with(jwt().jwt(token -> token.subject(other.getId().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reviews/" + reviewId + "/edit")
                        .with(jwt().jwt(token -> token.subject(owner.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.subImageKeys[0]").value("reviews/B"));
    }

    @Test
    void savesAllFieldsRetainsOldKeysAndAllowsBodyWithoutCover() throws Exception {
        mvc.perform(put("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(request("null", "[\"reviews/B\"]")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/reviews/" + reviewId + "/edit").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.product").value("New product"))
                .andExpect(jsonPath("$.duration").value("One month"))
                .andExpect(jsonPath("$.pros[0]").value("Good"))
                .andExpect(jsonPath("$.cons[0]").value("Bad"))
                .andExpect(jsonPath("$.verdict").value("Recommended"))
                .andExpect(jsonPath("$.content.content[0].attrs.width").value(240))
                .andExpect(jsonPath("$.mainImageKey").doesNotExist());
        transactions.executeWithoutResult(status -> {
            assertThat(reviews.findById(reviewId).orElseThrow().getImages())
                    .extracting(image -> image.getImageKey()).containsExactly("reviews/B");
        });
        assertThat(deletions.findAll()).extracting(deletion -> deletion.getImageKey()).containsExactly("reviews/A");
        verifyNoInteractions(s3);
    }

    @Test
    void attachesNewOwnedImageAndQueuesOnlyRemovedKey() throws Exception {
        addUpload(owner, "reviews/C");
        when(s3.headObject(org.mockito.ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).contentType("image/png").build());
        mvc.perform(put("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(request("\"reviews/A\"", "[\"reviews/C\"]")))
                .andExpect(status().isNoContent());
        assertThat(uploads.findAllByImageKeyIn(List.of("reviews/C")).getFirst().getStatus()).isEqualTo(UploadStatus.ATTACHED);
        assertThat(deletions.findAll()).extracting(deletion -> deletion.getImageKey()).containsExactly("reviews/B");
    }

    @Test
    void rejectsForeignImageAndOtherAuthorsWithoutChangingReview() throws Exception {
        addUpload(other, "reviews/foreign");
        mvc.perform(put("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(request("\"reviews/A\"", "[\"reviews/foreign\"]")))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/v1/reviews/" + reviewId).with(jwt().jwt(j -> j.subject(other.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(request("null", "[]")))
                .andExpect(status().isForbidden());
        assertThat(reviews.findById(reviewId).orElseThrow().getTitle()).isEqualTo("original");
        assertThat(deletions.count()).isZero();
        verifyNoInteractions(s3);
    }

    @Test
    void createAndPresignUseJwtOwnerAndAnonymousWritesAreRejected() throws Exception {
        String images = """
                {"images":[{"clientId":"new-image","name":"image.png","contentType":"image/png","fileSize":100}]}
                """;
        mvc.perform(post("/api/v1/storage/presigned-url").contentType(MediaType.APPLICATION_JSON).content(images))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(request("null", "[]")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON).content(request("null", "[]")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/storage/presigned-url").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(images)).andExpect(status().isOk());
        transactions.executeWithoutResult(status ->
                assertThat(uploads.findAll().getFirst().getMember().getId()).isEqualTo(owner.getId()));
        mvc.perform(post("/api/v1/reviews").with(jwt().jwt(j -> j.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(request("null", "[]"))).andExpect(status().isCreated());
        transactions.executeWithoutResult(status -> {
            Review created = reviews.findAll().stream().filter(r -> !r.getId().equals(reviewId)).findFirst().orElseThrow();
            assertThat(created.getAuthor().getId()).isEqualTo(owner.getId());
            assertThat(created.getProduct()).isEqualTo("New product");
            assertThat(created.getDuration()).isEqualTo("One month");
        });
    }

    private void addUpload(Member member, String key) {
        uploads.save(new ImageUploadStatus(member, key, "image/png", 100L, UploadStatus.PENDING,
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(3600)));
    }
}
