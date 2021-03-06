/**
 * @UNCC Fodor Lab
 * @author Michael Sioda
 * @email msioda@uncc.edu
 * @date Feb 9, 2017
 * @disclaimer This code is free software; you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version, provided that any use properly credits the author. This program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details at http://www.gnu.org
 */
package biolockj;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import biolockj.module.BioModule;
import biolockj.module.JavaModule;
import biolockj.module.JavaModuleImpl;
import biolockj.module.implicit.ImportMetadata;
import biolockj.module.report.Email;
import biolockj.util.*;

/**
 * This is the primary BioLockJ class - its main() method is executed when the jar is run.<br>
 * This class validates the runtime parameters to run a new pipeline or restart a failed pipeline.<br>
 * The Java log file is initialized and the configuration file is processed before starting the pipeline.<br>
 * If the pipeline is successful, the program executes clean up operations (if configured) and creates a status-complete
 * indicator file in the pipeline root directory.<br>
 */
public class BioLockJ
{

	private BioLockJ()
	{}

	/**
	 * Copy file to pipeline root directory.
	 * 
	 * @param file File to copy
	 * @throws Exception if errors occur
	 */
	public static void copyFileToPipelineRoot( final File file ) throws Exception
	{
		final File localFile = new File( Config.pipelinePath() + File.separator + file.getName() );
		if( !localFile.exists() )
		{
			FileUtils.copyFileToDirectory( file, new File( Config.pipelinePath() ) );
			if( !localFile.exists() )
			{
				throw new Exception( "Unable to copy file to pipeline root directory: " + file.getAbsolutePath() );
			}

		}
	}

	/**
	 * Print error file path, restart instructions, and link to the BioLockJ Wiki
	 * 
	 * @param errFile Error File
	 * @return Help Info
	 */
	public static String getHelpInfo( final File errFile )
	{
		try
		{
			return Constants.RETURN + "To view the BioLockJ help menu, run \"biolockj -h\"" + Constants.RETURN
					+ ( errFile != null ? "Writing error file to " + errFile.getAbsolutePath() + Constants.RETURN: "" )
					+ "For more information, please visit the BioLockJ Wiki:" + Constants.BLJ_WIKI + Constants.RETURN;
		}
		catch( final Exception ex )
		{
			ex.printStackTrace();
		}
		return "";
	}

	/**
	 * Determine project status based on existence of {@value biolockj.Constants#BLJ_COMPLETE} in pipeline root
	 * directory.
	 *
	 * @return true if {@value biolockj.Constants#BLJ_COMPLETE} exists in the pipeline root directory, otherwise false
	 */
	public static boolean isPipelineComplete()
	{
		File f = null;
		try
		{
			f = new File( Config.pipelinePath() + Constants.BLJ_COMPLETE );
		}
		catch( final Exception ex )
		{
			return false;
		}
		return f != null && f.exists();
	}

	/**
	 * {@link biolockj.BioLockJ} is the BioLockj.jar Main-Class, and is the first method executed.<br>
	 * Execution summary:<br>
	 * <ol>
	 * <li>Call {@link #initBioLockJ(String[])} to assign pipeline root dir and log file
	 * <li>If change password pipeline, call {@link biolockj.module.report.Email#encryptAndStoreEmailPassword()}
	 * <li>Otherwise execute {@link #startPipeline()}
	 * </ol>
	 * <p>
	 * If pipeline has failed, attempt execute {@link biolockj.module.report.Email} (if configured) to notify user of
	 * failures.
	 *
	 * @param args - String[] runtime parameters passed to the Java program when launching BioLockJ
	 */
	public static void main( final String[] args )
	{
		System.out.println( "Staring BioLockj..." );
		try
		{
			initBioLockJ( args );
		}
		catch( final Exception ex )
		{
			printErrorFileAndExitProgram( args, ex );
		}

		try
		{
			if( RuntimeParamUtil.doChangePassword() )
			{
				Log.info( BioLockJ.class, "Save encrypted password to: " + Config.getConfigFilePath() );
				Email.encryptAndStoreEmailPassword();
			}
			else
			{
				startPipeline();
			}
		}
		catch( final Exception ex )
		{
			logFinalException( args, ex );
			SummaryUtil.addSummaryFooterForFailedPipeline( getHelpInfo( null ) );
		}
		finally
		{
			pipelineShutDown( args );
		}
	}

