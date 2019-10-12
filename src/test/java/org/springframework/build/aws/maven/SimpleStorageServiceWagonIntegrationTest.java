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

package org.springframework.build.aws.maven;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.repository.Repository;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobListOption;

@Ignore
public final class SimpleStorageServiceWagonIntegrationTest {

    private static final String FILE_NAME = "robots.txt";

    private static final String BUCKET_NAME = "maven.springframework.org";

    private static final String BASE_DIRECTORY = "foo/bar/";

    private final Storage storage = mock(Storage.class);

    private final Blob blob = mock(Blob.class);

    @SuppressWarnings("unchecked")
    private final Page<Blob> blobListing = (Page<Blob>) mock(Page.class);

    private final TransferProgress transferProgress = mock(TransferProgress.class);

    private final SimpleStorageServiceWagon wagon =
            new SimpleStorageServiceWagon(this.storage, BUCKET_NAME, BASE_DIRECTORY);


    @Test
    public void regionConnections() throws WagonException {
        SimpleStorageServiceWagon remoteConnectingWagon = new SimpleStorageServiceWagon();

        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setPassword(System.getProperty("gcs.credentials.path"));

        for (String bucket : getBuckets()) {
            Repository repository = new Repository("test", String.format("gcs://%s/", bucket));
            remoteConnectingWagon.connectToRepository(repository, authenticationInfo, null);
            assertNotNull(remoteConnectingWagon.getFileList(""));
            remoteConnectingWagon.disconnectFromRepository();
        }
    }

    private List<String> getBuckets() {
        List<String> buckets = new ArrayList<String>();

        String value = System.getProperty("buckets");
        if (value != null) {
            for (String bucket : value.split(",")) {
                buckets.add(bucket.trim());
            }
        }

        return buckets;
    }

    @Test
    public void doesRemoteResourceExistExists() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(this.blob);
        assertTrue(this.wagon.doesRemoteResourceExist(FILE_NAME));
    }

    @Test
    public void doesRemoteResourceExistDoesNotExist() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(null);
        assertFalse(this.wagon.doesRemoteResourceExist(FILE_NAME));
    }

    @Test
    public void isRemoteResourceNewerNewer() throws Exception {
        when(this.blob.getUpdateTime()).thenReturn(System.currentTimeMillis());
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertTrue(this.wagon.isRemoteResourceNewer(FILE_NAME, 0));
    }

    @Test
    public void isRemoteResourceNewerOlder() throws Exception {
        when(this.blob.getUpdateTime()).thenReturn(System.currentTimeMillis());
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertFalse(this.wagon.isRemoteResourceNewer(FILE_NAME, Long.MAX_VALUE));
    }

    @Test
    public void isRemoteResourceNewerNoLastModified() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertTrue(this.wagon.isRemoteResourceNewer(FILE_NAME, 0));
    }

    @Test(expected = ResourceDoesNotExistException.class)
    public void isRemoteResourceNewerDoesNotExist() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(null);

        this.wagon.isRemoteResourceNewer(FILE_NAME, 0);
    }

    @Test
    public void listDirectoryTopLevel() throws Exception {
        when(this.storage.list(
            BUCKET_NAME,
            BlobListOption.currentDirectory(),
            BlobListOption.prefix(BASE_DIRECTORY)
        )).thenReturn(this.blobListing);
        when(this.blobListing.iterateAll()).thenReturn(Collections.singletonList(blob));
        when(this.blob.getBlobId()).thenReturn(BlobId.of(BUCKET_NAME, FILE_NAME));

        List<String> directoryContents = this.wagon.listDirectory("");
        assertTrue(directoryContents.contains(FILE_NAME));
        assertFalse(directoryContents.contains("frogs.txt"));
    }

    @Test
    public void listDirectoryTopNested() throws Exception {
        when(this.storage.list(
            BUCKET_NAME,
            BlobListOption.currentDirectory(),
            BlobListOption.prefix(BASE_DIRECTORY + "release/")
        )).thenReturn(this.blobListing);
        when(this.blobListing.iterateAll()).thenReturn(Collections.singletonList(blob));
        when(this.blob.getBlobId()).thenReturn(BlobId.of(BUCKET_NAME, FILE_NAME));

        List<String> directoryContents = this.wagon.listDirectory("release/");
        assertTrue(directoryContents.contains(FILE_NAME));
        assertFalse(directoryContents.contains("frogs.txt"));
    }

    @Test
    public void getResource() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(blob);

        doAnswer(new Answer() {

          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
              try (InputStream inputStream = new FileInputStream("src/test/resources/test.txt")) {
                  OutputStream outputStream = (OutputStream) invocation.getArguments()[0];
                  IoUtils.copy(inputStream, outputStream);
              }
              return null;
          }
        }).when(this.blob).downloadTo(any(OutputStream.class));

        File target = new File("target/robots.txt");
        target.delete();
        assertFalse(target.exists());

        this.wagon.getResource(FILE_NAME, target, this.transferProgress);

        assertTrue(target.exists());
    }

    @Test(expected = ResourceDoesNotExistException.class)
    public void getResourceSourceDoesNotExist() throws Exception {
        when(this.storage.get(
            BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(null);

        File target = new File("target/robots.txt");
        this.wagon.getResource(FILE_NAME, target, this.transferProgress);
    }
}
