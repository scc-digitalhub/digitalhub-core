package it.smartcommunitylabdhub.files.s3;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.provider.S3Credentials;
import it.smartcommunitylabdhub.files.service.FilesStore;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

@Slf4j
public class S3FilesStore implements FilesStore {

    public static final int URL_DURATION = 3600 * 8; //8 hours
    public static final int MAX_KEYS = 200;

    private final String systemAccessKey;
    private final String systemSecretKey;
    private final String endpoint;
    private final String bucket;

    private int urlDuration = URL_DURATION;

    public S3FilesStore(String accessKey, String secretKey, String endpoint, String bucket) {
        this.systemAccessKey = accessKey;
        this.systemSecretKey = secretKey;
        this.endpoint = endpoint;
        this.bucket = bucket;
    }
    
    private S3Client getClient(S3Credentials s3Credentials) {
    	S3Client client = null;
    	String accessKey = null;
    	String secretKey = null;
    	if(s3Credentials != null) {
    		accessKey = s3Credentials.getAccessKey();
    		secretKey = s3Credentials.getSecretKey();
    	} else {
    		accessKey = systemAccessKey;
    		secretKey = systemSecretKey;
    	}
        AwsBasicCredentials credentials = AwsBasicCredentials
                .builder()
                .accessKeyId(accessKey)
                .secretAccessKey(secretKey)
                .build();
        if (StringUtils.hasText(endpoint)) {
        	client =
                S3Client
                    .builder()
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .endpointOverride(URI.create(endpoint))
                    //also enable path style for endpoint by default
                    .forcePathStyle(true)
                    .build();
        } else {
        	client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).build();
        }
    	return client;
    }
    
    private S3Presigner getPresignerClient(S3Credentials s3Credentials) {
    	S3Presigner presigner = null;
    	String accessKey = null;
    	String secretKey = null;
    	if(s3Credentials != null) {
    		accessKey = s3Credentials.getAccessKey();
    		secretKey = s3Credentials.getSecretKey();
    	} else {
    		accessKey = systemAccessKey;
    		secretKey = systemSecretKey;
    	}
        AwsBasicCredentials credentials = AwsBasicCredentials
                .builder()
                .accessKeyId(accessKey)
                .secretAccessKey(secretKey)
                .build();
        if (StringUtils.hasText(endpoint)) {
            presigner =
                    S3Presigner
                        .builder()
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        //also enable path style for endpoint by default
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .endpointOverride(URI.create(endpoint))
                        .build();        	
        } else {
        	presigner = S3Presigner.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).build();
        }
    	return presigner;
    }
    
    private S3Credentials getCredentials(UserAuthentication<?> auth) {
    	S3Credentials s3Credentials = null;
    	if(auth != null) {
    		for(Credentials credentials : auth.getCredentials()) {
    			if(credentials instanceof S3Credentials) {
    				s3Credentials = (S3Credentials) credentials;
    				break;
    			}
    		}
    	}
    	return s3Credentials;
    }

    @Override
    public DownloadInfo downloadAsUrl(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        log.debug("generate download url for {}", path);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }
        
        S3Credentials s3Credentials = getCredentials(auth);

        //generate temporary signed url for download
        if (log.isTraceEnabled()) {
            log.trace("generating presigned download url for {}: {}", bucketName, key);
        }

        try {
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("downloading folders is not supported: {}", path);
                return null;
            }
            
            S3Presigner presigner = getPresignerClient(s3Credentials);
            
            GetObjectPresignRequest preq = GetObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(urlDuration))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                .build();
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(preq);

            DownloadInfo info = new DownloadInfo();
            info.setPath(path);
            info.setUrl(presignedRequest.url().toExternalForm());
            info.setExpiration(presignedRequest.expiration());
            return info;
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    @Override
    public List<FileInfo> fileInfo(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        log.debug("file info for {}", path);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }
        
        S3Credentials s3Credentials = getCredentials(auth);

        if (log.isTraceEnabled()) {
            log.trace("read object metadata for {}: {}", bucketName, key);
        }

        try {
        	S3Client client = getClient(s3Credentials);
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("reading metadata for folders is partially supported: {}", path);
                String prefix = "/".equals(key) ? null : key;
                ListObjectsV2Request req = ListObjectsV2Request
                    .builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    // .delimiter("/") //disable grouping by path to let api list all objects as flat
                    .maxKeys(MAX_KEYS)
                    .build();

                return client
                    .listObjectsV2(req)
                    .contents()
                    .stream()
                    .limit(MAX_KEYS)
                    .map(o -> {
                        Keys kk = parseKey("s3://" + bucketName + "/" + (prefix != null ? prefix : "") + o.key());
                        return FileInfo
                            .builder()
                            .path(prefix != null ? o.key().substring(prefix.length()) : o.key())
                            .name(kk.fileName)
                            .size(o.size())
                            .lastModified(Date.from(o.lastModified()))
                            .build();
                    })
                    .toList();
            } else {
                HeadObjectResponse headObject = client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(key).build()
                );

                FileInfo response = new FileInfo();
                response.setPath(path);
                response.setName(keys.fileName);
                response.setContentType(headObject.contentType());
                response.setSize(headObject.contentLength());
                response.setLastModified(Date.from(headObject.lastModified()));

                if (StringUtils.hasText(headObject.checksumSHA256())) {
                    response.setHash("sha256:" + headObject.checksumSHA256());
                }

                headObject
                    .metadata()
                    .entrySet()
                    .forEach(entry -> {
                        response.getMetadata().put("Metadata." + entry.getKey(), entry.getValue());
                    });

                return Collections.singletonList(response);
            }
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadAsUrl(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        log.debug("generate upload url for {}", path);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }

        S3Credentials s3Credentials = getCredentials(auth);

        //generate temporary signed url for upload
        if (log.isTraceEnabled()) {
            log.trace("generating presigned url for {}: {}", bucketName, key);
        }

        try {
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("upload for folders is not supported: {}", path);
                return null;
            }
            
            S3Presigner presigner = getPresignerClient(s3Credentials);
            
            PutObjectPresignRequest preq = PutObjectPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(urlDuration))
                .putObjectRequest(PutObjectRequest.builder().bucket(bucketName).key(key).build())
                .build();
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(preq);

            UploadInfo info = new UploadInfo();
            info.setPath(path);
            info.setUrl(presignedRequest.url().toExternalForm());
            info.setExpiration(presignedRequest.expiration());
            return info;
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    @Override
    public UploadInfo startMultiPartUpload(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        log.debug("generate multipart upload for {}", path);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }

        S3Credentials s3Credentials = getCredentials(auth);

        //generate temporary signed url for upload
        if (log.isTraceEnabled()) {
            log.trace("generating presigned multipart upload url for {}: {}", bucketName, key);
        }

        try {
        	
        	S3Client client = getClient(s3Credentials);
        	
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("upload for folders is not supported: {}", path);
                return null;
            }

            CreateMultipartUploadResponse response = client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucketName).key(key).build()
            );

            UploadInfo info = new UploadInfo();
            info.setPath(path);
            info.setUploadId(response.uploadId());
            return info;
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadMultiPart(@NotNull String path, @NotNull String uploadId, @NotNull Integer partNumber, 
    		UserAuthentication<?> auth) throws StoreException {
        log.debug("generate part upload url for {} -> {} - {}", path, uploadId, partNumber);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }
        
        S3Credentials s3Credentials = getCredentials(auth);

        if (log.isTraceEnabled()) {
            log.trace("generating presigned multipart upload part url for {}: {}", bucketName, key);
        }

        try {
        	S3Presigner presigner = getPresignerClient(s3Credentials);
        	
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("upload for folders is not supported: {}", path);
                return null;
            }

            UploadPartPresignRequest preq = UploadPartPresignRequest
                .builder()
                .signatureDuration(Duration.ofSeconds(urlDuration))
                .uploadPartRequest(
                    UploadPartRequest
                        .builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build()
                )
                .build();
            PresignedUploadPartRequest presignedRequest = presigner.presignUploadPart(preq);

            UploadInfo info = new UploadInfo();
            info.setPath(path);
            info.setUrl(presignedRequest.url().toExternalForm());
            info.setExpiration(presignedRequest.expiration());
            return info;
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList,
        UserAuthentication<?> auth
    ) throws StoreException {
        log.debug("generate complete upload url for {} -> {}", path, uploadId);

        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }
        
        S3Credentials s3Credentials = getCredentials(auth);

        if (log.isTraceEnabled()) {
            log.trace("generating presigned multipart complete upload url for {}: {}", bucketName, key);
        }

        try {
        	S3Client client = getClient(s3Credentials);
        	
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("upload for folders is not supported: {}", path);
                return null;
            }
            List<CompletedPart> parts = new ArrayList<>();
            for (int i = 0; i < eTagPartList.size(); i++) {
                CompletedPart cp = CompletedPart.builder().eTag(eTagPartList.get(i)).partNumber(i + 1).build();
                parts.add(cp);
            }
            CompletedMultipartUpload mp = CompletedMultipartUpload.builder().parts(parts).build();
            CompleteMultipartUploadRequest req = CompleteMultipartUploadRequest
                .builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(mp)
                .build();
            client.completeMultipartUpload(req);
            //            CompleteMultipartUploadPresignRequest preq = CompleteMultipartUploadPresignRequest
            //                .builder()
            //                .signatureDuration(Duration.ofSeconds(urlDuration))
            //                .completeMultipartUploadRequest(req)
            //                .build();
            //            PresignedCompleteMultipartUploadRequest presignedRequest = presigner.presignCompleteMultipartUpload(preq);

            UploadInfo info = new UploadInfo();
            info.setPath(path);
            info.setUploadId(uploadId);
            return info;
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }
    }

    private Keys parseKey(String path) {
        //parse as URI where host == bucket, or host == endpoint
        //TODO support other path styles
        UriComponents uri = UriComponentsBuilder.fromUriString(path).build();
        if (uri.getPath() == null || uri.getHost() == null) {
            return null;
        }

        String bucketName = uri.getHost();
        String key = uri.getPath();

        if (endpoint != null && endpoint.equals(bucketName)) {
            //use first path el as bucket
            bucketName = uri.getPathSegments().stream().findFirst().orElse(null);
            if (bucketName != null) {
                key = uri.getPath().substring(bucketName.length() + 1);
            }
        }

        if (bucketName == null || key == null) {
            return null;
        }

        if (key.startsWith("/")) {
            key = key.substring(1);
        }

        //support root as /
        if (key.isEmpty()) {
            key = "/";
        }

        String fileName = key.endsWith("/") ? null : key.substring(key.lastIndexOf('/') + 1, key.length());

        return new Keys(endpoint, bucketName, key, fileName);
    }

    private record Keys(String endpoint, String bucket, String key, String fileName) {}

	@Override
	public void remove(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
        Keys keys = parseKey(path);
        log.debug("resolved path to {}", keys);

        String key = keys.key;
        String bucketName = keys.bucket;

        if (StringUtils.hasText(bucket) && !bucket.equals(bucketName)) {
            throw new StoreException("bucket mismatch");
        }
        
        S3Credentials s3Credentials = getCredentials(auth);

        if (log.isTraceEnabled()) {
            log.trace("remove object for {}: {}", bucketName, key);
        }
        
        try {
        	S3Client client = getClient(s3Credentials);
        	
            //support single file in path for now
            if (key.endsWith("/")) {
                log.warn("remove for folders is not supported: {}", path);
                return;
            }
            
            DeleteObjectRequest req = DeleteObjectRequest
            		.builder()
            		.bucket(bucketName)
            		.key(key)
            		.build();
            client.deleteObject(req);
        } catch (SdkException e) {
            log.error("error with s3 for {}: {}", path, e.getMessage());
            throw new StoreException("error with s3: " + e.getMessage());
        }		
	}
}
