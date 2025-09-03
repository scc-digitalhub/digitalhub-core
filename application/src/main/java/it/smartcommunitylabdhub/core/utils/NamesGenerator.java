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

package it.smartcommunitylabdhub.core.utils;

import com.google.common.io.Files;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NamesGenerator implements StringKeyGenerator, InitializingBean {

    private List<String> adjectives = Collections.emptyList();
    private List<String> names = Collections.emptyList();

    @Autowired
    ResourceLoader resourceLoader;

    @Override
    public void afterPropertiesSet() throws Exception {
        //load wordlists from txt files
        //TODO make configurable via props
        log.debug("Loading wordlists...");

        this.adjectives =
            Collections.unmodifiableList(
                Files.readLines(
                    resourceLoader.getResource("classpath:wordlist/adj.txt").getFile(),
                    StandardCharsets.UTF_8
                )
            );

        log.debug("Loaded {} adjectives", this.adjectives.size());

        this.names =
            Collections.unmodifiableList(
                Files.readLines(
                    resourceLoader.getResource("classpath:wordlist/animals.txt").getFile(),
                    StandardCharsets.UTF_8
                )
            );
        log.debug("Loaded {} names", this.names.size());
    }

    @Override
    public String generateKey() {
        int ap = ThreadLocalRandom.current().nextInt(adjectives.size());
        int np = ThreadLocalRandom.current().nextInt(names.size());
        String key = adjectives.get(ap) + "-" + names.get(np);

        log.trace("key generated: {}", key);

        return key;
    }
}
