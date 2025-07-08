<!--
SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler

SPDX-License-Identifier: Apache-2.0
-->

TODO: 09/11/2023

- [x] Fix poller scheduler at the moment it recreate a thread every time

TODO: 20/09/2011

-[x] il campo task nel modello Task non e' piu univoco

-[x] Rinominare il campo "task" in Task, TaskDTO come "function"

- [x] Aggiungere i DataItem a projects

- [x] Creare modelli METADATI per tutti i tipi che abbiamo Project, artifact, function....

- [x] In Run e RunDTO il campo task contiene una stringa di questo tipo "dbt+<perform>://<project>/<function>:<version>

- [x] tutti task vengono eseguite quando invoco la sua run, la relazione e' 1 a 1

- [x] il builder della run non utilizza piu la stringa del task -> dbt://<project>/<function>..ma utilizza il kind vero
      e proprio della funziona. Quindi avro' kind: "build" di conseguenza faro' un build di DBT.

- [x] runBuilderFactory.getBuilder(taskAccessor.getKind()) prendera' poi il kind vero e proprio del task e il tipo di
      funzione non solo il tipo di funzione. runBuilderFactory.getBuilder(task.getKind(), taskAccessor.getKind()).
      taskAccessor.getKind() -> descrive il tipo della funzione.

- [x] Function, Artifact, Dataitem, Workflow ed implementare Model che vanno pero' nelle spec

- [x] Integrare nuova library for kubernetes.

- [x] Definire un Poller che controlla il risultato del job.

- State machine usata solo per il singolo Task -> Run
- Qubeflow ....verra' utilizzato per una di Task -> Run che vengono eseguiti in sequenza.
- Variabili d'ambiente definite tutte con DHUB\_ come prefisso che va eliminato quando le inietto nel container.

- IL runtime dobbiamo avere due funzioni
  - [x] build()
  - [x] run() -> non in termini di eseguo ma traduco nel framework specifico della run
  - TODO: parse()

========================= PROCEDURA =====================

5519 cd src/main/java/it/smartcommunitylabdhub/dbt/config
5520 rm -rf sdk
5521 cp -r ../../../../../../../sdk .
5522 ls
5523 cat docker-compose.yaml
5524 minikube docker-env
5525 eval $(minikube -p minikube docker-env)
5526 docker images
5527 docker build -t ltrubbianifbk/dbt_core .
5528 docker push ltrubbianifbk/dbt_core:latest
5529 exit
5530 clear

================================= ROBA VECCHIA ==================================

- [x] La run puo avere un parametro local = True o False,
      nel caso di True costruisco la run e ritorno direttamente la run sara' poi a carico del SDK lanciare la Run e chiamare
      un Patch per aggiornare la run.

// [x] creare nuova context api

// [x] Done
Any object has a context that is the 'project'

- project + name -> List of all version
- project + name + uuid -> Specific element
- project + name + '/latest' get latest specific element for the the project.

This pattern is applied to all Models

Artifact:

/api/v1/-/ProjectName/artifacts/ArtifactName -> List of Artifact
/api/v1/-/ProjectName/artifacts/ArtifactName/90i23o-fdkfjl-fdkjfkld-fjdhfkn -> Artifact
/api/v1/-/ProjectName/artifacts/ArtifactName/latest -> Artifact

// [x] Done
Project : When I get the project, if a function for instance is embedded = True I get all the fields in function
otherwise I get only kind and name.

<!--
Project -> {
  name:..,
  extra:...
  Function -> { // if embedded = true
    name: xxx,
    kind:xxx,
    spec....
    extra
  }
  Function -> { // if embedded = false
    name: xxx,
    kind:xxx,
  }
} -->

Same thing is applied for workflow, dataitem, artifact.
// [x] DONE. On project delete ...cascade functions, workflows, artifacts, dataitems

// TODO:
Define .....Artifact, Function, Workflow, DataItem, Project scheme. Function of type: 'job', 'nuclio', 'serving' ->
define the spec fields.
For the Spec mapping define Accessor (Pattern) following this
solution : https://github.com/spring-projects/spring-security/blob/main/oauth2/oauth2-core/src/main/java/org/springframework/security/oauth2/core/ClaimAccessor.java

Map all yaml data coming from mlrun

<!--
kind: job
metadata:
  credentials:
    access_key: $generate
  labels:
    color: blue
  name: test-func
  project: default
  tag: latest
  hash: c482bd8bcaffbb15b5557d89bfddb4e496bfa32e
  updated: '2023-06-06T13:15:08.851869+00:00'
spec:
  args: []
  build:
    base_image: ''
    commands: ''
    functionSourceCode: ZGVmIGhhbmRsZXIoY29udGV4dCk6CiAgICBjb250ZXh0LmxvZ2dlci5pbmZvKCdIZWxsbyB3b3JsZCcp
    image: ''
  description: Test description
  env:
    - name: LocalVariable
      value: '111'
  image: mlrun/mlrun
  priority_class_name: ''
  preemption_mode: ''
  volume_mounts: []
  volumes: []
  resources:
    limits:
      cpu: '4'
      memory: 3Mi
      nvidia.com/gpu: '5'
    requests:
      cpu: '2'
      memory: 1Mi
  default_handler: ''
status: {} -->

//[x] SYNC WITH MLRUN

- CAll mlrun api to sync data
  - DHCORE -> Mlrun // [x]
  - DHCORE <- Mlrun // TODO

Check DB and API for DATA sync, write service for sync

- Project
- Function
- Artifact
- Dataitem
- Workflow

The Hash is generated by mlrun and passed back to the object created in CORE. Hash in our system is an EXTRA Field.

//[x]

- Il Create del task Crea il task
- Il Create della run crea la run a partire dal task ( solo id ) con opzionali parametri extra per la run (RunDTO) ed
  esegue la run subito.

//[x]
Run need to have run kind. When I create the publisher from builder I have to pass the kind from the task string job://
in that case "job"

//[x]
get log per una run specifica (API)

//[x]
for any entities task, log, function, artifact , dataitem, project, workflow check if duplicate uuid exist..if yes throw
an exception.

[x] clear controllers
[x] clear services

// TODO

- Stoppare una run:
  - stoppo tutte le run (per il task)
  - elimino tutte le run (per il task)
  - elimino il task

// [x]

dalla chiave ricevuta outputs:["dataset"] salvo nella run con la chiave "dataset" i risultati della run quindi i
riferimenti artifact con questo formato : "store://<project>/artifacts/<kind>/<name>:<uuid>"

// TODO:

- Object list instead of list of "store://<project>/artifacts/<kind>/<name>:<uuid>"

```json
output :["<key>", "..."]
[
{
key: "dataset",
id: "store://<project>/artifacts/<kind>/<name>:<uuid>",
kind: "dataitem"
},
....
]
```

// [x]: Come creare l'immagine del backend ed eseguirla.

1. creare immagine : mvn spring-boot:build-image -f "/home/ltrubbiani/Labs/digitalhub-core/core/pom.xml"
   -DskipTests=true

2. Descrivere le varie entita' : Progetti, funzioni, .....
