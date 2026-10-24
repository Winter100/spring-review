# 조회수 집계

배포 전에 `review-views.sql`을 기존 PostgreSQL DB에 적용합니다. 기존 게시글의 조회수는 0부터 시작합니다.

`GET /api/v1/reviews/{id}`는 `reviewVisitorId` 쿠키로 방문자를 구분하고 한국 시간 날짜별 1회 집계합니다.
상세·목록·생성 응답의 `viewCount`는 누적 조회수입니다. 로그인 상태는 집계 기준에 영향을 주지 않습니다.
쿠키 삭제·다른 브라우저·쿠키 없는 최초 병렬 요청은 같은 사람으로 구분할 수 없습니다.

프런트에서는 상세 API 요청을 캐시하거나 prefetch하지 않아야 합니다. 다른 출처의 브라우저 요청은
`credentials: 'include'`를 지정하고 `security.app.frontend-origin`을 실제 프런트 출처로 설정합니다.
현재 쿠키 정책은 동일 사이트 배포를 전제로 합니다. HTTPS에서는 Secure 쿠키를 사용하므로
프록시가 TLS를 종료한다면 애플리케이션이 요청을 HTTPS로 인식하도록 배포 환경에서 설정해야 합니다.

조회 기록은 매일 한국 시간 00:10에 오늘과 어제만 남기고 정리합니다. 누적 조회수는 삭제하지 않습니다.
봇 및 미리 가져오기 요청도 상세 API에 도착하면 집계합니다.

## PostgreSQL 통합 테스트

`ReviewViewPostgresTests`는 `REVIEW_VIEWS_TEST_URL`이 있을 때만 실행합니다.
**해당 DB의 스키마를 생성·삭제하므로 반드시 폐기 가능한 별도 DB를 사용합니다.**

PowerShell 예시:

```powershell
$env:REVIEW_VIEWS_TEST_URL = 'jdbc:postgresql://localhost:15432/review_views_test'
$env:REVIEW_VIEWS_TEST_USER = 'review_views_test'
$env:REVIEW_VIEWS_TEST_PASSWORD = 'review_views_test'
./gradlew.bat test --tests '*ReviewViewPostgresTests'
```

환경 변수가 없으면 PostgreSQL 테스트는 생략됩니다. H2 기반 기존 테스트는 PostgreSQL의 충돌 처리와
동시성을 검증하지 않습니다.
