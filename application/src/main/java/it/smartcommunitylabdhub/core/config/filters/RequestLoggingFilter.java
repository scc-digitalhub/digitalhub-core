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

package it.smartcommunitylabdhub.core.config.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        WrappedRequest wrappedRequest = new WrappedRequest((HttpServletRequest) request);
        logRequestBody(wrappedRequest);
        chain.doFilter(wrappedRequest, response);
    }

    private void logRequestBody(WrappedRequest request) {
        // Check if the request method is POST or PUT (or any other method you are interested in)
        if (
            request.getMethod().equalsIgnoreCase("GET") ||
            request.getMethod().equalsIgnoreCase("POST") ||
            request.getMethod().equalsIgnoreCase("PUT")
        ) {
            try (BufferedReader reader = request.getReader()) {
                String requestBody = reader.lines().collect(Collectors.joining());
                System.out.println("Request Body: " + requestBody);
                // You can customize the logging mechanism or use a logger here
            } catch (IOException e) {
                // Handle exception, if any
            }
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code, if needed
    }

    @Override
    public void destroy() {
        // Cleanup code, if needed
    }

    private static class WrappedRequest extends HttpServletRequestWrapper {

        private final String body;

        public WrappedRequest(HttpServletRequest request) throws IOException {
            super(request);
            // Read the request body and store it for later use
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader bufferedReader = request.getReader()) {
                char[] charBuffer = new char[1024];
                int bytesRead;
                while ((bytesRead = bufferedReader.read(charBuffer)) != -1) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            }
            body = stringBuilder.toString();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            final byte[] bytes = body.getBytes();
            return new ServletInputStream() {
                private int index;

                @Override
                public boolean isFinished() {
                    return index == bytes.length;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() throws IOException {
                    return index < bytes.length ? bytes[index++] : -1;
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(this.getInputStream()));
        }
    }
}
