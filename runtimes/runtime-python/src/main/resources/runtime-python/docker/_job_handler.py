# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import os
import typing
from pathlib import Path
from typing import Any, Callable

from digitalhub.context.api import get_context
from digitalhub.entities.project.crud import get_project
from digitalhub.entities.run.crud import get_run
from digitalhub.runtimes.enums import RuntimeEnvVar
from digitalhub_runtime_python.utils.configuration import (
    import_function_and_init_from_source,
)
from digitalhub_runtime_python.utils.inputs import compose_init, compose_inputs
from digitalhub_runtime_python.utils.outputs import build_new_status, parse_outputs

if typing.TYPE_CHECKING:
    from digitalhub_runtime_python.entities.run._base.entity import RunPythonRun
    from nuclio_sdk import Context, Event, Response


DEFAULT_PATH = Path("/shared")


def execute_user_init(
    init_function: Callable,
    context: Context,
    run: RunPythonRun,
) -> None:
    """
    Execute user init function.

    Parameters
    ----------
    init_function : Callable
        User init function.
    context : Context
        Nuclio context.
    run : RunPythonRun
        Run entity.
    """
    init_params: dict = run.spec.to_dict().get("init_parameters", {})
    params = compose_init(init_function, context, init_params)
    context.logger.info("Execute user init function.")
    init_function(**params)


def init_context(context: Context) -> None:
    """
    Set the context attributes.
    Collect project, run and functions.

    Parameters
    ----------
    context : Context
        Nuclio context.
    """
    context.logger.info("Initializing context...")

    # Get project
    project_name = os.getenv(RuntimeEnvVar.PROJECT.value)
    project = get_project(project_name)

    # Set root directory from context
    ctx = get_context(project.name)
    ctx.root.mkdir(parents=True, exist_ok=True)

    # Get run
    run: RunPythonRun = get_run(
        os.getenv(RuntimeEnvVar.RUN_ID.value),
        project=project_name,
    )

    # Set running context
    context.logger.info("Starting execution.")
    run.start_execution()

    # Get inputs if they exist
    run.spec.inputs = run.inputs(as_dict=True)

    # Get function (and eventually init) to execute and
    # set it in the context. Path refers to the working
    # user dir (will be taken from run spec in the future),
    # default_py_file filename is "main.py", source is the
    # function source
    source = {{source}}
    func, init_function = import_function_and_init_from_source(DEFAULT_PATH, source)

    # Set attributes
    setattr(context, "project", project)
    setattr(context, "run", run)
    setattr(context, "user_function", func)
    setattr(context, "root", ctx.root)

    # Execute user init function
    if init_function is not None:
        execute_user_init(init_function, context, run)

    context.logger.info("Context initialized.")


def handler(context: Context, event: Event) -> Response:
    """
    Nuclio handler for python function.

    Parameters
    ----------
    context : Context
        Nuclio context.
    event : Event
        Nuclio event.

    Returns
    -------
    Response
        Response.
    """
    ############################
    # Set inputs
    #############################
    try:
        spec: dict = context.run.spec.to_dict()
        context.logger.info("Configuring function inputs.")
        func_args = compose_inputs(
            spec.get("inputs", {}),
            spec.get("parameters", {}),
            False,
            context.user_function,
            context.project,
            context,
            event,
        )
    except Exception as e:
        raise e

    ############################
    # Execute function
    ############################
    try:
        project: str = context.project.name
        context.logger.info("Executing function.")
        if hasattr(context.user_function, "__wrapped__"):
            results: dict = context.user_function(project, context.run.key, **func_args)
        else:
            exec_result = context.user_function(**func_args)
            results = parse_outputs(exec_result, project, context.run.key)

        context.logger.info(f"Output results: {results}")

        context.logger.info("Setting run status.")
        context.run.refresh()
        new_status = {
            **build_new_status(context.project.name, results),
            **context.run.status.to_dict(),
        }
        context.run.set_status(new_status)
        context.run.save(update=True)

    except Exception as e:
        raise e
    finally:
        context.run.end_execution()

    ############################
    # End
    ############################
    context.logger.info("Done.")
    return context.Response(
        body="OK",
        headers={},
        content_type="text/plain",
        status_code=200,
    )
