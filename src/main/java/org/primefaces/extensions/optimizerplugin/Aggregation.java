/*
 * Copyright 2011-2015 PrimeFaces Extensions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */

package org.primefaces.extensions.optimizerplugin;

import java.io.File;

/**
 * Class representing an aggregation.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public class Aggregation {

	/**
	 * Input directory.
	 *
	 * @parameter
	 */
	private File inputDir;

	/**
	 * Aggregation per sub-folder. Names of aggregated files should be the same as their folder names where they are placed.
	 *
	 * @parameter
	 */
	private boolean subDirMode = false;

	/**
	 * Flag whether included original files must be removed.
	 *
	 * @parameter
	 */
	private boolean removeIncluded = true;

	/**
	 * Flag whether included files must be compressed or not.
	 *
	 * @parameter
	 */
	private boolean withoutCompress = false;

	/**
	 * Aggregation to one big file. Output file.
	 *
	 * @parameter
	 */
	private File outputFile;

	/**
	 * File to be prepended to the aggregated file.
	 *
	 * @parameter
	 */
	private File prependedFile;

	public File getInputDir() {
		return inputDir;
	}

	public void setInputDir(File inputDir) {
		this.inputDir = inputDir;
	}

	public boolean isSubDirMode() {
		return subDirMode;
	}

	public void setSubDirMode(final boolean subDirMode) {
		this.subDirMode = subDirMode;
	}

	public boolean isRemoveIncluded() {
		return removeIncluded;
	}

	public void setRemoveIncluded(final boolean removeIncluded) {
		this.removeIncluded = removeIncluded;
	}

	public boolean isWithoutCompress() {
		return withoutCompress;
	}

	public void setWithoutCompress(final boolean withoutCompress) {
		this.withoutCompress = withoutCompress;
	}

	public File getOutputFile() {
		return outputFile;
	}

	public void setOutputFile(final File outputFile) {
		this.outputFile = outputFile;
	}

	public File getPrependedFile() {
		return prependedFile;
	}

	public void setPrependedFile(final File prependedFile) {
		this.prependedFile = prependedFile;
	}
}
