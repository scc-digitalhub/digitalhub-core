"""
Local store module.
"""
from __future__ import annotations

import typing

from sdk.store.objects.base import Store
from sdk.utils.file_utils import build_path, copy_file, get_dir
from sdk.utils.uri_utils import get_name_from_uri, get_uri_path

if typing.TYPE_CHECKING:
    import pandas as pd

    from sdk.store.models import LocalStoreConfig


class LocalStore(Store):
    """
    Local store class. It implements the Store interface and provides methods to fetch and persist
    artifacts on local filesystem based storage.
    """

    def __init__(self, name: str, store_type: str, config: LocalStoreConfig) -> None:
        """
        Constructor.

        Parameters
        ----------
        config : LocalStoreConfig
            Local store configuration.

        See Also
        --------
        Store.__init__
        """
        super().__init__(name, store_type)
        self.config = config

    ############################
    # IO methods
    ############################

    def download(self, src: str, dst: str | None = None) -> str:
        """
        Method to download an artifact from the backend. Please note that this method is not
        implemented since the local store is not meant to download artifacts.

        Raises
        ------
        NotImplementedError
            This method is not implemented.
        """
        raise NotImplementedError(
            "Local store does not support download. Use as_file() instead."
        )

    def fetch_artifact(self, src: str, dst: str | None = None) -> str:
        """
        Method to fetch an artifact from the backend and to register it on the paths registry.
        If destination is not provided, return the source path, otherwise the path of the copied
        file.

        Parameters
        ----------
        src : str
            The source location of the artifact.
        dst : str
            The destination of the artifact.

        Returns
        -------
        str
            Returns the path of the artifact.
        """
        if dst is None:
            return src
        self._check_local_dst(dst)
        return copy_file(src, dst)

    def upload(self, src: str, dst: str | None = None) -> str:
        """
        Method to upload an artifact to the backend. Please note that this method is not implemented
        since the local store is not meant to upload artifacts.

        Returns
        -------
        None

        Raises
        ------
        NotImplementedError
            This method is not implemented.
        """
        raise NotImplementedError("Local store does not support upload.")

    def persist_artifact(self, src: str, dst: str | None = None) -> str:
        """
        Method to persist (copy) an artifact on local filesystem.
        If destination is not provided, the artifact is written to the default
        store path with source name.

        Parameters
        ----------
        src : str
            The source location of the artifact.
        dst : str
            The destination of the artifact.
        src_name : str
            The name of the artifact.

        Returns
        -------
        dst
            Returns the URI of the artifact.
        """
        if dst is None:
            dst = build_path(get_dir(self.config.path), get_name_from_uri(src))
        self._check_local_dst(dst)
        return copy_file(src, dst)

    def write_df(self, df: pd.DataFrame, dst: str | None = None, **kwargs) -> str:
        """
        Method to write a dataframe to a file. Kwargs are passed to df.to_parquet().
        If destination is not provided, the dataframe is written to the default
        store path with name data.parquet.

        Parameters
        ----------
        df : pd.DataFrame
            The dataframe to write.
        dst : str
            The destination of the dataframe.
        **kwargs
            Keyword arguments.

        Returns
        -------
        str
            Path of written dataframe.
        """
        if dst is None or not dst.endswith(".parquet"):
            dst = build_path(get_uri_path(self.config.path), "data.parquet")
        self._check_local_dst(dst)
        df.to_parquet(dst, index=False, **kwargs)
        return dst

    ############################
    # Store interface methods
    ############################

    @staticmethod
    def is_local() -> bool:
        """
        Check if the store is local.

        Returns
        -------
        bool
            True
        """
        return True
