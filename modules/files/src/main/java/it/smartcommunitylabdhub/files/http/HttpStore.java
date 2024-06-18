package it.smartcommunitylabdhub.files.http;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import it.smartcommunitylabdhub.commons.models.base.FileInfo;
import it.smartcommunitylabdhub.files.service.FilesStore;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpStore implements FilesStore {

    public static final String[] PROTOCOLS = { "http", "https", "ftp" };
    private RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public String downloadAsUrl(@NotNull String path) {
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
            return url.toExternalForm();
        } catch (MalformedURLException e) {
            //not a valid url...
            return null;
        }
    }

	@Override
	public Map<String, List<FileInfo>> readMetadata(@NotNull String path) {
		Map<String, List<FileInfo>> result = new HashMap<>();
		try {
			String[] split = path.split("/");
			HttpHeaders headers = restTemplate.headForHeaders(new URI(path));
			FileInfo response = new FileInfo();
			response.setPath(path);
			response.setName(split[split.length - 1]);
			response.setContentType(headers.getContentType().toString());
			response.setLength(headers.getContentLength());
			response.setLastModified(Instant.ofEpochMilli(headers.getLastModified()));
			List<FileInfo> list = new ArrayList<>();
			list.add(response);
			result.put(path, list);
		} catch (Exception e) {
			log.error("generate metadata for {}:  {}", path, e.getMessage());
		}
		return result;
	}
}
