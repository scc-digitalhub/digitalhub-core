/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.containerimages.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageDescription {

    /** {@code org.opencontainers.image.title} – human-readable image name */
    private String title;

    /** {@code org.opencontainers.image.description} – short description, or Docker Hub short description */
    private String description;

    /** Full markdown README – populated only for Docker Hub images */
    private String fullDescription;

    /** {@code org.opencontainers.image.documentation} – URL to documentation */
    private String documentation;

    /** {@code org.opencontainers.image.source} – URL to source code repository */
    private String source;
}
