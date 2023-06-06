"""
Dataitem operations module.
"""
from sdk.entities.project.context import get_context
from sdk.entities.dataitem.dataitem import Dataitem, DataitemMetadata, DataitemSpec
from sdk.entities.utils import file_importer
from sdk.entities.api import (
    read_api,
    delete_api,
    DTO_DTIT,
)


def new_dataitem(
    project: str,
    name: str,
    description: str = None,
    kind: str = None,
    key: str = None,
    path: str = None,
    local: bool = False,
    embed: bool = False,
) -> Dataitem:
    """
    Create an Dataitem instance with the given parameters.

    Parameters
    ----------
    project : str
        Name of the project associated with the dataitem.
    name : str
        Identifier of the dataitem.
    description : str, optional
        Description of the dataitem.
    kind : str, optional
        The type of the dataitem.
    key : str
        Representation of artfact like store://etc..
    path : str
        Path to the dataitem on local file system or remote storage.
    local : bool, optional
        Flag to determine if object has local execution.
    embed : bool, optional
        Flag to determine if object must be embedded in project.

    Returns
    -------
    Dataitem
        Instance of the Dataitem class representing the specified dataitem.
    """
    context = get_context(project)
    if context.local != local:
        raise Exception("Context local flag does not match local flag of dataitem")
    meta = DataitemMetadata(name=name, description=description)
    spec = DataitemSpec(key=key, path=path)
    obj = Dataitem(
        project=project,
        name=name,
        kind=kind,
        metadata=meta,
        spec=spec,
        local=local,
        embed=embed,
    )
    context.add_dataitem(obj)
    if local:
        obj.export()
    else:
        obj.save()
    return obj


def get_dataitem(project: str, name: str, uuid: str = None) -> Dataitem:
    """
    Retrieves dataitem details from the backend.

    Parameters
    ----------
    project : str
        Name of the project.
    name : str
        The name of the dataitem.
    uuid : str, optional
        UUID of dataitem specific version.

    Returns
    -------
    Dataitem
        An object that contains details about the specified dataitem.

    Raises
    ------
    KeyError
        If the specified dataitem does not exist.

    """
    context = get_context(project)
    api = read_api(project, DTO_DTIT, name, uuid=uuid)
    r = context.client.get_object(api)
    return Dataitem.from_dict(r)


def import_dataitem(file: str) -> Dataitem:
    """
    Import an Dataitem object from a file using the specified file path.

    Parameters
    ----------
    file : str
        The absolute or relative path to the file containing the Dataitem object.

    Returns
    -------
    Dataitem
        The Dataitem object imported from the file using the specified path.

    """
    d = file_importer(file)
    return Dataitem.from_dict(d)


def delete_dataitem(project: str, name: str, uuid: str = None) -> None:
    """
    Delete dataitem from the backend. If the uuid is not specified, delete all versions.

    Parameters
    ----------
    project : str
        Name of the project.
    name : str
        The name of the dataitem.
    uuid : str, optional
        UUID of dataitem specific version.

    Returns
    -------
    None
        This function does not return anything.
    """
    context = get_context(project)
    api = delete_api(project, DTO_DTIT, name, uuid=uuid)
    r = context.client.get_object(api)
    return r