package it.smartcommunitylabdhub.s3.credentials;

import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;

public interface S3CredentialsProvider<T extends S3Credentials> extends CredentialsProvider<T> {}
