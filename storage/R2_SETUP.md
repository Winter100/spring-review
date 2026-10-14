# Cloudflare R2 설정

업로드는 5분짜리 presigned PUT URL, 조회는 만료 없는 공개 URL을 사용합니다.
DB에 저장하는 객체 키와 `/api/v1/storage/presigned-url`의 응답 형식은 그대로입니다.

## .env에 입력할 값

프로젝트 루트의 `.env`에 입력합니다. 항목 예시는 `.env.example`에 있습니다.
표의 예시는 실제 값으로 교체해 주세요. `application.yml`은 `.env`를 properties 형식으로 읽습니다.
`KEY=VALUE` 형식을 사용하고, 값에 따옴표나 `export`를 붙이지 않습니다.
`.env`와 `.env.*`는 Git에서 제외하며, 입력 양식인 `.env.example`만 공유합니다.
실행 작업 디렉터리는 프로젝트 루트로 설정합니다. IntelliJ에서는 Working directory를 `$PROJECT_DIR$`로 지정합니다.
기존 실행 환경변수가 있으면 `.env`보다 우선하므로, `.env` 값만 사용하려면 기존 R2 환경변수는 제거합니다.

| 환경변수 | 입력할 값 |
|---|---|
| `R2_ENDPOINT` | R2 버킷 설정의 S3 API 주소. 보통 `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`이며 버킷 이름은 붙이지 않습니다. |
| `R2_BUCKET` | 생성한 버킷 이름. 예: `review-images` |
| `R2_ACCESS_KEY_ID` | R2 API 토큰 생성 시 제공되는 Access Key ID |
| `R2_SECRET_ACCESS_KEY` | 같은 토큰의 Secret Access Key |
| `R2_PUBLIC_BASE_URL` | 버킷의 공개 URL. 예: `https://pub-실제값.r2.dev` 또는 `https://images.example.com`. 버킷 이름은 붙이지 않습니다. |

R2 토큰은 해당 버킷의 Object Read & Write 권한으로 생성합니다.
일반 Cloudflare API 토큰 문자열이 아니라 S3용 Access Key ID와 Secret Access Key를 입력합니다.
리전은 `auto`로 설정되어 있어 추가 입력이 필요 없습니다.
실제 R2 값은 `.env`에 입력해야 합니다. `.env`가 없는 배포 환경에서는 환경변수로 제공할 수도 있습니다.

## 공개 조회와 브라우저 업로드 설정

1. R2에서 리뷰 이미지용 버킷을 생성합니다.
2. 버킷 Settings > Public Development URL을 활성화하고 주소를 `R2_PUBLIC_BASE_URL`에 입력합니다.
   운영 환경에서는 Custom Domain을 연결하고 그 주소를 사용합니다. `r2.dev`는 개발용이며 요청 제한이 있습니다.
3. Settings > CORS Policy에 이 폴더의 `r2-cors.json`을 적용합니다.
   프론트 주소가 다르면 AllowedOrigins를 변경하고, 운영 도메인도 추가합니다.
4. 서버를 재시작합니다. 프론트는 발급받은 PUT URL에 파일 바이너리와 동일한 Content-Type을 전송합니다.

키가 `reviews/example`이면 조회 주소는 `R2_PUBLIC_BASE_URL/reviews/example`입니다.
공개 URL은 해당 버킷의 객체를 공개하므로 리뷰 이미지용 버킷을 사용합니다.
S3 API 주소는 공개 이미지 조회용 주소가 아닙니다.

기존 MinIO 이미지는 자동으로 이전되지 않습니다. 기존 DB 키를 유지하려면 동일한 키로 R2에 복사해야 합니다.
기존 Docker 컨테이너와 볼륨은 삭제하지 않았습니다.

## 공식 문서

- [Java SDK 및 presigned URL](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/)
- [R2 인증 키 생성](https://developers.cloudflare.com/r2/api/tokens/)
- [공개 버킷](https://developers.cloudflare.com/r2/buckets/public-buckets/)
- [CORS 설정](https://developers.cloudflare.com/r2/buckets/cors/)
