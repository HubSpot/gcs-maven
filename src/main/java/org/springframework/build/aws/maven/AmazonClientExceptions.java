package org.springframework.build.aws.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;

public class AmazonClientExceptions {

  private AmazonClientExceptions() {
    throw new AssertionError();
  }

  public static RuntimeException propagateForRead(
      AmazonClientException amazonClientException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(amazonClientException, "Error reading '%s'", s3Key);
  }

  public static RuntimeException propagateForWrite(
      AmazonClientException amazonClientException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(amazonClientException, "Error writing '%s'", s3Key);
  }

  public static RuntimeException propagateForAccess(
      AmazonClientException amazonClientException,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    return propagate(amazonClientException, "Error accessing '%s'", s3Key);
  }

  // Note: this is not 100% well-defined given the potential error codes:
  // http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
  private static RuntimeException propagate(
      AmazonClientException amazonClientException,
      String errorMessageTemplate,
      String s3Key
  ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
    if (!(amazonClientException instanceof AmazonServiceException)) {
      throw new TransferFailedException(
          String.format(errorMessageTemplate, s3Key),
          amazonClientException);
    }
    switch(((AmazonServiceException)amazonClientException).getStatusCode()) {
    case 403:
      throw new AuthorizationException(
          String.format(errorMessageTemplate, s3Key),
          amazonClientException);
    case 404:
      throw new ResourceDoesNotExistException(
          String.format("'%s' does not exist", s3Key),
          amazonClientException);
    default:
      throw new TransferFailedException(
          String.format(errorMessageTemplate, s3Key),
          amazonClientException);
    }
  }
}
