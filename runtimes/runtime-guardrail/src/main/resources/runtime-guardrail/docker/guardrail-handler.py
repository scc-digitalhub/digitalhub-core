# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import os
import inspect
from pathlib import Path
from typing import Any, Callable

from digitalhub.context.api import get_context
from digitalhub.entities.project.crud import get_project
from digitalhub.entities.run.crud import get_run
from digitalhub.runtimes.enums import RuntimeEnvVar

from nuclio_sdk import Context, Event, Response
from digitalhub_runtime_python.utils.configuration import (
    import_function_and_init_from_source,
)
from digitalhub_runtime_python.utils.inputs import compose_init, compose_inputs


DEFAULT_PATH = Path("/shared")


def execute_user_init(
    init_function: Callable,
    context: Context,
    run: Any,
) -> None:
    """
    Execute user init function.

    Parameters
    ----------
    init_function : Callable
        User init function.
    context : Context
        Nuclio context.
    run : Any
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
    setattr(context, "project", project)

    # Set root directory from context
    ctx = get_context(project.name)
    ctx.root.mkdir(parents=True, exist_ok=True)

    run: Any = None
    # # Get run TODO
    # run = get_run(
    #     os.getenv(RuntimeEnvVar.RUN_ID.value),
    #     project=project_name,
    # )
    # # Set running context
    # context.logger.info("Starting execution.")
    # run.start_execution()
    setattr(context, "run", run)


    # Get function (and eventually init) to execute and
    # set it in the context. Path refers to the working
    # user dir (will be taken from run spec in the future),
    # default_py_file filename is "main.py", source is the
    # function source
    source = {{source}}
    func, init_function = import_function_and_init_from_source(DEFAULT_PATH, source, "main.py")

    # Set attributes
    setattr(context, "user_function", func)
    setattr(context, "root", ctx.root)

    # Execute user init function
    if init_function is not None:
        execute_user_init(init_function, context, run)

    context.logger.info("Context initialized.")

def handler_serve(context: Context, event: Event) -> Any:
    """
    Main function.

    Parameters
    ----------
    context : Context
        Nuclio context.
    event : Event
        Nuclio event

    Returns
    -------
    Any
        User function response.
    """
    ############################
    # Set inputs
    #############################
    try:
        context.logger.info("Configuring function inputs.")
        func_args = compose_inputs(
            {},
            {},
            False,
            context.user_function,
            context.project,
            context,
            event,
        )
    except Exception as e:
        raise e
    finally:
        context.run.end_execution()

    ############################
    # Call user function
    ############################
    try:
        context.logger.info("Calling user function.")
        return context.user_function(**func_args)
    except Exception as e:
        raise e
    finally:
        context.run.end_execution()
