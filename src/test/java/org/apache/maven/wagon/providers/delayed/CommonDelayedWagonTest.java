package org.apache.maven.wagon.providers.delayed;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.HashSet;

import junit.framework.TestCase;

/**
 * Test static methods unaffected by underlying wagons.
 */
public class CommonDelayedWagonTest
    extends TestCase
{
    /**
     * @throws Exception nope.
     */
    public void testFindCommonPrefix()
        throws Exception
    {
        checkFindCommonPrefix( "foo/test-deploy-svn/", "foo/test-deploy-svn/",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.jar", "foo/",
                               "foo", "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.pom.md5",
                               "foo/test-deploy-svn/0.1-SNAPSHOT",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.pom",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.jar.sha1",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.pom.sha1",
                               "foo/test-deploy-svn", "foo/test-deploy-svn/0.1-SNAPSHOT/maven-metadata.xml",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/maven-metadata.xml.sha1",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/maven-metadata.xml.md5",
                               "foo/test-deploy-svn/maven-metadata.xml.md5",
                               "foo/test-deploy-svn/maven-metadata.xml.sha1", "foo/test-deploy-svn/maven-metadata.xml",
                               "foo/test-deploy-svn/0.1-SNAPSHOT/test-deploy-svn-0.1-20180301.144010-4.jar.md5" );

        checkFindCommonPrefix( null );
        checkFindCommonPrefix( "", "file.txt" );
        checkFindCommonPrefix( "", "file.txt", "dir", "dir/", "dir/file-2.txt" );
        checkFindCommonPrefix( "", "dir", "dir/", "dir/file-2.txt", "dir2", "dir2/", "dir2/file-3.txt" );
        checkFindCommonPrefix( "dir/", "dir", "dir/", "dir/file-2.txt", "dir/file-3.txt" );
        checkFindCommonPrefix( "dir/", "dir", "dir/", "dir/file-2.txt", "dir/file-3.txt", "dir/dir2", "dir/dir2/",
                               "dir/dir2/file-4.txt" );
        //
    }

    private void checkFindCommonPrefix( String expectedPrefix, String... addedResourcesArray )
        throws Exception
    {
        HashSet<String> addedResources = new HashSet<String>();
        for ( String s : addedResourcesArray )
        {
            addedResources.add( s );
        }
        String actualPrefix = DelayedWagon.findCommonDir( addedResources );
        assertEquals( expectedPrefix, actualPrefix );
    }
}
