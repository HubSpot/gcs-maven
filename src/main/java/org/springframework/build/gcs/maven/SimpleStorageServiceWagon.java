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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;

import com.google.api.gax.paging.Page;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;

/**
 * An implementation of the Maven Wagon interface that allows you to access the Amazon S3 service. URLs that reference
 * the S3 service should be in the form of <code>s3://bucket.name</code>. As an example
 * <code>s3://static.springframework.org</code> would put files into the <code>static.springframework.org</code> bucket
 * on the S3 service.
 * <p/>
 * This implementation uses the <code>username</code> and <code>passphrase</code> portions of the server authentication
 * metadata for credentials.
 */
public class SimpleStorageServiceWagon extends AbstractWagon {

    private static final String KEY_FORMAT = "%s%s";

    private volatile Storage storage;

    private volatile String bucketName;

    private volatile String baseDirectory;

    /**
     * Creates a new instance of the wagon
     */
    public SimpleStorageServiceWagon() {
        super(true);
    }

    protected SimpleStorageServiceWagon(Storage storage, String bucketName, String baseDirectory) {
        super(true);
        this.storage = storage;
        this.bucketName = bucketName;
        this.baseDirectory = baseDirectory;
    }

    @Override
    protected void connectToRepository(Repository repository, AuthenticationInfo authenticationInfo,
                                       ProxyInfoProvider proxyInfoProvider) throws AuthenticationException {
        if (this.storage == null) {
          // TODO respect ProxyInfoProvider?
          this.storage = StorageOptions.newBuilder()
              .setCredentials(buildCredentials(authenticationInfo))
              .setRetrySettings(
                  RetrySettings.newBuilder()
                      .setMaxAttempts(3)
                      .build()
              )
              .build()
              .getService();

            this.bucketName = GcsUtils.getBucketName(repository);
            this.baseDirectory = GcsUtils.getBaseDirectory(repository);
        }
    }

    @Override
    protected void disconnectFromRepository() {
        this.storage = null;
        this.bucketName = null;
        this.baseDirectory = null;
    }

    @Override
    protected boolean doesRemoteResourceExist(String resourceName) throws AuthorizationException, TransferFailedException {
        try {
            return getBlob(resourceName) != null;
        } catch (StorageException e) {
            try {
                throw GcsClientExceptions.propagateForAccess(e, resourceName);
            } catch (ResourceDoesNotExistException unexpected) {
                // don't think this should happen
                return false;
            }
        }
    }

    @Override
    protected boolean isRemoteResourceNewer(String resourceName, long timestamp) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        try {
            Blob blob = getBlob(resourceName, BlobField.UPDATED);
            GcsUtils.ensureBlobExists(blob, getKey(resourceName));

            Long lastModified = blob.getUpdateTime();
            return lastModified == null || lastModified > timestamp;
        } catch (StorageException e) {
            throw GcsClientExceptions.propagateForAccess(e, resourceName);
        }
    }

    @Override
    protected List<String> listDirectory(String directory) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        try {
            String prefix = ensureTrailingSlash(getKey(directory));

            Page<Blob> blobs = this.storage.list(
                this.bucketName,
                BlobListOption.currentDirectory(),
                BlobListOption.prefix(prefix)
            );

            List<String> directoryContents = new ArrayList<>();
            for (Blob blob : blobs.iterateAll()) {
                directoryContents.add(blob.getName());
                if (blob.isDirectory()) {
                  for (String nestedFile : listDirectory(blob.getName())) {
                    directoryContents.add(blob.getName() + nestedFile);
                  }
                }
            }

            return directoryContents;
        } catch (StorageException e) {
            throw GcsClientExceptions.propagateForAccess(e, directory);
        }
    }

    @Override
    protected void getResource(String resourceName, File destination, TransferProgress transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        try (OutputStream out = new TransferProgressFileOutputStream(destination, transferProgress)) {
            Blob blob = getBlob(resourceName);
            GcsUtils.ensureBlobExists(blob, getKey(resourceName));

            transferProgress.startTransferAttempt();
            blob.downloadTo(out);
        } catch (StorageException e) {
            throw GcsClientExceptions.propagateForRead(e, resourceName);
        } catch (FileNotFoundException e) {
            throw new TransferFailedException(String.format("Cannot write file to '%s'", destination), e);
        } catch (IOException e) {
            throw new TransferFailedException(String.format("Cannot read from '%s' and write to '%s'", resourceName, destination), e);
        }
    }

    @Override
    protected void putResource(File source, String destination, TransferProgress transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String key = getKey(destination);

        final String contentType;
        if (key.endsWith(".xml")) {
            contentType = "application/xml";
        } else {
            contentType = "application/octet-stream";
        }

        BlobInfo blobInfo = BlobInfo.newBuilder(this.bucketName, key)
            .setContentType(contentType)
            .build();

        try (InputStream inputStream = new TransferProgressFileInputStream(source, transferProgress);
             WriteChannel outputChannel = this.storage.writer(blobInfo);
             OutputStream outputStream = Channels.newOutputStream(outputChannel)) {
            IoUtils.copy(inputStream, outputStream);
        } catch (StorageException e) {
            throw GcsClientExceptions.propagateForWrite(e, key);
        } catch (FileNotFoundException e) {
            throw new TransferFailedException("Cannot find file: " + source, e);
        } catch (IOException e) {
            throw new TransferFailedException(String.format("Cannot read from '%s' and write to '%s'", source, key), e);
        }
    }

    private Blob getBlob(String resourceName, BlobField... fields) {
        return this.storage.get(
            this.bucketName,
            getKey(resourceName),
            BlobGetOption.fields(fields)
        );
    }

    private String getKey(String resourceName) {
        return String.format(KEY_FORMAT, this.baseDirectory, resourceName);
    }

    private static String ensureTrailingSlash(String original) {
        if (original.isEmpty() || original.endsWith("/")) {
            return original;
        } else {
            return original + "/";
        }
    }

    private static GoogleCredentials buildCredentials(AuthenticationInfo authenticationInfo) throws AuthenticationException {
        String credentialsPathString = authenticationInfo.getPassword();

        final Path credentialsPath;
        if (credentialsPathString.startsWith("~/")) {
            String subPath = credentialsPathString.substring(2);
            Path homePath = Paths.get(System.getProperty("user.home"));
            credentialsPath = homePath.resolve(subPath);
        } else {
            credentialsPath = Paths.get(credentialsPathString);
        }

        try {
            return GoogleCredentials
                .fromStream(Files.newInputStream(credentialsPath, StandardOpenOption.READ))
                .createScoped(StorageScopes.CLOUD_PLATFORM);
        } catch (IOException e) {
            throw new AuthenticationException("Error loading GCS credentials", e);
        }
    }

}
