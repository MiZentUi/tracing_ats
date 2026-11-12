package ats.resume.infrastructure.storage;

import ats.resume.config.StorageProperties;
import ats.resume.domain.storage.ObjectStorage;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-совместимое хранилище (MinIO). Bucket создаётся при старте, если его ещё нет. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ats.storage",
        name = "type",
        havingValue = "s3",
        matchIfMissing = true)
public class S3ObjectStorage implements ObjectStorage {

    private final StorageProperties properties;
    private S3Client client;

    @PostConstruct
    void init() {
        client =
                S3Client.builder()
                        .endpointOverride(URI.create(properties.getEndpoint()))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                properties.getAccessKey(),
                                                properties.getSecretKey())))
                        .region(Region.of(properties.getRegion()))
                        .forcePathStyle(true)
                        .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(
                    CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            log.info("object_storage_bucket_created bucket={}", properties.getBucket());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(
                        CreateBucketRequest.builder().bucket(properties.getBucket()).build());
                log.info("object_storage_bucket_created bucket={}", properties.getBucket());
            } else {
                throw e;
            }
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        ResponseBytes<?> response =
                client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        return response.asByteArray();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(
                software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build());
    }
}
