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

package it.smartcommunitylabdhub.core.config.handlers;

import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.condition.ConsumesRequestCondition;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.ProducesRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

public class VersionedHandlerMapping extends RequestMappingHandlerMapping {

    private static final String VERSION_ATTRIBUTE = "version";

    @Override
    protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
        String version = extractVersionFromRequest(request);
        request.setAttribute(VERSION_ATTRIBUTE, version);
        return super.lookupHandlerMethod(lookupPath, request);
    }

    private String extractVersionFromRequest(HttpServletRequest request) {
        // Extract the API version from the request headers or parameters
        // Here, you can use X-API-Version header or a request parameter
        // Modify this code according to how you want to extract the version
        return request.getHeader("X-API-Version");
    }

    @Override
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        RequestMappingInfo mappingInfo = super.getMappingForMethod(method, handlerType);
        if (mappingInfo != null) {
            String version = getVersionFromHandler(handlerType);
            if (version != null) {
                RequestMappingInfo versionedMapping = createVersionedMappingInfo(mappingInfo, version);
                return versionedMapping;
            }
        }
        return mappingInfo;
    }

    /**
     * Get Version From Hanlder
     *
     * @param handlerType
     * @return
     */
    private String getVersionFromHandler(Class<?> handlerType) {
        ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiVersion.class);
        if (apiVersion != null) {
            return apiVersion.value();
        }
        return null;
    }

    private RequestMappingInfo createVersionedMappingInfo(RequestMappingInfo mappingInfo, String version) {
        // String originalPattern =
        // mappingInfo.getPathPatternsCondition().getPatterns().iterator().next()
        // .getPatternString();

        String originalPattern = Optional
            .ofNullable(mappingInfo)
            .map(RequestMappingInfo::getPathPatternsCondition)
            .map(PathPatternsRequestCondition::getPatterns)
            .flatMap(patterns -> patterns.stream().findFirst())
            .map(PathPattern::getPatternString)
            .orElse("/error");

        String versionedPattern = "/api/" + version + originalPattern;

        // Putting the version as dynamic parameter I cannot register multiple
        // times the same path
        // String versionedPattern = "/api/{version}" + originalPattern;

        if (mappingInfo != null) {
            RequestMappingInfo.Builder builder = mappingInfo
                .mutate()
                .paths(versionedPattern)
                .methods(mappingInfo.getMethodsCondition().getMethods().toArray(RequestMethod[]::new))
                .params(mappingInfo.getParamsCondition().getExpressions().toArray(String[]::new))
                .headers(mappingInfo.getHeadersCondition().getExpressions().toArray(String[]::new))
                .consumes(getMediaTypeStrings(mappingInfo.getConsumesCondition()))
                .produces(getMediaTypeStrings(mappingInfo.getProducesCondition()));

            if (mappingInfo.getName() != null) {
                builder.mappingName(mappingInfo.getName());
            }
            if (mappingInfo.getCustomCondition() != null) {
                builder.customCondition(mappingInfo.getCustomCondition());
            }

            return builder.build();
        } else {
            return RequestMappingInfo.paths(versionedPattern).build();
        }
    }

    private String[] getMediaTypeStrings(ProducesRequestCondition producesCondition) {
        return producesCondition.getProducibleMediaTypes().stream().map(MediaType::toString).toArray(String[]::new);
    }

    private String[] getMediaTypeStrings(ConsumesRequestCondition consumesCondition) {
        return consumesCondition.getConsumableMediaTypes().stream().map(MediaType::toString).toArray(String[]::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleMatch(RequestMappingInfo info, String lookupPath, HttpServletRequest request) {
        String version = (String) request.getAttribute(VERSION_ATTRIBUTE);
        if (version != null) {
            Map<String, String> uriTemplateVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
            );
            if (uriTemplateVariables != null) {
                uriTemplateVariables.put("version", version);
            }
        }
        super.handleMatch(info, lookupPath, request);
    }
}
