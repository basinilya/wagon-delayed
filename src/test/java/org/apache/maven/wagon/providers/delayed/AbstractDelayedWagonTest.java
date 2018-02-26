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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.wagon.FileTestUtils;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonTestCase;

/**
 * nope.
 */
public abstract class AbstractDelayedWagonTest
    extends WagonTestCase
{
    private DelayedWagonProvider provider;

    @Override
    protected void setupContainer()
    {
        super.setupContainer();
        provider = new DelayedWagonProvider( getContainer() );
        provider.getProtocolsToWrap().add( getProtocol() );
    }

    @SuppressWarnings( "unchecked" )
    @Override
    protected <T> T lookup( Class<T> componentClass, String roleHint )
        throws Exception
    {
        if ( Wagon.class.equals( componentClass ) )
        {
            getContainer();
            Wagon res = provider.lookup( roleHint );
            initWagon( res );
            return (T) res;
        }
        return super.lookup( componentClass, roleHint );
    }

    private final AtomicInteger cacheDirId = new AtomicInteger();

    @Override
    protected Object lookup( String role, String roleHint )
        throws Exception
    {
        if ( Wagon.ROLE.equals( role ) )
        {
            getContainer();
            Object res = provider.lookup( roleHint );
            initWagon( res );
            return res;
        }
        return super.lookup( role, roleHint );
    }

    private void initWagon( Object obj )
        throws IOException
    {
        if ( obj instanceof DelayedWagon )
        {
            DelayedWagon dWagon = (DelayedWagon) obj;
            File cacheDir =
                FileTestUtils.createDir( getName() + ".wagon-delayed-cache-" + cacheDirId.getAndIncrement() );
            dWagon.setCacheDir( cacheDir );
        }
    }
}
