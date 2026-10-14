package com.project.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration // Cloudflare R2의 S3 호환 API에 사용할 서명 객체를 등록합니다.
public class StorageConfig {

    @Bean(destroyMethod = "close") // 스프링이 Presigner를 관리하고 종료 시 정리
    public S3Presigner s3Presigner(
            @Value("${storage.endpoint}") String endpoint, // R2 S3 API 주소 (공개 이미지 주소와 다름)
            @Value("${storage.region}") String region, // 서명 리전
            @Value("${storage.access-key}") String accessKey, // 접근 키
            @Value("${storage.secret-key}") String secretKey // 비밀 키
    ) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint)) // R2 S3 API로 업로드 요청 서명
                .region(Region.of(region)) // 서명 리전 지정
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        ) // R2 Access Key ID와 Secret Access Key 사용
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true) // S3 API 주소/bucket/key 형태로 서명
                                .build()
                )
                .build(); // S3Presigner 객체 생성
    }
}
