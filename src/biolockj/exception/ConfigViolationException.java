/**
 * @UNCC Fodor Lab
 * @author Michael Sioda
 * @email msioda@uncc.edu
 * @date Mar 18, 2018
 * @disclaimer This code is free software; you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version, provided that any use properly credits the author. This program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details at http://www.gnu.org *
 */
package biolockj.exception;

/**
 * ConfigViolationException is thrown at runtime due to application state violations caused by invalid module I/O
 * (sequences or metadata) as defined by {@link biolockj.Config} properties. Exceptions of this nature can be resolved
 * by modifying {@link biolockj.Config} properties to more permissive values, but are intended to enforce process
 * requirements so are meant to highlight harder to detect issues.
 */
public class ConfigViolationException extends ConfigException
{
	/**
	 * Generic exception message.
	 * 
	 * @param msg Error message
	 */
	public ConfigViolationException( final String msg )
	{
		super( msg );
	}

	/**
	 * ConfigViolationException is thrown application detects a violation due to a {@link biolockj.Config} property
	 * setting, such as finding Sample IDs with no corresponding sequence file enforced by
	 * {@link biolockj.Config}.{@value biolockj.util.MetaUtil#USE_EVERY_ROW} = {@value biolockj.Constants#TRUE}
	 *
	 * @param property {@link biolockj.Config} property name
	 * @param msg Exception message details
	 */
	public ConfigViolationException( final String property, final String msg )
	{
		super( property, msg );
	}

	private static final long serialVersionUID = -5659243602699272132L;
}
