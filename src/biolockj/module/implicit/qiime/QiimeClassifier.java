/**
 * @UNCC Fodor Lab
 * @author Michael Sioda
 * @email msioda@uncc.edu
 * @date June 19, 2017
 * @disclaimer This code is free software; you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version, provided that any use properly credits the author. This program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details at http://www.gnu.org *
 */
package biolockj.module.implicit.qiime;

import java.io.*;
import java.util.*;
import biolockj.*;
import biolockj.module.BioModule;
import biolockj.module.classifier.ClassifierModule;
import biolockj.module.classifier.ClassifierModuleImpl;
import biolockj.module.classifier.r16s.QiimeOpenRefClassifier;
import biolockj.module.seq.PearMergeReads;
import biolockj.util.*;

/**
 * This BioModule generates the bash script used to create QIIME summary scripts, taxonomy-level reports, and add alpha
 * diversity metrics (if configured) to the metadata file.<br>
 * For a complete list of available metrics, see:
 * <a href= "http://scikit-bio.org/docs/latest/generated/skbio.diversity.alpha.html" target=
 * "_top">http://scikit-bio.org/docs/latest/generated/skbio.diversity.alpha.html</a><br>
 * 
 * @blj.web_desc
 */
public class QiimeClassifier extends ClassifierModuleImpl implements ClassifierModule
{
	/**
	 * Generate bash script lines to summarize QIIME results, build taxonomy reports, and add alpha diversity metrics.
	 * <p>
	 * The QiimeClassifier script begins with the following QIIME scripts:
	 * <ol>
	 * <li>{@value #SCRIPT_PRINT_CONFIG} - logs version/environment info
	 * <li>{@value #SCRIPT_SUMMARIZE_TAXA} - processes {@value #OTU_TABLE} to create taxonomy-level reports in the
	 * output directory
	 * <li>{@value #SCRIPT_SUMMARIZE_BIOM} - processes {@value #OTU_TABLE} to create summary file:
	 * output/{@value #OTU_SUMMARY_FILE}
	 * </ol>
	 * <p>
	 * If {@link biolockj.Config}.{@value Constants#QIIME_ALPHA_DIVERSITY_METRICS} are defined, add lines to run
	 * additional scripts:
	 * <ol>
	 * <li>{@value #SCRIPT_CALC_ALPHA_DIVERSITY} - calculates {@value Constants#QIIME_ALPHA_DIVERSITY_METRICS} on
	 * {@value #OTU_TABLE} to create output/{@value #ALPHA_DIVERSITY_TABLE}
	 * <li>{@value #SCRIPT_ADD_ALPHA_DIVERSITY} - adds {@value #ALPHA_DIVERSITY_TABLE} data to
	 * {@link biolockj.Config}.{@value biolockj.util.MetaUtil#META_FILE_PATH}
	 * <li>
	 * </ol>
	 * <p>
	 * For complete list of skbio.diversity.alpha options, see
	 * <a href= "http://scikit-bio.org/docs/latest/generated/skbio.diversity.alpha.html" target=
	 * "_top">http://scikit-bio.org/docs/latest/generated/skbio.diversity.alpha.html</a>
	 */
	@Override
	public List<List<String>> buildScript( final List<File> files ) throws Exception
	{
		final String outDir = getOutputDir().getAbsolutePath() + File.separator;
		final String tempDir = getTempDir().getAbsolutePath() + File.separator;
		final List<List<String>> data = new ArrayList<>();
		final List<String> lines = new ArrayList<>();
		lines.add( SCRIPT_PRINT_CONFIG );
		lines.add( SCRIPT_SUMMARIZE_TAXA + " -a --" + SUMMARIZE_TAXA_SUPPRESS_BIOM + " -i " + files.get( 0 ) + " -L "
				+ getLowestQiimeTaxaLevel() + " -o " + outDir );
		lines.add( SCRIPT_SUMMARIZE_BIOM + " -i " + files.get( 0 ) + " -o " + tempDir + OTU_SUMMARY_FILE );
		if( Config.getString( this, Constants.QIIME_ALPHA_DIVERSITY_METRICS ) != null )
		{
			final File newMapping = new File( tempDir + MetaUtil.getMetadataFileName() );
			lines.add( SCRIPT_CALC_ALPHA_DIVERSITY + " -i " + files.get( 0 ) + " -m " + getAlphaDiversityMetrics()
					+ " -o " + tempDir + ALPHA_DIVERSITY_TABLE );
			lines.add( SCRIPT_ADD_ALPHA_DIVERSITY + " -m " + MetaUtil.getPath() + " -i " + tempDir
					+ ALPHA_DIVERSITY_TABLE + " -o " + newMapping );
			MetaUtil.setFile( newMapping );
		}

		data.add( lines );

		return data;
	}

