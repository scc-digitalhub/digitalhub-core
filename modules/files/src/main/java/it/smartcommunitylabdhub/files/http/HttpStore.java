/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.files.http;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.FilesStore;
import jakarta.annotation.Nullable;
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
    public DownloadInfo downloadAsUrl(@NotNull String path, @Nullable List<Credentials> credentials) {
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
    public List<FileInfo> fileInfo(@NotNull String path, @Nullable List<Credentials> credentials) {
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
    public UploadInfo uploadAsUrl(@NotNull String path, @Nullable List<Credentials> credentials) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo startMultiPartUpload(@NotNull String path, @Nullable List<Credentials> credentials) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo uploadMultiPart(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber,
        @Nullable List<Credentials> credentials
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        @Nullable List<Credentials> credentials
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@NotNull String path, @Nullable List<Credentials> credentials) throws StoreException {}
}
