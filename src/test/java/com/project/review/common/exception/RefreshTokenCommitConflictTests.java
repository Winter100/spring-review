package com.project.review.common.exception;

import com.project.review.auth.entities.RefreshToken;
import com.project.review.auth.repository.RefreshTokenRepository;
import com.project.review.member.entities.Member;
import com.project.review.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(properties = {
        "spring.config.import=",
        "security.jwt.access-token-expires-minutes=15",
        "security.jwt.refresh-token-expires-days=7"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenCommitConflictTests {

    @Autowired private MemberRepository members;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void commitConflictReturnsAuthenticationResponseAndKeepsWinningToken() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        TransactionTemplate competingTransaction = new TransactionTemplate(transactionManager);
        competingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long tokenId = Objects.requireNonNull(transaction.execute(status -> {
            Member member = members.save(Member.builder()
                    .email("conflict@example.com")
                    .nickname("conflict-user")
                    .password("unused")
                    .build());
            return tokens.save(new RefreshToken(member, "original", Instant.now().plusSeconds(3600)))
                    .getId();
        }));

        try {
            Throwable failure = catchThrowable(() -> transaction.executeWithoutResult(status -> {
                RefreshToken staleToken = tokens.findById(tokenId).orElseThrow();

                competingTransaction.executeWithoutResult(competingStatus ->
                        tokens.findById(tokenId).orElseThrow().rotationToken("winning-hash"));

                staleToken.rotationToken("losing-hash");
            }));

            assertThat(failure).isInstanceOfSatisfying(OptimisticLockingFailureException.class, conflict -> {
                var response = new GlobalExceptionHandler().handleOptimisticLock(conflict);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().message()).isEqualTo(ErrorCode.AUTH_TOKEN_CONFLICT.getMessage());
                assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
                assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
            });

            transaction.executeWithoutResult(status ->
                    assertThat(tokens.findById(tokenId).orElseThrow().getTokenHash())
                            .isEqualTo("winning-hash"));
        } finally {
            transaction.executeWithoutResult(status -> {
                RefreshToken token = tokens.findById(tokenId).orElseThrow();
                Long memberId = token.getMember().getId();
                tokens.delete(token);
                tokens.flush();
                members.deleteById(memberId);
            });
        }
    }
}
