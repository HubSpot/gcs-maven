package org.springframework.build.gcs.maven;

import java.util.Arrays;

public class RetryableTransferProgress implements TransferProgress {

  private final TransferProgress wrappedTransferProgress;

  private int totalBytesSeen;
  private int bytesSeenThisAttempt;

  public RetryableTransferProgress(TransferProgress transferProgress) {
    this.wrappedTransferProgress = transferProgress;

    this.totalBytesSeen = 0;
    this.bytesSeenThisAttempt = 0;
  }

  @Override
  public void notify(byte[] buffer, int length) {
    int newBytesInBuffer = bytesSeenThisAttempt + length - totalBytesSeen;
    if (newBytesInBuffer <= 0) {
      bytesSeenThisAttempt += length;
      return;
    }

    byte[] newBytes = newBytesInBuffer == length
        ? buffer
        : Arrays.copyOfRange(buffer, length - newBytesInBuffer, length);
    wrappedTransferProgress.notify(newBytes, newBytesInBuffer);

    bytesSeenThisAttempt += length;
    totalBytesSeen += newBytesInBuffer;
  }

  @Override
  public void startTransferAttempt() {
    bytesSeenThisAttempt = 0;
  }
}
