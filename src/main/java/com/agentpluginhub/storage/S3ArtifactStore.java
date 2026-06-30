package com.agentpluginhub.storage;

import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.config.S3Properties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3ArtifactStore implements ArtifactStore {

    private final S3Client s3;
    private final String bucket;

    public S3ArtifactStore(S3Client s3, S3Properties props) {
        this.s3 = s3;
        this.bucket = props.getBucket();
    }

    @PostConstruct
    void ensureBucket() {
        boolean exists = s3.listBuckets().buckets().stream()
                .anyMatch(b -> b.name().equals(bucket));
        if (!exists) {
            s3.createBucket(b -> b.bucket(bucket));
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            ResponseBytes<GetObjectResponse> resp = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return resp.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ArtifactNotFoundException(key);
        }
    }

    @Override
    public void save(String key, byte[] data) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