	/**
	 * QIIME does not support paired reads
	 */
	@Override
	public List<List<String>> buildScriptForPairedReads( final List<File> files ) throws Exception
	{
		throw new Exception(
				"QIIME does not support paired reads!  Prerequisite BioModule " + PearMergeReads.class.getName()
						+ " should be added your pipeline Config: " + Config.getConfigFileName() );
	}

	@Override
	public void checkDependencies() throws Exception
	{
		if( getClass().equals( QiimeClassifier.class ) )
		{
			boolean foundOtuPickingModule = false;
			for( final BioModule module: Pipeline.getModules() )
			{
				if( module.getClass().getPackage().equals( QiimeOpenRefClassifier.class.getPackage() ) )
				{
					foundOtuPickingModule = true;
				}
			}

			if( !foundOtuPickingModule )
			{
				throw new Exception( "QIIME pipelines require an OTU Picking module from package: "
						+ QiimeOpenRefClassifier.class.getPackage() );
			}
		}

		super.checkDependencies();
	}

	/**
	 * The cleanUp operation builds a new metadata file if alpha diversity metrics were generated by this module. The
	 * script {@value #SCRIPT_ADD_ALPHA_DIVERSITY} outputs {@value #ALPHA_DIV_NULL_VALUE} for null values which must be
	 * replaced by {@link biolockj.Config}.{@value biolockj.util.MetaUtil#META_NULL_VALUE} if any are found.
	 * <p>
	 * This method also removes the redundant normalized alpha metric column and reorganizes the metadata so that alpha
	 * metric columns are move to the first columns after the 1st ID column.
	 */
	@Override
	public void cleanUp() throws Exception
	{
		super.cleanUp();
		final List<String> metrics = Config.getList( this, Constants.QIIME_ALPHA_DIVERSITY_METRICS );
		if( ModuleUtil.isComplete( this ) || !getClass().equals( QiimeClassifier.class ) || metrics.isEmpty()
				|| Config.requireString( this, MetaUtil.META_NULL_VALUE ).equals( ALPHA_DIV_NULL_VALUE ) )
		{
			if( !metrics.isEmpty() )
			{
				MetaUtil.refreshCache();
			}
			return; // nothing to do
		}

		MetaUtil.refreshCache(); // to get the new alpha metric fields
		final BufferedReader reader = BioLockJUtil.getFileReader( MetaUtil.getFile() );
		MetaUtil.setFile(
				new File( getOutputDir().getAbsolutePath() + File.separator + MetaUtil.getMetadataFileName() ) );
		final BufferedWriter writer = new BufferedWriter( new FileWriter( MetaUtil.getFile() ) );
		final int numCols = MetaUtil.getFieldNames().size() - metrics.size() * 3;

		try
		{

			final Set<Integer> skipCols = new HashSet<>();

			boolean isHeader = true;
			for( String line = reader.readLine(); line != null; line = reader.readLine() )
			{
				final StringTokenizer st = new StringTokenizer( line, TAB_DELIM );
				writer.write( st.nextToken() ); // write ID col

				final List<String> record = new ArrayList<>();
				while( st.hasMoreTokens() )
				{
					record.add( st.nextToken() );
				}

				// Add Alpha Metrics as 1st columns since these will be used later as count cols instead of meta cols
				for( int i = numCols; i < record.size(); i++ )
				{
					String val = record.get( i );
					if( isHeader && val != null
							&& ( val.endsWith( NORMALIZED_ALPHA ) || val.endsWith( NORMALIZED_ALPHA_LABEL ) ) )
					{
						skipCols.add( i );
					}
					else if( isHeader || !skipCols.contains( i ) )
					{
						// replace any N/A values with configured MetaUtil.META_ULL_VALUE
						if( !isHeader && val.equals( ALPHA_DIV_NULL_VALUE ) )
						{
							val = Config.requireString( this, MetaUtil.META_NULL_VALUE );
						}

						writer.write( TAB_DELIM + val );
					}
				}

				// Add previously existing meta cols
				for( int i = 0; i < numCols; i++ )
				{
					writer.write( TAB_DELIM + record.get( i ) );
				}

				writer.write( RETURN );
				isHeader = false;
			}
		}
		finally
		{
			reader.close();
			writer.close();
			MetaUtil.refreshCache();
		}
	}

