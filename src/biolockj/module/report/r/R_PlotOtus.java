/**
 * @UNCC Fodor Lab
 * @author Michael Sioda
 * @email msioda@uncc.edu
 * @date Feb 18, 2017
 * @disclaimer This code is free software; you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version, provided that any use properly credits the author. This program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details at http://www.gnu.org *
 */
package biolockj.module.report.r;

import java.util.List;
import java.util.TreeSet;
import biolockj.module.ScriptModule;

/**
 * This BioModule is used to build the R script used to generate OTU-metadata box-plots and scatter-plots for each
 * report field and taxonomy level.
 */
public class R_PlotOtus extends R_Module implements ScriptModule
{
	@Override
	public void checkDependencies() throws Exception
	{
		super.checkDependencies();
	}

	/**
	 * Returns {@link #getStatPreReqs()}
	 */
	@Override
	public List<String> getPreRequisiteModules() throws Exception
	{
		return getStatPreReqs();
	}

	/**
	 * Return the set of file extensions available for download by {@link biolockj.util.DownloadUtil} Add
	 * {@value #PDF_EXT} to super class set.
	 * 
	 * @return Set of file extensions
	 * @throws Exception if errors occur
	 */
	@Override
	public TreeSet<String> scpExtensions() throws Exception
	{
		final TreeSet<String> set = super.scpExtensions();
		set.add( PDF_EXT.substring( 1 ) );
		return set;
	}
}