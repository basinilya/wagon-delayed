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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.internal.transport.wagon.PlexusWagonProvider;
import org.eclipse.aether.transport.wagon.WagonProvider;

/**
 * A wagon provider that wraps SOME wagons with a DelayedWagon
 */
@Component( role = WagonProvider.class, hint = "plexus" )
public class DelayedWagonProvider
    extends PlexusWagonProvider
{
    private final Set<String> protocolsToWrap = Collections.synchronizedSet( new HashSet<String>() );

    {
        protocolsToWrap.add( "scm" );
    }

    /**
     * Get wagon protocols to wrap
     * 
     * @return a modifiable synchronized set
     */
    public Set<String> getProtocolsToWrap()
    {
        return protocolsToWrap;
    }

    /**
     * Creates an uninitialized provider.
     */
    public DelayedWagonProvider()
    {
        int i = -1;
    }

    /**
     * Creates a provider using the specified Plexus container.
     * 
     * @param container The Plexus container instance to use, must not be {@code null}.
     */
    public DelayedWagonProvider( PlexusContainer container )
    {
        super( container );
    }

    @Override
    public Wagon lookup( String roleHint )
        throws Exception
    {
        Wagon wagon = super.lookup( roleHint );
        return wagon == null ? null : wrap( wagon, roleHint );
    }

    private Wagon wrap( Wagon wagon, String roleHint )
    {
        if ( protocolsToWrap.contains( roleHint ) )
        {
            return new DelayedWagon( wagon );
        }
        return wagon;
    }

    @Override
    public void release( final Wagon wagonArg )
    {
        DelayedWagon dWagon = null;
        Wagon wagon = wagonArg;
        if ( wagon instanceof DelayedWagon )
        {
            dWagon = (DelayedWagon) wagon;
            wagon = dWagon.getWagon();
        }
        super.release( wagon );
        if ( dWagon != null )
        {
            // TODO: we throw it here, because exception thrown by disconnect() will not fail the build
            dWagon.validateCleanRelease();
        }
    }
}
