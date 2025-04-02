package it.smartcommunitylabdhub.files.http;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.FilesStore;
import jakarta.validation.constraints.NotNull;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
public class HttpStore implements FilesStore {

    public static final String[] PROTOCOLS = { "http", "https", "ftp" };
    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public DownloadInfo downloadAsUrl(@NotNull String path, UserAuthentication<?> auth) {
        log.debug("generate download url for {}", path);

        //path must be a valid url
        try {
            URL url = URI.create(path).toURL();
            if (url == null) {
                return null;
            }

            if (!Arrays.asList(PROTOCOLS).contains(url.getProtocol())) {
                //not supported
                return null;
            }

            //use as-is
            DownloadInfo info = new DownloadInfo();
            info.setPath(path);
            info.setUrl(url.toExternalForm());
            return info;
        } catch (MalformedURLException e) {
            //not a valid url...
            return null;
        }
    }

    @Override
    public List<FileInfo> fileInfo(@NotNull String path, UserAuthentication<?> auth) {
        List<FileInfo> result = new ArrayList<>();
        try {
            String[] split = path.split("/");
            HttpHeaders headers = restTemplate.headForHeaders(new URI(path));
            FileInfo response = new FileInfo();
            response.setPath(path);
            response.setName(split[split.length - 1]);
            response.setContentType(
                Optional.ofNullable(headers.getContentType()).map(MediaType::toString).orElse(null)
            );
            response.setSize(headers.getContentLength());
            response.setLastModified(new Date(headers.getLastModified()));
            result.add(response);
        } catch (RestClientException | URISyntaxException e) {
            log.error("generate metadata for {}:  {}", path, e.getMessage());
        }
        return result;
    }

    @Override
    public UploadInfo uploadAsUrl(@NotNull String path, UserAuthentication<?> auth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo startMultiPartUpload(@NotNull String path, UserAuthentication<?> auth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo uploadMultiPart(@NotNull String path, @NotNull String uploadId, @NotNull Integer partNumber,
    		UserAuthentication<?> auth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        UserAuthentication<?> auth
    ) {
        throw new UnsupportedOperationException();
    }

	@Override
	public void remove(@NotNull String path, UserAuthentication<?> auth) throws StoreException {
	}
}
