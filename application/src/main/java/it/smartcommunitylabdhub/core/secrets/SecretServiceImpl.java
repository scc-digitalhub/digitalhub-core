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

package it.smartcommunitylabdhub.core.secrets;

import it.smartcommunitylabdhub.commons.annotations.common.Identifier;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.secret.Secret;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.commons.services.SecretsProvider;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SpecValidator;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.secrets.persistence.SecretEntity;
import it.smartcommunitylabdhub.core.secrets.specs.SecretBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Service
@Transactional
@Slf4j
public class SecretServiceImpl implements SecretService {

    // private static final String K8S_PROVIDER = "kubernetes";
    private static final String PATH_FORMAT = "%s://%s/%s";
    private static final Pattern PATH_PATTERN = Pattern.compile("secret://([\\w-]+)");

    @Autowired
    private SearchableEntityRepository<SecretEntity, Secret> entityRepository;

    @Autowired
    private EntityRepository<Project> projectService;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    private Map<String, SecretsProvider> providers = new HashMap<>();

    @Autowired(required = false)
    public void setProviders(List<SecretsProvider> providers) {
        this.providers = new HashMap<>();
        providers
            .stream()
            .forEach(p -> {
                //read identifier as key
                //TODO move to a shared registry
                Identifier id = p.getClass().getAnnotation(Identifier.class);
                if (id != null) {
                    //register
                    this.providers.put(id.value(), p);
                }
            });
    }

