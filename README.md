# DigitalHub CORE

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/scc-digitalhub/digitalhub-core/release.yaml?event=release) [![license](https://img.shields.io/badge/license-Apache%202.0-blue)](https://github.com/scc-digitalhub/digitalhub-core/LICENSE) ![GitHub Release](https://img.shields.io/github/v/release/scc-digitalhub/digitalhub-core)
![Status](https://img.shields.io/badge/status-stable-gold)

**Digital Hub** is an Open-Source platform for building and managing Data and AI applications and services.
Bringing the principles of **DataOps**, **MLOps**, **DevOps** and **GitOps**, integrating the state-of-art open source technologies and modern standards, DigitalHub extends your
development process and operations to the whole application life-cycle, from exploration to deployment, monitoring, and evolution.

![Overview](https://scc-digitalhub.github.io/docs/0.11/images/intro.png)

- **Explore**. Use on-demand interactive scalable workspaces of your choice to code, explore, experiment and analyze the data and ML. Workspace provide a secure and isolated execution environments natively connected to the platform, such as Jupyter Notebooks or VS Code, Dremio distributed query engine, etc. Use extensibility mechanisms provided by the underlying workspace manager to bring your own workspace templates into the platform.
- **Process**. Use persistent storages (Datalake and Relational DBs) to manage structured and non-structured data on top of the data abstraction layer. Elaborate data, perform data analysis activites and train AI models using frameworks and libraries of your choice (e.g., from python-based to DBT, to arbitrary containers). Manage the supporting computational and storage resources in a declarative and transparent manner.
- **Execute**. Delegate the code execution, image preparation, run-time operations and services to the underlying Kubernetes-based execution infrastructure, Serverless platform, and pipeline execution automation environment.
- **Integrate**. Build new AI services and expose your data in a standard and interoperable manner, to facilitate the integration within different applications, systems, business intelligence tools and visualizations.

To support this functionality, the platform relies on scalable Kubernetes platform and its extensions (operators) as well as on the modular architecture and functionality model that allows for dealing with arbitrary jobs, functions, frameworks and solutions without affecting your development workflow. The underlying methodology and management approach aim at facilitating the re-use, reproducibility, and portability of the solution among different contexts and settings.

Explore the full documentation at the [link](https://scc-digitalhub.github.io/docs/).

The full [CHANGELOG](CHANGELOG.md) is available in the root of the repo.

## Quick start

DigitalHub Core is part of the DigitalHub platform, and depends on external components to support the complete set of functionalities. To bootstrap the platform in its entirety please see the documentation at [link](https://scc-digitalhub.github.io/docs/quickstart/)

Nevertheless, it is possible to run _CORE_ standalone, by:

- deploying the pre-built images in a local Kubernetes environment such as minikube
- deploying the pre-built images in Docker/Podman
- running the application from dist/source

Running locally is suitable only for testing and developing the application itself.

### Running in kubernetes

If you have a kubernetes cluster available, deploy the latest core image using the files in `hack/k8s`.
Core requires a service account with all the permissions required to interact with the Kubernetes API.

```sh
kubectl apply -f hack/k8s
```

After configuring the environment and pulling the images, the core API and console should be accessible via http from the service named `core`.

For example, with minikube:

```sh
minikube service core

|-----------|------|-------------|---------------------------|
| NAMESPACE | NAME | TARGET PORT |            URL            |
|-----------|------|-------------|---------------------------|
| default   | core | http/8080   | http://192.168.49.2:30180 |
|-----------|------|-------------|---------------------------|


```

### Running in docker/podman

If you have a docker/podman environment and want to test DigitalHub core, download the latest stable version from the repository and launch it via

```sh
docker pull ghcr.io/scc-digitalhub/digitalhub-core:latest
docker run -p 8080:8080 ghcr.io/scc-digitalhub/digitalhub-core:latest
```

After the bootstrap, the web console will be accessible at `http://localhost:8080`.

The application leverages an internal embedded database along with an internal index and query engine. To persist data between container restarts mount a volume into the `data` folder as follows:

```sh
mkdir data && chmod 777 data
docker run -p 8080:8080 -v $(pwd)/data:/app/data ghcr.io/scc-digitalhub/digitalhub-core:latest
```

If a Kubernetes deployment is accessible via '.kube/config', CORE will be able to launch tasks and runs, but the actual execution may end in error due to limited network connectivity between docker and the containers running inside the kubernetes cluster. Without Kubernetes the application functionalities will be limited to CRUD.
If the local docker environment is accessible via IP or FQDN from inside the Kubernetes cluster, use the address as `DH_ENDPOINT` to enable communication from inside Kubernetes towards CORE and run the container with the host network. Do note that kube config needs to have certificates embedded in order to be complete.

```
docker run -p 8080:8080 -e DH_ENDPOINT=http://172.17.0.1:8080 -e KUBECONFIG=/app/.kube/config -v $(pwd)/data:/app/data -v ~/.kube/config:/app/.kube/config --network=host ghcr.io/scc-digitalhub/digitalhub-core:latest
```

## Configuration

You can locate the project configuration file in the **application [core]** module, named **_resources/application.yml_**. This file specifies configurations for various sections.

Each section allows configurations to be written directly in the YAML file or through environment variables (ENV).

The full configurable parameters are in [CONFIGURATION](CONFIGURATION.md).

The minimal set of configuration parameters required to deploy CORE are listed below.

**Server and networking**

Core is a networked application which exposes an API and a user web console. It is fundamental that the application is accessible by every client, web or machine, via a public facing address. To configure a split-horizon (unsupported!) system override the DH_ENDPOINT variable in the kubernetes env with the locally accessible address.

| KEY               | DEFAULT               | DESCRIPTION                                                                                                       |
| ----------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------- |
| DH_ENDPOINT       | http://localhost:8080 | external user facing address for core. The endpoint needs to be accessible from the cluster and from the outside. |
| DH_NAME           | dhcore                | The name of the application as shown in API responses and user console                                            |
| DH_CONTACTS_EMAIL |                       | A contact email for the deployment. Blank by default                                                              |

**Database**
While CORE can use the internal embedded H2 Database, it is not suitable for production usage.
Deploy a PostgreSQL (>12) database and provide the connection details as shown in table.

| KEY           | DEFAULT                         | DESCRIPTION                                          |
| ------------- | ------------------------------- | ---------------------------------------------------- |
| JDBC_PLATFORM | h2                              | Database platform. Valid values `h2` or `postgresql` |
| JDBC_DIALECT  | org.hibernate.dialect.H2Dialect | Hibernate dialet                                     |
| JDBC_DRIVER   | org.h2.Driver                   | JDBC driver                                          |
| JDBC_PASS     | password                        | Password for connecting to database                  |
| JDBC_USER     | sa                              | User for connecting to database                      |
| JDBC_URL      | jdbc:h2:file:./data/db          | Database JDBC url                                    |

An example with postgresql:

```
JDBC_DRIVER=org.postgresql.Driver
JDBC_DIALECT=org.hibernate.dialect.PostgreSQLDialect
JDBC_PASS=password
JDBC_USER=dhcore
JDBC_URL=jdbc:postgresql://localhost:5432/dhcore
JDBC_PLATFORM=postgresql
```

**Security and Authentication**

CORE implements a token based (OAuth2) authorization mechanism based on the principles of minimal privileges and zero-knowledge, where every job is provided with a set of unique, temporary credentials derived from the user authentication context.

Credentials are issued as _access tokens_ and _refresh tokens_: configure a suitable duration for job execution. Do note that the minimum supported duration for access tokens is 2 hours, and 8 hours for refresh tokens. Any value under that threshold may produce unexpected issues.

Certificated are autogenerated at every start for test purposes: please create a valid JSON Web Key and pass to CORE to secure the application.

| KEY                        | DEFAULT                    | DESCRIPTION                                                    |
| -------------------------- | -------------------------- | -------------------------------------------------------------- |
| JWT_KEYSTORE_PATH          | classpath:/keystore.jwks   | Path for the JWKS file containing the keystore.                |
| JWT_KEYSTORE_KID           |                            | Key Identifier for selecting the signing key from the keystore |
| JWT_ACCESS_TOKEN_DURATION  | 28800                      | Access token duration (in seconds). Defaults to 8 hours        |
| JWT_REFRESH_TOKEN_DURATION | 2592000                    | Refresh token duration (in seconds). Defaults to 30 days       |
| JWT_CLIENT_ID              | ${security.basic.username} | Client id                                                      |
| JWT_CLIENT_SECRET          | ${security.basic.password} | Client secret. Not required                                    |

To fully activate the authorization system administrators need to enable one of the authentication providers between basic (a shared username/password) or oidc (OpenId Connect). Only the latter (oidc) is suitable for production use.

| KEY                        | DEFAULT              | DESCRIPTION                                          |
| -------------------------- | -------------------- | ---------------------------------------------------- |
| DH_AUTH_BASIC_USER         | admin                | Basic authentication username                        |
| DH_AUTH_BASIC_PASSWORD     |                      | Basic authentication password. Disabled by default   |
| DH_AUTH_OIDC_ISSUER_URI    |                      | OpenId Connect issuer to use as upstream IdP         |
| DH_AUTH_OIDC_CLIENT_NAME   | ${application.name}  | Client Name used to connect to upstream IdP          |
| DH_AUTH_OIDC_CLIENT_ID     |                      | Client Id used to connect to upstream IdP            |
| DH_AUTH_OIDC_CLIENT_SECRET |                      | Client Secret used to connect to upstream IdP        |
| DH_AUTH_OIDC_CLAIM         | roles                | (Id Token) claim used to extract user authorizations |
| DH_AUTH_OIDC_USERNAME      | preferred_username   | (Id Token) claim used to extract username            |
| DH_AUTH_OIDC_SCOPE         | openid,email,profile | Scopes used to connect to upstream IdP               |

**Kubernetes**
Core uses K8S as the compute engine and requires a set of valid credentials to interact with the API, for example in the form of a ServiceAccount with the right privileges. See `hack/k8s` for details.
Furthermore, it is advisable to configure the integration with details regarding _namespaces_, _resources_ and _features_ (logging, metrics...) of your cluster.

| KEY                            | DEFAULT                                                    | DESCRIPTION                                                               |
| ------------------------------ | ---------------------------------------------------------- | ------------------------------------------------------------------------- |
| K8S_NAMESPACE                  | default                                                    | K8s namespace to use                                                      |
| K8S_ENABLE_LOGS                | true                                                       | Enable log collection from Pods                                           |
| K8S_ENABLE_METRICS             | true                                                       | Enable metrics collection from Metric Server                              |
| K8S_ENABLE_RESULTS             | default                                                    | Enable collection of k8s resources created for runs for logging/debugging |
| K8S_SEC_DISABLE_ROOT           | false                                                      | Enforce non-root workloads by applying security policy                    |
| K8S_SEC_USER                   |                                                            | User ID used for runAsUser                                                |
| K8S_SEC_GROUP                  |                                                            | Group ID used for runAsGroup                                              |
| K8S_IMAGE_PULL_POLICY          | IfNotPresent                                               | Image pull policy for containers                                          |
| K8S_INIT_IMAGE                 | ghcr.io/scc-digitalhub/digitalhub-core-builder-tool:latest | Image used for init containers                                            |
| K8S_SERVICE_TYPE               | NodePort                                                   | Default service type used to deploy services                              |
| K8S_RESOURCE_CPU_DEFAULT       | 100m                                                       | Default CPU requested quota                                               |
| K8S_RESOURCE_CPU_LIMIT         |                                                            | Default CPU limit quota                                                   |
| K8S_RESOURCE_MEM_DEFAULT       | 64m                                                        | Default RAM requested quota                                               |
| K8S_RESOURCE_MEM_LIMIT         |                                                            | Default RAM limit quota                                                   |
| K8S_RESOURCE_GPU_KEY           | nvidia.com/gpu                                             | Key used to define gpu resources                                          |
| K8S_RESOURCE_PVC_DEFAULT       | 2Gi                                                        | Default PVC size requested                                                |
| K8S_RESOURCE_PVC_LIMIT         |                                                            | Default PVC size limit                                                    |
| K8S_RESOURCE_PVC_STORAGE_CLASS |                                                            | Default PVC storage class                                                 |

**External services**

Core can leverage externally deployed components for specific advanced functionalities, such as credentials generation, full text search, event queue etc.

- **Solr**
  Solr is used to externalize indexing and full-text search. Pass the url and the collection to enable.
  When disabled, CORE uses an internal search engine based on Lucene.

  SOLR needs the collection and schema+fields initialized:

  - either pass ADMIN credentials to core to auto-create, or
  - create beforehand leveraging schemas in `solr/` and avoid passing ADMIN credentials

| KEY                 | DEFAULT       | DESCRIPTION                                                      |
| ------------------- | ------------- | ---------------------------------------------------------------- |
| SOLR_URL            | false         | URL of solr                                                      |
| SOLR_USER           |               | Username for solr authentication                                 |
| SOLR_PASSWORD       |               | Password for solr authentication                                 |
| SOLR_ADMIN_USER     | SOLR_USER     | Admin Username for solr authentication.                          |
| SOLR_ADMIN_PASSWORD | SOLR_PASSWORD | Admin Password for solr authentication                           |
| SOLR_COLLECTION     | dhcore        | Name of the collection                                           |
| SOLR_REINDEX        | never         | Set to `always` to reindex the whole repository at every restart |

- **Argo**

Argo is used to evaluate and orchestrate complex pipelines in K8s. Configure the parameters matching the Argo deployment to enable the integration

| KEY                               | DEFAULT                     | DESCRIPTION |
| --------------------------------- | --------------------------- | ----------- |
| ARGOWORKFLOWS_ARTIFACTS_CONFIGMAP | artifact-repositories       |             |
| ARGOWORKFLOWS_ARTIFACTS_KEY       | default-artifact-repository |             |
| ARGOWORKFLOWS_SERVICE_ACCOUNT     | default                     |             |
| ARGOWORKFLOWS_USER                | 1000                        |             |

- **S3**

The platform supports S3 as artifacts store. In order to provide jobs and user tools (i.e. console,cli, sdk) with credentials, it is possible to configure a static S3 Provider which will distribute the same set of static credentials.
Do note that this configuration is aimed at testing and local deployment. Use a different provider or inject credentials outside CORE for production.

| KEY                     | DEFAULT | DESCRIPTION                             |
| ----------------------- | ------- | --------------------------------------- |
| S3_CREDENTIALS_PROVIDER | false   | Set to `enable` to activate             |
| S3_ENDPOINT_URL         |         | Custom ENDPOINT for S3 service (if any) |
| S3_BUCKET               |         | Default bucket to use (if any)          |
| AWS_DEFAULT_REGION      |         | Default region (if any)                 |
| AWS_ACCESS_KEY          |         | Access Key (required)                   |
| AWS_SECRET_KEY          |         | Secret Key (required)                   |
| S3_PATH_STYLE_ACCESS    |         | Enable/disable path style access        |

- **Minio**

When the platform is deployed along side Minio, administrators can configure the usage of dynamic credentials in place of static S3. This way every run will obtain a different set of temporary credentials derived from the ARN role defined in config. The flow used is `AssumeRole`: if available from the S3 provider, this config can be used with providers different than Minio.

| KEY                          | DEFAULT   | DESCRIPTION                             |
| ---------------------------- | --------- | --------------------------------------- |
| MINIO_CREDENTIALS_PROVIDER   | false     | Set to `enable` to activate             |
| MINIO_CREDENTIALS_ENDPOINT   |           | Custom ENDPOINT for S3 service (if any) |
| MINIO_CREDENTIALS_REGION     | us-east-1 | Default region (if any)                 |
| MINIO_CREDENTIALS_BUCKET     |           | Default bucket to use (if any)          |
| MINIO_CREDENTIALS_ACCESS_KEY |           | Access Key (required)                   |
| MINIO_CREDENTIALS_SECRET_KEY |           | Secret Key (required)                   |

- **PostgreSQL**

The platform supports storing data items such as tables and vectors inside a PostgreSQL instance, separated from the one dedicated to JDBC. In order to provide jobs and user tools (i.e. console,cli, sdk) with DB credentials, it is possible to configure a dynamic provider which will exchange credentials to obtain a temporary (i.e. expiring) role in the DB.
Configure the properties to enable.

| KEY                     | DEFAULT | DESCRIPTION                          |
| ----------------------- | ------- | ------------------------------------ |
| DB_CREDENTIALS_PROVIDER | false   | Set to `enable` to activate          |
| DB_CREDENTIALS_DATABASE |         | Name of the database to use          |
| DB_CREDENTIALS_ENDPOINT |         | Endpoint of the DB STS api           |
| DB_CREDENTIALS_ROLE     |         | Role to assume in the DB             |
| DB_CREDENTIALS_USER     |         | Username for authenticating with STS |
| DB_CREDENTIALS_PASSWORD |         | Password for authenticating with STS |

## Source code and modules

DigitalHub core is a multi-module java application, where every component is defined by its scope and role in the project.

The project is essentially divided into the following parts:

1. **Core**
2. **Modules**
3. **Frontend**

_Core_ manages all the basic elements that are the foundation of the project, while the _frontend_ integrates the user console.

_Modules_ include:

1. Common objects shared across the project and utilized by other modules.
2. The authorization and authentication component
3. Credentials providers for external data stores:

   - DB for obtaining dynamic, temporary database credentials
   - Minio for obtaining dynamic, temporary S3 credentials for Minio

4. The Fsm module is the state machine integrated with Core that ensures a function run progresses correctly. This prevents a function's execution from ending in an invalid state.
5. Files handles the access to file stores
6. Frameworks are the execution engines, supporting the various execution stacks:
   - K8s executes tasks on Kubernetes. We currently support Job, CronJob, Deployment, and Serve frameworks, as well as Monitor functionality for our running objects
   - The Kaniko module, integrated with Core, provides the capability to build images for running specific functions.
   - Argo handles the execution of complex workflows via the Argo engine
7. Runtimes modules transform user-defined functions into executables for frameworks:
   - Dbt database transformation function
   - Kfp workflow execution
   - KubeAI
   - ML Model serving
   - Python functions
   - Container
8. Triggers support automation for functions
   - Scheduler is a cron-like trigger
   - Lifecycle triggers executions based on entities events, as managed by the FSM

### Build from source

In order to build core from source clone the project along with all submodules into a folder.

```sh
# Download DH Core from GitHub
git clone https://github.com/scc-digitalhub/digitalhub-core.git && cd digitalhub-core

# Update all submodules
git submodule init
git submodule update
```

Building the application requires an environment with:

- Java 21 or higher
- Maven 3.8 or higher
- Docker (to build a container image)

To install all the dependencies you can use [SDKMAN](https://sdkman.io/):

```sh
curl -s "https://get.sdkman.io" | bash
```

and then

```sh
sdk install java
sdk install maven
```

Follow the [installation docs](https://sdkman.io/install) for more information on initializing a suitable development environment.

CORE uses Maven as build system and is split into multiple modules. While it is possible to build directly via `mvn package`, there is a build script optimized for the project at `build.sh`.

Run it to build a packaged distributable for the application:

```sh
sh build.sh
```

If you want to build without the script, it is advisable to build _modules_ and _frontend_ before building the core _application_:

```sh
./mvnw clean install -pl '!application'
./mvnw clean package -pl 'application'

```

Afterwards, you can run core via:

```
./mvnw spring-boot:run -pl application

```

Do note that running the application from source is advisable only for developing new features or debugging issues.

### Build container images

To make a local container image, use the `Dockerfile` included with the project. Do note that the file uses the cache image published by the project: if you want to build everything locally you need to use the `Dockerfile-nocache` as input.

```sh
docker built -t core:snapshot -f Dockerfile-nocache .
```

and then run it as container:

```
docker run -p 8080:8080 core:snapshot
```

## Security Policy

The current release is the supported version. Security fixes are released together with all other fixes in each new release.

If you discover a security vulnerability in this project, please do not open a public issue.

Instead, report it privately by emailing us at digitalhub@fbk.eu. Include as much detail as possible to help us understand and address the issue quickly and responsibly.

## Contributing

To report a bug or request a feature, please first check the existing issues to avoid duplicates. If none exist, open a new issue with a clear title and a detailed description, including any steps to reproduce if it's a bug.

To contribute code, start by forking the repository. Clone your fork locally and create a new branch for your changes. Make sure your commits follow the [Conventional Commits v1.0](https://www.conventionalcommits.org/en/v1.0.0/) specification to keep history readable and consistent.

Once your changes are ready, push your branch to your fork and open a pull request against the main branch. Be sure to include a summary of what you changed and why. If your pull request addresses an issue, mention it in the description (e.g., “Closes #123”).

Please note that new contributors may be asked to sign a Contributor License Agreement (CLA) before their pull requests can be merged. This helps us ensure compliance with open source licensing standards.

We appreciate contributions and help in improving the project!

## Authors

This project is developed and maintained by **DSLab – Fondazione Bruno Kessler**, with contributions from the open source community. A complete list of contributors is available in the project’s commit history and pull requests.

For questions or inquiries, please contact: [digitalhub@fbk.eu](mailto:digitalhub@fbk.eu)

## Copyright and license

Copyright © 2025 DSLab – Fondazione Bruno Kessler and individual contributors.

This project is licensed under the Apache License, Version 2.0.
You may not use this file except in compliance with the License. Ownership of contributions remains with the original authors and is governed by the terms of the Apache 2.0 License, including the requirement to grant a license to the project.
