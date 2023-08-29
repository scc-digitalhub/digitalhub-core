"""
S3Store module.
"""
from __future__ import annotations

import re

import pandas as pd
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError

from sdk.store.objects.base import Store
from sdk.utils.exceptions import StoreError
from sdk.utils.file_utils import build_path


class SqlStore(Store):
    """
    SQL store class. It implements the Store interface and provides methods to fetch and persist
    artifacts on SQL based storage.
    """

    ############################
    # IO methods
    ############################

    def download(self, src: str, dst: str | None = None) -> str:
        """
        Download an artifact from SQL based storage.

        See Also
        --------
        fetch_artifact
        """
        return self._registry.get(src, self.fetch_artifact(src, dst))

    def fetch_artifact(self, src: str, dst: str | None = None) -> str:
        """
        Fetch an artifact from SQL based storage. If the destination is not provided,
        a temporary directory will be created and the artifact will be saved there.

        Parameters
        ----------
        src : str
            Table name.
        dst : str
            The destination of the artifact on local filesystem.

        Returns
        -------
        str
            Returns a file path.
        """
        dst = dst if dst is not None else self._build_temp(src)
        dst = build_path(dst, "data.parquet")
        schema = self._get_schema(src)
        table = self._get_table_name(src)
        return self._download_table(schema, table, dst)

    def upload(self, src: str, dst: str | None = None) -> str:
        """
        Method to upload an artifact to the backend. Please note that this method is not implemented
        since the SQL store is not meant to upload artifacts.

        Raises
        ------
        NotImplementedError
            This method is not implemented.
        """
        raise NotImplementedError("SQL store does not support upload.")

    def persist_artifact(self, src: str, dst: str | None = None) -> str:
        """
        Method to persist an artifact. Note that this method is not implemented
        since the SQL store is not meant to write artifacts.

        Raises
        ------
        NotImplementedError
            This method is not implemented.
        """
        raise NotImplementedError("SQL store does not support persist_artifact.")

    def write_df(self, df: pd.DataFrame, dst: str | None = None, **kwargs) -> str:
        """
        Write a dataframe to a database. Kwargs are passed to df.to_sql().

        Parameters
        ----------
        df : pd.DataFrame
            The dataframe.
        dst : str
            The destination table on database.
        **kwargs
            Keyword arguments.

        Returns
        -------
        str
            The SQL uri where the dataframe was saved.
        """
        if dst is None:
            schema = self._get_store_schema()
            table = "table"
        else:
            schema = self._get_schema(dst)
            table = self._get_table_name(dst)
        return self._upload_table(df, schema, table, **kwargs)

    ############################
    # Private helper methods
    ############################

    def _get_store_schema(self) -> str:
        """
        Return Store URI Schema.

        Returns
        -------
        str
            The name of the Store URI schema.
        """
        return self.uri.split("/")[-1]

    def _get_engine(self) -> Engine:
        """
        Create engine from connection string.

        Returns
        -------
        Engine
            An SQLAlchemy engine.
        """
        connection_string = self.config.get("connection_string")
        if not isinstance(connection_string, str):
            raise StoreError("Connection string must be a string.")
        try:
            return create_engine(connection_string, future=True)
        except Exception as ex:
            raise StoreError(
                f"Something wrong with connection string. Arguments: {str(ex.args)}"
            )

    def _check_factory(self) -> Engine:
        """
        Check if the database is accessible and return the engine.

        Returns
        -------
        Engine
            The database engine.
        """
        engine = self._get_engine()
        self._check_access_to_storage(engine)
        return engine

    @staticmethod
    def _parse_path(path: str) -> dict:
        """
        Parse the path and return the components.

        Parameters
        ----------
        path : str
            The path.

        Returns
        -------
        dict
            A dictionary containing the components of the path.
        """
        pattern = (
            r"^sql:\/\/(postgres\/)?(?P<database>.+)?\/(?P<schema>.+)\/(?P<table>.+)$"
        )
        match = re.match(pattern, path)
        if match is None:
            raise ValueError(
                "Invalid SQL path. Path must be in the form sql://postgres/<database>/<schema>/<table>"
            )
        return match.groupdict()

    def _get_schema(self, uri: str) -> str:
        """
        Get the name of the SQL schema from the URI.

        Parameters
        ----------
        uri : str
            The URI.

        Returns
        -------
        str
            The name of the SQL schema.
        """
        return str(self._parse_path(uri).get("schema"))

    def _get_table_name(self, uri: str) -> str:
        """
        Get the name of the table from the URI.

        Parameters
        ----------
        uri : str
            The URI.

        Returns
        -------
        str
            The name of the table
        """
        return str(self._parse_path(uri).get("table"))

    @staticmethod
    def _check_access_to_storage(engine: Engine) -> None:
        """
        Check if there is access to the storage.

        Parameters
        ----------
        engine : Engine
            An SQLAlchemy engine.

        Returns
        -------
        None

        Raises
        ------
        StoreError
            If there is no access to the storage.
        """
        try:
            engine.connect()
        except SQLAlchemyError:
            engine.dispose()
            raise StoreError("No access to db!")

    def _download_table(self, schema: str, table: str, dst: str) -> str:
        """
        Download a table from SQL based storage.

        Parameters
        ----------
        schema : str
            The origin schema.
        table : str
            The origin table.
        dst : str
            The destination path.

        Returns
        -------
        str
            The destination path.
        """
        engine = self._check_factory()
        self._check_local_dst(dst)
        pd.read_sql_table(table, engine, schema=schema).to_parquet(dst, index=False)
        engine.dispose()
        return dst

    def _upload_table(self, df: pd.DataFrame, schema: str, table: str, **kwargs) -> str:
        """
        Upload a table to SQL based storage.

        Parameters
        ----------
        df : pd.DataFrame
            The dataframe.
        schema : str
            Destination schema.
        table : str
            Destination table.
        **kwargs
            Keyword arguments.

        Returns
        -------
        str
            The SQL URI where the dataframe was saved.
        """
        engine = self._check_factory()
        df.to_sql(table, engine, schema=schema, index=False, **kwargs)
        engine.dispose()
        return f"sql://postgres/{engine.url.database}/{schema}/{table}"

    ############################
    # Store interface methods
    ############################

    def _validate_uri(self) -> None:
        """
        Validate the URI of the store.

        Returns
        -------
        None

        Raises
        ------
        StoreError
            If no bucket is specified in the URI.
        """
        super()._validate_uri()
        pattern = r"^sql:\/\/(postgres\/)?(?P<database>.+)?\/(?P<schema>.+)$"
        if re.match(pattern, self.uri) is None:
            raise StoreError(
                "Invalid Store URI. SQL Store URI must be in the form sql://postgres/<database>/<schema>/"
            )

    @staticmethod
    def is_local() -> bool:
        """
        Check if the store is local.

        Returns
        -------
        bool
            False
        """
        return False
