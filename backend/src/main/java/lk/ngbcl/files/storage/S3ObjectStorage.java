package lk.ngbcl.files.storage;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** R2 / MinIO implementation. Credentials stay on the server (SRS §7.3). */
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public S3ObjectStorage(StorageProperties props) {
        this.props = props;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build();
        URI endpoint = URI.create(props.endpoint());
        Region region = Region.of(props.region());
        this.s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                // R2/MinIO don't need the SDK's newer default integrity checksums
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
    }

    @Override
    public PresignedUpload presignPrivatePut(String key, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.privateBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
        Map<String, String> headers = new LinkedHashMap<>();
        // The browser must send exactly these; Host and Content-Length are set by the browser itself.
        presigned.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length") && !values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        headers.putIfAbsent("content-type", contentType);
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    @Override
    public Optional<ObjectInfo> headPrivate(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(props.privateBucket()).key(key).build());
            return Optional.of(new ObjectInfo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readPrivate(String key, long maxBytes) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.privateBucket())
                .key(key)
                .range("bytes=0-" + (maxBytes - 1))
                .build();
        return s3.getObjectAsBytes(get).asByteArray();
    }

    @Override
    public String presignPrivateGet(String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(props.privateBucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build())
                .url().toString();
    }

    @Override
    public void deletePrivate(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.privateBucket()).key(key).build());
    }

    @Override
    public void putPublic(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(props.publicBucket())
                .key(key)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public void deletePublic(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.publicBucket()).key(key).build());
    }

    @Override
    public String publicUrl(String key) {
        return props.publicBaseUrl() + "/" + key;
    }

}
