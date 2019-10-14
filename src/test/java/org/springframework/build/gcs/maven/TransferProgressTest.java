/*
 * Copyright 2010-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.gcs.maven;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import org.junit.Test;

public final class TransferProgressTest {

    private static final int REQUEST_TYPE = TransferEvent.REQUEST_GET;

    private final Resource resource = mock(Resource.class);

    private final TransferListenerSupport transferListenerSupport = mock(TransferListenerSupport.class);

    private final StandardTransferProgress transferProgress = new StandardTransferProgress(this.resource, REQUEST_TYPE,
            this.transferListenerSupport);

    @Test
    public void notifyProgress() {
        byte[] buffer = new byte[0];
        int length = 0;

        this.transferProgress.notify(buffer, length);

        verify(this.transferListenerSupport).fireTransferProgress(this.resource, REQUEST_TYPE, buffer, length);
    }

    @Test
    public void retryableTransferProgress() {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        TransferProgress retyableTransferProgess = new RetryableTransferProgress(
            new TransferProgress() {
                @Override
                public void notify(byte[] buffer, int length) {
                    output.write(buffer, 0, length);
                }
                @Override public void startTransferAttempt() {}
        });

        String inputString = "abcdefghijklmnopqrstuvwxyz";
        byte[] firstThird = inputString.substring(0, 9).getBytes();
        byte[] secondThird = inputString.substring(9, 18).getBytes();
        byte[] firstHalf = inputString.substring(0, 13).getBytes();
        byte[] secondHalf = inputString.substring(13).getBytes();

        retyableTransferProgess.startTransferAttempt();
        retyableTransferProgess.notify(firstThird, firstThird.length);

        retyableTransferProgess.startTransferAttempt();
        retyableTransferProgess.notify(firstThird, firstThird.length);
        retyableTransferProgess.notify(secondThird, secondThird.length);

        retyableTransferProgess.startTransferAttempt();
        retyableTransferProgess.notify(firstHalf, firstHalf.length);
        retyableTransferProgess.notify(secondHalf, secondHalf.length);

        assertEquals(inputString, output.toString());
    }
}
