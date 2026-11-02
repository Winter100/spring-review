package com.project.review.review;

import com.project.review.category.entities.Category;
import com.project.review.category.repository.CategoryRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import com.project.review.review.entities.Review;
import com.project.review.review.repository.ReviewRepository;
import com.project.review.review.repository.ReviewViewRepository;
import com.project.review.review.service.ReviewService;
import com.project.review.review.service.ReviewViewCleanup;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 반드시 폐기 가능한 PostgreSQL DB URL을 전달해야 합니다. 이 테스트는 스키마를 생성·삭제합니다. */
@EnabledIfEnvironmentVariable(named = "REVIEW_VIEWS_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${REVIEW_VIEWS_TEST_URL}",
        "spring.datasource.username=${REVIEW_VIEWS_TEST_USER:review_views_test}",
        "spring.datasource.password=${REVIEW_VIEWS_TEST_PASSWORD:review_views_test}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.access-token-expires-minutes=15", "security.jwt.refresh-token-expires-days=7",
        "storage.endpoint=https://example.com", "storage.bucket=test-bucket",
        "storage.access-key=test", "storage.secret-key=test", "storage.public-base-url=https://images.example.com"
})
@AutoConfigureMockMvc
class ReviewViewPostgresTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private MockMvc mvc;
    @Autowired private ReviewService service;
    @Autowired private ReviewRepository reviews;
    @Autowired private MemberRepository members;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ReviewViewCleanup cleanup;
    @MockitoBean private Clock reviewViewClock;
    @MockitoSpyBean private ReviewViewRepository views;

    private Instant now;
    private Long reviewId;
    private Member author;
    private Category category;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        members.deleteAll();
        categories.deleteAll();
        now = Instant.parse("2026-10-21T14:59:59Z");
        when(reviewViewClock.withZone(SEOUL)).thenAnswer(invocation -> Clock.fixed(now, SEOUL));
        author = members.save(Member.builder().email("views@example.com").nickname("viewer")
                .password("unused").build());
        category = categories.save(new Category("조회수"));
        reviewId = saveReview();
    }

    @Test
    void issuesCookieAndKeepsIdentityAcrossAuthenticationChanges() throws Exception {
        var first = mvc.perform(get("/api/v1/reviews/{id}", reviewId).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(cookie().httpOnly("reviewVisitorId", true))
                .andExpect(cookie().secure("reviewVisitorId", true))
                .andExpect(cookie().path("reviewVisitorId", "/"))
                .andExpect(cookie().maxAge("reviewVisitorId", 365 * 24 * 60 * 60))
                .andExpect(cookie().attribute("reviewVisitorId", "SameSite", "Lax"))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andReturn().getResponse();
        Cookie cookie = first.getCookie("reviewVisitorId");
        assertThat(cookie).isNotNull();
        UUID.fromString(cookie.getValue());
        mvc.perform(get("/api/v1/reviews/{id}", reviewId).cookie(cookie)
                        .with(jwt().jwt(token -> token.subject(author.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        mvc.perform(get("/api/v1/reviews/{id}", reviewId).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/v1/reviews/{id}", reviewId)
                        .cookie(new Cookie("reviewVisitorId", "broken")))
                .andExpect(status().isOk()).andExpect(cookie().exists("reviewVisitorId"))
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void countsIndependentlyPerVisitorAndReviewAndResetsAtKoreanMidnight() {
        UUID visitor = UUID.randomUUID();
        Long other = saveReview();
        assertThat(service.findByIdAndCountView(reviewId, visitor).viewCount()).isEqualTo(1);
        assertThat(service.findByIdAndCountView(reviewId, visitor).viewCount()).isEqualTo(1);
        assertThat(service.findByIdAndCountView(other, visitor).viewCount()).isEqualTo(1);
        assertThat(service.findByIdAndCountView(reviewId, UUID.randomUUID()).viewCount()).isEqualTo(2);
        now = Instant.parse("2026-10-21T15:00:00Z");
        assertThat(service.findByIdAndCountView(reviewId, visitor).viewCount()).isEqualTo(3);
        assertThat(service.findByIdAndCountView(reviewId, visitor).viewCount()).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT DISTINCT viewed_on FROM review_daily_views", LocalDate.class))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22));
    }

    @Test
    void concurrentRequestsForSameVisitorIncrementOnlyOnce() throws Exception {
        UUID visitor = UUID.randomUUID();
        runConcurrent(Collections.nCopies(16, visitor));
        assertThat(views.findCount(reviewId)).isEqualTo(1);
        assertThat(dailyViewCount()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentVisitorsDoNotLoseIncrements() throws Exception {
        List<UUID> visitors = IntStream.range(0, 16).mapToObj(index -> UUID.randomUUID()).toList();
        runConcurrent(visitors);
        assertThat(views.findCount(reviewId)).isEqualTo(16);
        assertThat(dailyViewCount()).isEqualTo(16);
    }

    @Test
    void rollsBackDailyRecordAndCounterTogether() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected failure after increment");
        }).when(views).increment(reviewId);
        assertThatThrownBy(() -> service.findByIdAndCountView(reviewId, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(views.findCount(reviewId)).isZero();
        assertThat(dailyViewCount()).isZero();
    }

    @Test
    void listingAndReadOnlyLookupDoNotCountAndMissingReviewReturns404() throws Exception {
        assertThat(service.findById(reviewId).viewCount()).isZero();
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].viewCount").value(0));
        mvc.perform(get("/api/v1/reviews/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(dailyViewCount()).isZero();
        service.findByIdAndCountView(reviewId, UUID.randomUUID());
        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].viewCount").value(1));
        assertThat(dailyViewCount()).isEqualTo(1);
    }

    @Test
    void viewsDoNotChangeReviewVersionOrTimestampAndSurviveConcurrentEdit() throws Exception {
        Review before = reviews.findById(reviewId).orElseThrow();
        service.findByIdAndCountView(reviewId, UUID.randomUUID());
        Review after = reviews.findById(reviewId).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());

        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch viewed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> edit = executor.submit(() -> transactions.executeWithoutResult(status -> {
                Review review = reviews.findById(reviewId).orElseThrow();
                review.updateDetails("수정됨", review.getContent(), review.getRating(), review.getProduct(),
                        review.getCategory(), review.getDuration(), review.getPros(), review.getCons(), review.getVerdict());
                loaded.countDown();
                await(viewed);
            }));
            try {
                assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                service.findByIdAndCountView(reviewId, UUID.randomUUID());
            } finally {
                viewed.countDown();
            }
            edit.get(10, TimeUnit.SECONDS);
        }
        assertThat(reviews.findById(reviewId).orElseThrow().getTitle()).isEqualTo("수정됨");
        assertThat(views.findCount(reviewId)).isEqualTo(2);
    }

    @Test
    void cleanupPreservesTodayYesterdayAndTotalAndDeletionCascades() {
        UUID visitor = UUID.randomUUID();
        for (String instant : List.of("2026-10-19T00:00:00Z", "2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z")) {
            now = Instant.parse(instant);
            service.findByIdAndCountView(reviewId, visitor);
        }
        cleanup.deleteExpiredViews();
        assertThat(dailyViewCount()).isEqualTo(2);
        assertThat(views.findCount(reviewId)).isEqualTo(3);
        assertThat(service.findByIdAndCountView(reviewId, visitor).viewCount()).isEqualTo(3);
        reviews.deleteById(reviewId);
        assertThat(dailyViewCount()).isZero();
        assertThat(views.findCount(reviewId)).isZero();
    }

    @Test
    void migrationCreatesTablesForExistingReviewsWithoutChangingThem() throws Exception {
        // 각 테스트가 순차 실행되는 폐기용 DB에서 신규 테이블 생성 경로를 검증합니다.
        jdbc.execute("DROP TABLE review_daily_views");
        jdbc.execute("DROP TABLE review_view_counts");
        jdbc.execute(Files.readString(Path.of("database/review-views.sql")));
        assertThat(service.findById(reviewId).viewCount()).isZero();
        assertThat(service.findByIdAndCountView(reviewId, UUID.randomUUID()).viewCount()).isEqualTo(1);
        reviews.deleteById(reviewId);
        assertThat(dailyViewCount()).isZero();
        assertThat(views.findCount(reviewId)).isZero();
    }

    @Test
    void allowsCredentialedFrontendRequestsAndRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/v1/reviews/{id}", reviewId)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options("/api/v1/reviews/{id}", reviewId)
                        .header(HttpHeaders.ORIGIN, "https://other.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
        assertThat(dailyViewCount()).isZero();
    }

    private Long saveReview() {
        return reviews.saveAndFlush(Review.builder().title("조회 테스트").product("Product")
                .content(ReviewContentFixtures.textDocument("본문"))
                .rating(BigDecimal.ONE).author(author).category(category).build()).getId();
    }

    private long dailyViewCount() {
        return jdbc.queryForObject("SELECT count(*) FROM review_daily_views", Long.class);
    }

    private void runConcurrent(List<UUID> visitors) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(visitors.size())) {
            List<Future<?>> requests = new ArrayList<>();
            for (UUID visitor : visitors) {
                requests.add(executor.submit(() -> {
                    await(start);
                    service.findByIdAndCountView(reviewId, visitor);
                }));
            }
            start.countDown();
            for (Future<?> request : requests) {
                request.get(30, TimeUnit.SECONDS);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent request");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