    @Override
    public Page<Secret> listSecrets(Pageable pageable) {
        log.debug("list secrets page {}", pageable);
        try {
            return entityRepository.list(pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Secret> listSecretsByUser(@NotNull String user) {
        log.debug("list all secrets for user {}  ", user);
        try {
            return entityRepository
                .searchAll(CommonSpecification.createdByEquals(user))
                .stream()
                .collect(Collectors.toList());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<Secret> listSecretsByProject(@NotNull String project) {
        log.debug("list secrets for project {}", project);
        Specification<SecretEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityRepository.searchAll(specification).stream().collect(Collectors.toList());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Page<Secret> listSecretsByProject(@NotNull String project, Pageable pageable) {
        log.debug("list secrets for project {}", project);
        Specification<SecretEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
        try {
            return entityRepository.search(specification, pageable);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Secret findSecret(@NotNull String id) {
        log.debug("find secret with id {}", String.valueOf(id));
        try {
            return entityRepository.find(id);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Secret getSecret(@NotNull String id) throws NoSuchEntityException {
        log.debug("get secret with id {}", String.valueOf(id));

        try {
            return entityRepository.get(id);
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.SECRET.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Secret createSecret(@NotNull Secret dto)
        throws DuplicatedEntityException, BindException, IllegalArgumentException {
        log.debug("create secret");
        try {
            //validate project
            String projectId = dto.getProject();
            if (!StringUtils.hasText(projectId) || projectService.find(projectId) == null) {
                throw new IllegalArgumentException("invalid or missing project");
            }

            try {
                //parse base
                SecretBaseSpec secretSpec = new SecretBaseSpec();
                secretSpec.configure(dto.getSpec());

                String path = secretSpec.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new IllegalArgumentException("invalid or missing path in spec");
                }

                //path must match
                if (!PATH_PATTERN.matcher(path).matches()) {
                    throw new IllegalArgumentException("invalid or missing path in spec");
                }

                // Parse and export Spec
                Spec spec = specRegistry.createSpec(dto.getKind(), dto.getSpec());
                if (spec == null) {
                    throw new IllegalArgumentException("invalid kind");
                }

                //validate
                validator.validateSpec(spec);

                //update spec as exported
                dto.setSpec(spec.toMap());

                //check if a secret with this name already exists for the project
                Optional<Secret> existingSecret = listSecretsByProject(projectId)
                    .stream()
                    .filter(s -> s.getName().equals(dto.getName()))
                    .findFirst();
                if (existingSecret.isPresent()) {
                    throw new DuplicatedEntityException(EntityName.SECRET.toString(), dto.getName());
                }

                // store in DB, do not create physically the secret
                Secret res = entityRepository.create(dto);
                return res;
            } catch (DuplicatedEntityException e) {
                throw new DuplicatedEntityException(EntityName.SECRET.toString(), dto.getId());
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Secret updateSecret(@NotNull String id, @NotNull Secret dto)
        throws NoSuchEntityException, BindException, IllegalArgumentException {
        log.debug("update secret with id {}", String.valueOf(id));
        try {
            //fetch current and merge
            Secret current = entityRepository.get(id);

            //keep spec
            dto.setSpec(current.getSpec());

            //update
            Secret res = entityRepository.update(id, dto);
            return res;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(EntityName.SECRET.toString());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteSecret(@NotNull String id) {
        log.debug("delete secret with id {}", String.valueOf(id));
        try {
            Secret secret = findSecret(id);
            if (secret != null) {
                //read spec and call provider
                SecretBaseSpec secretSpec = new SecretBaseSpec();
                secretSpec.configure(secret.getSpec());

                String path = secretSpec.getPath();
                String provider = secretSpec.getProvider();
                Matcher matcher = PATH_PATTERN.matcher(path);

                if (
                    StringUtils.hasText(path) &&
                    StringUtils.hasText(provider) &&
                    matcher.matches() &&
                    providers != null &&
                    providers.containsKey(provider)
                ) {
                    log.debug(
                        "cascade delete secret data for secret with id {} via provider {}",
                        String.valueOf(id),
                        provider
                    );

                    String key = matcher.group(1);
                    providers
                        .get(provider)
                        .clearSecretData(getSecretPath(provider, getProjectSecretName(secret.getProject()), key));
                }

                //delete the secret
                entityRepository.delete(id);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void deleteSecretsByProject(@NotNull String project) {
        log.debug("delete secrets for project {}", project);
        try {
            //delete one-by-one to clear data
            Specification<SecretEntity> specification = Specification.allOf(CommonSpecification.projectEquals(project));
            entityRepository.searchAll(specification).stream().forEach(s -> deleteSecret(s.getName()));
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * Secret data (via provider)
     */

    @Override
    public Map.Entry<String, String> getSecretData(@NotNull String id) {
        log.debug("read secret data with id {}", String.valueOf(id));
        try {
            Secret secret = getSecret(id);
            //read spec and call provider
            SecretBaseSpec secretSpec = new SecretBaseSpec();
            secretSpec.configure(secret.getSpec());

            String path = secretSpec.getPath();
            String provider = secretSpec.getProvider();
            Matcher matcher = PATH_PATTERN.matcher(path);

            if (
                StringUtils.hasText(path) &&
                StringUtils.hasText(provider) &&
                matcher.matches() &&
                providers != null &&
                providers.containsKey(provider)
            ) {
                log.debug("read secret data for secret with id {} via provider {}", String.valueOf(id), provider);

                String key = matcher.group(1);
                String value = providers
                    .get(provider)
                    .readSecretData(getSecretPath(provider, getProjectSecretName(secret.getProject()), key));

                return Map.entry(key, value);
            }

            throw new StoreException("invalid or unavailable provider");
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void storeSecretData(@NotNull String id, @NotNull String value) {
        log.debug("store secret data with id {}", String.valueOf(id));
        try {
            Secret secret = getSecret(id);
            //read spec and call provider
            SecretBaseSpec secretSpec = new SecretBaseSpec();
            secretSpec.configure(secret.getSpec());

            String path = secretSpec.getPath();
            String provider = secretSpec.getProvider();
            Matcher matcher = PATH_PATTERN.matcher(path);

            if (
                StringUtils.hasText(path) &&
                StringUtils.hasText(provider) &&
                matcher.matches() &&
                providers != null &&
                providers.containsKey(provider)
            ) {
                log.debug("store secret data for secret with id {} via provider {}", String.valueOf(id), provider);

                String key = matcher.group(1);
                providers
                    .get(provider)
                    .writeSecretData(getSecretPath(provider, getProjectSecretName(secret.getProject()), key), value);
            } else {
                throw new StoreException("invalid or unavailable provider");
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Map<String, String> getSecretData(@NotNull String project, @NotNull Set<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptyMap();

        if (providers == null || providers.isEmpty()) {
            return Collections.emptyMap();
        }

        //fetch requested secrets
        List<Secret> secrets = listSecretsByProject(project)
            .stream()
            .filter(s -> names.contains(s.getName()))
            .collect(Collectors.toList());

        //unseal project secret data via provider
        return secrets
            .stream()
            .map(s -> getSecretData(s.getId()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public void storeSecretData(@NotNull String project, @NotNull Map<String, String> values) {
        if (values == null || values.isEmpty()) return;

        if (providers == null || providers.isEmpty()) {
            return;
        }

        //fetch requested secrets
        List<Secret> secrets = listSecretsByProject(project)
            .stream()
            .filter(s -> values.containsKey(s.getName()))
            .collect(Collectors.toList());

        //we expect all secrets to exists *before* settings
        List<String> found = secrets.stream().map(s -> s.getName()).toList();
        List<String> invalid = values.keySet().stream().filter(k -> !found.contains(k)).toList();

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("missing secrets for " + String.join(",", invalid));
        }

        //store project secret data via provider
        secrets.stream().forEach(s -> storeSecretData(s.getId(), values.get(s.getName())));
    }

    // /**
    //  * Group secrets by secret name as stored in provider. Only Kubernetes provider is supported at this moment.
    //  */
    // @Override
    // public Map<String, Set<String>> groupSecrets(String project, Collection<String> secrets) {
    //     Map<String, Set<String>> result = new HashMap<>();
    //     if (secrets != null && !secrets.isEmpty()) {
    //         listSecretsByProject(project)
    //             .stream()
    //             .filter(s -> secrets.contains(s.getName()))
    //             .forEach(secret -> {
    //                 String path = (String) secret.getSpec().get("path");
    //                 Matcher matcher = PATH_PATTERN.matcher(path);
    //                 if (matcher.matches()) {
    //                     String provider = matcher.group(1);
    //                     String secretName = matcher.group(2);
    //                     String key = matcher.group(3);
    //                     if (K8S_PROVIDER.equals(provider)) {
    //                         if (!result.containsKey(secretName)) {
    //                             result.put(secretName, new HashSet<>());
    //                         }
    //                         result.get(secretName).add(key);
    //                     }
    //                 }
    //             });
    //     }
    //     return result.isEmpty() ? Map.of() : result;
    // }

    private String getProjectSecretName(String project) {
        return K8sBuilderHelper.sanitizeNames("proj-secrets-" + project);
    }

    private String getSecretPath(String provider, String secret, String key) {
        return String.format(PATH_FORMAT, provider, secret, key);
    }
}
