# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import os
import typing
from typing import Any, Callable
from pathlib import Path

import yaml
import sys

from digitalhub.context.api import get_context
from digitalhub.entities.project.crud import get_project
from digitalhub.entities.run.crud import get_run
from digitalhub.runtimes.enums import RuntimeEnvVar

from digitalhub_runtime_python.utils.configuration import (
    _get_function_path, import_function_and_init_from_source, has_git_scheme, has_remote_scheme, has_s3_scheme, 
    _clone_git_source, _download_remote_source, _download_s3_source
)
from digitalhub_runtime_python.utils.inputs import compose_init, compose_inputs
from digitalhub_runtime_python.utils.outputs import build_new_status, parse_outputs

from digitalhub.utils.generic_utils import (
    import_function,
    decode_base64_string
)

if typing.TYPE_CHECKING:
    from digitalhub_runtime_python.entities.run._base.entity import RunPythonRun
    from nuclio_sdk import Context, Event, Response


from digitalhub.utils.uri_utils import has_local_scheme

DEFAULT_PY_FILE = "main.py"
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
    func, init_function = import_function_and_init_from_source(source)

    # Set attributes
    setattr(context, "project", project)
    setattr(context, "run", run)
    setattr(context, "user_function", func)
    setattr(context, "root", ctx.root)

    # Execute user init function
    if init_function is not None:
        execute_user_init(init_function, context, run)

    complete_function: Callable | None = None
    complete_handler: str | None = source.get("complete_function")
    if complete_handler is not None:
        handler = source.get("handler")
        source_code = source.get("source")

        function_path = _get_function_path(handler, source_code, ctx.root)
        complete_function = import_function(function_path, complete_handler)
        setattr(context, "complete_function", complete_function)


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

        func_args = _configure_execution(spec, context.run, context)

    except Exception as e:
        context.logger.error(f"Function _configure_execution failed with error: {str(e)}")
        raise e

    ############################
    # Execute function
    ############################
    try:
        project: str = context.project.name
        context.logger.info("Executing function.")
        exec_result = context.user_function(**func_args)
        results = parse_outputs(exec_result, project, context.run.key)
        context.logger.info(f"Output results: {results}")

        if hasattr(context, "complete_function"):
            context.logger.info("Executing complete function.")
            complete_args = {"project": context.project, "run": context.run}
            context.complete_function(**complete_args)
            context.logger.info(f"Complete function executed.")

        context.logger.info("Setting run status.")
        context.run.refresh()
        new_status = {
            **build_new_status(context.project.name, results),
            **context.run.status.to_dict(),
        }
        context.run.set_status(new_status)
        context.run.save(update=True)

    except BaseException as e:
        context.logger.error(f"Function execution failed with error: {str(e)}")
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

def _get_function_path(handler: str, source_code: str | None, root_path: Path) -> Path:
    """
    Get function path from handler string.

    Parameters
    ----------
    handler : str
        Function handler in format 'module:function' or 'path.to.module:function'.
    source_code : str | None
        Source code (used to infer function name if handler is just a function name).

    Returns
    -------
    Path
        Function path.
    """
    # If handler is not specified, assume function module is in main
    try:
        function_module, _ = handler.split(":")
    except ValueError:
        if source_code is not None and has_local_scheme(source_code):
            function_module = ".".join(Path(source_code.removesuffix(".py")).parts)
        else:
            function_module = DEFAULT_PY_FILE.removesuffix(".py")

    function_path = (root_path / Path(function_module.replace(".", "/") + ".py")).resolve()
    if not function_path.exists():
        raise RuntimeError(f"Function module {function_module} not found at path {function_path}.")

    return function_path


def _configure_execution(spec: dict, run: dict, ctx: Context) -> tuple[Callable, bool]:
    args = ["main", "-m"]

    # write runtime config for dh launcher
    dh_launcher_config = {
        "defaults": ["dh"],
        "n_jobs": spec.get("workers", 1),
        "function": spec.get("function", "hydra"),
        "project_name": ctx.project.name,
        "local_execution": False,
    }

    runtime_dir = Path("/shared")

    extra_conf_dir = runtime_dir / "dh_extra_conf" / "hydra" / "launcher"
    extra_conf_dir.mkdir(parents=True, exist_ok=True)
    with open(extra_conf_dir / "dh_launcher.yaml", "w") as outfile:
        yaml.dump(dh_launcher_config, outfile, default_flow_style=False)



    if "config" in spec:
        config = spec["config"]
        # Here we handle a specific situation: main and config are provided and loaded to the runtime_dir
        # In this case we need anticipate the command line arguments overwriting the config path and config name
        if "base64" in config:
            path = runtime_dir
            sys.argv = args + [f"--config-path={path.absolute()}", "--config-name=config"]        
        # download config to runtime dir
        elif "source" in config:
            path = runtime_dir / "config"
            sys.argv = args + [f"--config-path={path.absolute()}"]
        elif "path" in config:
            path = runtime_dir / config["path"]
            sys.argv = args + [f"--config-path={path.absolute()}"]

    sys.argv += [f"--config-dir={(runtime_dir / "dh_extra_conf" ).absolute()}", "hydra/launcher=dh_launcher", f"hydra.launcher.job_ref={run.id}"]

    # treat as not wrapped, as the wrapping is done by hydra.main and we do not need to pass the extra attributes
    return {"cfg_passthrough": None}