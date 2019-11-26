package org.springframework.build.gcs.maven;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.cloud.storage.Storage;
import com.google.common.base.Throwables;

public class RetryingSimpleStorageWagon extends SimpleStorageServiceWagon {
  private static final Logger LOG = LoggerFactory.getLogger(RetryingSimpleStorageWagon.class);
  private static final int TRANSFER_ATTEMPTS = 10;
  private static final int TRANSFER_RETRY_WAIT_SECONDS = 2;

  public RetryingSimpleStorageWagon() {
    super();
  }

  protected RetryingSimpleStorageWagon(Storage storage, String bucketName, String baseDirectory) {
    super(storage, bucketName, baseDirectory);
  }

  @Override
  protected void putResource(
      final File source,
      final String destination,
      TransferProgress transferProgress
  ) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
      final TransferProgress retryableTransferProgress = new RetryableTransferProgress(transferProgress);
      transferWithRetryer(new Callable<Void>() {

        @Override
        public Void call() throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
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
  ) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
      final TransferProgress retryableTransferProgress = new RetryableTransferProgress(transferProgress);
      transferWithRetryer(new Callable<Void>() {

          @Override
          public Void call() throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
              RetryingSimpleStorageWagon.super.getResource(resourceName, destination, retryableTransferProgress);
              return null;
          }
      });
  }

  private void transferWithRetryer(
      Callable<Void> callable
  ) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    Retryer<Void> retryer = RetryerBuilder.<Void>newBuilder()
        .retryIfExceptionOfType(TransferFailedException.class)
        .withWaitStrategy(WaitStrategies.fixedWait(TRANSFER_RETRY_WAIT_SECONDS, TimeUnit.SECONDS))
        .withStopStrategy(StopStrategies.stopAfterAttempt(TRANSFER_ATTEMPTS))
        .withRetryListener(new TransferFailureLogger())
        .withRetryListener(new TransferExceptionLogger())
        .build();
    try {
      retryer.call(callable);
    } catch (ExecutionException e) {
      // This should always be a ResourceDoesNotExistException, AuthorizationException, or RuntimeException
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

  private static class TransferFailureLogger implements RetryListener {
    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      boolean transferFailed = attempt.hasException()
          && attempt.getExceptionCause() instanceof TransferFailedException;
      if (!transferFailed) {
        return;
      } else if (attempt.getAttemptNumber() < TRANSFER_ATTEMPTS) {
        LOG.warn("Transfer attempt {}/{} failed. Retrying in {} seconds",
                 attempt.getAttemptNumber(),
                 TRANSFER_ATTEMPTS,
                 TRANSFER_RETRY_WAIT_SECONDS);
      } else {
        LOG.warn("Transfer attempt {}/{} failed. Will not retry",
                 attempt.getAttemptNumber(),
                 TRANSFER_ATTEMPTS);
      }
    }
  }

  private static class TransferExceptionLogger implements RetryListener {
    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      if (!attempt.hasException()) {
        return;
      }
      LOG.debug("Transfer attempt {}/{} failed with exception:",
                attempt.getAttemptNumber(),
                TRANSFER_ATTEMPTS,
                attempt.getExceptionCause());
    }
  }
}
