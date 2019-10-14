package org.springframework.build.gcs.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.junit.Test;

import com.google.cloud.storage.StorageException;

public class GcsClientExceptionsTest {

  @Test(expected = TransferFailedException.class)
  public void itThrowsATransferFailedExceptionOnANonServiceException() throws Exception {
    GcsClientExceptions.propagateForAccess(new StorageException(500, "Test message"), "some/key");
  }

  @Test(expected = AuthorizationException.class)
  public void itThrowsAnAuthorizationExceptionFor403() throws Exception {
    createAndPropagateException(403, "AccessDenied", "some/key");
  }

  @Test(expected = ResourceDoesNotExistException.class)
  public void itThrowsAResourceDoesNotExistExceptionOn403() throws Exception {
    createAndPropagateException(404, "NoSuchKey", "some/key");
  }

  @Test(expected = TransferFailedException.class)
  public void itThrowsATransferFailedExceptionOn500() throws Exception {
    createAndPropagateException(500, "InternalError", "some/key");
  }

  private void createAndPropagateException(
      int statusCode,
      String errorCode,
      String s3Key
  ) throws Exception {
    StorageException gcsException = new StorageException(statusCode, errorCode);

    GcsClientExceptions.propagateForAccess(gcsException, s3Key);
  }
}
