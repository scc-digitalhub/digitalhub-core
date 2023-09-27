"""
Task specification registry module.
"""
from sdk.entities.task.kinds import TaskKinds
from sdk.entities.task.spec.models import TaskParamsBuild, TaskParamsPerform
from sdk.entities.task.spec.objects import TaskSpecBuild, TaskSpecJob

TASK_SPEC = {
    TaskKinds.BUILD.value: TaskSpecBuild,
    TaskKinds.JOB.value: TaskSpecJob,
}
TASK_MODEL = {
    TaskKinds.BUILD.value: TaskParamsBuild,
    TaskKinds.JOB.value: TaskParamsPerform,
}
