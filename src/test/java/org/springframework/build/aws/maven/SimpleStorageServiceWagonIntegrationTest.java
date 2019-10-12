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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.build.aws.maven.matchers.Matchers.eq;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.repository.Repository;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.StorageException;

@Ignore
public final class SimpleStorageServiceWagonIntegrationTest {

    private static final String FILE_NAME = "robots.txt";

    private static final String BUCKET_NAME = "maven.springframework.org";

    private static final String BASE_DIRECTORY = "foo/bar/";

    private final Storage storage = mock(Storage.class);

    private final Blob blob = mock(Blob.class);

    private final ObjectListing objectListing = mock(ObjectListing.class);

    private final S3ObjectSummary s3ObjectSummary = mock(S3ObjectSummary.class);

    private final S3Object s3Object = mock(S3Object.class);

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
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(this.blob);
        assertTrue(this.wagon.doesRemoteResourceExist(FILE_NAME));
    }

    @Test
    public void doesRemoteResourceExistDoesNotExist() throws Exception {
        when(this.storage.get(
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields()
        )).thenReturn(null);
        assertFalse(this.wagon.doesRemoteResourceExist(FILE_NAME));
    }

    @Test
    public void isRemoteResourceNewerNewer() throws Exception {
        when(this.blob.getUpdateTime()).thenReturn(System.currentTimeMillis());
        when(this.storage.get(
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertTrue(this.wagon.isRemoteResourceNewer(FILE_NAME, 0));
    }

    @Test
    public void isRemoteResourceNewerOlder() throws Exception {
        when(this.blob.getUpdateTime()).thenReturn(System.currentTimeMillis());
        when(this.storage.get(
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertFalse(this.wagon.isRemoteResourceNewer(FILE_NAME, Long.MAX_VALUE));
    }

    @Test
    public void isRemoteResourceNewerNoLastModified() throws Exception {
        when(this.storage.get(
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(this.blob);

        assertTrue(this.wagon.isRemoteResourceNewer(FILE_NAME, 0));
    }

    @Test(expected = ResourceDoesNotExistException.class)
    public void isRemoteResourceNewerDoesNotExist() throws Exception {
        when(this.storage.get(
            SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
            BASE_DIRECTORY + FILE_NAME,
            BlobGetOption.fields(BlobField.UPDATED)
        )).thenReturn(null);

        this.wagon.isRemoteResourceNewer(FILE_NAME, 0);
    }

    @Test
    public void listDirectoryTopLevel() throws Exception {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                .withBucketName(BUCKET_NAME) //
                .withPrefix(BASE_DIRECTORY) //
                .withDelimiter("/");

        when(this.storage.listObjects(eq(listObjectsRequest))).thenReturn(this.objectListing);
        when(this.objectListing.isTruncated()).thenReturn(true, false);
        when(this.objectListing.getCommonPrefixes()).thenReturn(Arrays.asList("foo/"));
        when(this.objectListing.getObjectSummaries()).thenReturn(Arrays.asList(this.s3ObjectSummary));
        when(this.s3ObjectSummary.getKey()).thenReturn(BASE_DIRECTORY + FILE_NAME);

        List<String> directoryContents = this.wagon.listDirectory("");
        assertTrue(directoryContents.contains(FILE_NAME));
        assertFalse(directoryContents.contains("frogs.txt"));
    }

    @Test
    public void listDirectoryTopNested() throws Exception {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                .withBucketName(BUCKET_NAME) //
                .withPrefix(BASE_DIRECTORY + "release/") //
                .withDelimiter("/");

        when(this.storage.listObjects(eq(listObjectsRequest))).thenReturn(this.objectListing);
        when(this.objectListing.isTruncated()).thenReturn(true, false);
        when(this.objectListing.getCommonPrefixes()).thenReturn(Arrays.asList("foo/"));
        when(this.objectListing.getObjectSummaries()).thenReturn(Arrays.asList(this.s3ObjectSummary));
        when(this.s3ObjectSummary.getKey()).thenReturn(BASE_DIRECTORY + "release/robots.txt");

        List<String> directoryContents = this.wagon.listDirectory("release/");
        assertTrue(directoryContents.contains(FILE_NAME));
        assertFalse(directoryContents.contains("frogs.txt"));
    }

    @Test(expected = ResourceDoesNotExistException.class)
    public void listDirectoryDoesNotExist() throws Exception {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                .withBucketName(BUCKET_NAME) //
                .withPrefix(BASE_DIRECTORY + "frogs") //
                .withDelimiter("/");

        when(this.storage.listObjects(eq(listObjectsRequest))).thenThrow(new AmazonServiceException(""));
        this.wagon.listDirectory("frogs");
    }

    @Test
    public void getResource() throws Exception {
        when(this.storage.getObject(SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
                BASE_DIRECTORY + FILE_NAME)).thenReturn(this.s3Object);
        when(this.s3Object.getObjectContent())
                .thenReturn(new S3ObjectInputStream(new FileInputStream("src/test/resources/test.txt"), null));

        File target = new File("target/robots.txt");
        target.delete();
        assertFalse(target.exists());

        this.wagon.getResource(FILE_NAME, target, this.transferProgress);

        assertTrue(target.exists());
    }

    @Test(expected = ResourceDoesNotExistException.class)
    public void getResourceSourceDoesNotExist() throws Exception {
        when(this.storage.getObject(SimpleStorageServiceWagonIntegrationTest.BUCKET_NAME,
                BASE_DIRECTORY + FILE_NAME)).thenThrow(new AmazonServiceException(""));
        File target = new File("target/robots.txt");
        this.wagon.getResource(FILE_NAME, target, this.transferProgress);
    }

    @Test
    public void putResource() throws Exception {
        File file = new File("src/test/resources/test.txt");
        this.wagon.putResource(file, FILE_NAME, this.transferProgress);

        ArgumentCaptor<PutObjectRequest> putObjectRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(this.storage, times(3)).putObject(putObjectRequest.capture());

        List<PutObjectRequest> putObjectRequests = putObjectRequest.getAllValues();
        for (int i = 0; i < 2; i++) {
            assertEquals(BUCKET_NAME, putObjectRequests.get(i).getBucketName());
            assertNotNull(putObjectRequests.get(i).getInputStream());
            assertEquals(0, putObjectRequests.get(i).getMetadata().getContentLength());
            assertEquals(CannedAccessControlList.PublicRead, putObjectRequests.get(i).getCannedAcl());
        }

        assertEquals("foo/", putObjectRequests.get(0).getKey());
        assertEquals("foo/bar/", putObjectRequests.get(1).getKey());

        PutObjectRequest fileRequest = putObjectRequests.get(2);
        assertEquals(BUCKET_NAME, fileRequest.getBucketName());
        assertEquals(BASE_DIRECTORY + FILE_NAME, fileRequest.getKey());
        assertNotNull(fileRequest.getInputStream());

        ObjectMetadata objectMetadata = fileRequest.getMetadata();
        assertNotNull(objectMetadata);
        assertEquals(file.length(), objectMetadata.getContentLength());
        assertEquals("text/plain", objectMetadata.getContentType());
    }

    @Test(expected = TransferFailedException.class)
    public void putResourceMkdirException() throws Exception {
        when(this.storage.putObject(any(PutObjectRequest.class))).thenThrow(new AmazonServiceException(""));
        File file = new File("src/test/resources/test.txt");
        this.wagon.putResource(file, FILE_NAME, this.transferProgress);
    }

    @Test(expected = TransferFailedException.class)
    public void putResourcePutException() throws Exception {
        when(this.storage.putObject(any(PutObjectRequest.class))).thenReturn(null, (PutObjectResult) null)
                .thenThrow(new AmazonServiceException(""));
        File file = new File("src/test/resources/test.txt");
        this.wagon.putResource(file, FILE_NAME, this.transferProgress);
    }
}
