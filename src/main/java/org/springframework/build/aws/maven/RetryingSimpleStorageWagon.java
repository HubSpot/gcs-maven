package org.springframework.build.aws.maven;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;

import com.amazonaws.services.s3.AmazonS3;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Throwables;

public class RetryingSimpleStorageWagon extends SimpleStorageServiceWagon {

  public RetryingSimpleStorageWagon() {
    super();
  }

  protected RetryingSimpleStorageWagon(AmazonS3 amazonS3, String bucketName, String baseDirectory) {
    super(amazonS3, bucketName, baseDirectory);
  }

    @Override
  protected void putResource(
      final File source,
      final String destination,
      TransferProgress transferProgress
  ) throws TransferFailedException, ResourceDoesNotExistException {
    final TransferProgress retryableTransferProgress = new RetryableTransferProgress(transferProgress);
      transferWithRetryer(new Callable<Void>() {
      @Override
      public Void call() throws TransferFailedException, ResourceDoesNotExistException {
        RetryingSimpleStorageWagon.super.putResource(source, destination, retryableTransferProgress);
        return null;
      }
    });
  }

  @Override
  protected void getResource(
      final String resourceName,
      final File destination,
      TransferProgress transferProgress
  ) throws TransferFailedException, ResourceDoesNotExistException {
    final TransferProgress retryableTransferProgress = new RetryableTransferProgress(transferProgress);
    transferWithRetryer(new Callable<Void>() {
      @Override
      public Void call() throws TransferFailedException, ResourceDoesNotExistException {
        RetryingSimpleStorageWagon.super.getResource(resourceName, destination, retryableTransferProgress);
        return null;
      }
    });
  }

  private void transferWithRetryer(
      Callable<Void> callable
  ) throws TransferFailedException, ResourceDoesNotExistException {
    Retryer<Void> retryer = RetryerBuilder.<Void>newBuilder()
        .retryIfExceptionOfType(TransferFailedException.class)
        .withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS))
        .withStopStrategy(StopStrategies.stopAfterAttempt(3))
        .build();
    try {
      retryer.call(callable);
    } catch (ExecutionException e) {
      // This should always be a ResourceDoesNotExistException or RuntimeException
      Throwables.propagateIfPossible(e.getCause(),
                                     TransferFailedException.class,
                                     ResourceDoesNotExistException.class);
      throw new RuntimeException(e.getCause());
    } catch (RetryException e) {
      // This should always be a TransferFailedException
      Throwables.propagateIfPossible(e.getCause(),
                                     TransferFailedException.class,
                                     ResourceDoesNotExistException.class);
      throw new RuntimeException(e.getCause() == null ? e : e.getCause());
    }
  }

}
