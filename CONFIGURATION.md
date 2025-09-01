| KEY                               | DEFAULT                                                            | DESCRIPTION |
| --------------------------------- | ------------------------------------------------------------------ | ----------- |
| SERVER_HOST                       | localhost                                                          |             |
| SERVER_PORT                       | 8080                                                               |             |
| SERVER_CONTEXT                    | /                                                                  |             |
| SERVER_TOMCAT_REMOTE_IP_HEADER    | x-forwarded-for                                                    |             |
| SERVER_TOMCAT_PROTOCOL_HEADER     | x-forwarded-proto                                                  |             |
| JDBC_PLATFORM                     | h2                                                                 |             |
| JDBC_DIALECT                      | org.hibernate.dialect.H2Dialect                                    |             |
| JDBC_DRIVER                       | org.h2.Driver                                                      |             |
| JDBC_PASS                         | password                                                           |             |
| JDBC_USER                         | sa                                                                 |             |
| JDBC_URL                          | jdbc:h2:file:./data/db                                             |
| H2_CONSOLE                        | false                                                              |             |
| MANAGEMENT_PORT                   | 8081                                                               |             |
| RUNTIME_DBT_IMAGE                 | ghcr.io/scc-digitalhub/digitalhub-sdk/wrapper-dbt:latest           |
| RUNTIME_KFP_IMAGE                 | ghcr.io/scc-digitalhub/digitalhub-sdk/wrapper-kfp:latest           |
| RUNTIME_PYTHON_IMAGE_3_9          | ghcr.io/scc-digitalhub/digitalhub-serverless/python-runtime:latest |
| RUNTIME_PYTHON_IMAGE_3_10         | ghcr.io/scc-digitalhub/digitalhub-serverless/python-runtime:latest |
| RUNTIME_PYTHON_USER_ID            | ${kubernetes.security.user}                                        |             |
| RUNTIME_PYTHON_GROUP_ID           | ${kubernetes.security.group}                                       |             |
| RUNTIME_SKLEARN_SERVE_IMAGE       | seldonio/mlserver:latest                                           |
| RUNTIME_SKLEARN_SERVE_USER_ID     | ${kubernetes.security.user}                                        |             |
| RUNTIME_SKLEARN_SERVE_GROUP_ID    | ${kubernetes.security.group}                                       |             |
| RUNTIME_MLFLOW_SERVE_IMAGE        | seldonio/mlserver:latest                                           |
| RUNTIME_MLFLOW_SERVE_USER_ID      | ${kubernetes.security.user}                                        |             |
| RUNTIME_MLFLOW_SERVE_GROUP_ID     | ${kubernetes.security.group}                                       |             |
| RUNTIME_HUGGINGFACE_SERVE_IMAGE   | kserve/huggingfaceserver:latest                                    |
| RUNTIME_HUGGINGFACE_USER_ID       | ${kubernetes.security.user}                                        |             |
| RUNTIME_HUGGINGFACE_GROUP_ID      | ${kubernetes.security.group}                                       |             |
| RUNTIME_KUBEAI_ENDPOINT           |                                                                    |
| RUNTIME_FLOWER_SERVER             | flwr/superlink:1.20.0-py3.12-ubuntu24.04                           |             |    
| RUNTIME_FLOWER_CLIENT             | flwr/supernode:1.20.0-py3.12-ubuntu24.04                           |             |    
| RUNTIME_FLOWER_RUNNER             | flwr/clientapp:1.20.0-py3.12-ubuntu24.04                           |             |    
| RUNTIME_FLOWER_USER_ID            |                                                                    |             |    
| RUNTIME_FLOWER_GROUP_ID           |                                                                    |             |    
| RUNTIME_FLOWER_TLS_CA_CERT        |                                                                    |             |    
| RUNTIME_FLOWER_TLS_CA_KEY         |                                                                    |             |    
| RUNTIME_FLOWER_TLS_CONF           | classpath:/runtime-flower/docker/certificate.conf                  |             |
| RUNTIME_FLOWER_TLS_INT_DOMAIN     | ${kubernetes.namespace}                                            |             |
| RUNTIME_FLOWER_TLS_EXT_DOMAIN     |                                                                    |             |
| KANIKO_IMAGE                      | gcr.io/kaniko-project/executor:latest                              |
| KANIKO_IMAGE_PREFIX               | dhcore                                                             |             |
| KANIKO_IMAGE_REGISTRY             | ${registry.name}                                                   |             |
| KANIKO_SECRET                     | ${registry.secret}                                                 |             |
| KANIKO_ARGS                       |                                                                    |
| DOCKER_REGISTRY                   |                                                                    |
| DOCKER_REGISTRY_SECRET            |                                                                    |
| ARGOWORKFLOWS_ARTIFACTS_CONFIGMAP | artifact-repositories                                              |             |
| ARGOWORKFLOWS_ARTIFACTS_KEY       | default-artifact-repository                                        |             |
| ARGOWORKFLOWS_SERVICE_ACCOUNT     | default                                                            |             |
| ARGOWORKFLOWS_USER                | 1000                                                               |             |
| K8S_NAMESPACE                     | default                                                            |             |
| K8S_ENABLE_LOGS                   | true                                                               |             |
| K8S_ENABLE_METRICS                | true                                                               |             |
| K8S_ENABLE_RESULTS                | default                                                            |             |
| K8S_SEC_DISABLE_ROOT              | false                                                              |             |
| K8S_SEC_USER                      |                                                                    |
| K8S_SEC_GROUP                     |                                                                    |
| K8S_IMAGE_PULL_POLICY             | IfNotPresent                                                       |             |
| K8S_INIT_IMAGE                    | ghcr.io/scc-digitalhub/digitalhub-core-builder-tool:latest         |
| K8S_SERVICE_TYPE                  | NodePort                                                           |             |
| K8S_RESOURCE_CPU_DEFAULT          | 100m                                                               |             |
| K8S_RESOURCE_CPU_LIMIT            |                                                                    |
| K8S_RESOURCE_MEM_DEFAULT          | 64m                                                                |             |
| K8S_RESOURCE_MEM_LIMIT            |                                                                    |
| K8S_RESOURCE_GPU_KEY              | nvidia.com/gpu                                                     |             |
| K8S_RESOURCE_PVC_DEFAULT          | 2Gi                                                                |             |
| K8S_RESOURCE_PVC_LIMIT            |                                                                    |
| K8S_RESOURCE_PVC_STORAGE_CLASS    |                                                                    |
| DH_CONFIG_COMMON_MAPS             |                                                                    |
| DH_CONFIG_COMMON_SECRETS          |                                                                    |
| K8S_TEMPLATES                     |                                                                    |
| K8S_JOB_DEADLINE                  | 259200                                                             |             |
| K8S_JOB_SUSPEND                   | false                                                              |             |
| K8S_CUSTOM_API_GROUPS             | kubeai.org/v1                                                      |             |
| DH_ENDPOINT                       | http://localhost:8080                                              |
| DH_NAME                           | dhcore                                                             |             |
| DH_CONTACTS_EMAIL                 |                                                                    |             |
| DH_CONTACTS_NAME                  |                                                                    |
| DH_CONTACTS_LINK                  | https://github.com/scc-digitalhub/digitalhub-core                  |             |
| DH_API_LEVEL                      |                                                                    |
| spring.profiles.active            | default                                                            |             |
| LOG_LEVEL                         | INFO                                                               |             |
| LOG_LEVEL_K8S                     | INFO                                                               |             |
| DH_CORS_ORIGINS                   |                                                                    |
| DH_AUTH_BASIC_USER                | admin                                                              |             |
| DH_AUTH_BASIC_PASSWORD            |                                                                    |
| DH_AUTH_JWT_ISSUER_URI            | ${security.oidc.issuer-uri}                                        |             |
| DH_AUTH_JWT_AUDIENCE              | ${security.oidc.client-id}                                         |             |
| DH_AUTH_JWT_CLAIM                 | roles                                                              |             |
| DH_AUTH_JWT_USERNAME              | preferred_username                                                 |             |
| DH_AUTH_OIDC_ISSUER_URI           |                                                                    |
| DH_AUTH_OIDC_CLIENT_NAME          | ${application.name}                                                |             |
| DH_AUTH_OIDC_CLIENT_ID            |                                                                    |
| DH_AUTH_OIDC_CLIENT_SECRET        |                                                                    |
| DH_AUTH_OIDC_CLAIM                | ${security.jwt.claim}                                              |             |
| DH_AUTH_OIDC_USERNAME             | preferred_username                                                 |             |
| DH_AUTH_OIDC_SCOPE                | openid,email,profile                                               |             |
| ENABLE_EVENTS                     | false                                                              |             |
| RABBITMQ_HOST                     |                                                                    |
| RABBITMQ_PORT                     |                                                                    |
| RABBITMQ_USER                     |                                                                    |
| RABBITMQ_PASSWORD                 |                                                                    |
| RABBITMQ_VHOST                    | /                                                                  |             |
| RABBITMQ_QUEUE                    |                                                                    |
| RABBITMQ_TOPIC                    | entityTopic                                                        |             |
| RABBITMQ_ROUTING_KEY              | entityRoutingKey                                                   |             |
| RABBITMQ_ENABLE                   | false                                                              |             |
| MONITORS_DELAY                    | 60                                                                 |             |
| SOLR_URL                          | false                                                              |             |
| SOLR_USER                         |                                                                    |
| SOLR_PASSWORD                     |                                                                    |
| SOLR_ADMIN_USER                   | SOLR_USER                                                          |             |
| SOLR_ADMIN_PASSWORD               | SOLR_PASSWORD                                                      |             |
| SOLR_COLLECTION                   | dhcore                                                             |             |
| SOLR_TIMEOUT                      | 5000                                                               |             |
| SOLR_COLLECTION_SHARDS_NUM        | 1                                                                  |             |
| SOLR_COLLECTION_REPLICATION       | 1                                                                  |             |
| SOLR_REINDEX                      | never                                                              |             |
| LUCENE_INDEX_PATH                 | false                                                              |             |
| LUCENE_REINDEX                    | never                                                              |             |
| FILES_MAX_COLUMN_SIZE             | 2097152                                                            |             |
| FILES_DEFAULT_STORE               | s3://${application.name}                                           |
| JWT_KEYSTORE_PATH                 | classpath:/keystore.jwks                                           |
| JWT_KEYSTORE_KID                  |                                                                    |
| JWT_ACCESS_TOKEN_DURATION         |                                                                    |
| JWT_REFRESH_TOKEN_DURATION        |                                                                    |
| JWT_CLIENT_ID                     | ${security.basic.username}                                         |             |
| JWT_CLIENT_SECRET                 | ${security.basic.password}                                         |             |
| JWT_REDIRECT_URIS                 | http://localhost:\*,${application.endpoint}/console/auth-callback  |
| JWKS_CACHE_CONTROL                | public, max-age=900, must-revalidate, no-transform                 |             |
| S3_CREDENTIALS_PROVIDER           | false                                                              |             |
| S3_ENDPOINT_URL                   |                                                                    |
| S3_BUCKET                         |                                                                    |
| AWS_DEFAULT_REGION                |                                                                    |
| AWS_ACCESS_KEY                    |                                                                    |
| AWS_SECRET_KEY                    |                                                                    |
| S3_PATH_STYLE_ACCESS              |                                                                    |
| DB_CREDENTIALS_PROVIDER           | false                                                              |             |
| DB_CREDENTIALS_DATABASE           |                                                                    |
| DB_CREDENTIALS_ENDPOINT           |                                                                    |
| DB_CREDENTIALS_CLAIM              | db/role                                                            |             |
| DB_CREDENTIALS_ROLE               |                                                                    |
| DB_CREDENTIALS_USER               |                                                                    |
| DB_CREDENTIALS_PASSWORD           |                                                                    |
| MINIO_CREDENTIALS_PROVIDER        | false                                                              |             |
| MINIO_CREDENTIALS_ENDPOINT        |                                                                    |
| MINIO_CREDENTIALS_REGION          | us-east-1                                                          |             |
| MINIO_CREDENTIALS_BUCKET          |                                                                    |
| MINIO_CREDENTIALS_CLAIM           | minio/policy                                                       |             |
| MINIO_CREDENTIALS_POLICY          | readwrite                                                          |             |
| MINIO_CREDENTIALS_ACCESS_KEY      |                                                                    |
| MINIO_CREDENTIALS_SECRET_KEY      |                                                                    |
| TEMPLATES_PATH                    | classpath:/templates                                               |
