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

import static org.junit.Assert.assertEquals;

import org.apache.maven.wagon.repository.Repository;
import org.junit.Test;

public final class GcsUtilsTest {

    @Test
    public void getBucketName() {
        assertEquals("dist.springsource.com", GcsUtils.getBucketName(createRepository("/")));
    }

    @Test
    public void getBaseDirectory() {
        assertEquals("", GcsUtils.getBaseDirectory(createRepository("")));
        assertEquals("", GcsUtils.getBaseDirectory(createRepository("/")));
        assertEquals("foo/", GcsUtils.getBaseDirectory(createRepository("/foo")));
        assertEquals("foo/", GcsUtils.getBaseDirectory(createRepository("/foo/")));
        assertEquals("foo/bar/", GcsUtils.getBaseDirectory(createRepository("/foo/bar")));
        assertEquals("foo/bar/", GcsUtils.getBaseDirectory(createRepository("/foo/bar/")));
    }

    private Repository createRepository(String path) {
        return new Repository("foo", String.format("gcs://dist.springsource.com%s", path));
    }
}