	/**
	 * Return the pipeline input directory
	 * 
	 * @return Input dir
	 * @throws Exception if unable to obtain file
	 */
	public static File pipelineInputDir() throws Exception
	{
		return new File( Config.pipelinePath() + File.separator + "input" );
	}

	/**
	 * Create a copy of the sequence files in property {@value biolockj.Constants#INPUT_DIRS}, output to a directory
	 * named {@value biolockj.Constants#PIPELINE_DIR}/input.
	 *
	 * @throws Exception if unable to copy the files
	 */
	protected static void copyInputData() throws Exception
	{
		final String statusFileName = pipelineInputDir().getName() + File.separator + Constants.BLJ_COMPLETE;
		final File statusFile = new File( Config.pipelinePath() + File.separator + statusFileName );
		if( !pipelineInputDir().exists() )
		{
			pipelineInputDir().mkdirs();
		}
		else if( statusFile.exists() )
		{
			return;
		}

		for( final File dir: BioLockJUtil.getInputDirs() )
		{
			Log.info( BioLockJ.class, "Copying input files from " + dir + " to " + pipelineInputDir() );
			FileUtils.copyDirectory( dir, pipelineInputDir() );
			markStatus( statusFileName );
			BioLockJUtil.ignoreFile( statusFile );
		}

		final List<File> inputFiles = new ArrayList<>(
				FileUtils.listFiles( pipelineInputDir(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE ) );
		Log.info( BioLockJ.class, "Total number of input files: " + inputFiles.size() );
		int i = 0;
		for( final File file: inputFiles )
		{
			Log.info( BioLockJ.class, "Imported Input File [ " + i++ + " ]: " + file.getAbsolutePath() );
		}

		BioLockJUtil.setPipelineInputFiles( inputFiles );
		Config.setConfigProperty( Constants.INPUT_DIRS, pipelineInputDir().getAbsolutePath() );
	}

	/**
	 * Create the pipeline root directory under $DOCKER_PROJ and save the path to
	 * {@link biolockj.Config}.{@value biolockj.Constants#PIPELINE_DIR}.
	 * <p>
	 * For example, the following {@link biolockj.Config} settings will create:
	 * <b>/projects/MicrobeProj_2018Jan01</b><br>
	 * <ul>
	 * <li>$DOCKER_PROJ = /projects
	 * <li>{@link biolockj.Config} file name = MicrobeProj.properties
	 * <li>Current date = January 1, 2018
	 * </ul>
	 *
	 * @return Pipeline root directory
	 * @throws Exception if errors occur
	 */
	protected static File createPipelineDirectory() throws Exception
	{
		final String year = String.valueOf( new GregorianCalendar().get( Calendar.YEAR ) );
		final String month = new GregorianCalendar().getDisplayName( Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH );
		final String day = BioLockJUtil.formatDigits( new GregorianCalendar().get( Calendar.DATE ), 2 );
		final String baseString = RuntimeParamUtil.getBaseDir().getAbsolutePath() + File.separator + getProjectName();
		final String dateString = "_" + year + month + day;
		File projectDir = new File( baseString + dateString );

		int i = 2;
		while( projectDir.exists() )
		{
			projectDir = new File( baseString + "_" + i++ + dateString );
		}

		projectDir.mkdirs();
		return projectDir;
	}

	/**
	 * Initialize AWS manager pipeline:<br>
	 * <ol>
	 * <li>Build Nexflow main.nf
	 * <li>Run ImportMetadata module
	 * <li>Set files editable
	 * <li>Update pipeline root directory to EFS directory
	 * <li>Update EFS MASTER {@link biolockj.Config} with new pipeline root directory path
	 * <li>Save pipeline input files to EFS for faster processing
	 * </ol>
	 * 
	 * @throws Exception if runtime errors occur
	 */
	protected static void initAwsManager() throws Exception
	{
		NextflowUtil.buildNextflowMain( Pipeline.getModules() );
		Pipeline.executeModule( importMeta() );
		setPipelineSecurity();
		Config.setPipelineDir( NextflowUtil.copyPipelineToEfs() );
		PropUtil.saveMasterConfig( null );
		copyInputData();
	}

	/**
	 * Execution summary:<br>
	 * <ol>
	 * <li>Call {@link biolockj.util.MemoryUtil#reportMemoryUsage(String)} for baseline memory info
	 * <li>Call {@link biolockj.util.RuntimeParamUtil#registerRuntimeParameters(String[])}
	 * <li>Call {@link biolockj.util.MetaUtil#initialize()} to verify metadata dependencies
	 * <li>Call {@link biolockj.Config#initialize()} to create pipeline root dir and load properties
	 * <li>Initialize {@link Log} with /resources/log4J.properties
	 * <li>Copy initial metadata file into the pipeline root directory
	 * <li>Call {@link biolockj.util.SeqUtil#initialize()} to set Config parameters based on sequence files
	 * </ol>
	 * <p>
	 *
	 * @param args - String[] runtime parameters passed to the Java program when launching BioLockJ
	 * @throws Exception if errors occur
	 */
	protected static void initBioLockJ( final String[] args ) throws Exception
	{
		Log.info( BioLockJ.class, "App Runtime start time (as long): " + Constants.APP_START_TIME );
		MemoryUtil.reportMemoryUsage( "INTIAL MEMORY STATS" );
		RuntimeParamUtil.registerRuntimeParameters( args );

		Config.initialize();
		if( isPipelineComplete() )
		{
			throw new Exception( "Pipeline Cancelled!  Pipeline already contains status file: " + Constants.BLJ_COMPLETE
					+ " --> Check directory: " + Config.pipelinePath() );
		}
		Config.getString( null, Constants.INPUT_DIRS );
		MetaUtil.initialize();

		if( RuntimeParamUtil.isDirectMode() )
		{
			Log.initialize( getDirectLogName( RuntimeParamUtil.getDirectModuleDir() ) );
		}
		else
		{
			Log.initialize( Config.pipelineName() );
		}

		if( RuntimeParamUtil.doRestart() )
		{
			initRestart();
		}

		if( MetaUtil.getMetadata() != null )
		{
			BioLockJ.copyFileToPipelineRoot( MetaUtil.getMetadata() );
		}

		// Initializes PIPELINE_SEQ_INPUT_TYPE
		BioLockJUtil.getPipelineInputFiles();

		if( doCopyInput() )
		{
			copyInputData();
		}

		SeqUtil.initialize();
	}

	/**
	 * Initialize restarted pipeline by:
	 * <ol>
	 * <li>Initialize {@link biolockj.Log} file using the name of the pipeline root directory
	 * <li>Update summary #Attempts count
	 * <li>Delete status file {@value biolockj.Constants#BLJ_FAILED} in pipeline root directory
	 * <li>If pipeline status = {@value biolockj.Constants#BLJ_COMPLETE}
	 * <li>Delete file {@value biolockj.util.DownloadUtil#DOWNLOAD_LIST} in pipeline root directory
	 * </ol>
	 * 
	 * @throws Exception if errors occur
	 */
	protected static void initRestart() throws Exception
	{
		Log.initialize( Config.pipelineName() );

		Log.warn( BioLockJ.class,
				Constants.RETURN + Constants.LOG_SPACER + Constants.RETURN + "RESTART PROJECT DIR --> "
						+ RuntimeParamUtil.getRestartDir().getAbsolutePath() + Constants.RETURN + Constants.LOG_SPACER
						+ Constants.RETURN );
		Log.info( BioLockJ.class, "Initializing Restarted Pipeline - this may take a couple of minutes..." );

		SummaryUtil.updateNumAttempts();
		if( DownloadUtil.getDownloadListFile().exists() )
		{
			DownloadUtil.getDownloadListFile().delete();
		}
		if( NextflowUtil.getMainNf().exists() )
		{
			NextflowUtil.getMainNf().delete();
		}

		final File f = new File( Config.pipelinePath() + File.separator + Constants.BLJ_FAILED );
		if( f.exists() )
		{
			if( !BioLockJUtil.deleteWithRetry( f, 5 ) )
			{
				Log.warn( BioLockJ.class, "Unable to delete " + f.getAbsolutePath() );
			}
		}
	}

	/**
	 * Create indicator file in pipeline root directory, with name = status parameter.
	 * {@link biolockj.Config}.{@value biolockj.Constants#PIPELINE_DIR}.
	 * 
	 * @param status Status indicator file name
	 */
	protected static void markProjectStatus( final String status )
	{
		try
		{
			Log.info( BioLockJ.class, "BioLockJ Pipeline [" + Config.pipelineName() + "] = " + status );
			markStatus( status );
		}
		catch( final Exception ex )
		{
			Log.error( BioLockJ.class, "Unable to create pipeline status indicator file!", ex );
			pipelineShutDown( null );
		}
	}

	/**
	 * Delete all {@link biolockj.module.BioModule}/{@value biolockj.module.BioModule#TEMP_DIR} folders.
	 *
	 * @throws Exception if unable to delete temp files
	 */
	protected static void removeTempFiles() throws Exception
	{
		Log.info( BioLockJ.class, "Cleaning up BioLockJ Modules..." );
		for( final BioModule bioModule: Pipeline.getModules() )
		{
			if( ModuleUtil.subDirExists( bioModule, BioModule.TEMP_DIR ) )
			{
				Log.info( BioLockJ.class, "Delete temp dir for BioLockJ Module: " + bioModule.getClass().getName() );
				BioLockJUtil.deleteWithRetry( ModuleUtil.requireSubDir( bioModule, BioModule.TEMP_DIR ), 10 );
			}
		}
	}

	/**
	 * Execution summary:<br>
	 * <ol>
	 * <li>Call {@link biolockj.Pipeline#initializePipeline()} to initialize Pipeline modules
	 * <li>For direct module execution call {@link biolockj.Pipeline#runDirectModule(Integer)}
	 * <li>Otherwise execute {@link biolockj.Pipeline#runPipeline()} and save MASTER {@link biolockj.Config}
	 * <li>If initializing AWS Cloud manager, call {@link #initAwsManager()}.
	 * <li>If {@link biolockj.Config}.{@value biolockj.Constants#PIPELINE_DELETE_TEMP_FILES} =
	 * {@value biolockj.Constants#TRUE}, Call {@link #removeTempFiles()} to delete tem files
	 * <li>Call {@link #markProjectStatus(String)} to set the overall pipeline status as successful
	 * </ol>
	 * 
	 * @throws Exception if runtime errors occur
	 */
	protected static void startPipeline() throws Exception
	{
		Pipeline.initializePipeline();

		if( RuntimeParamUtil.isDirectMode() )
		{
			runDirectPipeline();
		}
		else
		{
			PropUtil.saveMasterConfig( null );
			if( DockerUtil.initAwsCloudManager() )
			{
				initAwsManager();
			}
			else
			{
				Pipeline.runPipeline();

				if( Config.getBoolean( null, Constants.PIPELINE_DELETE_TEMP_FILES ) )
				{
					removeTempFiles();
				}

				PropUtil.sanitizeMasterConfig();
				markProjectStatus( Constants.BLJ_COMPLETE );
				Log.info( BioLockJ.class, "Log Pipeline Summary..." + Constants.RETURN + SummaryUtil.getSummary() );

			}
		}
	}

	private static boolean doCopyInput() throws Exception
	{
		final boolean hasMixedInputs = BioLockJUtil.pipelineInputType( BioLockJUtil.PIPELINE_R_INPUT_TYPE )
				|| BioLockJUtil.pipelineInputType( BioLockJUtil.PIPELINE_HUMANN2_COUNT_TABLE_INPUT_TYPE )
				|| BioLockJUtil.pipelineInputType( BioLockJUtil.PIPELINE_NORMAL_TAXA_COUNT_TABLE_INPUT_TYPE )
				|| BioLockJUtil.pipelineInputType( BioLockJUtil.PIPELINE_TAXA_COUNT_TABLE_INPUT_TYPE )
				|| BioLockJUtil.pipelineInputType( BioLockJUtil.PIPELINE_STATS_TABLE_INPUT_TYPE );

		if( hasMixedInputs )
		{
			Log.warn( BioLockJ.class,
					"Non-sequence inputs found - copy input files from "
							+ Config.requireString( null, Constants.INPUT_DIRS ) + " to:"
							+ pipelineInputDir().getAbsolutePath() );
		}
		return !RuntimeParamUtil.isDirectMode() && Config.getBoolean( null, Constants.PIPELINE_COPY_FILES )
				|| hasMixedInputs;
	}

	private static String getDirectLogName( final String moduleDir ) throws Exception
	{
		final File modDir = new File( Config.pipelinePath() + File.separator + moduleDir );
		if( !modDir.exists() )
		{
			throw new Exception( "Direct module directory not found --> " + modDir.getAbsolutePath() );
		}

		final File tempDir = new File( modDir.getAbsoluteFile() + File.separator + BioModule.TEMP_DIR );
		if( !tempDir.exists() )
		{
			tempDir.mkdir();
		}

		return modDir.getName() + File.separator + tempDir.getName() + File.separator + moduleDir;

	}

	private static Integer getDirectModuleID( final String moduleDir ) throws Exception
	{
		return Integer.valueOf( moduleDir.substring( 0, moduleDir.indexOf( "_" ) ) );
	}

	private static String getProjectName() throws Exception
	{
		String name = RuntimeParamUtil.getConfigFile().getName();
		final String[] exts = { ".ascii", ".asc", ".plain", ".rft", ".tab", ".text", ".tsv", ".txt", ".properties",
				".prop", ".props", ".config" };

		for( final String ext: exts )
		{
			if( name.toLowerCase().endsWith( ext ) )
			{
				name = name.substring( 0, name.length() - ext.length() );
			}
		}

		if( name.startsWith( Constants.MASTER_PREFIX ) )
		{
			name = name.replace( Constants.MASTER_PREFIX, "" );
		}

		return name;
	}

	private static BioModule importMeta() throws Exception
	{
		final BioModule module = Pipeline.getModules().get( 0 );
		if( module instanceof ImportMetadata )
		{
			return module;
		}

		return null;
	}

	private static void logFinalException( final String[] args, final Exception ex )
	{
		if( printedFinalExcp )
		{
			return;
		}

		if( Config.pipelineName() != null )
		{
			markProjectStatus( Constants.BLJ_FAILED );
		}

		if( Log.getFile() != null )
		{
			Log.error( BioLockJ.class, Constants.LOG_SPACER );
			Log.error( BioLockJ.class,
					Constants.RETURN + "FATAL APPLICATION ERROR - " + ex.getMessage()
							+ ( args == null ? ""
									: " -->" + Constants.RETURN + " Program args: "
											+ BioLockJUtil.getCollectionAsString( Arrays.asList( args ) ) ),
					ex );
			Log.error( BioLockJ.class, Constants.LOG_SPACER );
			ex.printStackTrace();
			Log.error( BioLockJ.class, Constants.LOG_SPACER );
			Log.error( BioLockJ.class, getHelpInfo( Log.getFile() ) );
			Log.error( BioLockJ.class, Constants.LOG_SPACER );
		}
		else
		{
			System.out.println( Constants.LOG_SPACER );
			System.out.println( Constants.RETURN + "FATAL APPLICATION ERROR - " + ex.getMessage()
					+ ( args == null ? ""
							: " -->" + Constants.RETURN + " Program args: "
									+ BioLockJUtil.getCollectionAsString( Arrays.asList( args ) ) ) );
			System.out.println( Constants.LOG_SPACER );
			ex.printStackTrace();
			System.out.println( Constants.LOG_SPACER );
			System.out.println( getHelpInfo( null ) );
			System.out.println( Constants.LOG_SPACER );
		}

		printedFinalExcp = true;
	}

	private static void markStatus( final String status ) throws Exception
	{
		final File f = new File( Config.pipelinePath() + File.separator + status );
		final FileWriter writer = new FileWriter( f );
		writer.close();
		if( !f.exists() )
		{
			throw new Exception( "Unable to create " + f.getAbsolutePath() );
		}
	}

	private static void pipelineShutDown( final String[] args )
	{
		if( !RuntimeParamUtil.isDirectMode() )
		{
			try
			{
				setPipelineSecurity();
			}
			catch( final Exception ex )
			{
				logFinalException( args, ex );
			}
		}
		else if( isPipelineComplete() )
		{
			Log.info( BioLockJ.class, "Analysis complete --> End program [ 42 ]" );
		}
		else
		{
			System.exit( 1 );
		}
	}

	/**
	 * Print the {@link biolockj.Log} messages and the exception stack trace info to the $USER $HOME directory.
	 * 
	 * @param fatalException Fatal application Exception
	 */
	private static void printErrorFileAndExitProgram( final String[] args, final Exception fatalException )
	{
		try
		{
			String suffix = "";
			try
			{
				if( Config.pipelineName() != null )
				{
					suffix = Config.pipelineName();
				}
				if( RuntimeParamUtil.isDirectMode() )
				{
					suffix += ( suffix.isEmpty() ? "": "_" ) + RuntimeParamUtil.getDirectModuleDir();
				}

				if( suffix.isEmpty() && RuntimeParamUtil.getConfigFile() != null )
				{
					suffix = RuntimeParamUtil.getConfigFile().getName();
				}
				else
				{
					suffix = "Config_param_not_found";
				}
			}
			catch( final Exception ex )
			{
				suffix = "Config_init_failed";
			}

			int index = 0;
			final String prefix = ( RuntimeParamUtil.isDockerMode() ? DockerUtil.CONTAINER_OUTPUT_DIR: "~" )
					+ File.separator;
			File errFile = new File(
					Config.getSystemFilePath( prefix + FATAL_ERROR_FILE_PREFIX + suffix + Constants.LOG_EXT ) );
			while( errFile.exists() )
			{
				errFile = new File( Config.getSystemFilePath( prefix + FATAL_ERROR_FILE_PREFIX + suffix + "_"
						+ new Integer( ++index ).toString() + Constants.LOG_EXT ) );
			}

			Log.error( BioLockJ.class, Constants.LOG_SPACER );
			Log.error( BioLockJ.class, "Pipeline failed before root directory or Log file was created!" );
			Log.error( BioLockJ.class, Constants.LOG_SPACER );
			logFinalException( args, fatalException );

			final BufferedWriter writer = new BufferedWriter( new FileWriter( errFile ) );
			try
			{
				for( final String msg: Log.getMsgs() )
				{
					writer.write( msg + Constants.RETURN );
				}
			}
			finally
			{
				if( writer != null )
				{
					writer.close();
				}
			}
		}
		catch( final Exception ex )
		{
			System.out.println( "Unable to access Log or write to $USER $HOME directory!" );
			System.out.println( getHelpInfo( null ) );

		}
		finally
		{
			pipelineShutDown( args );
		}
	}

	private static void reportDirectModuleFailure( final Exception ex ) throws Exception
	{
		final JavaModule module = (JavaModuleImpl) Pipeline.getModules()
				.get( getDirectModuleID( RuntimeParamUtil.getDirectModuleDir() ) );
		Log.info( BioLockJ.class, "Save failure status for direct module: " + module.getClass().getName() );
		module.moduleFailed();
		SummaryUtil.reportFailure( ex );
	}

	private static void reportDirectModuleSucess() throws Exception
	{
		final JavaModule module = (JavaModuleImpl) Pipeline.getModules()
				.get( getDirectModuleID( RuntimeParamUtil.getDirectModuleDir() ) );
		Log.info( BioLockJ.class, "Save success status for direct module: " + module.getClass().getName() );
		module.moduleComplete();
		SummaryUtil.reportSuccess( module );
		System.exit( 0 );
	}

	private static void runDirectPipeline() throws Exception
	{
		try
		{
			final Integer id = getDirectModuleID( RuntimeParamUtil.getDirectModuleDir() );
			Pipeline.runDirectModule( id );
			reportDirectModuleSucess();
			System.exit( 0 );
		}
		catch( final Exception ex )
		{
			reportDirectModuleFailure( ex );
			System.exit( 1 );
		}
	}

	private static void setPipelineSecurity() throws Exception
	{
		final String perm = Config.getString( null, Constants.PROJECT_PERMISSIONS );
		if( perm != null )
		{
			Job.setFilePermissions( Config.pipelinePath(), perm );
		}
	}

	private static final String FATAL_ERROR_FILE_PREFIX = "BioLockJ_FATAL_ERROR_";
	private static boolean printedFinalExcp = false;
}
