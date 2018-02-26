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

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * nope.
 */
public class TestMavenBuilder
{
    /**
     * @param testRepositoryUrl nope.
     * @throws Exception nope.
     */
    public void run( String testRepositoryUrl )
        throws Exception
    {
        CommandResult res;
    
        Commandline cl = createCommandMvn();
        cl.createArg().setValue( "-version" );
        res = executeMvn( cl, false );
        if ( res.exitCode != 0 )
        {
            throw new Exception( "failed: " + res.saveCommandLine + ":\n" + res.getOutput().trim() );
        }
    
        cl = createCommandMvn();
        cl.setWorkingDirectory( "." );
        cl.createArg().setValue( "install" );
        cl.createArg().setValue( "-Dmaven.test.skip" );
    
        res = executeMvn( cl, true );
        if ( res.exitCode != 0 )
        {
            throw new Exception( "failed: " + res.saveCommandLine );
        }
    
        cl = createCommandMvn();
        cl.setWorkingDirectory( "target/test-classes/test-deploy" );
        cl.createArg().setValue( "deploy" );
        cl.createArg().setValue( "-Dtest.deploy.svn.repo.url=" + testRepositoryUrl );
    
        res = executeMvn( cl, true );
        if ( res.exitCode != 0 )
        {
            throw new Exception( "failed: " + res.saveCommandLine );
        }
    }

    private Commandline createCommandMvn()
    {
        Commandline cl = new Commandline();
        cl.createArg().setValue( "mvn" );
        return cl;
    }

    private void enableShell( Commandline cl )
    {
        String[] save = cl.getShellCommandline();
        cl.clearArgs();
        cl.addArguments( save );
    }

    private static class CommandResult
        extends CommandLineUtils.StringStreamConsumer
    {
        private boolean logging;
        private int exitCode;
        private String saveCommandLine;

        @Override
        public void consumeLine( String line )
        {
            super.consumeLine( line );
            if ( logging )
            {
                System.out.println( line );
            }
        }
    }

    private CommandResult executeMvn( Commandline cl, boolean logging )
        throws CommandLineException
    {
        CommandResult res = new CommandResult();
        res.saveCommandLine = cl.toString();
        System.out.println( res.saveCommandLine );
        File wd = cl.getWorkingDirectory();
        System.out.println( ( wd == null ? new File( "" ) : wd ).getAbsolutePath() );
        System.out.println();
        enableShell( cl ); // to find mvn.cmd on Windows
        res.logging = logging;
        res.exitCode = CommandLineUtils.executeCommandLine( cl, res, res );
        return res;
    }
}
