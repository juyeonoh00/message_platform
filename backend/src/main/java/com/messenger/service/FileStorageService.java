package com.messenger.service;

import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.documents-bucket-name}")
    private String documentsBucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * MinIO에 파일을 업로드하고 접근 가능한 URL을 반환합니다.
     *
     * @param file 업로드할 파일
     * @return 업로드된 파일의 URL
     * @throws RuntimeException 업로드 실패 시
     */
    public String uploadFile(MultipartFile file) {
        try {
            // 버킷 존재 여부 확인 및 생성
            ensureBucketExists();

            // 고유한 파일명 생성 (UUID + 원본 확장자)
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String filename = UUID.randomUUID().toString() + extension;

            // 파일 업로드
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filename)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
                );
            }

            log.info("파일 업로드 성공: {}", filename);

            // 파일 URL 반환
            return String.format("%s/%s/%s", endpoint, bucketName, filename);

        } catch (Exception e) {
            log.error("파일 업로드 실패", e);
            throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 버킷 존재 여부를 확인하고 없으면 생성합니다.
     */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build()
            );

            // 버킷을 public으로 설정 (프로필 이미지는 공개적으로 접근 가능해야 함)
            String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {
                                "AWS": ["*"]
                            },
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """.formatted(bucketName);

            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(policy)
                    .build()
            );

            log.info("버킷 생성 완료: {}", bucketName);
        }
    }

    /**
     * MinIO에서 파일을 삭제합니다.
     *
     * @param fileUrl 삭제할 파일의 URL
     */
    public void deleteFile(String fileUrl) {
        try {
            // URL에서 파일명 추출
            String filename = extractFilenameFromUrl(fileUrl);
            if (filename == null || filename.isEmpty()) {
                log.warn("잘못된 파일 URL: {}", fileUrl);
                return;
            }

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filename)
                    .build()
            );

            log.info("파일 삭제 성공: {}", filename);

        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", fileUrl, e);
            // 삭제 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    /**
     * URL에서 파일명을 추출합니다.
     *
     * @param url 파일 URL
     * @return 파일명 또는 null
     */
    private String extractFilenameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // URL 형식: http://218.38.54.88:9000/messenger-avatars/filename.jpg
        String[] parts = url.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    /**
     * MinIO에 문서 파일을 업로드하고 접근 가능한 URL을 반환합니다.
     *
     * @param file 업로드할 파일
     * @param originalFilename 원본 파일명 (UUID와 함께 저장)
     * @return 업로드된 파일의 URL
     * @throws RuntimeException 업로드 실패 시
     */
    public String uploadDocument(MultipartFile file, String originalFilename) {
        try {
            // 문서 버킷 존재 여부 확인 및 생성
            ensureDocumentBucketExists();

            // 고유한 파일명 생성 (UUID + 원본 확장자)
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String filename = UUID.randomUUID().toString() + extension;

            // 파일 업로드
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(documentsBucketName)
                        .object(filename)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
                );
            }

            log.info("문서 파일 업로드 성공: {}", filename);

            // 파일 URL 반환
            return String.format("%s/%s/%s", endpoint, documentsBucketName, filename);

        } catch (Exception e) {
            log.error("문서 파일 업로드 실패", e);
            throw new RuntimeException("문서 파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * MinIO에서 문서 파일을 다운로드합니다.
     *
     * @param fileUrl 다운로드할 파일의 URL
     * @return InputStream
     * @throws RuntimeException 다운로드 실패 시
     */
    public InputStream downloadDocument(String fileUrl) {
        try {
            String filename = extractFilenameFromUrl(fileUrl);
            if (filename == null || filename.isEmpty()) {
                throw new RuntimeException("잘못된 파일 URL: " + fileUrl);
            }

            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(documentsBucketName)
                    .object(filename)
                    .build()
            );

        } catch (Exception e) {
            log.error("문서 파일 다운로드 실패: {}", fileUrl, e);
            throw new RuntimeException("문서 파일 다운로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * MinIO에서 문서 파일을 삭제합니다.
     *
     * @param fileUrl 삭제할 파일의 URL
     */
    public void deleteDocument(String fileUrl) {
        try {
            String filename = extractFilenameFromUrl(fileUrl);
            if (filename == null || filename.isEmpty()) {
                log.warn("잘못된 파일 URL: {}", fileUrl);
                return;
            }

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(documentsBucketName)
                    .object(filename)
                    .build()
            );

            log.info("문서 파일 삭제 성공: {}", filename);

        } catch (Exception e) {
            log.error("문서 파일 삭제 실패: {}", fileUrl, e);
            // 삭제 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }

    /**
     * 문서 버킷 존재 여부를 확인하고 없으면 생성합니다.
     */
    private void ensureDocumentBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(documentsBucketName)
                .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(documentsBucketName)
                    .build()
            );

            // 버킷을 public으로 설정
            String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {
                                "AWS": ["*"]
                            },
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """.formatted(documentsBucketName);

            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                    .bucket(documentsBucketName)
                    .config(policy)
                    .build()
            );

            log.info("문서 버킷 생성 완료: {}", documentsBucketName);
        }
    }
}