	/**
	 * QIIME calls python scripts, so no special command is required
	 */
	@Override
	public String getClassifierExe() throws Exception
	{
		return null;
	}

	/**
	 * Obtain the QIIME runtime params
	 */
	@Override
	public List<String> getClassifierParams() throws Exception
	{
		return Config.getList( this, QIIME_PARAMS );
	}

	/**
	 * Check DB parameters for the comment parent directory path, there are 3 parameters:
	 * <ol>
	 * <li>{@value #QIIME_PYNAST_ALIGN_DB}
	 * <li>{@value #QIIME_REF_SEQ_DB}
	 * <li>{@value #QIIME_TAXA_DB}
	 * </ol>
	 * 
	 * @return Parent DB directory
	 * @throws Exception if errors occur
	 */
	@Override
	public File getDB() throws Exception
	{
		String pynastDB = Config.getString( this, QIIME_PYNAST_ALIGN_DB );
		String refSeqDB = Config.getString( this, QIIME_REF_SEQ_DB );
		String taxaDB = Config.getString( this, QIIME_TAXA_DB );

		if( pynastDB == null && refSeqDB == null && taxaDB == null )
		{
			return null;
		}

		if( pynastDB == null || refSeqDB == null || taxaDB == null )
		{
			if( pynastDB == null )
			{
				pynastDB = UNDEFINED;
			}
			if( pynastDB == null )
			{
				refSeqDB = UNDEFINED;
			}
			if( pynastDB == null )
			{
				taxaDB = UNDEFINED;
			}
			throw new Exception(
					"Alternate QIIME database cannot be partially defined.  If any of these Config properties are defined, they must all be defined: "
							+ QIIME_PYNAST_ALIGN_DB + "=" + pynastDB + ", " + QIIME_REF_SEQ_DB + "=" + refSeqDB + ", "
							+ QIIME_TAXA_DB + "=" + taxaDB );
		}

		final File pynastFile = new File( Config.getSystemFilePath( pynastDB ) );
		final File refSeqFile = new File( Config.getSystemFilePath( refSeqDB ) );
		final File taxaFile = new File( Config.getSystemFilePath( taxaDB ) );

		final File parentDir = BioLockJUtil.getCommonParent( BioLockJUtil.getCommonParent( pynastFile, refSeqFile ),
				taxaFile );
		Log.info( getClass(), "Found common database dir: " + parentDir.getAbsolutePath() );
		return parentDir;
	}

	@Override
	public List<File> getInputFiles() throws Exception
	{
		if( getFileCache().isEmpty() )
		{
			if( getClass().getName().equals( QiimeClassifier.class.getName() ) )
			{
				cacheInputFiles( findModuleInputFiles() );
			}
			else
			{
				cacheInputFiles( getSeqFiles( findModuleInputFiles() ) );
			}
		}

		return getFileCache();
	}

	/**
	 * Subclasses of QiimeClassifier add post-requisite module: {@link biolockj.module.implicit.qiime.QiimeClassifier}.
	 * Only the QiimeClassifier itself adds the QiimeParser as a post-requisite module.
	 */
	@Override
	public List<String> getPostRequisiteModules() throws Exception
	{
		final List<String> postReqs = new ArrayList<>();
		if( !getClass().equals( QiimeClassifier.class ) )
		{
			postReqs.add( QiimeClassifier.class.getName() );
		}

		postReqs.addAll( super.getPostRequisiteModules() );

		return postReqs;
	}

	/**
	 * If paired reads found, add prerequisite module: {@link biolockj.module.seq.PearMergeReads}. If sequences are not
	 * fasta format, add prerequisite module: {@link biolockj.module.seq.AwkFastaConverter}, or similar module specified
	 * by {@value biolockj.Constants#DEFAULT_MOD_FASTA_CONV}. Subclasses of QiimeClassifier add prerequisite module:
	 * {@link biolockj.module.implicit.qiime.BuildQiimeMapping}.
	 */
	@Override
	public List<String> getPreRequisiteModules() throws Exception
	{
		final List<String> preReqs = new ArrayList<>();
		preReqs.addAll( super.getPreRequisiteModules() );
		if( Config.getBoolean( this, Constants.INTERNAL_PAIRED_READS ) )
		{
			preReqs.add( ModuleUtil.getDefaultMergePairedReadsConverter() );
		}
		if( SeqUtil.piplineHasSeqInput() && !SeqUtil.isFastA() )
		{
			preReqs.add( ModuleUtil.getDefaultFastaConverter() );
		}
		if( !getClass().getName().equals( QiimeClassifier.class.getName() ) ) // must be a classifier
		{
			preReqs.add( BuildQiimeMapping.class.getName() );
		}

		return preReqs;
	}

