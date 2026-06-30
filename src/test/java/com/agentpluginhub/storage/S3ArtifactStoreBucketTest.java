package com.agentpluginhub.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agentpluginhub.config.S3Properties;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

class S3ArtifactStoreBucketTest {

    private S3Properties props(boolean autoCreate) {
        S3Properties p = new S3Properties();
        p.setBucket("b");
        p.setAutoCreateBucket(autoCreate);
        return p;
    }

    @Test
    void skips_list_and_create_when_auto_create_disabled() {
        S3Client s3 = mock(S3Client.class);
        new S3ArtifactStore(s3, props(false)).ensureBucket();
        verifyNoInteractions(s3);   // 受限 IAM 下不触发 ListBuckets/CreateBucket
    }

    @Test
    @SuppressWarnings("unchecked")
    void creates_bucket_when_enabled_and_absent() {
        S3Client s3 = mock(S3Client.class);
        when(s3.listBuckets()).thenReturn(ListBucketsResponse.builder().build());   // 无任何桶
        new S3ArtifactStore(s3, props(true)).ensureBucket();
        verify(s3).createBucket((Consumer<CreateBucketRequest.Builder>) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void does_not_create_when_bucket_already_exists() {
        S3Client s3 = mock(S3Client.class);
        when(s3.listBuckets()).thenReturn(ListBucketsResponse.builder()
                .buckets(b -> b.name("b")).build());
        new S3ArtifactStore(s3, props(true)).ensureBucket();
        verify(s3, never()).createBucket((Consumer<CreateBucketRequest.Builder>) any());
    }
}
