# [0.12.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.11.0...0.12.0) (2025-06-30)

### Features

* k8s templates for deploying core ([81b600e](https://github.com/scc-digitalhub/digitalhub-core/commit/81b600e720803a7d71e39eca85ee838b420699ac))
* minio credentials expiration handling ([345130d](https://github.com/scc-digitalhub/digitalhub-core/commit/345130dd1f163b6524af7bd67a82ed97d5a58f31))
* security.txt well-known ([52a5dcf](https://github.com/scc-digitalhub/digitalhub-core/commit/52a5dcf1856e65b39cabba98898e10abaf4ec004))

### Bug Fixes

* add missing processors param to deserde for kubeai spec ([e96fdac](https://github.com/scc-digitalhub/digitalhub-core/commit/e96fdac2e5ce3be4c4a80e12d6c7267373557769))
* db credentials expiration should be more than access token ([e463938](https://github.com/scc-digitalhub/digitalhub-core/commit/e463938f9f3b756abaf2fa8aa6b20b23630e3f36))
* remove annotation for k8s watchers ([6488263](https://github.com/scc-digitalhub/digitalhub-core/commit/6488263d4aaa791391fa7563e7c24a5934dadedd))
* update kfp build env vars to align with sdk ([e6388ac](https://github.com/scc-digitalhub/digitalhub-core/commit/e6388ac18f4f844f741f1d235dd09c84d842a1ac))

**Full Changelog**: https://github.com/scc-digitalhub/digitalhub-core/compare/0.11.0...0.12.0

# [0.11.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.10.0-...0.11.0) (2025-06-04)

## What's Changed
* feat: Support for CR runnable in K8s Framework by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/154
* feat: add watcher for Job, Serve and Deployment. Each watcher has own… by @trubbio in https://github.com/scc-digitalhub/digitalhub-core/pull/155
* feat: KubeAI Runtime by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/157
* feat: remove doc from indexer by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/158
* Remove files by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/156
* feat!: create dedicated pvcs for runs and block usage of user supppli… by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/159
* Triggers - first impl by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/160
* feat: jdbc for triggers by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/161
* feat: Entities lifecycle by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/164
* Trigger lifecycle by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/165
* feat: Personal access token via exchange by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/166
* refactor!: use jdbc for refresh tokens repository by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/167
* feat!: build run envs from configurations by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/168
* feat: API for trigger relationships by @etomaselli in https://github.com/scc-digitalhub/digitalhub-core/pull/163
* feat: user management for personal and refresh tokens by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/169
* -feat!: kubeai to openai run status + fixes + add configurable whitel… by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/170
* Triggers jdbc factory by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/162
* refactor!: split kubeai runtime in kubeai-speech and kubea-text by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/171


### Features

* i18n keys generation ([6f6a99f](https://github.com/scc-digitalhub/digitalhub-core/commit/6f6a99f33e7ca780a79392a696aaeaa794d859aa))
* let admin configure default pvc storage class ([982f9ff](https://github.com/scc-digitalhub/digitalhub-core/commit/982f9ff55b8ae77eb06407258d26a4d7e20d9728))
* minio credentials expiration handling ([345130d](https://github.com/scc-digitalhub/digitalhub-core/commit/345130dd1f163b6524af7bd67a82ed97d5a58f31))
* add user label to k8s resources ([db10aea](https://github.com/scc-digitalhub/digitalhub-core/commit/db10aea4df5bbef94a69e01aa484c1d929785850))
* bump spring boot version + bump k8s lib version + drop mysql dep ([ca74feb](https://github.com/scc-digitalhub/digitalhub-core/commit/ca74feb0cb663e839ea62f47d96480c731826db0))
* collect runtime errors for k8s runs ([f84e7a3](https://github.com/scc-digitalhub/digitalhub-core/commit/f84e7a3cfa374310b06888fe8651e66743350f79))
* kubeai templates ([d318f71](https://github.com/scc-digitalhub/digitalhub-core/commit/d318f7161a3084feb8914d0bd3e7a5a36065efe3))
* define new logger LOG_LEVEL_K8S to debug k8s ([55c8f02](https://github.com/scc-digitalhub/digitalhub-core/commit/55c8f029afd7bf276cd2214d5573827660e3bf3c))
* validation function template ([bece039](https://github.com/scc-digitalhub/digitalhub-core/commit/bece0392e32e3773739e8461c1aa9d3ad5d4122c))
* envs exporter for config and credentials to ENV_VARS ([eaabf2e](https://github.com/scc-digitalhub/digitalhub-core/commit/eaabf2ef97237b2290337a7047bc655a5b518a96))
* resolve entity names for dtos in cloud messages ([dc04f79](https://github.com/scc-digitalhub/digitalhub-core/commit/dc04f79b5988ff238a1fe05346e302fdae12b18b))
* trigger lifecycle adds consumes rel to runs ([8460401](https://github.com/scc-digitalhub/digitalhub-core/commit/8460401735cfd919a6b1e61a2e5456f813e3128f))
* trigger lifecycle define supported states enum ([9f840a1](https://github.com/scc-digitalhub/digitalhub-core/commit/9f840a1734004aa56118715840ed5f0c3f016d3c))
* triggers support workflow runs ([bf002ea](https://github.com/scc-digitalhub/digitalhub-core/commit/bf002ea85843fb22a40c5fbbea33ad79da48d312))
* use ClusterIP as default service type ([5a861e0](https://github.com/scc-digitalhub/digitalhub-core/commit/5a861e02124dacb5ae0a7c8c3a7838be3c2c43ff))
* API for trigger relationships ([6574c43](https://github.com/scc-digitalhub/digitalhub-core/commit/6574c43e4539bc76d8bc633e94eda24f956c036d))
* artifact lifecycle ([1f2a6b8](https://github.com/scc-digitalhub/digitalhub-core/commit/1f2a6b8296a18dbb08af83d7f35824da75e595fc))
* artifacts lifecycle ([45da6f0](https://github.com/scc-digitalhub/digitalhub-core/commit/45da6f0f1b46cc7f15f1b42a8c7efa4c1bbf34c3))
* broadcast user events for entities lifecycle ([a289e36](https://github.com/scc-digitalhub/digitalhub-core/commit/a289e36ed3a7efdd1ac7b426494c87810b2b34f9))
* core events lifecylcle with no-ops ([e207f67](https://github.com/scc-digitalhub/digitalhub-core/commit/e207f6712aaadc2d055586d3b7a0d94450df110c))
* dataItems lifecycle ([6c1369b](https://github.com/scc-digitalhub/digitalhub-core/commit/6c1369b5cc5eaafe5b1dd8ff67ee3af6769238ed))
* key accessor supports partial keys ([9f9c1f8](https://github.com/scc-digitalhub/digitalhub-core/commit/9f9c1f803d95dfa8e0aa3d918bef434253f27c41))
* kubeAi let uses configure number of processors ([92400fc](https://github.com/scc-digitalhub/digitalhub-core/commit/92400fc26a18eef5ff03d4cac78ff1d72b73188e))
* lifecycle trigger add jdbc store ([e7d5015](https://github.com/scc-digitalhub/digitalhub-core/commit/e7d50158833d2e4236ad595c6ea880e5ff1a0114))
* models lifecycle ([7918002](https://github.com/scc-digitalhub/digitalhub-core/commit/791800237131e0ebea7a42da3947bed8d214be3a))
* mustache template add support writing objects as json ([c6921f4](https://github.com/scc-digitalhub/digitalhub-core/commit/c6921f4171a246c74074d728c3583d31f088da98))
* Personal access token via exchange ([3b5c742](https://github.com/scc-digitalhub/digitalhub-core/commit/3b5c74287471c1551e2a361fa94562c5c0d44540))
* support envFrom for kubeAi + use envFrom to inject core secrets ([22311dd](https://github.com/scc-digitalhub/digitalhub-core/commit/22311dd42ab8dda5b0562f24ca7fbbd44789ef28))
* template processor with mustache ([845adfe](https://github.com/scc-digitalhub/digitalhub-core/commit/845adfe1aa3585c1b01a08e1aaff7533c493ed23))
* trigger lifecycle ([67512b0](https://github.com/scc-digitalhub/digitalhub-core/commit/67512b03b29540e581b10b922acf57ebd92ffbf1))
* trigger lifecycle event execution ([22b55fc](https://github.com/scc-digitalhub/digitalhub-core/commit/22b55fcde7c75c454a8148ece59e6e04b4ac5eb5))
* user management for personal and refresh tokens ([6454778](https://github.com/scc-digitalhub/digitalhub-core/commit/6454778c13eb991fd0c11d53e5d3c738c6feebe2))
* add clear to files info for entities ([639a1d1](https://github.com/scc-digitalhub/digitalhub-core/commit/639a1d1b291bd330d4250afc8ae1cb5694215651))
* add expiration checks to db credentials provider ([168e849](https://github.com/scc-digitalhub/digitalhub-core/commit/168e8499f6e245a8db718aeac4404f0c0164d4e1))
* add prefix bulk delete to s3 store to support pseudo-folders (experimental) ([dfda130](https://github.com/scc-digitalhub/digitalhub-core/commit/dfda130a1ada668b86100f5c9f2eac58a778665b))
* add security context to kaniko pods ([422f129](https://github.com/scc-digitalhub/digitalhub-core/commit/422f1290b799a416dd9c9c21de492bb36f54f08c))
* add watcher for Job, Serve and Deployment. Each watcher has own monitor that call refresh with the runnable Id with a debouce of 1 sec to refresh the runnable ([39f78d7](https://github.com/scc-digitalhub/digitalhub-core/commit/39f78d7ca7b3c59554e403afe90f8735335c9fb0))
* always specify uid/gid for known runtimes + make configurable ([edf0113](https://github.com/scc-digitalhub/digitalhub-core/commit/edf011315c01216a19b388a28bf0db57170613ed))
* bump spring versions ([c6064a8](https://github.com/scc-digitalhub/digitalhub-core/commit/c6064a87860ebf0abcf4740c368c80a5e12dd030))
* cascade delete (opt-in)_for files defined in entities path ([5f5f01b](https://github.com/scc-digitalhub/digitalhub-core/commit/5f5f01bf46a33bdf9eb210b804e0a42fc5ac99ae))
* delete CR resources with propagation foreground ([350e36f](https://github.com/scc-digitalhub/digitalhub-core/commit/350e36fe5bc85e01c6b9b96393cf5bcfc979d7cd))
* K8s Custom Resource framework ([511b0cf](https://github.com/scc-digitalhub/digitalhub-core/commit/511b0cfa79d428184fda34b43c3adb1ceb1298d2))
* make default files store configurable in project ([d4b79a5](https://github.com/scc-digitalhub/digitalhub-core/commit/d4b79a5355d62407fbf058682fc5c31d9e895165))
* require project for entity files upload, use context by default ([4c274e0](https://github.com/scc-digitalhub/digitalhub-core/commit/4c274e069b9fc6845a82927998a45de3ba1006fd))
* scheduler trigger (wip) ([0983490](https://github.com/scc-digitalhub/digitalhub-core/commit/0983490de2a57c6b512189e5b84f1f5165206370))
* scheduler trigger runs + support quartz expression and simple triggers ([3d25390](https://github.com/scc-digitalhub/digitalhub-core/commit/3d25390d8363949a3c26563d6d27c5a23b251492))
* Support for CR runnable in K8s Framework ([da360ce](https://github.com/scc-digitalhub/digitalhub-core/commit/da360ce8fa6b3c859770cab1d02000764ea33a4b))
* triggers (wip) ([6ab9bf3](https://github.com/scc-digitalhub/digitalhub-core/commit/6ab9bf3a7d920f0d9ca50b674eeee3edfe7800cc))


### Bug Fixes

* build kubeai status descriptors only once ([ec804ec](https://github.com/scc-digitalhub/digitalhub-core/commit/ec804ec920fcb9975233fe297a5b6843301e4100))
* db credentials expiration should be more than access token ([e463938](https://github.com/scc-digitalhub/digitalhub-core/commit/e463938f9f3b756abaf2fa8aa6b20b23630e3f36))
* restore old version of jetty for solr ([7bc9fb4](https://github.com/scc-digitalhub/digitalhub-core/commit/7bc9fb4d87d27a88579e9a91f50d6fca3663bdc1))
* update kfp build env vars to align with sdk ([e6388ac](https://github.com/scc-digitalhub/digitalhub-core/commit/e6388ac18f4f844f741f1d235dd09c84d842a1ac))
* make sure auth manager is available for triggered runs ([02d5cdb](https://github.com/scc-digitalhub/digitalhub-core/commit/02d5cdbfe79be08677a39c71e293c15eb60d498b))
* triggered runs may have partial credentials, restore full context on fire ([4cbb160](https://github.com/scc-digitalhub/digitalhub-core/commit/4cbb16022e078069444c463b8f84fcf34b73d6c9))
* dbt runner supports templates as per spec ([bfc782c](https://github.com/scc-digitalhub/digitalhub-core/commit/bfc782c33003e710f8f25b38156a00e1c8b90162))
* fix bucketName in delete of folders for s3 ([a0e3ac9](https://github.com/scc-digitalhub/digitalhub-core/commit/a0e3ac9449d06408a0aef8593569b60a5bd6f5ff))
* builder tool for s3 ([b95c095](https://github.com/scc-digitalhub/digitalhub-core/commit/b95c095f9742709601f1786a4e38f46bc269a5d9))
* kaniko jobs need to run as root ([58d948b](https://github.com/scc-digitalhub/digitalhub-core/commit/58d948b60d4044022ce937df9598fba4c5b371e6))
* kfp build runs need to have the correct task kind associated ([4f80862](https://github.com/scc-digitalhub/digitalhub-core/commit/4f808621a398b840880131638a2a5895700e2aa6))
* inflate rebuilt user authentications for trigger and run callbacks ([ce4e644](https://github.com/scc-digitalhub/digitalhub-core/commit/ce4e644a4a404e3ede59a749294beef2085978a2))
* missing monthly schedule ([52059c8](https://github.com/scc-digitalhub/digitalhub-core/commit/52059c88e58182ed35779d655dbf21fc881d06bb))
* keep init-config-map for init containers ([13362e5](https://github.com/scc-digitalhub/digitalhub-core/commit/13362e5d1605344089a8944f4df2266c93e2cd4f))
* init container should mount only shared volume, not user defined ([965ce85](https://github.com/scc-digitalhub/digitalhub-core/commit/965ce85c37ec6e3550b0c55535e3fcbfbb475ea1))
* properly initialize quartz for postgresql + fix serde issues ([0583c5e](https://github.com/scc-digitalhub/digitalhub-core/commit/0583c5ea753d15852636eeeb87650790b349c932))
* S3files store should respect credentials region when set ([1eb90bf](https://github.com/scc-digitalhub/digitalhub-core/commit/1eb90bf006bce3c6c709a0971a4e52946e4559e1))
* use AWS_DEFAULT_REGION in place of non standard S3_REGION ([087ad36](https://github.com/scc-digitalhub/digitalhub-core/commit/087ad364d49fbaa96134ca28925cddb12aae15e4))
* empty dir spec params should be snake_case ([7b2e10b](https://github.com/scc-digitalhub/digitalhub-core/commit/7b2e10b1f4bbaadf0d4ca42187e4c2e447b6ed48))
* kubeai runtime profiles should define number of processors - use 1 for now ([0e3b76f](https://github.com/scc-digitalhub/digitalhub-core/commit/0e3b76f13aa9a4decf15566c211511135fd1dc41))
* refactor filesStorage to use credentials and break loop dependencies between service implementations ([daa85f0](https://github.com/scc-digitalhub/digitalhub-core/commit/daa85f07111a080cbe27f1f8944e08567e7973fe))
* add check to avoid deployment replicas set to 0 ([bdbba7f](https://github.com/scc-digitalhub/digitalhub-core/commit/bdbba7f3863ad2d7273c4dd5fb803f1249a1fb02))
* explicitely delete k8s resources in foreground to avoid leaving oprhaned pods ([63d185c](https://github.com/scc-digitalhub/digitalhub-core/commit/63d185ca042d97ec3e7929ecc88d6dad988c5462))
* fix null variable check in builder-tool ([44ae13c](https://github.com/scc-digitalhub/digitalhub-core/commit/44ae13c2ffb1594e1671abbf6bf21844644b6994))
* kaniko framework removes configMaps on stop/delete ([243efa7](https://github.com/scc-digitalhub/digitalhub-core/commit/243efa7488319589dd4065fe846bb8815307aaf3))
* remove delete files after entity deletion due to potential lack of credentials in callbacks ([cd8a267](https://github.com/scc-digitalhub/digitalhub-core/commit/cd8a2675a33fb18761db398de533e17bdf0de007))
* runnable listener should always persist runnable after processing ([2abe5ef](https://github.com/scc-digitalhub/digitalhub-core/commit/2abe5ef3ee330eba01efe57150af99f49c08d496))
* runnables should be final otherwise parent listener will also receive subclasses, fixes double execution of argo workflows ([0bb86c3](https://github.com/scc-digitalhub/digitalhub-core/commit/0bb86c3140129ba0d049a9b065483e1c4517e449))
* s3 filesinfo for single files should have an empty relative path ([77a9144](https://github.com/scc-digitalhub/digitalhub-core/commit/77a91449aa4a1a7ab40b040cb565f83f05666fe8))
* set all kubeai props as snake case ([b59fa5c](https://github.com/scc-digitalhub/digitalhub-core/commit/b59fa5c6abbd080e059180021b911cc914d5dfc8))



# [0.10.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.9.0...0.10.0) (2025-03-14)

## What's Changed
* feat: add http single file download and branch support for git by @trubbio in https://github.com/scc-digitalhub/digitalhub-core/pull/147
* Metrics by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/148
* Credentials providers by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/149
* Solr roles by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/151
* broadcast notifications also without id by @etomaselli in https://github.com/scc-digitalhub/digitalhub-core/pull/153
* feat: add workflow nodes in KFRunStatus by @trubbio in https://github.com/scc-digitalhub/digitalhub-core/pull/150
* Lucene by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/152


### Features
* add user to runnables to track owner ([64fc654](https://github.com/scc-digitalhub/digitalhub-core/commit/64fc654bffcdacad7cddec8adf79f3812a3fd5af))
* delegate auth context to async + mock user auth for async callbacks for runs ([90f37c5](https://github.com/scc-digitalhub/digitalhub-core/commit/90f37c55f35a4494536519bd351789bea0690ce0))
* move container args from fn to run ([8dbf5d6](https://github.com/scc-digitalhub/digitalhub-core/commit/8dbf5d67722e0926e0a372fccf1a4a257808ef67))
* update build tool to support s3 temp credentials ([b40d951](https://github.com/scc-digitalhub/digitalhub-core/commit/b40d951811c533586b16f163cce7d94ef0ecf9f1))
* add workflow nodes in KFRunStatus ([06e3fc0](https://github.com/scc-digitalhub/digitalhub-core/commit/06e3fc09d6005f22f35fb832e60f9f9c556f7da9))
* export S3_BUCKET from minio provider + fix S3_ENDPOINT_URL ([a5688f0](https://github.com/scc-digitalhub/digitalhub-core/commit/a5688f06e58350d0b9ad997c4784a75cce41b75d))
* let admin set application name via ENV ([3d5a1f3](https://github.com/scc-digitalhub/digitalhub-core/commit/3d5a1f3f5f31a488f74f8f85651ad6a43fb27605))
* remove configurable backoffPolicy + detect job failure from conditions ([9dfa29c](https://github.com/scc-digitalhub/digitalhub-core/commit/9dfa29c6b74ac382b325bfb6a51d0132ca6d2968))
* remove env prefix and write core props with DHCORE_ ([24d763e](https://github.com/scc-digitalhub/digitalhub-core/commit/24d763e4ee38d3297b6056bd815ccbd9577118cd))
* set DHCORE as default envs prefix for k8s ([6ccae28](https://github.com/scc-digitalhub/digitalhub-core/commit/6ccae2817c4b7ac5c175af05d7c7893f67ba8dfd))
* add init_parameters to python run spec ([19dbf06](https://github.com/scc-digitalhub/digitalhub-core/commit/19dbf065516860b93153c9d262d1490d1a06f8b7))
* read authorities from oidc userinfo/token for external auth ([6b3cbfb](https://github.com/scc-digitalhub/digitalhub-core/commit/6b3cbfb9e4ee3d0d37e2df545d116536e6275aa5))
* split user notification and broadcast messages ([5fd1119](https://github.com/scc-digitalhub/digitalhub-core/commit/5fd11198534697f51f8b2c6c11b39b280172ed0e))
* support customizing imagePullPolicy for container runtime ([afd801c](https://github.com/scc-digitalhub/digitalhub-core/commit/afd801cb9e93d4c76c309ff7c283be343394ff97))
* add http single file download and branch support for git ([00f0870](https://github.com/scc-digitalhub/digitalhub-core/commit/00f08702d8ac9e792a9a8796a23cbb93f88ce454))
* add scopes to oauth2 flows ([54327e3](https://github.com/scc-digitalhub/digitalhub-core/commit/54327e349842cdaf8f6ff4e27ab9aed79b1a3bc1))
* auth code flow (wip) ([5d2ae1f](https://github.com/scc-digitalhub/digitalhub-core/commit/5d2ae1f9333c6befd84ab0d744c3e56289118edb))
* authorization code flow + oauth login ([9011190](https://github.com/scc-digitalhub/digitalhub-core/commit/90111907b3af69cab46594d84c123d64523da4cb))
* cred providers flow integration (wip) ([c099639](https://github.com/scc-digitalhub/digitalhub-core/commit/c099639f0a711e4fe536276e14f8154936af0dc6))
* credentials and config providers ([0f4be0b](https://github.com/scc-digitalhub/digitalhub-core/commit/0f4be0b53d850069e92977e49ac7eb78ce5dceaa))
* custom authentication manager + token services (wip) ([18d8987](https://github.com/scc-digitalhub/digitalhub-core/commit/18d898717cf7e34353368d7833785b66e7b38522))
* db credentials provider ([35ca136](https://github.com/scc-digitalhub/digitalhub-core/commit/35ca136a09079040d1c379f6ead68968aaa4a046))
* minio sts credentials provider ([33fded1](https://github.com/scc-digitalhub/digitalhub-core/commit/33fded1a6826590d07c74d3f8dd2e449af45e14f))
* oidc external auth + internal oauth flows (wip) ([87dddba](https://github.com/scc-digitalhub/digitalhub-core/commit/87dddba84af45129aef722e7b0508f920069a780))
* q search should be case-insensitive ([011a1e2](https://github.com/scc-digitalhub/digitalhub-core/commit/011a1e2292805a168d04aa91c15192afe1cc777a))
* support internal jwt auth for websockets ([310e5cf](https://github.com/scc-digitalhub/digitalhub-core/commit/310e5cf51635eab975ab489db1a480f82bba38ab))
* userinfo for openid + console login to core + minor fixes ([1bcf6ea](https://github.com/scc-digitalhub/digitalhub-core/commit/1bcf6ea34097d26f0140b6aac655f43c4c0a6b63))



### Bug Fixes

* add job delete message ([a44f1d4](https://github.com/scc-digitalhub/digitalhub-core/commit/a44f1d40f1cf2bce2d324b3a260fe504191a8dd5))
* call super from k8sbase runtime to allow deletion of runnables from store ([18cfda3](https://github.com/scc-digitalhub/digitalhub-core/commit/18cfda3bea2a9ad3b400801f5a0ff6621cd5ba54))
* update argo nodes processing ([3d056b5](https://github.com/scc-digitalhub/digitalhub-core/commit/3d056b53cf27dc67cb546aba352e942cac15d5c3))
* deserialization of workflow obj ([cf461ad](https://github.com/scc-digitalhub/digitalhub-core/commit/cf461ad5d673dafd0ac65e05d11d086d578501d2))
* solr index project and name as strings not text ([935b146](https://github.com/scc-digitalhub/digitalhub-core/commit/935b146d7f1d135b5cb01ff48f8b7117f4480499))
* add nodes information to kfp run status ([a08f1db](https://github.com/scc-digitalhub/digitalhub-core/commit/a08f1dba6977c4827531c583adc1ecbc342ab965))
* entry indexers are disabled by default ([99b338e](https://github.com/scc-digitalhub/digitalhub-core/commit/99b338eae3d7fd79256d8511662e8ff360a2ee2a))
* make sure we do not collect null in map.of ([f7252f3](https://github.com/scc-digitalhub/digitalhub-core/commit/f7252f39f02be07ed2ed9a01d4a4f15b92359b9a))
* remove temporary Kfp run status nodes information. ([a3b7d4e](https://github.com/scc-digitalhub/digitalhub-core/commit/a3b7d4e8df31e37d9b289d677b1c021610981f0e))
* s3_endpoint_url ([87c9a47](https://github.com/scc-digitalhub/digitalhub-core/commit/87c9a47d94ba4c15c9490141512584e4af9bb3f0))
* security config for oauth2 endpoints ([0888548](https://github.com/scc-digitalhub/digitalhub-core/commit/0888548552645bfc947090aee2de6ef042e25c27))
* update workflows node information in KFPRunStatus. Added temporary property on status because at the moment FrontEnd read node information in a sub status properties and not directly from nodes property. This temporary property should be removed once front-end is fixed. Modify onComplete method on KFPRuntime now once the node execution is completed the status is update with right information. Also added input and ouputs parameters information on node status. ([52e7ac9](https://github.com/scc-digitalhub/digitalhub-core/commit/52e7ac983cb52285293a693b47c7155679665a66))
* workaround for deserialization of credentials in k8srunnable: rename field ([532fd9f](https://github.com/scc-digitalhub/digitalhub-core/commit/532fd9f1f2ba993e4095bd4ba4f350dbb10036f9))
* k8srunnable shoud store credentials in a deserializable manner ([66fbd41](https://github.com/scc-digitalhub/digitalhub-core/commit/66fbd4113d27fe37f1726e74f8680595c9a492f3))
* kaniko framework should not allow users to define pull policy ([7e15683](https://github.com/scc-digitalhub/digitalhub-core/commit/7e156833b7b280a4d308d5d093e44ccfafb78ffc))
* null credentials from providers should be filtered by service ([c40ce48](https://github.com/scc-digitalhub/digitalhub-core/commit/c40ce4839767fdbb6ca29e09ba362d2dde0f39e3))
* workaround for building with broken upstream ([17a2b9b](https://github.com/scc-digitalhub/digitalhub-core/commit/17a2b9be8fd1acfff7ace4a586f15522378e229c))
* add https ([5fca46e](https://github.com/scc-digitalhub/digitalhub-core/commit/5fca46ef42ab85890a3fcd2248e311d3df715258))
* add missing info to auth config ([1741d21](https://github.com/scc-digitalhub/digitalhub-core/commit/1741d21dfd440cf1c369bfee2cebf4815b57e942))
* fix wrong query for relationship service breaking lineage for runs ([4bc6bd3](https://github.com/scc-digitalhub/digitalhub-core/commit/4bc6bd310024866b9711546779284d4ba4f68e98))
* minio provider can handle only assumeRole for now ([1ff8ec0](https://github.com/scc-digitalhub/digitalhub-core/commit/1ff8ec037cd8c7b02272828d838b35f17d8b5f0e))


**Full Changelog**: https://github.com/scc-digitalhub/digitalhub-core/compare/0.9.0...0.10.0


# [0.9.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.8.0...0.9.0) (2024-12-02)

## What's Changed
* Relationships by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/133
* feat: Create ModelServe functions from model reference by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/135
* Schema exporting by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/136
* Argoworkflows v2 by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/137
* Argoworkflows by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/134
* workflow runs refactor by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/140
* fix(huggingfaceserve): Correct translation of LLM task types by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/139
* refactor: fsm for states + transitions and new lifecycle manager by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/142
* fix: runAsUser added to the argo workflow spec by @kazhamiakin in https://github.com/scc-digitalhub/digitalhub-core/pull/143
* Argo disable serviceaccount by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/144
* API for templates by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/145
* Update LICENSE by @Calcagiara in https://github.com/scc-digitalhub/digitalhub-core/pull/146

### Features

* container runtime expose runAs into task specs ([ad0a6d6](https://github.com/scc-digitalhub/digitalhub-core/commit/ad0a6d616f8fac17ef8cbb3268c5f4862d983687))
* function templates ([7ab8653](https://github.com/scc-digitalhub/digitalhub-core/commit/7ab8653cc68776d079801d97cc474fe4b11a9f20))
* add generated schemas to repo ([fcc5ecf](https://github.com/scc-digitalhub/digitalhub-core/commit/fcc5ecfa069e2bd999cc5d42701f1ef0474e0016))
* add metadata.ref to exported resources ([3d0b90a](https://github.com/scc-digitalhub/digitalhub-core/commit/3d0b90a0d42b22a0aad1db63382e789fc0605567))
* add run_of relationship to all runs ([6e2fb40](https://github.com/scc-digitalhub/digitalhub-core/commit/6e2fb40fc482a076e7fb1763e700d94a0cc122d6))
* Create ModelServe functions from model reference ([93a4587](https://github.com/scc-digitalhub/digitalhub-core/commit/93a4587aaeb4593c470396d088f17599a4fcc890))
* drop nefertem runtime from builds ([56fcf71](https://github.com/scc-digitalhub/digitalhub-core/commit/56fcf71821d0f6435069bec5edd0082b77ba94be))
* implement keyGenerator for id generation ([aacce6f](https://github.com/scc-digitalhub/digitalhub-core/commit/aacce6f3fd56629a8c8ec7ab79a722af52ecd66b))
* key utils + accessor ([62d343e](https://github.com/scc-digitalhub/digitalhub-core/commit/62d343e389149b7d9389594ffb5d20d562beba3b))
* kfp workflow spec update to use source format for build ([0dc9759](https://github.com/scc-digitalhub/digitalhub-core/commit/0dc97595c04c314a30e3f0fd340dfc570ddce595))
* openapi generator goal + export ([6c2b6f7](https://github.com/scc-digitalhub/digitalhub-core/commit/6c2b6f7dcba7c7ef65d7060c807c8217cef7df6a))
* project expose relationships for all embedded entities ([22eaaec](https://github.com/scc-digitalhub/digitalhub-core/commit/22eaaec4a86a3aae0d4125e91f68bb663c36a2d6))
* remove config_map volume type ([9f0c95b](https://github.com/scc-digitalhub/digitalhub-core/commit/9f0c95b5173b98d67bb80b71ac2abe9fcafcc2f5))
* remove deprecated handler field from workflow spec ([dfdac79](https://github.com/scc-digitalhub/digitalhub-core/commit/dfdac79f7e92c97eb020370c7125bd6a743fbe84))
* schema exporter runner (wip) ([e01790d](https://github.com/scc-digitalhub/digitalhub-core/commit/e01790da85432fc7a7d6877e3a72cb907e287570))
* spec exported to file for all specs with custom profile ([b393c16](https://github.com/scc-digitalhub/digitalhub-core/commit/b393c16018e7eb6c3fc37d5b9bd1724d43ffd4ad))
* Support for Build task and native Argo Workflow framework. ([ed6325a](https://github.com/scc-digitalhub/digitalhub-core/commit/ed6325a3ce9760020dc51a36ba476e6fe0fdf496))
* support k8s jobs as suspended by default ([f0951e2](https://github.com/scc-digitalhub/digitalhub-core/commit/f0951e2a996c83e6032cf8d6ba5cad9c83474402))
* update spring + spring boot and bump version ([8ddab95](https://github.com/scc-digitalhub/digitalhub-core/commit/8ddab95a1f1cafc255e7e41713fcaa66c8c431aa))


### Bug Fixes
* use varbinary instead of lob to avoid orhpaned oids in postgres ([ced0154](https://github.com/scc-digitalhub/digitalhub-core/commit/ced015453bea546c121e3f3354a447fee0c87972))
* clear leftover extra fields ([63018c5](https://github.com/scc-digitalhub/digitalhub-core/commit/63018c58b8f099c2fc87f21b8780537593fa62b6))
* snake_case for k8s fields ([ae97821](https://github.com/scc-digitalhub/digitalhub-core/commit/ae97821549322e33372597c2a8924d9258006d70))
* add templates to dockerfile to include in images ([3ad4183](https://github.com/scc-digitalhub/digitalhub-core/commit/3ad41835655fc1e1843b90bb33f14709159a643c))
* replace getFile with getInputStream to allow template service to read from classpath ([61f4ef2](https://github.com/scc-digitalhub/digitalhub-core/commit/61f4ef2d07dd87f1c159aae61e51946c51f0af3a))
* lock jetty deps for solr ([6bf0bcd](https://github.com/scc-digitalhub/digitalhub-core/commit/6bf0bcd2f674315100f1ff008e159857efab4d34))
* remove outputs from python run spec ([ceb5173](https://github.com/scc-digitalhub/digitalhub-core/commit/ceb51733d1d07f669c6cbcd54caaad5f4c79a9ef))
* avoid external state modification for non-local runs ([ffd5faa](https://github.com/scc-digitalhub/digitalhub-core/commit/ffd5faaa5088d06eb16c3bfd6b16fd562f77e413))
* fix nullpointer (+potential npes) in argo framework ([7b44208](https://github.com/scc-digitalhub/digitalhub-core/commit/7b44208411f8078377697ca60d70f8bbc3edeb43))
* remove NONE entityType ([d1a9485](https://github.com/scc-digitalhub/digitalhub-core/commit/d1a9485c9f20fff036d46bcc96c88d91bcd0934b))
* disable service account and token mont config ([e2149f2](https://github.com/scc-digitalhub/digitalhub-core/commit/e2149f2ef5869a986f322a9259a39277fff3ffb2))
* cascade delete models on project delete ([b624260](https://github.com/scc-digitalhub/digitalhub-core/commit/b6242601cc76fcd11f56dde529b0a347a2cbd2ef))
* enforce run spec readonly after created stage ([ff59564](https://github.com/scc-digitalhub/digitalhub-core/commit/ff59564ec1778e8b63b38b2f4403869db55846a2))
* fix compile error for kfp build task ([fdc1f36](https://github.com/scc-digitalhub/digitalhub-core/commit/fdc1f36437956ecdab9170d6eae35c670d5cf705))
* fix imports forargo workflow in kfp runtime ([f64ecde](https://github.com/scc-digitalhub/digitalhub-core/commit/f64ecdec743750b7811450aa1feae1429239545a))
* fix kfp pipeline runner reading from base64 encoded spec ([3d15c2a](https://github.com/scc-digitalhub/digitalhub-core/commit/3d15c2a97e79be76c906012351edb9912d1a6d09))
* fix relationships repo definition ([89524e3](https://github.com/scc-digitalhub/digitalhub-core/commit/89524e3f07c05f3f9b130f0f35e1b7d3cd217e80))
* **huggingfaceserve:** Correct translation of LLM task types ([7f75530](https://github.com/scc-digitalhub/digitalhub-core/commit/7f75530364e0f835a253e30752ef28f9d4aebc7b))
* keep snake_case for values in metadata ([6cc1832](https://github.com/scc-digitalhub/digitalhub-core/commit/6cc1832fb07ef72af0e4c6ff9a11f05483093ba2))
* leftover check for functionId in task service + run rels ([238d233](https://github.com/scc-digitalhub/digitalhub-core/commit/238d233539513b57ac01d15a4edea72f7ebc120d))
* reprase relationship find query to be more precise ([46457b1](https://github.com/scc-digitalhub/digitalhub-core/commit/46457b1f8651bd91e336912a4166bed088c982f5))
* runAccessor task field ([f5fbb1c](https://github.com/scc-digitalhub/digitalhub-core/commit/f5fbb1c7f9c4f4bbb713786b962d51ac494244dc))
* runAsUser added to the argo workflow spec ([e9414d0](https://github.com/scc-digitalhub/digitalhub-core/commit/e9414d007c6e2b2672fd23cb342eb6ef80cac16a))
* runmanager loads workflows ([d96ee4f](https://github.com/scc-digitalhub/digitalhub-core/commit/d96ee4ff1349f35b39b9efb54fd699bb2483d3e3))

**Full Changelog**: https://github.com/scc-digitalhub/digitalhub-core/compare/0.8.0...0.9.0



# [0.8.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.8.0...0.7.0) (2024-10-24)

## What's Changed
* Websocket for run states by @etomaselli in https://github.com/scc-digitalhub/digitalhub-core/pull/120
* Project sharing by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/123
* add solr basic auth by @micnori in https://github.com/scc-digitalhub/digitalhub-core/pull/122
* refactor: solr update + implement reindex + fixes by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/124
* feat: drop mlrun runtime by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/125
* refactor: secrets handled via providers + clear path logic to avoid s… by @matteo-s in https://github.com/scc-digitalhub/digitalhub-core/pull/129

### Features

* update embedding to keep ids by default and reduce meta to base metadata ([360ab4c](https://github.com/scc-digitalhub/digitalhub-core/commit/360ab4cde4a9b26828abd9da90188a397aeb65db))
* add fsGroup to secContext + make user configurable for container ([ee75082](https://github.com/scc-digitalhub/digitalhub-core/commit/ee750828a4a90b59c405ac7527fbd762e1eb61b6))
* drop mlrun from application ([d54ec17](https://github.com/scc-digitalhub/digitalhub-core/commit/d54ec17914ea8f059eae01b5e60dca5cf68137f0))
* drop mlrun runtime ([71e3a4b](https://github.com/scc-digitalhub/digitalhub-core/commit/71e3a4b38182b5991df689ad078f48c3e3c15154))
* kfp workflow encoded as base64 ([8871bd8](https://github.com/scc-digitalhub/digitalhub-core/commit/8871bd8321d7b55b6ba3a977854a8b5bb7ccbec7))
* projects search by user ([71f746b](https://github.com/scc-digitalhub/digitalhub-core/commit/71f746be13f816e519fd03789330f5a51899acb9))
* refresh project authorities for internal tokens ([e6343df](https://github.com/scc-digitalhub/digitalhub-core/commit/e6343df0fb20ff0e914c8d5edb215c334a1f5a44))
* use preferred_username by default for jwt auth, with fallback to sub ([5c57559](https://github.com/scc-digitalhub/digitalhub-core/commit/5c575591d7f36e9af900499e1405f91405ed9276))
* container tasks export context for source ([1356ee2](https://github.com/scc-digitalhub/digitalhub-core/commit/1356ee2634e8e371c1a23a4335145bc31daa0214))
* k8s service status info detailed collection ([88d4149](https://github.com/scc-digitalhub/digitalhub-core/commit/88d41490eb7a1bcbe33ed771d3a03bf408cb1a81))
* notify updatedBy user if different from owner ([769b7c0](https://github.com/scc-digitalhub/digitalhub-core/commit/769b7c0d7c231bc756706c910cc5bdf576b0c6d1))
* k8s api response error reporting ([c33456e](https://github.com/scc-digitalhub/digitalhub-core/commit/c33456e083357fb3853262aad8995f6dc9f95e8a))
* cache on project share lookup ([80f989d](https://github.com/scc-digitalhub/digitalhub-core/commit/80f989d86975cc0f9c35841a1bddb622716b545a))
* k8s serve/deploy resume + framework messages ([f0c3d42](https://github.com/scc-digitalhub/digitalhub-core/commit/f0c3d42db6a27e5a24b8258d1a0b316ede18cf30))
* project sharing ([03dc7a4](https://github.com/scc-digitalhub/digitalhub-core/commit/03dc7a4fef91abdeb0262537a54dfd1ac49075f1))
* project sharing + user permissions ([017e6bc](https://github.com/scc-digitalhub/digitalhub-core/commit/017e6bcc1b457c3342a941952a3877257f959d11))
* user notifications model ([41da04e](https://github.com/scc-digitalhub/digitalhub-core/commit/41da04e8dc29148b2640c6ed9b2cfce72ac6ff61))
* websocket authentication + config ([fdcb968](https://github.com/scc-digitalhub/digitalhub-core/commit/fdcb9680183245035d8755c72976b453a6669373))


### Bug Fixes

* secrets should be exposed as ENVS as provided ([3faa787](https://github.com/scc-digitalhub/digitalhub-core/commit/3faa787efc0907ec2cc09b0e0e4272e12b57146a))
* builder-tool as non root ([7587cd1](https://github.com/scc-digitalhub/digitalhub-core/commit/7587cd15381f7f64ab0e3e6a02e6b6251b053031))
* builder-tool does not exit for chmod issues ([07b4667](https://github.com/scc-digitalhub/digitalhub-core/commit/07b4667ebf189e2eed0ecacc2ce85ac8051aaa1d))
* cronJob framework avoid double build of volumes ([d17e52e](https://github.com/scc-digitalhub/digitalhub-core/commit/d17e52e92297e11f010ccdf65b83ad10685101df))
* project spec includes models ([a63c7e6](https://github.com/scc-digitalhub/digitalhub-core/commit/a63c7e6392a441e023e1a8bfac123a4cfb583591))
* align secrets provider + add path checks ([047ae8f](https://github.com/scc-digitalhub/digitalhub-core/commit/047ae8f55b4b200480e4d3d8a6030a40059566fd))
* fix huggingface path regex ([d957384](https://github.com/scc-digitalhub/digitalhub-core/commit/d9573844f754a7006d74c236c58c94fea6c6ce02))
* sanitize project secrets name for k8s ([5444f16](https://github.com/scc-digitalhub/digitalhub-core/commit/5444f163e9b02d7cf4e9591cd7fd78c87d26e878))
* fix crontab regex ([6c783b2](https://github.com/scc-digitalhub/digitalhub-core/commit/6c783b2d0bda9160fab07df238e2f69aeebff57f))
* python init_function field is a plain string ([81e7827](https://github.com/scc-digitalhub/digitalhub-core/commit/81e7827c889cd176d02e13881bdf1be489022aa3))
* refresh_token native queries require token as text, postgres uses oid by default ([d62c80e](https://github.com/scc-digitalhub/digitalhub-core/commit/d62c80e9db196478c2c550c413f65777ab99618a))
* rewrite native query for refresh_tokens to fix text issues ([e78b808](https://github.com/scc-digitalhub/digitalhub-core/commit/e78b808512526975b3eb6d96afa3fa626adc34fe))
* token exchange should propagate claims as extracted by securityConfig ([e904839](https://github.com/scc-digitalhub/digitalhub-core/commit/e904839b0de58f896b1d3f687c66313d4ce207e1))
* wrong kfp wrapper image ([9677b2e](https://github.com/scc-digitalhub/digitalhub-core/commit/9677b2e589547f216a92f6e65bb90642a4734537))
* container build workdir set to /shared ([1a18ba1](https://github.com/scc-digitalhub/digitalhub-core/commit/1a18ba1af214f9109b556e36014694d595523abc))
* fix container build mismatch between workdir and copy dest ([bbb1655](https://github.com/scc-digitalhub/digitalhub-core/commit/bbb165586dfb7afaaf3ecf76b55a1644b5dc6ed6))
* fix serve framework nullpointer ex ([d3ee34b](https://github.com/scc-digitalhub/digitalhub-core/commit/d3ee34bbc5c9cc0cc7c14f31d20a1baeea1b04ea))
* service processor should avoid looking for status if type != loadBalancer ([569b7b9](https://github.com/scc-digitalhub/digitalhub-core/commit/569b7b9d6e75fbd4df08a4d613ab64f75329d358))
* set LOGIN_URL for console for oidc ([1fc3a2f](https://github.com/scc-digitalhub/digitalhub-core/commit/1fc3a2ffe6d03511dd97a4a1861a4dac49aec9d5))
* fix nullpointer on run callback when not existing ([8607fd5](https://github.com/scc-digitalhub/digitalhub-core/commit/8607fd5e2a8a337c0888fbbef3d823abfa1b3885))
* k8s pvc claimName in place of claim_name ([0dab756](https://github.com/scc-digitalhub/digitalhub-core/commit/0dab756cbb4f420c86305b6ae015b4daedfac76a))
* batch delete should send events ([72d17be](https://github.com/scc-digitalhub/digitalhub-core/commit/72d17be2e97e8d12b026105d0a7ae05302fb03d4))
* fix delete of run with no runnable ([e6dc6d7](https://github.com/scc-digitalhub/digitalhub-core/commit/e6dc6d7a897579cb7038f2079993f8eae5a8f34f))
* specRegistry schema generator additional modules should be optional + cleanups ([013eb30](https://github.com/scc-digitalhub/digitalhub-core/commit/013eb30d0d28bcac71b65b950c4de9a2b41e999d))




# [0.7.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta10...0.7.0) (2024-09-20)


### Bug Fixes

* python build should set WORKDIR for user instructions ([b270412](https://github.com/scc-digitalhub/digitalhub-core/commit/b2704121262784161b587c6b1f1c69854d2f1609))



# [0.7.0-beta10](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta9...0.7.0-beta10) (2024-09-19)


### Features

* add locking to run manager to fix fsm concurrency + add error messages to status ([216d5b2](https://github.com/scc-digitalhub/digitalhub-core/commit/216d5b2ac37f7d2ed905fc9a01c7b0d90006083b))



# [0.7.0-beta9](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta8-r4...0.7.0-beta9) (2024-09-18)


### Bug Fixes

* fix python install of requirements.txt ([d8252ae](https://github.com/scc-digitalhub/digitalhub-core/commit/d8252ae43678dae7f20a645b73157ded78a5cc3e))
* fix seriazable definition to avoid loop + fix array def ([6b03c9a](https://github.com/scc-digitalhub/digitalhub-core/commit/6b03c9ac709734c8bce28f76afe69ec344670625))


### Features

* mlserve runtimes add validation to image and path ([523b204](https://github.com/scc-digitalhub/digitalhub-core/commit/523b204c31a85dd4bc9ede492f10d1979a77ecae))
* python job schedule support + schedule validation via regex as crontab ([41b531a](https://github.com/scc-digitalhub/digitalhub-core/commit/41b531a753234a4c34391adac1b1d625f29a218f))



# [0.7.0-beta8](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta7r2...0.7.0-beta8) (2024-09-16)


### Bug Fixes

* build sub path on download only if it differs from path ([81566f8](https://github.com/scc-digitalhub/digitalhub-core/commit/81566f8754e49f11258a85c6b236e9ef18bec474))
* clean fileInfo and fix bug in httpStore ([e17053e](https://github.com/scc-digitalhub/digitalhub-core/commit/e17053edc30d4fa86cc0a854b4f7445d8c7af706))
* fix secret removal not working due to patch issue ([63f76a9](https://github.com/scc-digitalhub/digitalhub-core/commit/63f76a9a50b8531c105d4acb64e641d74cf0ce0d))
* fix store key building ([50ad12d](https://github.com/scc-digitalhub/digitalhub-core/commit/50ad12df5cf772e0e8bd69c032ba25579796ba3c))
* handle log collection errors in loop to avoid stopping on first error ([a9e9c25](https://github.com/scc-digitalhub/digitalhub-core/commit/a9e9c25d6a584b0451fde385e0df6d798a23ba30))
* keep k8s status when there is no updates ([97e2229](https://github.com/scc-digitalhub/digitalhub-core/commit/97e2229706dd60c9e84b8ed134e36bc6708effbe))
* remove source path modification for ml serving ([66ed314](https://github.com/scc-digitalhub/digitalhub-core/commit/66ed31450a683e5f17a366c66e60d7c6d316fdd9))
* use latest tag for python runtimes ([8995114](https://github.com/scc-digitalhub/digitalhub-core/commit/8995114bc170007409a93b0b114758ca598d90a7))


### Features

* add deps install for mlflow ([c79db7c](https://github.com/scc-digitalhub/digitalhub-core/commit/c79db7cfb34d6a4c48cf85b118a68c05352458ff))
* add transitions log to run status ([d673785](https://github.com/scc-digitalhub/digitalhub-core/commit/d673785c11e74bca3948f0216872280ef600bb77))
* hide secret and configMaps details from run status ([50a628b](https://github.com/scc-digitalhub/digitalhub-core/commit/50a628bf8a05ee6cc0e92a4d68a0e19b0480b12c))
* monitor restartCounts for deployments to check for errors ([d56de95](https://github.com/scc-digitalhub/digitalhub-core/commit/d56de9565a6915c44638c9936d18a6e108e6a47e))



# [0.7.0-beta7r2](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta6...0.7.0-beta7r2) (2024-09-12)


### Bug Fixes

* fix kaniko init volume double definition ([e4fadea](https://github.com/scc-digitalhub/digitalhub-core/commit/e4fadeaf887fafd63fcf9c91ccfc59023804a0fe))
* remove double slash from upload url built path ([5eddbc8](https://github.com/scc-digitalhub/digitalhub-core/commit/5eddbc8dca2fbc22b6b84f276d75e55bed25777f))


### Features

* zip+s3 store ([cd14de3](https://github.com/scc-digitalhub/digitalhub-core/commit/cd14de347b2b5c62a122048a39a7ee6db73526e9))



# [0.7.0-beta6](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta5...0.7.0-beta6) (2024-09-06)


### Bug Fixes

* fix service url building ([b370ea9](https://github.com/scc-digitalhub/digitalhub-core/commit/b370ea9d1036f3c81dd82c30ce4737541ff73400))
* schema generation for serializable as ref to fix gui ([ae6be88](https://github.com/scc-digitalhub/digitalhub-core/commit/ae6be8894c6e668c4a5489bda21c570c963cea54))


### Features

* expose fake node for profiles descriptions ([f5bec00](https://github.com/scc-digitalhub/digitalhub-core/commit/f5bec00a47bbf6528380357587c0770b0ad773da))
* json schema custom generator for serializable fields + proxy building for specs before generation ([c5e1664](https://github.com/scc-digitalhub/digitalhub-core/commit/c5e166498d12471fe3d18103195ccc356d98de0f))
* support templates for k8s objects via composite profiles ([663d643](https://github.com/scc-digitalhub/digitalhub-core/commit/663d643c379c5f0c6e4b1597e2a5634cf0d1ec2c))
* transform schema utils into factory + add k8s schema module + enable proxy + build custom spec for templates/profiles ([a8dd58a](https://github.com/scc-digitalhub/digitalhub-core/commit/a8dd58a1aad29950262caba3230450b3cc62d023))
* update run specs to exclude embedded function from schema generation ([69982c3](https://github.com/scc-digitalhub/digitalhub-core/commit/69982c3ee4c2040e904a52b3ffb887e0f452b61a))
* use resources definition from template when provided over user provided ([49c02eb](https://github.com/scc-digitalhub/digitalhub-core/commit/49c02eb52ce285f73023ac294da897b7879aa88c))



# [0.7.0-beta5](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta4-1...0.7.0-beta5) (2024-08-30)



# [0.7.0-beta4](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta3...0.7.0-beta4) (2024-08-28)



# [0.7.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta2...0.7.0-beta3) (2024-08-23)


### Bug Fixes

* add scanning for subpackages to include authorization ([3fb3dbe](https://github.com/scc-digitalhub/digitalhub-core/commit/3fb3dbe431743e202d2659ed177f8baec7302890))
* enforce name as read-only for entities ([c047743](https://github.com/scc-digitalhub/digitalhub-core/commit/c047743fb4032bd112a510c7077a8bb6e61c78dc))
* fix wrong auth used for refresh token consumption ([71a60df](https://github.com/scc-digitalhub/digitalhub-core/commit/71a60df915697b692dd5a6cc6e78f02123bdcf0a))


### Features

* authorization aware service for projects + helper auth to inject roles for project owners ([030fe1b](https://github.com/scc-digitalhub/digitalhub-core/commit/030fe1b1e8ce7f226dc024e730c00703745165b3))



# [0.7.0-beta2](https://github.com/scc-digitalhub/digitalhub-core/compare/0.7.0-beta1...0.7.0-beta2) (2024-08-22)


### Bug Fixes

* fix anonymous auth for token endpoint ([becb7bd](https://github.com/scc-digitalhub/digitalhub-core/commit/becb7bd28eac61104c3459b55bb5959b2914250e))
* revert changed application properties and apply new config ([bc13096](https://github.com/scc-digitalhub/digitalhub-core/commit/bc13096a21387abfbf4c667e59989dc12467bfd2))
* revert changed jwt configuration to correct default ([3c97c06](https://github.com/scc-digitalhub/digitalhub-core/commit/3c97c061139cb0f6177967b6a408a88b89e2e3c0))


### Features

* add exchange token flow between client and (internal/external) jwt auth ([51a24be](https://github.com/scc-digitalhub/digitalhub-core/commit/51a24beb0d10d326a8a66cfdb72bf5ee4cb0f099))
* implement auth server + add refresh + add endpoints + refactoring ([db5782b](https://github.com/scc-digitalhub/digitalhub-core/commit/db5782b1b78bb870e26ffb827732ce5bb425923c))



# [0.6.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta13...0.6.0) (2024-07-31)


### Bug Fixes

* fix build issues ([4ecec34](https://github.com/scc-digitalhub/digitalhub-core/commit/4ecec3467967be99bca1da3d00d459dfdb2408ae))
* fix k8s gpu and volumes usage ([ab1981b](https://github.com/scc-digitalhub/digitalhub-core/commit/ab1981b28481f1079d140b31da689e4bbfa63c1d))
* fix kaniko framework usage of secret volume mounts ([62c23b6](https://github.com/scc-digitalhub/digitalhub-core/commit/62c23b68e7255c91bc6adeb2ae1cb9ed1d80cfcf))



# [0.6.0-beta13](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta12...0.6.0-beta13) (2024-07-22)


### Bug Fixes

* fix k8s names sanitization, shorten generated names + disable fix for throw error ([fae02ad](https://github.com/scc-digitalhub/digitalhub-core/commit/fae02ad92b3d23c1fdae1f8461877f61fcb318b1))
* fix templates reading for base k8s framework ([55d4774](https://github.com/scc-digitalhub/digitalhub-core/commit/55d47749123eb57f3d0a0d9f3a20badca4af2a68))
* runtimes should start without k8s ([3cd8046](https://github.com/scc-digitalhub/digitalhub-core/commit/3cd804648fcc285c447c15d64d79b3aba900637f))


### Features

* add label support to k8s templats ([6cfc7fd](https://github.com/scc-digitalhub/digitalhub-core/commit/6cfc7fd8122a86430f87f9da1b0aba2b37939add))
* add security config and default resources to k8s ([6670cc7](https://github.com/scc-digitalhub/digitalhub-core/commit/6670cc7e607bb422949b4554d1e8f92838d5a285))
* k8s improve observability ([8d8e3e7](https://github.com/scc-digitalhub/digitalhub-core/commit/8d8e3e711c97452e9ffc11ba9824ec8b8282fec3))
* k8s make image pull policy configurable ([c526fdd](https://github.com/scc-digitalhub/digitalhub-core/commit/c526fdd48f9aa44813c2332338426fd4606b3c17))
* k8s profiles as templates ([8295d68](https://github.com/scc-digitalhub/digitalhub-core/commit/8295d6868b0df4a488048718b35143f9f39e506c))
* update k8s configurability and logging ([2f28f9e](https://github.com/scc-digitalhub/digitalhub-core/commit/2f28f9e2db37ae37356bbacdacbbd0e2cb1f7281))



# [0.6.0-beta12](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta11...0.6.0-beta12) (2024-07-19)


### Bug Fixes

* fix image pull policy wrongly specified for k8s ([91c3c8f](https://github.com/scc-digitalhub/digitalhub-core/commit/91c3c8f38229cd11de4c9a936f1f39e76d671b2c))
* fix wrong type in runnables written by runtimes ([0d3d950](https://github.com/scc-digitalhub/digitalhub-core/commit/0d3d9501b0865eba460cabd9f8c07178aaccb639))


### Features

* add custom entrypoint for python-runtime ([d7f40d0](https://github.com/scc-digitalhub/digitalhub-core/commit/d7f40d0e3d30cbf91984987ce99aec5b199306ef))
* add function name label to python and container runnables ([6372ac8](https://github.com/scc-digitalhub/digitalhub-core/commit/6372ac826e9e51c3b3249d7ce62684953d2f67e6))
* clean up k8s details in run after deletion ([8929233](https://github.com/scc-digitalhub/digitalhub-core/commit/8929233e651f8863787e369556d6e5d7f2832038))



# [0.6.0-beta11](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta10...0.6.0-beta11) (2024-07-17)


### Bug Fixes

* fix issue with deleting secret values from existing secrets ([a9e44b9](https://github.com/scc-digitalhub/digitalhub-core/commit/a9e44b9b6c6b22bb314223cb74d5dcb141d4d630))
* fix issue with s3 presigner not using path style with custom endpoints ([166e921](https://github.com/scc-digitalhub/digitalhub-core/commit/166e9215c0f59acda2c383175391e13444fd80e6))
* fix removal of orphaned pods for k8s jobs via propagaition policy ([4f4169b](https://github.com/scc-digitalhub/digitalhub-core/commit/4f4169bf16239fab4a7bc26de5ad72e084d20af9))


### Features

* add k8s function label to container runtime runnables ([895895b](https://github.com/scc-digitalhub/digitalhub-core/commit/895895bb9bf7c6c38d3ed16ee11b98602e0f4c6f))
* add namespaced labels to k8s resources ([af26f64](https://github.com/scc-digitalhub/digitalhub-core/commit/af26f64a4979dd9f4abb0404075fa9fd70294025))
* add prev entity to update events ([debe27f](https://github.com/scc-digitalhub/digitalhub-core/commit/debe27f6b12f8d02d63e8fec519598962217d488))



# [0.6.0-beta10](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta9...0.6.0-beta10) (2024-07-12)


### Features

* add service to k8s run status ([2b054b9](https://github.com/scc-digitalhub/digitalhub-core/commit/2b054b98d358904e9d524aa704e55567cc5272b1))
* build container supports source ([22f40db](https://github.com/scc-digitalhub/digitalhub-core/commit/22f40db868e27a43561368b89d8bb773072ee776))



# [0.6.0-beta9](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta8...0.6.0-beta9) (2024-07-12)


### Bug Fixes

* fix incorrect usage of k8deployment framework for serving ([5adc24d](https://github.com/scc-digitalhub/digitalhub-core/commit/5adc24d43d949baf0635b647792a7d47ada78909))
* schema controller should be accessible to all users ([5012805](https://github.com/scc-digitalhub/digitalhub-core/commit/5012805c667d835315e7e33709bcb408601c2745))



# [0.6.0-beta8](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta7...0.6.0-beta8) (2024-07-11)


### Bug Fixes

* add secrets to serving ([43dc2c5](https://github.com/scc-digitalhub/digitalhub-core/commit/43dc2c5ce31e104fd8c7639570cbf800daedb22a))
* enforce sanitization on names generated for k8s and images ([159e5c2](https://github.com/scc-digitalhub/digitalhub-core/commit/159e5c247dfe08ab9533d95f9c4d9456e367228a))



# [0.6.0-beta7](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta6...0.6.0-beta7) (2024-07-11)


### Bug Fixes

* fix k8s init config map and cleanup ([1414aaf](https://github.com/scc-digitalhub/digitalhub-core/commit/1414aafb229e98835211529caa024eab71b8828a))
* remove notNull annotation from python serve: task should have all fields optional ([0ec534e](https://github.com/scc-digitalhub/digitalhub-core/commit/0ec534e91ebe7676f655f9ae7e28f964013ae257))



# [0.6.0-beta6](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta5...0.6.0-beta6) (2024-07-10)


### Bug Fixes

* fix issue with event publisher not distinguishing between runnable listeners ([7d5492d](https://github.com/scc-digitalhub/digitalhub-core/commit/7d5492da9e5f8118f906ec7a56cfeb1286baa0bb))


### Features

* update python spec to default serviceType to nodePort ([f56d8d4](https://github.com/scc-digitalhub/digitalhub-core/commit/f56d8d4a0a39e5c5081a2fecdde323e2b3c3991b))



# [0.6.0-beta4](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta3...0.6.0-beta4) (2024-07-08)



# [0.6.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta2...0.6.0-beta3) (2024-07-04)


### Bug Fixes

* fix incorrect usage of urlEncoder for base64 ([0957532](https://github.com/scc-digitalhub/digitalhub-core/commit/0957532d28bd59251f1cabb31a20a9acea0e49cf))
* fix issue with patching k8s secrets ([61651c4](https://github.com/scc-digitalhub/digitalhub-core/commit/61651c4fe7c0b07f71c9e7f25b8d6b62ed0a20c1))
* fix unauthorized response from project controller with auth disabled ([39f8ef0](https://github.com/scc-digitalhub/digitalhub-core/commit/39f8ef0f5f79927e0e1f6466f7d0a14d5500b379))
* fix wrong state management on delete ([b7c924a](https://github.com/scc-digitalhub/digitalhub-core/commit/b7c924a1912b3de2f61b9acad24e55811e2d12b7))


### Features

* add init_function to python runtime ([30bf8cf](https://github.com/scc-digitalhub/digitalhub-core/commit/30bf8cfcb1ab909dbb2b5edf9d77ee1bebc76e02))
* cleanup k8s listeners and fix errors ([0324ea5](https://github.com/scc-digitalhub/digitalhub-core/commit/0324ea5cdc05b4d6569e66c98b9ecfaa5688857e))
* container runtime from base runtime ([34542bd](https://github.com/scc-digitalhub/digitalhub-core/commit/34542bd099dde4d59993dbfc45387b64de8d59f3))
* dbt runtime from base runtime ([7ea4f8e](https://github.com/scc-digitalhub/digitalhub-core/commit/7ea4f8ee4916ed6ee333756046cfd4146bb7448e))
* kfp runtime from base runtime ([c640230](https://github.com/scc-digitalhub/digitalhub-core/commit/c6402307cc6d6eba3de22f9df3c832f2004f2b26))
* mlrun runtime from base runtime ([1a0ce5a](https://github.com/scc-digitalhub/digitalhub-core/commit/1a0ce5a462a044b256fb0f3e4584fe11a2801853))
* nefertem runtime from base runtime ([befe3a1](https://github.com/scc-digitalhub/digitalhub-core/commit/befe3a1d405b4bd550afbd92207b2a453ef683e5))
* python runtime from base runtime ([e0c765c](https://github.com/scc-digitalhub/digitalhub-core/commit/e0c765ca1151edcba74a176641cd3ca9f9ca9989))
* refactor runtimes to leverage base impl ([4dac16f](https://github.com/scc-digitalhub/digitalhub-core/commit/4dac16f9398ce1a8fbaa04c07f0d87048c74e38d))
* refactor specs usage ([84ab23f](https://github.com/scc-digitalhub/digitalhub-core/commit/84ab23f7ddb72a502f4163a71075993e380e10d8))
* set default value in schema for single value enums ([ff9474a](https://github.com/scc-digitalhub/digitalhub-core/commit/ff9474a69f9097d080c099f1fefb8a98266ec3b5))
* use yaml for kdp workflow field ([83d54d7](https://github.com/scc-digitalhub/digitalhub-core/commit/83d54d722bcbfb6d90acf88233090435451a8274))



# [0.6.0-beta2](https://github.com/scc-digitalhub/digitalhub-core/compare/0.6.0-beta1...0.6.0-beta2) (2024-06-24)


### Bug Fixes

* fix python build issues with requirements ([d39e06a](https://github.com/scc-digitalhub/digitalhub-core/commit/d39e06ae077f80e6be72dea01d1ee938d3d551fb))



# [0.6.0-beta1](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta8...0.6.0-beta1) (2024-06-20)


### Bug Fixes

* align python builders logic ([073ce75](https://github.com/scc-digitalhub/digitalhub-core/commit/073ce75726564f2e3b2dd46340e45240875e730b))
* clear edited files ([3a87962](https://github.com/scc-digitalhub/digitalhub-core/commit/3a8796206646f605ebb9d027e9d4480267e51afa))
* clear modified runtimes ([e3782ec](https://github.com/scc-digitalhub/digitalhub-core/commit/e3782ecfcfe992299a5e6ff9742a1140bf24fc9f))
* disable cors support by default ([0b7ed7e](https://github.com/scc-digitalhub/digitalhub-core/commit/0b7ed7ecff69de5e763113ec71d7fec1500dd4f7))
* fix build-tool image pinning to latest ([b0c5e05](https://github.com/scc-digitalhub/digitalhub-core/commit/b0c5e0563408728133197b1411ada525ac8de02a))
* fix issues with building python images + update config ([52156d9](https://github.com/scc-digitalhub/digitalhub-core/commit/52156d998f3941447d9096d10b417523c6fc63b4))
* fix rsync error including hidden files in builder-tool ([caa391f](https://github.com/scc-digitalhub/digitalhub-core/commit/caa391f68ea290169269886b14ce6f05ac5afac5))
* fix wrongly modified container spec ([a8c064a](https://github.com/scc-digitalhub/digitalhub-core/commit/a8c064a199622b3c0c825ea65a4060230ccbecec))
* name audit metadata with snake_case ([f6fb81d](https://github.com/scc-digitalhub/digitalhub-core/commit/f6fb81de2126400873afe0eee643f499937d5efd))
* properly set condition on s3 store ([e3abd76](https://github.com/scc-digitalhub/digitalhub-core/commit/e3abd7695e13a02ada9be2a1350d94fd58753797))
* resolve symlink copy for build-tool ([df23b58](https://github.com/scc-digitalhub/digitalhub-core/commit/df23b587930d8a691ec7e9f16c74d92f188685c1))


### Features

* add headers for api version ([3f5b8eb](https://github.com/scc-digitalhub/digitalhub-core/commit/3f5b8ebc204671b02e5afa7ea403b5ae1a6cb873))
* cleanup redundant python builders ([ae727f7](https://github.com/scc-digitalhub/digitalhub-core/commit/ae727f7ead7a2ce274145fce07e991495a4a2376))
* files proxy initial support for s3/http ([0803999](https://github.com/scc-digitalhub/digitalhub-core/commit/08039994b72fbe5336c4bd4332cb5c40ba883241))



# [0.5.0-beta8](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta7...0.5.0-beta8) (2024-06-10)


### Bug Fixes

* update run specs for input/outputs defs ([5b21e99](https://github.com/scc-digitalhub/digitalhub-core/commit/5b21e990609a4369801ae21222b47d83f4e7c8e5))



# [0.5.0-beta7](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta6...0.5.0-beta7) (2024-06-06)


### Features

* add k8s runtime/priority classes ([b643c9f](https://github.com/scc-digitalhub/digitalhub-core/commit/b643c9fa51afa252c15bb10c7bf41d002173f8a3))
* run context filter for id like ([9511136](https://github.com/scc-digitalhub/digitalhub-core/commit/9511136f8dbd74669462005c725ac1ef4d329d1f))



# [0.5.0-beta6](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta5...0.5.0-beta6) (2024-06-05)


### Features

* python serve task ([a5a9ee9](https://github.com/scc-digitalhub/digitalhub-core/commit/a5a9ee9abd34d6a0e5696d08c7497bf50811e2f4))
* remove runs after finals state (completed+error) via delete + cleanups ([cb4cf32](https://github.com/scc-digitalhub/digitalhub-core/commit/cb4cf322775d7a3403e0604b62fcd9669506d7e9))



# [0.5.0-beta4](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta3...0.5.0-beta4) (2024-06-04)


### Bug Fixes

* handle local path in source for python runtime ([0c166b9](https://github.com/scc-digitalhub/digitalhub-core/commit/0c166b979c497d0c1e5b75417c1c27dbabf7e017))


### Features

* add input/outputs to python run ([2caa488](https://github.com/scc-digitalhub/digitalhub-core/commit/2caa4886d6e6ab888736a0b4cc11f63db58ee6c4))



# [0.5.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta2...0.5.0-beta3) (2024-05-31)


### Bug Fixes

* fix kaniko building contextSource map as array instead of strings ([4e6fe41](https://github.com/scc-digitalhub/digitalhub-core/commit/4e6fe4170d13989fd3b3eaa09f9df0952735b85a))


### Features

* add openmetadata integration as custom meta obj ([263801c](https://github.com/scc-digitalhub/digitalhub-core/commit/263801c141a685357e12695b35ee399af886a980))
* python runtime job execution ([0593154](https://github.com/scc-digitalhub/digitalhub-core/commit/05931541d3e9d4e4aa545822095d23c2adc0b8bd))
* update specs and add labels ([0390fad](https://github.com/scc-digitalhub/digitalhub-core/commit/0390fad356f3c5cbe6cccff229809dae4c3897ac))



# [0.5.0-beta2](https://github.com/scc-digitalhub/digitalhub-core/compare/0.5.0-beta1...0.5.0-beta2) (2024-05-29)


### Bug Fixes

* fix schema generation for records ([65f2826](https://github.com/scc-digitalhub/digitalhub-core/commit/65f2826f464d2810a164a775f476a9034c2145d0))


### Features

* add title+desc to schemas derived for specs via annotation scan ([d3b27de](https://github.com/scc-digitalhub/digitalhub-core/commit/d3b27de1323f142ff034c384141533af4306ab99))
* auto-generate title and desc in schema for fields + unwrap run specs ([0978f0e](https://github.com/scc-digitalhub/digitalhub-core/commit/0978f0eb94f7c9966e4a2b9e82a56d6bd7bfd057))
* unwrap container run specs + tweak json schema generation ([8d954c8](https://github.com/scc-digitalhub/digitalhub-core/commit/8d954c80ad23ef9b6b84cc481d7a70dcf7f9727f))
* update k8s internal logging ([77b4c10](https://github.com/scc-digitalhub/digitalhub-core/commit/77b4c1002a17feca3f8195765356cbcf4e6d3b3f))



# [0.5.0-beta1](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0...0.5.0-beta1) (2024-05-20)


### Features

* k8s metrics collection ([462b11f](https://github.com/scc-digitalhub/digitalhub-core/commit/462b11fa05f5ad2bc2f382df5886cbf6a3dc00fe))
* log collection and proper refactoring of monitors ([31b4b35](https://github.com/scc-digitalhub/digitalhub-core/commit/31b4b353ba70d472c68f0c7038a01c618ea6894f))
* model service align ([d572dcd](https://github.com/scc-digitalhub/digitalhub-core/commit/d572dcd66a761568c83c0d9a095cf75d7679dc86))
* remove timestamp from logs ([3a50713](https://github.com/scc-digitalhub/digitalhub-core/commit/3a507137397470ecc611ba0f17a33f9d0dea938e))
* support models for ml ([aa1ba15](https://github.com/scc-digitalhub/digitalhub-core/commit/aa1ba15dbd87af3f9b02ef8c6e00dd6f2adaab12))
* update log handling, collect all containers ([759d536](https://github.com/scc-digitalhub/digitalhub-core/commit/759d5362e335b03cbf50da8a742b124329b1cdab))



# [0.4.0](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta13...0.4.0) (2024-05-14)


### Bug Fixes

* always use (dhcore) for managed-by label ([55ecad0](https://github.com/scc-digitalhub/digitalhub-core/commit/55ecad01934d841396c99356dc46324847d64488))



# [0.4.0-beta13](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta12...0.4.0-beta13) (2024-05-14)


### Bug Fixes

* fix issues with run lifecycle in k8s + bump kubernetes client dep ([0ce18b9](https://github.com/scc-digitalhub/digitalhub-core/commit/0ce18b9df99f849ea578687add2659d5a46a3a2d))
* fix name in pom + fix nullpointer in k8serve ([f42b439](https://github.com/scc-digitalhub/digitalhub-core/commit/f42b439acb43eaea999358db10168edd164646d3))



# [0.4.0-beta12](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta11...0.4.0-beta12) (2024-05-10)


### Bug Fixes

* fix k8s labels ([b774509](https://github.com/scc-digitalhub/digitalhub-core/commit/b7745096358b50ae8c10f85c8e4da037ad21d365))
* project name and must must be equals ([5c4b20f](https://github.com/scc-digitalhub/digitalhub-core/commit/5c4b20f71272433ec9c97ab5c0a2e056527f66b8))


### Features

* use release name for core version ([7fe8e58](https://github.com/scc-digitalhub/digitalhub-core/commit/7fe8e58c3f25125f84a8bd0fdbca5a7c0466fefd))
* use revision for release version ([3a849c1](https://github.com/scc-digitalhub/digitalhub-core/commit/3a849c1680d3db863e2f6ec2c59402869bf03055))
* validation + search latest api + fixes ([6117fbb](https://github.com/scc-digitalhub/digitalhub-core/commit/6117fbb6d55a0a75a5640d96bc62f02fec6649dd))



# [0.4.0-beta11](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta10...0.4.0-beta11) (2024-05-09)


### Bug Fixes

* add converter to state in entities ([3beff36](https://github.com/scc-digitalhub/digitalhub-core/commit/3beff36aa1041b9e8031a28c7a85b464b617d320))
* fix issues with run fsm and concurrency + various fixes ([34e42f1](https://github.com/scc-digitalhub/digitalhub-core/commit/34e42f1e3c9a6720bc51be3947d622a1af357a5a))



# [0.4.0-beta10](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta9...0.4.0-beta10) (2024-05-09)


### Features

* add registry config shared between k8s and kaniko ([0d9a6ed](https://github.com/scc-digitalhub/digitalhub-core/commit/0d9a6ed75abc6ccf430b93cdf81c9881d637b9a7))



# [0.4.0-beta9](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta8...0.4.0-beta9) (2024-05-07)


### Bug Fixes

* fix labels on k8s objects + remove required imageName from container ([1e5c907](https://github.com/scc-digitalhub/digitalhub-core/commit/1e5c90771c458bffd6547b768d89f38df7fe1563))



# [0.4.0-beta8](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta7...0.4.0-beta8) (2024-05-07)


### Bug Fixes

* store secret for kaniko runs ([5ffba49](https://github.com/scc-digitalhub/digitalhub-core/commit/5ffba496d053fada7aa695c9a0c5378c07ceb376))



# [0.4.0-beta7](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta6...0.4.0-beta7) (2024-05-03)



# [0.4.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.4.0-beta2...0.4.0-beta3) (2024-04-29)


### Features

* secured runnable can transport auth details to frameworks ([d72c2d5](https://github.com/scc-digitalhub/digitalhub-core/commit/d72c2d5be7d956ee3b51a2d920ba7d83cca4f887))



# [0.4.0-beta6](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta5...0.4.0-beta6) (2024-04-30)


### Bug Fixes

* builder tool should skip refs if not set ([e9bd4be](https://github.com/scc-digitalhub/digitalhub-core/commit/e9bd4be69253062611ada9fa7503d392fe977240))
* fix dockerfile building for container runtime ([78ac814](https://github.com/scc-digitalhub/digitalhub-core/commit/78ac814475096a14c59bf953400f7cdc0785d308))


### Features

* update container function image after build via kaniko + clear k8s labels usage ([fedd1b0](https://github.com/scc-digitalhub/digitalhub-core/commit/fedd1b092332155398d4edd05535b4c988ebdb18))



# [0.4.0-beta5](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta4...0.4.0-beta5) (2024-04-30)


### Bug Fixes

* reattach console to upstream ([0ed5389](https://github.com/scc-digitalhub/digitalhub-core/commit/0ed53898c8bffb21a85296b6460752d73a32af24))



# [0.4.0-beta4](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta3...0.4.0-beta4) (2024-04-29)


### Bug Fixes

* update K8sKanikoFramework.java to use image registry ([b0bf5cc](https://github.com/scc-digitalhub/digitalhub-core/commit/b0bf5cc56546e103c701755fe6d663ab04b0cead))



# [0.4.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.4.0-beta2...0.4.0-beta3) (2024-04-29)


### Features

* secured runnable can transport auth details to frameworks ([d72c2d5](https://github.com/scc-digitalhub/digitalhub-core/commit/d72c2d5be7d956ee3b51a2d920ba7d83cca4f887))



# [0.4.0-beta2](https://github.com/scc-digitalhub/digitalhub-core/compare/0.4.0-beta1...v0.4.0-beta2) (2024-04-24)


### Bug Fixes

* fix entity type in workflow indexer ([5b9ee5b](https://github.com/scc-digitalhub/digitalhub-core/commit/5b9ee5b0eeb9b47af02560fc34de6a03ee15e1d4))
* fix oidc config params ([1c08ade](https://github.com/scc-digitalhub/digitalhub-core/commit/1c08ade59dae07141f6c084f45aa171f9540184e))


### Features

* action for building build-tool image ([62b765f](https://github.com/scc-digitalhub/digitalhub-core/commit/62b765f6398f784bde6f4d09c2e98aa8d3ad7772))
* kaniko build infrastructure ([5bd4fed](https://github.com/scc-digitalhub/digitalhub-core/commit/5bd4fed5ebf3765d6117233a4f5d86b11cdbead6))



# [0.4.0-beta1](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta8...0.4.0-beta1) (2024-04-16)


### Bug Fixes

* align kfp kinds and id handling ([241aff4](https://github.com/scc-digitalhub/digitalhub-core/commit/241aff472520d1352e0c545fe936843d91ecd0b5))
* disable request logging filter to avoid breaking request handling ([97c2e28](https://github.com/scc-digitalhub/digitalhub-core/commit/97c2e2833acd59e9f15df2b2028aa79257582eb0))
* fix auditor issue with null authentication on async threads ([77954e9](https://github.com/scc-digitalhub/digitalhub-core/commit/77954e9ab28231c88c2781ea5134f3a2b2f9575b))
* make oauth2 scopes configurable via env ([eec40cd](https://github.com/scc-digitalhub/digitalhub-core/commit/eec40cd60b5109d9db66c0792cc603c8af12c3a8))


### Features

* add cron job execution infrastructure + cleanups ([df983c1](https://github.com/scc-digitalhub/digitalhub-core/commit/df983c135713791b59e56fe83732e788d1f1e6a0))
* add handler to sourceCode in specs ([3b566a2](https://github.com/scc-digitalhub/digitalhub-core/commit/3b566a255b3e53bac130cb648081c9610bf154a5))
* add user filters to search and services ([f3e37f6](https://github.com/scc-digitalhub/digitalhub-core/commit/f3e37f6900696462605f21418971476c1869f246))
* add user to DTOs and audit user in entities + cleanups ([92eaac9](https://github.com/scc-digitalhub/digitalhub-core/commit/92eaac977b38ef8f2ec116d3684b7111093725fd))
* add validation instructions to k8s spec fields ([b1d2ece](https://github.com/scc-digitalhub/digitalhub-core/commit/b1d2ece165512f0b8a9ab3e51146a65e9c25781f))
* core volume as class with enum for volumeTypes ([1cde68f](https://github.com/scc-digitalhub/digitalhub-core/commit/1cde68f156a4d8b9458603d42c3026b203f1b94d))
* enable/disable solr for console via props ([e0a96f3](https://github.com/scc-digitalhub/digitalhub-core/commit/e0a96f363366c65d5fbe760f3cec83999f43a48a))
* indexers and indexable services + update controllers ([a1f5509](https://github.com/scc-digitalhub/digitalhub-core/commit/a1f5509e5075eccb307b84afeac41dec4b3ec355))
* update k8s core resource as (cpu/mem/gpu) ([a1c8623](https://github.com/scc-digitalhub/digitalhub-core/commit/a1c8623dc500e232c8bfa4d82326f73d5f57e45d))
* update project controller to let owners modify/delete owned projects ([1b507e0](https://github.com/scc-digitalhub/digitalhub-core/commit/1b507e0999d1b0be2c0acbe6cbfbf190fd3b0b36))



# [0.3.0-beta8](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta7...v0.3.0-beta8) (2024-04-04)


### Bug Fixes

* fix blob handling in psql ([5dabf59](https://github.com/scc-digitalhub/digitalhub-core/commit/5dabf5920d3528877e8f4fa7676b688ab9d3cc00))



# [0.3.0-beta7](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta6...v0.3.0-beta7) (2024-04-04)


### Bug Fixes

* fix errors in FSM and disable path traversal for more than 1-step state changes ([4a30939](https://github.com/scc-digitalhub/digitalhub-core/commit/4a309393003d714f91909ec3decfdf922cae687e))
* include only non null elements in spec toMap to avoid inserting invalid values in kube + handle find null in store ([b1db674](https://github.com/scc-digitalhub/digitalhub-core/commit/b1db674cb70fca606840bd847fd8b7044dd44774))
* typo in sql ([9748058](https://github.com/scc-digitalhub/digitalhub-core/commit/974805893180c96c98e0b62e470592912f12688e))



# [0.3.0-beta6](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta5...v0.3.0-beta6) (2024-04-02)


### Bug Fixes

* cleanup security config + add auth endpoint for console ([f36d53d](https://github.com/scc-digitalhub/digitalhub-core/commit/f36d53d4215721d81f6c80d26972069683278b46))



# [0.3.0-beta5](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta4...v0.3.0-beta5) (2024-04-02)


### Bug Fixes

* fix issues with jdbc initialization and refactor runnable store ([fb51646](https://github.com/scc-digitalhub/digitalhub-core/commit/fb51646da676f0e4f6025694735c37508d1a16b6))
* fix postgresql schema sql naming ([1adffb0](https://github.com/scc-digitalhub/digitalhub-core/commit/1adffb06cf4e224c4fb6c6c847755aaa339ad1e3))


### Features

* move actuator management endpoint to dedicated port + fix configuration issues ([146c50b](https://github.com/scc-digitalhub/digitalhub-core/commit/146c50b09df785e56274db9a1910915898bf0a8a))
* update specs and define tableSchema ([b0c8bfa](https://github.com/scc-digitalhub/digitalhub-core/commit/b0c8bfad757dd10f63ab648f321b2599e934cda1))



# [0.3.0-beta4](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.3.0-beta3...v0.3.0-beta4) (2024-03-27)



# [0.3.0-beta3](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.0.31...v0.3.0-beta3) (2024-03-26)


### Bug Fixes

* add deleting transaction to all run state ([0b57c91](https://github.com/scc-digitalhub/digitalhub-core/commit/0b57c910a316fe941d51eb1c2a62404c52a383e8))
* align specs and correct implementations ([c9463cc](https://github.com/scc-digitalhub/digitalhub-core/commit/c9463cc2ff430cc4f07f817054e6cfe40c8522c2))
* check for project existence before creating entities ([c1a42dd](https://github.com/scc-digitalhub/digitalhub-core/commit/c1a42dd250d0db8b49310c71d337b4b2dbb524f7))
* check for secret existence before deleting ([39e0964](https://github.com/scc-digitalhub/digitalhub-core/commit/39e0964753f0672bb780748bafbbc9758466104c))
* correct mlrun task job spec ([18d7aa1](https://github.com/scc-digitalhub/digitalhub-core/commit/18d7aa14644bf1e70c86d22ec87144756c2f1782))
* correct wrongly configured pagination handler to start page count from 0 ([ee542d1](https://github.com/scc-digitalhub/digitalhub-core/commit/ee542d18552b2afec39584445ae8328184d05e9c))
* enforce unique secret for name+project ([dd09cbe](https://github.com/scc-digitalhub/digitalhub-core/commit/dd09cbe3041aa76bfe8aaa6f132556dbc078ecb7))
* fix cloud broadcast to rabbit + make async ([c28e46b](https://github.com/scc-digitalhub/digitalhub-core/commit/c28e46b3203ef7297d6868b8f09cc287d05acbc9))
* fix image names in Dockerfile ([9fa9319](https://github.com/scc-digitalhub/digitalhub-core/commit/9fa93192f7227bee78af4ead0b0e50b675fb959a))
* fix mapping for delete all dataItem in context controller ([d9750c4](https://github.com/scc-digitalhub/digitalhub-core/commit/d9750c49178b7c7fe88048aada4a96fcc3641767))
* fix pattern usage in runUtils ([cae0de7](https://github.com/scc-digitalhub/digitalhub-core/commit/cae0de7f521711e9703da198dd69702b5611c3ad))
* fix runmanager wrong states and transactions ([056a7d7](https://github.com/scc-digitalhub/digitalhub-core/commit/056a7d7fbe0da7e254ed7a917253d6536e462b02))
* include frontend in docker builds ([83cd53f](https://github.com/scc-digitalhub/digitalhub-core/commit/83cd53f2fd65f8045bb08eb166f9c32cc401910b))
* include frontend in docker cache ([6bac7e9](https://github.com/scc-digitalhub/digitalhub-core/commit/6bac7e9cc866a6d17f4091a93ff6a3dd5dba2d2c))
* refactor converters implementations and store state enum as string in db ([1b95480](https://github.com/scc-digitalhub/digitalhub-core/commit/1b95480f2f8bd2538dcfaecce6acb23f43bb8c22))
* runnable in events are optional ([5e47826](https://github.com/scc-digitalhub/digitalhub-core/commit/5e478266be28e7b385961f4e9a552c8045ea9d13))
* update base controllers to properly extract search filters + update schema generation ([f519e63](https://github.com/scc-digitalhub/digitalhub-core/commit/f519e634928d43778906208ace80123eb1e0f9c0))
* update dtos and specs ([9381802](https://github.com/scc-digitalhub/digitalhub-core/commit/9381802e170ce9e4bde9cdf5381d049d2b32ce94))
* use localDateTime with iso format for metadata + cleanups ([edcbd44](https://github.com/scc-digitalhub/digitalhub-core/commit/edcbd4438f6cce29928efa8b8eb5330807c2c776))
* use offsetDateTime with iso format for metadata ([bb9495f](https://github.com/scc-digitalhub/digitalhub-core/commit/bb9495fdc59d2b8cf4f8a2ca477bfbac83e01ae4))


### Features

* add LOG_LEVEL env variable to set logging ([5c6c395](https://github.com/scc-digitalhub/digitalhub-core/commit/5c6c3958cdfd5eb969b34a5901ab350d7c953bde))
* align runtimes to run manager + fix and align k8s frameworks + add run actions RPC to context controller ([5a47880](https://github.com/scc-digitalhub/digitalhub-core/commit/5a478809f1f78b029fe72536560e52e0827de1e8))
* async delete listener for runs ([afcae53](https://github.com/scc-digitalhub/digitalhub-core/commit/afcae530719967afcd053d6b1779a892926b60e7))
* delete runs via manager and properly handle cleanups in runtimes ([3d75f4b](https://github.com/scc-digitalhub/digitalhub-core/commit/3d75f4b22a15bd7d3c033f01130ec71820a96590))
* handle common exceptions in controllerAdvice ([e904621](https://github.com/scc-digitalhub/digitalhub-core/commit/e9046211685c9fa01dcb403e41771a7a2f86d65b))
* integrate user console + fix request handling for application ([1ccbe0f](https://github.com/scc-digitalhub/digitalhub-core/commit/1ccbe0f82bbd22ec94486c18dafee9518d8112fb))
* KFP remote implementation ([#71](https://github.com/scc-digitalhub/digitalhub-core/issues/71)) ([e6eb6a2](https://github.com/scc-digitalhub/digitalhub-core/commit/e6eb6a20832c2611916f90d5cf8896c8a78dad1d))
* sourceCode in spec supports a list of languages ([8c1db42](https://github.com/scc-digitalhub/digitalhub-core/commit/8c1db42ac9841bb34bdb37d1196c92777877d206))
* update specs (BREAKING) ([0429407](https://github.com/scc-digitalhub/digitalhub-core/commit/04294076d037755cd3dbb932fde651e31c357022))



## [0.0.31](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.0.30...v0.0.31) (2024-03-08)


### Bug Fixes

* update security config to enable ROLE_ADMIN access from oidc ([5269937](https://github.com/scc-digitalhub/digitalhub-core/commit/526993711367f714a4eca2bb0ac9919857793fc4))


### Features

* add store key to all dtos ([1bd0b38](https://github.com/scc-digitalhub/digitalhub-core/commit/1bd0b381cf6715faef6c8a267ab063f12d3e0021))



## [0.0.30](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.29...v0.0.30) (2024-03-04)


### Bug Fixes

* add pathvariable to schemaController.get ([ea18b12](https://github.com/scc-digitalhub/digitalhub-core/commit/ea18b12f374a8cd06231428ae52f056d6059d832))
* align run usage with models + handle store exceptions ([d6a36d4](https://github.com/scc-digitalhub/digitalhub-core/commit/d6a36d4464bcc093619891a76d9e5ee212a7897a))
* update run.sh script ([9bf299e](https://github.com/scc-digitalhub/digitalhub-core/commit/9bf299e0fd409037423e1f9083ea96e59a0257d0))


### Features

* banner + correct version number in app ([3b335d8](https://github.com/scc-digitalhub/digitalhub-core/commit/3b335d8542ec6cf0b45dcc35a49f597b685ac5c5))
* cronJob k8s support + cleanups ([caa4278](https://github.com/scc-digitalhub/digitalhub-core/commit/caa4278e2978b7aca5010d05a81bf5f973ef1987))



## [0.0.29](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.28...0.0.29) (2024-02-21)


### Bug Fixes

* add converter for entityName enum with value, supporting lowercase+ pluralization ([e867eb2](https://github.com/scc-digitalhub/digitalhub-core/commit/e867eb216c1ef180b4d17eb30c958961e0776d40))
* cache dockerfile ([8c32692](https://github.com/scc-digitalhub/digitalhub-core/commit/8c326924611b10f822af7ccc4649b2100418ebca))
* use conditional kube annotation for k8s stores ([a571ebb](https://github.com/scc-digitalhub/digitalhub-core/commit/a571ebb9bd603f6e2167c447e722b2f3ba91f120))


### Features

* auth annotations with roles on controllers ([2de6ca2](https://github.com/scc-digitalhub/digitalhub-core/commit/2de6ca2d6452f1cae77521969c9efed9aa48a84f))



## [0.0.28](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.27...0.0.28) (2024-02-21)



## [0.0.27](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.26...0.0.27) (2024-02-20)


### Bug Fixes

* add constructor to dataitem base spec ([57d578d](https://github.com/scc-digitalhub/digitalhub-core/commit/57d578d3e015bfe4855ab1edd1d9e322deefbc78))
* change maven image tag ([a70a19c](https://github.com/scc-digitalhub/digitalhub-core/commit/a70a19cb750f68da18a889afd7e922a6fa66b3d8))
* correct default db location ([dcdb033](https://github.com/scc-digitalhub/digitalhub-core/commit/dcdb0331d200b0b84c528c2fb3c02e68f9591c2a))


### Features

* add runtime to specs + add runtime filtering to schemas via registry ([a78173b](https://github.com/scc-digitalhub/digitalhub-core/commit/a78173bdfa50428e401ed49a364099ee31abe1db))
* add schema handling with registry + controllers + utils ([c742079](https://github.com/scc-digitalhub/digitalhub-core/commit/c742079b463c004d487f887ecf4482a6ad098cd2))
* allow runs to override k8s specs defined in tasks ([a7177de](https://github.com/scc-digitalhub/digitalhub-core/commit/a7177deb54328f8ed026ad3134f14550e93f707b))
* auto build images on tags ([153f951](https://github.com/scc-digitalhub/digitalhub-core/commit/153f951166669194258ea97f4d210ff1540df3bb))



## [0.0.26](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.25...0.0.26) (2024-02-12)



## [0.0.25](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.24...0.0.25) (2024-02-06)



## [0.0.24](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.23...0.0.24) (2024-02-06)



## [0.0.23](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.22...0.0.23) (2024-02-05)



## [0.0.22](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.21...0.0.22) (2024-01-25)



## [0.0.21](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.20...0.0.21) (2024-01-25)



## [0.0.20](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.19...0.0.20) (2024-01-25)



## [0.0.19](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.18...0.0.19) (2024-01-25)



## [0.0.18](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.17...0.0.18) (2024-01-24)



## [0.0.17](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.16...0.0.17) (2024-01-22)



## [0.0.16](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.15...0.0.16) (2024-01-22)



## [0.0.15](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.14...0.0.15) (2024-01-22)



## [0.0.14](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.13...0.0.14) (2024-01-22)



## [0.0.13](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.12...0.0.13) (2024-01-19)



## [0.0.12](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.11...0.0.12) (2024-01-18)



## [0.0.11](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.10...0.0.11) (2023-12-13)



## [0.0.10](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.9...0.0.10) (2023-12-12)



## [0.0.9](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.8...0.0.9) (2023-12-12)



## [0.0.8](https://github.com/scc-digitalhub/digitalhub-core/compare/0.0.6...0.0.8) (2023-12-11)



## [0.0.4](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.0.3-snapshot...0.0.4) (2023-11-14)



## [0.0.3-snapshot](https://github.com/scc-digitalhub/digitalhub-core/compare/v0.0.2-snapshot...v0.0.3-snapshot) (2023-09-19)



## 0.0.2-snapshot (2023-07-11)



