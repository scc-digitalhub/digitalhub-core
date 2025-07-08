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

package it.smartcommunitylabdhub.framework.kaniko;

import io.kubernetes.client.openapi.ApiClient;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class KanikoImageBuilderTest {

    //////////////////////// TO USE THI BUILDER //////////////////////////////
    // HelloWorld.java deve essere messo in /config
    //
    // FROM {{baseImage}}
    //
    // # Add additional instructions here
    // COPY HelloWorld.java /app
    // WORKDIR /app
    // RUN javac HelloWorld.java
    //
    // ENTRYPOINT ["java", "HelloWorld"]
    //
    //////////////////////////////////////

    @Autowired
    ApiClient client;

    // @Value("${kaniko.source.path}")
    // private String kanikoSourcePath;

    // @Value("${kaniko.target.path}")
    // private String kanikoTargetPath;

    // @Test
    void testBuildDockerImage() throws IOException {
        String basePath = Paths.get(System.getProperty("user.dir")).getParent().toString();
        // Create a sample DockerBuildConfiguration
        //        DockerBuildConfig dockerBuildConfig = new DockerBuildConfig();
        //        dockerBuildConfig.setDockerTemplatePath(Path.of(basePath, kanikoSourcePath).toString());
        //        dockerBuildConfig.setDockerTargetPath(Path.of(basePath, kanikoTargetPath).toString());
        //        dockerBuildConfig.setSharedData("https://www.dwsamplefiles.com/?dl_id=557");
        //        dockerBuildConfig.setBaseImage("openjdk:11");
        //        dockerBuildConfig
        //                .addCommand("WORKDIR /app")
        //                .addCommand("COPY . /app")
        //                .addCommand("RUN javac ./HelloWorld.java");
        //        dockerBuildConfig.setEntrypointCommand("\"java\", \"HelloWorld\"");
        //
        //        JobBuildConfig jobBuildConfig = JobBuildConfig
        //                .builder()
        //                .type("function")
        //                .name("testfunction")
        //                .uuid(UUID.randomUUID().toString())
        //                .build();
        //        // Invoke the buildDockerImage method
        //        CompletableFuture<?> kaniko = KanikoImageBuilder.buildDockerImage(client, dockerBuildConfig, jobBuildConfig);
        //
        //        kaniko.join();
    }
}