	/**
	 * This method extends the classifier summary by adding the Qiime OTU summary metrics.
	 */
	@Override
	public String getSummary() throws Exception
	{
		final StringBuffer sb = new StringBuffer();
		try
		{
			final String endString = "Counts/sample detail:";
			final File otuSummary = new File( getTempDir().getAbsolutePath() + File.separator + OTU_SUMMARY_FILE );
			if( otuSummary.exists() )
			{
				final BufferedReader reader = BioLockJUtil.getFileReader( otuSummary );
				sb.append( "OTU Summary" + RETURN );
				for( String line = reader.readLine(); line != null; line = reader.readLine() )
				{
					if( line.trim().equals( endString ) )
					{
						break;
					}
					if( !line.trim().isEmpty() )
					{
						sb.append( line + RETURN );
					}
				}

				reader.close();
			}
			return super.getSummary() + sb.toString();
		}
		catch( final Exception ex )
		{
			Log.warn( getClass(), "Unable to complete module summary: " + ex.getMessage() );
		}

		return super.getSummary();
	}

	@Override
	public List<String> getWorkerScriptFunctions() throws Exception
	{
		final List<String> lines = super.getWorkerScriptFunctions();
		if( DockerUtil.hasDB( this ) )
		{
			lines.addAll( buildQiimeConfigLines() );
			lines.add( "" );
		}

		return lines;
	}

	/**
	 * If superclass is fed by another QiimeClassifier, it must be a subclass with biom output. Otherwise, if a
	 * subclass, it must expect sequence file input.
	 */
	@Override
	public boolean isValidInputModule( final BioModule module )
	{
		if( getClass().getName().equals( QiimeClassifier.class.getName() )
				&& module.getClass().getName().toLowerCase().contains( Constants.QIIME ) )
		{
			return true;
		}

		return super.isValidInputModule( module );
	}

	/**
	 * Build ~/.qiime_config to define the alternate Docker qiime_classifier DB with local container path
	 * references.<br>
	 * 
	 * @return Bash script lines to build the qiime_config file
	 * @throws Exception if errors occur build file
	 */
	protected List<String> buildQiimeConfigLines() throws Exception
	{
		final List<String> lines = new ArrayList<>();
		if( getDB() != null )
		{
			lines.add( "echo '" + QIIME_CONFIG_SEQ_REF + " " + getDB( QIIME_REF_SEQ_DB ) + "' > " + QIIME_CONFIG );
			lines.add( "echo '" + QIIME_CONFIG_PYNAST_ALIGN_REF + " " + getDB( QIIME_PYNAST_ALIGN_DB ) + "' >> "
					+ QIIME_CONFIG );
			lines.add(
					"echo '" + QIIME_CONFIG_TAXA_SEQ_REF + " " + getDB( QIIME_REF_SEQ_DB ) + "' >> " + QIIME_CONFIG );
			lines.add( "echo '" + QIIME_CONFIG_TAXA_ID_REF + " " + getDB( QIIME_TAXA_DB ) + "' >> " + QIIME_CONFIG );
		}

		return lines;
	}

	/**
	 * Return the Docker container database directory (starting with /db/...)
	 * 
	 * @param prop QIIME database dir
	 * @return Docker container DB dir
	 * @throws Exception if errors occur
	 */
	protected File getDB( final String prop ) throws Exception
	{
		return new File( Config.getSystemFilePath( Config.requireString( this, prop ) )
				.replace( getDB().getAbsolutePath(), DockerUtil.CONTAINER_DB_DIR ) );
	}

