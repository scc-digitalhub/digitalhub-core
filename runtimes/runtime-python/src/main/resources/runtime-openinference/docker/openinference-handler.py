# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import os
import typing
import json
from pathlib import Path
from typing import Any, Callable

from digitalhub.context.api import get_context
from digitalhub.entities.project.crud import get_project
from digitalhub.entities.run.crud import get_run
from digitalhub.runtimes.enums import RuntimeEnvVar
from digitalhub_runtime_python.utils.configuration import import_function_and_init_from_source
from digitalhub_runtime_python.utils.inputs import compose_init, compose_inputs

if typing.TYPE_CHECKING:
    from digitalhub_runtime_guardrail.entities.run._base.entity import RunOpeninferenceRun
    from nuclio_sdk import Context, Event, Response

def execute_user_init(
    init_function: Callable,
    context: Context,
    run: RunOpeninferenceRun,
) -> None:
    """
    Execute user init function.

    Parameters
    ----------
    init_function : Callable
        User init function.
    context : Context
        Nuclio context.
    run : RunOpeninferenceRun
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

    # Get run
    run: RunOpeninferenceRun = get_run(
        os.getenv(RuntimeEnvVar.RUN_ID.value),
        project=project_name,
    )
    # Set running context
    context.logger.info("Starting execution.")
    run.start_execution()
    setattr(context, "run", run)


    # Get function (and eventually init) to execute and
    # set it in the context. Path refers to the working
    # user dir (will be taken from run spec in the future),
    # default_py_file filename is "main.py", source is the
    # function source
    source = {{source}}
    func, init_function = import_function_and_init_from_source(source)

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
        func_args = compose_inputs(
            {},
            {"request": InferenceRequest(event.body)},
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
        return context.user_function(**func_args)
    except Exception as e:
        raise e
    finally:
        context.run.end_execution()


class InferenceRequest:
    id: str | None = None
    parameters: dict | None = None
    inputs: list[RequestInput] = []
    outputs: list[RequestOutput] = []

    def __init__(self, request: dict) -> None:
        self.id = request.get("id")
        self.parameters = request.get("parameters")
        self.inputs = [RequestInput(**input) for input in request.get("inputs", [])]
        self.outputs = [RequestOutput(**output) for output in request.get("outputs", [])]

    def __repr__(self) -> str:
        return self.__str__()

    def __str__(self) -> str:
        return f"InferenceRequest(id={self.id}, parameters={self.parameters}, inputs={self.inputs}, outputs={self.outputs})"

    def dict(self) -> dict:
        return {
            "id": self.id,
            "parameters": self.parameters,
            "inputs": [i.dict() for i in self.inputs],
            "outputs": [o.dict() for o in self.outputs],
        }

    def json(self) -> str:
        return json.dumps(self.dict())


class RequestInput:
    name: str
    datatype: str
    shape: list[int]
    data: list[any]
    parameters: dict | None = {}

    def __init__(self, **kwargs) -> None:
        self.name = kwargs.get("name")
        self.datatype = kwargs.get("datatype")
        self.shape = kwargs.get("shape")
        self.data = kwargs.get("data")
        self.parameters = kwargs.get("parameters")

    def __repr__(self) -> str:
        return self.__str__()

    def __str__(self) -> str:
        return f"RequestInput(name={self.name}, datatype={self.datatype}, shape={self.shape}, data={self.data}, parameters={self.parameters})"

    def dict(self) -> dict:
        return {
            "name": self.name,
            "datatype": self.datatype,
            "shape": self.shape,
            "data": self.data,
            "parameters": self.parameters,
        }

    def json(self) -> str:
        return json.dumps(self.dict())


class RequestOutput:
    name: str
    datatype: str
    shape: list[int]
    parameters: dict | None = {}

    def __init__(self, **kwargs) -> None:
        self.name = kwargs.get("name")
        self.datatype = kwargs.get("datatype")
        self.shape = kwargs.get("shape")
        self.parameters = kwargs.get("parameters")

    def __repr__(self) -> str:
        return self.__str__()

    def __str__(self) -> str:
        return f"RequestOutput(name={self.name}, datatype={self.datatype}, shape={self.shape}, parameters={self.parameters})"

    def dict(self) -> dict:
        return {
            "name": self.name,
            "datatype": self.datatype,
            "shape": self.shape,
            "parameters": self.parameters,
        }

    def json(self) -> str:
        return json.dumps(self.dict())
