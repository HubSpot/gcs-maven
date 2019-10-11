package org.springframework.build.aws.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;

import com.google.cloud.storage.StorageException;

public class GcsClientExceptions {

  private GcsClientExceptions() {
    throw new AssertionError();
  }

  public static RuntimeException propagateForRead(
      StorageException gcsException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(gcsException, "Error reading '%s'", s3Key);
  }

  public static RuntimeException propagateForWrite(
      StorageException gcsException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(gcsException, "Error writing '%s'", s3Key);
  }

  public static RuntimeException propagateForAccess(
      StorageException gcsException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(gcsException, "Error accessing '%s'", s3Key);
  }

  // Note: this is not 100% well-defined given the potential error codes:
  // http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
  private static RuntimeException propagate(
      StorageException gcsException,
      String errorMessageTemplate,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    switch(gcsException.getCode()) {
    case 403:
      throw new AuthorizationException(
          String.format(errorMessageTemplate, s3Key),
          gcsException);
    case 404:
      throw new ResourceDoesNotExistException(
          String.format("'%s' does not exist", s3Key),
          gcsException);
    default:
      throw new TransferFailedException(
          String.format(errorMessageTemplate, s3Key),
          gcsException);
    }
  }
}