	/**
	 * Module input directories are set to the previous module output directory.<br>
	 * To ensure we use the correct path, get path from {@link #getInputFiles()}
	 *
	 * @return File directory containing module input files
	 * @throws Exception if propagated by {@link #getInputFiles()}
	 */
	protected File getInputFileDir() throws Exception
	{
		final String inDir = getInputFiles().get( 0 ).getAbsolutePath();
		final int i = inDir.indexOf( File.separator + getInputFiles().get( 0 ).getName() );
		final File dir = new File( inDir.substring( 0, i ) );
		if( !dir.exists() )
		{
			throw new Exception( "Module input directory not found! --> " + dir.getAbsolutePath() );
		}

		return dir;
	}

	/**
	 * Subclasses call this method to check dependencies before picking OTUs to validate
	 * {@link biolockj.Config}.{@value #QIIME_PARAMS}
	 *
	 * @return Validated QIIME runtime parameters
	 * @throws Exception if {@value #QIIME_PARAMS} contains invalid parameters
	 */
	protected String getParams() throws Exception
	{
		if( switches == null )
		{
			final String params = BioLockJUtil.join( getClassifierParams() );
			if( params.indexOf( "-i " ) > -1 || params.indexOf( "--input_fp " ) > -1 )
			{
				throw new Exception( "INVALID CLASSIFIER OPTION (-i or --input_fp) FOUND IN PROPERTY (" + QIIME_PARAMS
						+ "). PLEASE REMOVE.  INPUT DETERMINED BY: " + Constants.INPUT_DIRS );
			}
			if( params.indexOf( "-o " ) > -1 || params.indexOf( "--output_dir " ) > -1 )
			{
				throw new Exception( "INVALID CLASSIFIER OPTION (-o or --output_dir) FOUND IN PROPERTY (" + QIIME_PARAMS
						+ "). PLEASE REMOVE THIS VALUE FROM PROPERTY FILE. " );
			}
			if( params.indexOf( "-a " ) > -1 || params.indexOf( "-O " ) > -1 )
			{
				throw new Exception( "INVALID CLASSIFIER OPTION (-a or -O) FOUND IN PROPERTY (" + QIIME_PARAMS
						+ "). BIOLOCKJ DERIVES THIS VALUE FROM: " + SCRIPT_NUM_THREADS );
			}
			if( params.indexOf( "-f " ) > -1 )
			{
				throw new Exception( "INVALID CLASSIFIER OPTION (-f or --force) FOUND IN PROPERTY (" + QIIME_PARAMS
						+ "). OUTPUT OPTIONS AUTOMATED BY BIOLOCKJ." );
			}

			switches = getRuntimeParams( getClassifierParams(), NUM_THREADS_PARAM );
			if( switches == null || switches.trim().isEmpty() )
			{
				throw new Exception( "No threads + no exe.classifierParams found!" );
			}

			Log.info( getClass(), "Set Qiime params: \"" + switches + "\"" );
		}

		return " " + switches;
	}

	/**
	 * Subclasses call this method to add OTU picking lines by calling {@value #SCRIPT_ADD_LABELS} via OTU picking
	 * script. Sleep for 5 seconds before running so that freshly created batch fasta files and mapping files can be
	 * found on the file system.
	 *
	 * @param otuPickingScript QIIME script
	 * @param fastaDir Fasta File directory
	 * @param mapping File-path of mapping file
	 * @param outputDir Directory to output {@value #COMBINED_FNA}
	 * @return 2 script lines for the bash script
	 * @throws Exception if errors occur
	 */
	protected List<String> getPickOtuLines( final String otuPickingScript, final File fastaDir, final String mapping,
			final File outputDir ) throws Exception
	{
		final List<String> lines = new ArrayList<>();
		lines.add( "sleep 5s" );
		lines.add( SCRIPT_ADD_LABELS + " -n 1 -i " + fastaDir.getAbsolutePath() + " -m " + mapping + " -c "
				+ Constants.QIIME_DEMUX_COL + " -o " + outputDir.getAbsolutePath() );
		final String fnaFile = outputDir + File.separator + COMBINED_FNA;
		lines.add( otuPickingScript + getParams() + "-i " + fnaFile + " -fo " + outputDir );
		return lines;
	}

	/**
	 * Return runtime parameters for {@value #EXE_VSEARCH_PARAMS}
	 * 
	 * @return Vsearch runtime parameters
	 * @throws Exception if errors occur
	 */
	protected String getVsearchParams() throws Exception
	{
		return " " + getRuntimeParams( Config.getList( this, EXE_VSEARCH_PARAMS ), VSEARCH_NUM_THREADS_PARAM );
	}

