/**
 * @UNCC Fodor Lab
 * @author Michael Sioda
 * @email msioda@uncc.edu
 * @date Jul 19, 2018
 * @disclaimer This code is free software; you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version, provided that any use properly credits the author. This program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details at http://www.gnu.org *
 */
package biolockj.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import biolockj.Config;
import biolockj.Constants;
import biolockj.Log;
import biolockj.module.BioModule;
import biolockj.module.JavaModule;
import biolockj.module.ScriptModule;

/**
 * This util adds auxiliary log info to BioLockJ log file
 */
public class LogUtil
{

	/**
	 * Not used currently.
	 * 
	 * @param module BioModule
	 * @throws Exception if errors occur
	 */
	public static void syncModuleLogs( final ScriptModule module ) throws Exception
	{
		if( module instanceof JavaModule && Config.getBoolean( module, Constants.CLUSTER_RUN_JAVA_AS_SCRIPT ) )
		{
			merge( cacheLog( getModuleLog( module ) ) );
		}

		if( module instanceof ScriptModule && Config.isOnCluster() )
		{
			// final List<String> cache = new ArrayList<>();
			// for( final File log: module.getScriptDir().listFiles() )
			// {
			// final List<String> lines = cacheLog( log );
			// if( !lines.isEmpty() )
			// {
			// cache.add( "Logging info from: " + log.getAbsolutePath() );
			// cache.addAll( lines );
			// }
			// }
			//
			// merge( cache );
		}
	}

	private static List<String> cacheLog( final File log ) throws Exception
	{
		final List<String> cache = new ArrayList<>();
		final BufferedReader reader = BioLockJUtil.getFileReader( log );
		try
		{
			for( String line = reader.readLine(); line != null; line = reader.readLine() )
			{
				if( !line.trim().isEmpty() && !getProfile().contains( line ) )
				{
					cache.add( line );
				}
			}
		}
		finally
		{
			reader.close();
		}

		return cache;
	}

	private static File getModuleLog( final BioModule module ) throws Exception
	{
		return new File( module.getTempDir().getAbsolutePath() + File.separator + module.getClass().getSimpleName()
				+ Constants.LOG_EXT );
	}

	private static List<String> getProfile()
	{
		if( profile.isEmpty() )
		{
			String userProfile = "Config property [" + USER_PROFILE
					+ "] is undefined.  Bash users typically use: ~/.bash_profile";
			try
			{
				userProfile = Config.requireString( null, USER_PROFILE );
				final File bashProfile = new File( Config.getSystemFilePath( userProfile ) );
				if( bashProfile.exists() )
				{
					profile.addAll( cacheLog( bashProfile ) );
				}
			}
			catch( final Exception ex )
			{
				Log.warn( LogUtil.class, "Unable to find " + userProfile );
			}
		}
		return profile;
	}

	private static String getTempLogPath()
	{
		return Log.getFile().getAbsolutePath().replace( Log.getFile().getName(), ".temp" + Log.getFile().getName() );
	}

	private static void merge( final List<String> lines ) throws Exception
	{
		final File tempLog = new File( getTempLogPath() );
		FileUtils.copyFile( Log.getFile(), tempLog );
		final BufferedWriter writer = new BufferedWriter( new FileWriter( tempLog, true ) );
		try
		{
			for( final String line: lines )
			{
				writer.write( line + Constants.RETURN );
			}
		}
		finally
		{
			if( writer != null )
			{
				writer.close();
			}
		}
		FileUtils.copyFile( tempLog, Log.getFile() );
	}

	/**
	 * {@link biolockj.Config} File property: {@value #USER_PROFILE}<br>
	 * Bash users typically use ~/.bash_profile.
	 */
	public static final String USER_PROFILE = "pipeline.userProfile";

	private static final List<String> profile = new ArrayList<>();
}
