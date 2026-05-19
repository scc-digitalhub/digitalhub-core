# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import os
import typing
from typing import Any, Callable

from digitalhub.context.api import get_context
from digitalhub.entities.project.crud import get_project
from digitalhub.entities.run.crud import get_run
from digitalhub.runtimes.enums import RuntimeEnvVar
from digitalhub_runtime_python.utils.configuration import import_function_and_init_from_source
from digitalhub_runtime_python.utils.inputs import compose_inputs
from digitalhub_runtime_python.utils.outputs import parse_outputs, build_new_status


if __name__ == "__main__":

    # Get project
    project_name = os.getenv(RuntimeEnvVar.PROJECT.value)
    project = get_project(project_name)

    # Set root directory from context
    ctx = get_context(project.name)
    ctx.root.mkdir(parents=True, exist_ok=True)

    # Get run
    run = get_run(
        os.getenv(RuntimeEnvVar.RUN_ID.value),
        project=project_name,
    )

    # Set running context
    run.start_execution()

    # Get inputs if they exist
    run.spec.inputs = run.inputs(as_dict=True)

    # Get function (and eventually init) to execute and
    # set it in the context. Path refers to the working
    # user dir (will be taken from run spec in the future),
    # default_py_file filename is "main.py", source is the
    # function source
    source = {{source}}
    func, _ = import_function_and_init_from_source(source)

    func_args = {}

    # Compose inputs
    try:
        spec: dict = run.spec.to_dict()
        func_args = compose_inputs(
            spec.get("inputs", {}),
            spec.get("parameters", {}),
            False,
            func,
            project.name,
            None,
            None,
        )
    except Exception as e:
        raise e

    ############################
    # Execute function
    ############################
    try:
        project_name: str = project.name
        if hasattr(func, "__wrapped__"):
            results: dict = func(project_name, run.key, **func_args)
        else:
            exec_result = func(**func_args)
            results = parse_outputs(exec_result, project_name, run.key)

        run.refresh()
        new_status = {
            **build_new_status(project_name, results),
            **run.status.to_dict(),
        }
        run.set_status(new_status)
        run.save(update=True)

    except Exception as e:
        raise e
    finally:
        run.end_execution()