	/**
	 * Typically we verify no duplicate file names are used, but for QIIME we may be combining multiple files with the
	 * same name ({@value #OTU_TABLE}), so QiimeClassifier skips this validation.
	 */
	@Override
	protected void validateFileNameUnique( final Set<String> fileNames, final File file ) throws Exception
	{
		// Not needed for QIIME. Multiple file named otu_table.biom & others exist.
	}

	private String getAlphaDiversityMetrics() throws Exception
	{
		final StringBuffer sb = new StringBuffer();
		final Iterator<String> metrics = Config.requireList( this, Constants.QIIME_ALPHA_DIVERSITY_METRICS ).iterator();
		sb.append( metrics.next() );
		while( metrics.hasNext() )
		{
			sb.append( "," ).append( metrics.next() );
		}
		return sb.toString();
	}

	private String getLowestQiimeTaxaLevel() throws Exception
	{
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.SPECIES ) )
		{
			return "7";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.GENUS ) )
		{
			return "6";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.FAMILY ) )
		{
			return "5";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.ORDER ) )
		{
			return "4";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.CLASS ) )
		{
			return "3";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.PHYLUM ) )
		{
			return "2";
		}
		if( TaxaUtil.bottomTaxaLevel().equals( Constants.DOMAIN ) )
		{
			return "1";
		}

		throw new Exception( "Should not be possible to reach this error, value based on required field: "
				+ Constants.REPORT_TAXONOMY_LEVELS );

	}

	private String switches = null;

	/**
	 * Value output by {@value #SCRIPT_CALC_ALPHA_DIVERSITY} for null values: {@value #ALPHA_DIV_NULL_VALUE}
	 */
	protected static final String ALPHA_DIV_NULL_VALUE = "N/A";

	/**
	 * File produced by QIIME {@value #SCRIPT_CALC_ALPHA_DIVERSITY} script: {@value #ALPHA_DIVERSITY_TABLE}
	 */
	protected static final String ALPHA_DIVERSITY_TABLE = "alphaDiversity" + TXT_EXT;

	/**
	 * Multiplexed fasta file produced by QIIME {@value #SCRIPT_ADD_LABELS} script: {@value #COMBINED_FNA}
	 */
	protected static final String COMBINED_FNA = "combined_seqs.fna";

	/**
	 * {@link biolockj.Config} property for vsearch exectuable used for chimera detection: {@value #EXE_VSEARCH}
	 */
	protected static final String EXE_VSEARCH = "exe.vsearch";

	/**
	 * {@link biolockj.Config} property for {@value #EXE_VSEARCH} parameters (such as alternate reference database
	 * path): {@value #EXE_VSEARCH_PARAMS}
	 */
	protected static final String EXE_VSEARCH_PARAMS = "exe.vsearchParams";

	/**
	 * File produced by QIIME {@value #SCRIPT_SUMMARIZE_BIOM} script: {@value #OTU_SUMMARY_FILE}
	 */
	protected static final String OTU_SUMMARY_FILE = "otuSummary" + TXT_EXT;

	/**
	 * File produced by OTU picking scripts holding read taxonomy assignments: {@value #OTU_TABLE}
	 */
	protected static final String OTU_TABLE = "otu_table.biom";

	/**
	 * {@link biolockj.Config} List property used to obtain the QIIME executable params
	 */
	protected static final String QIIME_PARAMS = "qiime.params";

	/**
	 * {@link biolockj.Config} File property to define ~/.qiime_config pynast_template_alignment_fp:
	 * {@value #QIIME_PYNAST_ALIGN_DB}
	 */
	protected static final String QIIME_PYNAST_ALIGN_DB = "qiime.pynastAlignDB";

	/**
	 * {@link biolockj.Config} File property to define ~/.qiime_config pick_otus_reference_seqs_fp and
	 * assign_taxonomy_reference_seqs_fp: {@value #QIIME_REF_SEQ_DB}
	 */
	protected static final String QIIME_REF_SEQ_DB = "qiime.refSeqDB";

	/**
	 * {@link biolockj.Config} boolean property to indicate if {@value #EXE_VSEARCH} is needed for chimera removal:
	 * {@value #QIIME_REMOVE_CHIMERAS}
	 */
	protected static final String QIIME_REMOVE_CHIMERAS = "qiime.removeChimeras";

	/**
	 * {@link biolockj.Config} File property to define ~/.qiime_config assign_taxonomy_id_to_taxonomy_fp:
	 * {@value #QIIME_TAXA_DB}
	 */
	protected static final String QIIME_TAXA_DB = "qiime.taxaDB";

	/**
	 * Directory created by {@value biolockj.module.classifier.r16s.QiimeDeNovoClassifier#PICK_OTU_SCRIPT} and
	 * {@value biolockj.module.classifier.r16s.QiimeOpenRefClassifier#PICK_OTU_SCRIPT}: {@value #REP_SET}
	 */
	protected static final String REP_SET = "rep_set";

	/**
	 * QIIME script to add {@value #ALPHA_DIVERSITY_TABLE} to the metadata file: {@value #SCRIPT_ADD_ALPHA_DIVERSITY}
	 */
	protected static final String SCRIPT_ADD_ALPHA_DIVERSITY = "add_alpha_to_mapping_file.py";

	/**
	 * QIIME script that produces {@value #COMBINED_FNA}, the multiplexed fasta file: {@value #SCRIPT_ADD_LABELS}
	 */
	protected static final String SCRIPT_ADD_LABELS = "add_qiime_labels.py";

	/**
	 * QIIME script that creates alpha diversity metrics file in output/{@value #ALPHA_DIVERSITY_TABLE}:
	 * {@value #SCRIPT_CALC_ALPHA_DIVERSITY}
	 */
	protected static final String SCRIPT_CALC_ALPHA_DIVERSITY = "alpha_diversity.py";

	/**
	 * QIIME script used to remove chimeras detected by {@value #EXE_VSEARCH}: {@value #SCRIPT_FILTER_OTUS}
	 */
	protected static final String SCRIPT_FILTER_OTUS = "filter_otus_from_otu_table.py";

	/**
	 * QIIME script to print environment configuration to qsub output file: {@value #SCRIPT_PRINT_CONFIG}
	 */
	protected static final String SCRIPT_PRINT_CONFIG = "print_qiime_config.py";

	/**
	 * Produces output/{@value #OTU_SUMMARY_FILE} summarizing dataset: {@value #SCRIPT_SUMMARIZE_BIOM}
	 */
	protected static final String SCRIPT_SUMMARIZE_BIOM = "biom summarize-table";

	/**
	 * QIIME script used to produce taxonomy-level reports in the module output directory:
	 * {@value #SCRIPT_SUMMARIZE_TAXA}
	 */
	protected static final String SCRIPT_SUMMARIZE_TAXA = "summarize_taxa.py";

	/**
	 * QIIME script {@value #SCRIPT_SUMMARIZE_TAXA} parameter used to suppress the output of biom files. BioLockJ
	 * BioLockJ parsers expect clear text files in the module output directory, so the biom files must be excluded.
	 */
	protected static final String SUMMARIZE_TAXA_SUPPRESS_BIOM = "suppress_biom_table_output";

	private static final String NORMALIZED_ALPHA = "_normalized_alpha";
	private static final String NORMALIZED_ALPHA_LABEL = "_alpha_label";
	private static final String NUM_THREADS_PARAM = "-aO";
	private static final String QIIME_CONFIG = DockerUtil.DOCKER_ROOT_HOME + "/.qiime_config";
	private static final String QIIME_CONFIG_PYNAST_ALIGN_REF = "pynast_template_alignment_fp";
	private static final String QIIME_CONFIG_SEQ_REF = "pick_otus_reference_seqs_fp";
	private static final String QIIME_CONFIG_TAXA_ID_REF = "assign_taxonomy_id_to_taxonomy_fp";
	private static final String QIIME_CONFIG_TAXA_SEQ_REF = "assign_taxonomy_reference_seqs_fp";
	private static final String UNDEFINED = "UNDEFINED";
	private static final String VSEARCH_NUM_THREADS_PARAM = "--threads";

	// OTHER SCRIPT THAT MAY BE ADDED IN THE FUTURE
	// public static final String VALIDATED_MAPPING = "_corrected.txt";
	// private static final String OTUS_TREE_97 = "97_otus.tree";
	// private static final String TAXA_TREE = "taxa.tre";
	// private static final String SCRIPT_FILTER_TREE = "filter_tree.py -i ";
	// private static final String DIFF_OTU_SUMMARY = "differential_otu_summary.txt";
	// private static final String SCRIPT_DIFF_ABUNDANCE = "differential_abundance.py -i ";
	// private static final String SCRIPT_COMP_CORE_MB = "compute_core_microbiome.py ";

}
