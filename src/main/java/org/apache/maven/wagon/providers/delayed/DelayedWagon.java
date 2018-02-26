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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.SessionListener;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;
import org.codehaus.plexus.util.FileUtils;

/**
 * A wagon that delays put operations until disconnect()
 */
public class DelayedWagon
    extends AbstractWagon
{
    /**
     * Wrap.
     * 
     * @param wagon existing wagon
     */
    public DelayedWagon( Wagon wagon )
    {
        this.wagon = wagon;
        wagon.addTransferListener( transLsn );
    }

    private class TransLsn
        implements TransferListener
    {

        private TransferEvent lastTransferStarted;

        @Override
        public void transferInitiated( TransferEvent transferEvent )
        {
            // not needed
        }

        @Override
        public void transferStarted( TransferEvent transferEvent )
        {
            lastTransferStarted = transferEvent;
            if ( transferEvent.getRequestType() == TransferEvent.REQUEST_GET )
            {
                fireGetStarted( transferEvent.getResource(), transferEvent.getLocalFile() );
            }
        }

        @Override
        public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
        {
            // not needed
        }

        @Override
        public void transferCompleted( TransferEvent transferEvent )
        {
            // not needed
        }

        @Override
        public void transferError( TransferEvent transferEvent )
        {
            // not needed
        }

        @Override
        public void debug( String message )
        {
            // not needed
        }
    }

    private final TransLsn transLsn = new TransLsn();

    private final Wagon wagon;

    private boolean explicitCacheDir;

    private File cacheDir;

    private Exception commitException;

    private boolean connected;

    private HashSet<String> addedResources = new HashSet<String>();

    private HashMap<String, String> missingResources = new HashMap<String, String>();

    private HashSet<String> listedDirs = new HashSet<String>();

    private HashMap<String, Long> guessStampsHi = new HashMap<String, Long>();

    private HashMap<String, Long> guessStampsLo = new HashMap<String, Long>();

    /**
     * Underlying wagon.
     * 
     * @return Underlying wagon.
     */
    public Wagon getWagon()
    {
        return wagon;
    }

    @Override
    public void get( String resourceName, File destination )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        getIfNewer( resourceName, destination, 0 );
    }

    @Override
    public boolean getIfNewer( String resourceNameArg, File destination, long timestamp )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        String resourceName = canonRes( resourceNameArg );
        Resource resource = new Resource( resourceName );

        fireGetInitiated( resource, destination );

        try
        {
            String message = missingResources.get( resourceName );
            if ( message != null )
            {
                throw new ResourceDoesNotExistException( message );
            }

            Long guessStampHi = guessStampsHi.get( resourceName );
            if ( guessStampHi != null && timestamp >= guessStampHi )
            {
                return false;
            }

            File cachedFile = new File( cacheDir, resourceName );

            transLsn.lastTransferStarted = null;

            if ( guessStampHi == null )
            {
                cachedFile.getParentFile().mkdirs();
                if ( timestamp == 0 )
                {
                    wagon.get( resourceName, cachedFile );
                    guessStampsHi.put( resourceName, Long.MAX_VALUE );
                }
                else
                {
                    if ( !getIfNewer0( resourceName, cachedFile, timestamp ) )
                    {
                        return false;
                    }
                }
            }
            else if ( !updateCachedFile( resourceName, cachedFile, timestamp ) )
            {
                return false;
            }

            if ( transLsn.lastTransferStarted != null )
            {
                Resource res2 = transLsn.lastTransferStarted.getResource();
                if ( res2 != null )
                {
                    resource = res2;
                    long lastMod = res2.getLastModified();
                    if ( lastMod != 0 )
                    {
                        guessStampsHi.put( resourceName, lastMod );
                        guessStampsLo.put( resourceName, lastMod );
                    }
                }
            }
            else
            {
                fireGetStarted( resource, destination );
            }

            try
            {
                FileUtils.copyFile( cachedFile, destination );
            }
            catch ( IOException e )
            {
                throw new TransferFailedException( "Failure transferring " + resourceName, e );
            }
        }
        catch ( Exception e )
        {
            if ( e instanceof ResourceDoesNotExistException )
            {
                missingResources.put( resourceName, "cached: " + e.getMessage() );
            }
            fireTransferError( resource, e, TransferEvent.REQUEST_GET );
            throw e;
        }

        postProcessListeners( resource, destination, TransferEvent.REQUEST_GET );
        fireGetCompleted( resource, destination );
        return true;
    }

    private boolean updateCachedFile( String resourceName, File cachedFile, long timestamp )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        return timestamp == 0 || isCachedFileNewer( resourceName, timestamp )
            || getIfNewer0( resourceName, cachedFile, timestamp );
    }

    private boolean isCachedFileNewer( String resourceName, long timestamp )
    {
        Long guessStampLo = guessStampsLo.get( resourceName );
        return guessStampLo != null && timestamp <= guessStampLo;
    }

    private boolean getIfNewer0( String resourceName, File cachedFile, long timestamp )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        boolean received = wagon.getIfNewer( resourceName, cachedFile, timestamp );
        if ( received )
        {
            // resource is newer than timestamp
            guessStampsLo.put( resourceName, timestamp );
        }
        else
        {
            // resource not newer than timestamp
            guessStampsHi.put( resourceName, timestamp );
        }
        return received;
    }

    @Override
    public void put( File source, String destinationArg )
        throws TransferFailedException, ResourceDoesNotExistException
    {
        String destination = canonRes( destinationArg );
        Resource resource = new Resource( destination );

        firePutInitiated( resource, source );

        resource.setContentLength( source.length() );
        resource.setLastModified( source.lastModified() );

        try
        {
            if ( guessStampsHi.containsKey( destination + "/" ) )
            {
                throw new TransferFailedException( destination + "is a directory" );
            }

            firePutStarted( resource, source );

            File cachedFile = new File( cacheDir, destination );
            FileUtils.copyFile( source, cachedFile );

            Long timestamp = System.currentTimeMillis();
            guessStampsHi.put( destination, timestamp );
            guessStampsLo.put( destination, timestamp );

            for ( ;; )
            {
                addedResources.add( destination );
                missingResources.remove( destination );
                int i = destination.lastIndexOf( '/' );
                if ( i == -1 )
                {
                    break;
                }
                destination = destination.substring( 0, i + 1 );
                addedResources.add( destination );
                missingResources.remove( destination );
                destination = destination.substring( 0, i );
            }
        }
        catch ( Exception e )
        {
            fireTransferError( resource, e, TransferEvent.REQUEST_PUT );
            throw new TransferFailedException( "Failure transferring " + source, e );
        }

        postProcessListeners( resource, source, TransferEvent.REQUEST_PUT );

        firePutCompleted( resource, source );
    }

    @Override
    public void putDirectory( File sourceDirectory, String destinationDirectoryArg )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        String destinationDirectory = canonRes( destinationDirectoryArg );
        String dstPref = destinationDirectory.length() == 0 ? destinationDirectory : destinationDirectory + "/";
        putDirectory0( sourceDirectory, dstPref );
    }

    private void putDirectory0( File sourceDirectory, String dstPref )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        File[] files = sourceDirectory.listFiles();

        for ( File file : files )
        {
            String destination = dstPref + file.getName();
            if ( file.isFile() )
            {
                put( file, destination );
            }
            else if ( file.isDirectory() )
            {
                putDirectory0( file, destination + "/" );
            }
            else
            {
                throw new TransferFailedException( "Unknown file type: " + file.getAbsolutePath() );
            }
        }
    }

    @Override
    public boolean resourceExists( String resourceName )
        throws TransferFailedException, AuthorizationException
    {
        String canonResource = canonRes( resourceName );
        if ( guessStampsHi.containsKey( canonResource ) )
        {
            return true;
        }

        String pref = canonResource + "/";

        if ( missingResources.containsKey( canonResource ) )
        {
            return false;
        }

        if ( resourceName.endsWith( "/" ) && missingResources.containsKey( pref ) )
        {
            return false;
        }

        // check if parent directory of some resource
        for ( String s : guessStampsHi.keySet() )
        {
            if ( s.startsWith( pref ) )
            {
                return true;
            }
        }

        boolean res = wagon.resourceExists( resourceName );
        if ( res )
        {
            guessStampsHi.put( canonResource, Long.MAX_VALUE );
            if ( resourceName.endsWith( "/" ) )
            {
                guessStampsHi.put( canonResource + "/", Long.MAX_VALUE );
            }
        }
        else
        {
            if ( resourceName.endsWith( "/" ) )
            {
                canonResource = pref;
            }
            missingResources.put( canonResource, "cached: missing resource: " + canonResource );
        }
        return res;
    }

    @Override
    public List<String> getFileList( String destinationDirectory )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        String canonResource = canonRes( destinationDirectory );
        String dirResource = canonResource + "/";
        String prefix = canonResource.length() == 0 ? canonResource : dirResource;

        if ( listedDirs.contains( dirResource ) )
        {
            HashSet<String> fileSet = new HashSet<String>();
            for ( String s : guessStampsHi.keySet() )
            {
                if ( s.startsWith( prefix ) && !s.endsWith( "/" ) )
                {
                    int i = s.indexOf( '/', prefix.length() );
                    String sFile = s.substring( prefix.length(), i == -1 ? s.length() : i );
                    fileSet.add( sFile );
                }
            }
            return new ArrayList<String>( fileSet );
        }
        // guessStampsHi.containsKey( dirResource )
        List<String> res = wagon.getFileList( destinationDirectory );
        guessStampsHi.put( canonResource, Long.MAX_VALUE );
        guessStampsHi.put( dirResource, Long.MAX_VALUE );
        listedDirs.add( dirResource );
        for ( String s : res )
        {
            canonResource = prefix + canonRes( s );
            if ( !guessStampsHi.containsKey( canonResource ) )
            {
                guessStampsHi.put( canonResource, Long.MAX_VALUE );
            }
        }
        return res;
    }

    private static String canonRes( String s )
    {
        // normalize only works reliably with /absolute/dirs/
        String res = FileUtils.normalize( "/" + s + "/" );
        res = res.length() > 1 ? res.substring( 1, res.length() - 1 ) : "";
        return res;
    }

    @Override
    public boolean supportsDirectoryCopy()
    {
        return true;
    }

    @Override
    public Repository getRepository()
    {
        return wagon.getRepository();
    }

    @Override
    public void connect( Repository source )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void connect( Repository source, ProxyInfo proxyInfo )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source, proxyInfo );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void connect( Repository source, ProxyInfoProvider proxyInfoProvider )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source, proxyInfoProvider );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void connect( Repository source, AuthenticationInfo authenticationInfo )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source, authenticationInfo );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void connect( Repository source, AuthenticationInfo authenticationInfo, ProxyInfo proxyInfo )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source, authenticationInfo, proxyInfo );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void connect( Repository source, AuthenticationInfo authenticationInfo, ProxyInfoProvider proxyInfoProvider )
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.connect( source, authenticationInfo, proxyInfoProvider );
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @SuppressWarnings( "deprecation" )
    @Override
    public void openConnection()
        throws ConnectionException, AuthenticationException
    {
        createCacheDir();
        try
        {
            wagon.openConnection();
            connected = true;
        }
        finally
        {
            deleteCacheIfDisconnected();
        }
    }

    @Override
    public void disconnect()
        throws ConnectionException
    {
        if ( !connected )
        {
            return;
        }

        commitException = new Exception( "unknown" );
        String commonPrefix = "";
        try
        {
            retainAdded( cacheDir, "" );

            if ( !wagon.supportsDirectoryCopy() )
            {
                putAdded( cacheDir, "" );
            }
            else if ( addedResources.size() != 0 )
            {
                commonPrefix = canonRes( findCommonDir( addedResources ) );
                File commonPrefixFile = new File( cacheDir, commonPrefix );
                wagon.putDirectory( commonPrefixFile, commonPrefix );
            }

            // we don't clear these in finally, because commit errors are irrecoverable
            // a failed-to-commit wagon cannot be used again
            missingResources.clear();
            listedDirs.clear();
            guessStampsHi.clear();
            guessStampsLo.clear();
            addedResources.clear();

            connected = false;
        }
        catch ( Exception e )
        {
            commitException = e;
            // TODO: exception thrown by disconnect() will not fail the build
            throw new ConnectionException( "Commit failed", e );
        }
        finally
        {
            if ( connected )
            {
                fireTransferError( new Resource( commonPrefix ), commitException, TransferEvent.REQUEST_PUT );
            }

            deleteCache();
            wagon.disconnect();
        }
    }

    private void deleteCacheIfDisconnected()
    {
        if ( !connected )
        {
            deleteCache();
        }
    }

    private void deleteCache()
    {
        try
        {
            File save = cacheDir;
            if ( !explicitCacheDir )
            {
                cacheDir = null;
                FileUtils.deleteDirectory( save );
            }
        }
        catch ( Exception e )
        {
            //
        }
    }

    static String findCommonDir( Set<String> addedResourcesArg )
    {
        HashSet<String> addedResources = new HashSet<String>( addedResourcesArg );
        for ( String s : addedResourcesArg )
        {
            int i = 0;
            String parent = s;
            boolean removedLast = false;
            for ( ;; )
            {
                i = s.indexOf( '/', i );
                if ( i == -1 )
                {
                    if ( removedLast )
                    {
                        addedResources.add( parent );
                    }
                    break;
                }
                parent = s.substring( 0, i );
                addedResources.remove( parent );
                i++;
                parent = s.substring( 0, i );
                removedLast = addedResources.remove( parent );
            }
        }

        String commonPrefix = null;
        for ( String s : addedResources )
        {
            String maybeDir = s + "/";
            if ( !addedResources.contains( maybeDir ) )
            {
                int i = s.lastIndexOf( '/' );
                if ( i == -1 )
                {
                    // it's a root file
                    return "";
                }
                else
                {
                    maybeDir = s.substring( 0, i + 1 );
                }
            }
            commonPrefix = extractCommonDir( commonPrefix, maybeDir );
            if ( commonPrefix.length() == 0 )
            {
                break;
            }
        }
        return commonPrefix;
    }

    private static String extractCommonDir( String a, String b )
    {
        if ( a == null )
        {
            return b;
        }
        int minLength = Math.min( a.length(), b.length() );
        int iLastSlash = 0;
        for ( int i = 0; i < minLength; i++ )
        {
            if ( a.charAt( i ) != b.charAt( i ) )
            {
                return a.substring( 0, iLastSlash );
            }
            if ( a.charAt( i ) == '/' )
            {
                iLastSlash = i;
            }
        }
        return a.substring( 0, minLength );
    }

    private void putAdded( File dir, String dstPref )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        for ( File file : dir.listFiles() )
        {
            String resName = dstPref + file.getName();
            if ( file.isDirectory() )
            {
                putAdded( file, resName + "/" );
            }
            else
            {
                wagon.put( file, resName );
            }
        }
    }

    private void retainAdded( File dir, String dstPref )
        throws TransferFailedException
    {
        for ( File file : dir.listFiles() )
        {
            String resName = dstPref + file.getName();
            if ( file.isDirectory() )
            {
                retainAdded( file, resName + "/" );
            }

            if ( !addedResources.contains( resName ) && !file.delete() )
            {
                throw new TransferFailedException( "failed to delete: " + file );
            }
        }
    }

    private void createCacheDir()
        throws ConnectionException
    {
        if ( connected )
        {
            throw new IllegalStateException( "already connected" );
        }

        Throwable cause = null;
        if ( cacheDir == null )
        {
            DecimalFormat fmt = new DecimalFormat( "#####" );

            Random rand = new Random( System.currentTimeMillis() + Runtime.getRuntime().freeMemory() );
            String tmpdir = System.getProperty( "java.io.tmpdir" );
            do
            {
                cacheDir = new File( tmpdir, "wagon-delayed" + fmt.format( Math.abs( rand.nextInt() ) ) );
                if ( cacheDir.mkdir() )
                {
                    return;
                }
            }
            while ( cacheDir.exists() );
        }
        else if ( cacheDir.mkdir() )
        {
            return;
        }
        else
        {
            try
            {
                FileUtils.cleanDirectory( cacheDir );
                return;
            }
            catch ( IOException e )
            {
                cause = e;
            }
        }

        throw new ConnectionException( "unable to mkdir: " + cacheDir, cause );
    }

    @Override
    public void setTimeout( int timeoutValue )
    {
        wagon.setTimeout( timeoutValue );
    }

    @Override
    public int getTimeout()
    {
        return wagon.getTimeout();
    }

    @Override
    public void setReadTimeout( int timeoutValue )
    {
        wagon.setReadTimeout( timeoutValue );
    }

    @Override
    public int getReadTimeout()
    {
        return wagon.getReadTimeout();
    }

    @Override
    public void addSessionListener( SessionListener listener )
    {
        wagon.addSessionListener( listener );
    }

    @Override
    public void removeSessionListener( SessionListener listener )
    {
        wagon.removeSessionListener( listener );
    }

    @Override
    public boolean hasSessionListener( SessionListener listener )
    {
        return wagon.hasSessionListener( listener );
    }

    @Override
    public void addTransferListener( TransferListener listener )
    {
        super.addTransferListener( listener );
    }

    @Override
    public void removeTransferListener( TransferListener listener )
    {
        super.removeTransferListener( listener );
    }

    @Override
    public boolean hasTransferListener( TransferListener listener )
    {
        return super.hasTransferListener( listener );
    }

    @Override
    public boolean isInteractive()
    {
        return wagon.isInteractive();
    }

    @Override
    public void setInteractive( boolean interactive )
    {
        wagon.setInteractive( interactive );
    }

    @Override
    protected void openConnectionInternal()
        throws ConnectionException, AuthenticationException
    {
        throw new RuntimeException( "shouldn't be here" );
    }

    @Override
    protected void closeConnection()
        throws ConnectionException
    {
        throw new RuntimeException( "shouldn't be here" );
    }

    /**
     * @return the cache directory or null.
     */
    public File getCacheDir()
    {
        return cacheDir;
    }

    /**
     * @param cacheDir the cache directory.
     */
    public void setCacheDir( File cacheDir )
    {
        if ( connected )
        {
            throw new IllegalStateException( "already connected" );
        }
        explicitCacheDir = cacheDir != null;
        this.cacheDir = cacheDir;
    }

    /**
     * @throws IllegalStateException when some uncommitted resources remain.
     */
    public void validateCleanRelease()
        throws IllegalStateException
    {
        if ( addedResources.size() != 0 )
        {
            throw new IllegalStateException( "Unclean release of delayed wagon", commitException );
        }
    }
}
