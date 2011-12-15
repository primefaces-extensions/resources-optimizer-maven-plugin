/*
 * Copyright 2011 PrimeFaces Extensions.
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

/**
 * Scans JavaScript and CSS resources by specifing includes / excludes and prepares two sets with file objects.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public class ResourcesScanner {

	public static final String CSS_FILE_EXTENSION = "css";

	public static final String JS_FILE_EXTENSION = "js";

	private Set<File> jsFiles = new LinkedHashSet<File>();

	private Set<File> cssFiles = new LinkedHashSet<File>();

	public Set<File> getJsFiles() {
		return jsFiles;
	}

	public Set<File> getCssFiles() {
		return cssFiles;
	}

	public void scan(final File inputDir, final String[] includes, final String[] excludes) throws MojoExecutionException {
		try {
			if (inputDir.isFile()) {
				throw new MojoExecutionException("Config parameter 'inputDir' is wrong. " + inputDir.getAbsolutePath()
				                                 + " is not a directory");
			}

			DirectoryScanner directoryScanner = new DirectoryScanner();
			directoryScanner.setBasedir(inputDir);
			directoryScanner.setExcludes(excludes);

			for (String include : includes) {
				directoryScanner.setIncludes(new String[] {include});
				directoryScanner.scan();

				String[] fileNames = directoryScanner.getIncludedFiles();
				if (fileNames.length > 1) {
					// sort files by pathnames lexicographically
					Arrays.sort(fileNames);
				}

				for (String fileName : fileNames) {
					final String extension = FileUtils.extension(fileName);
					if (CSS_FILE_EXTENSION.equalsIgnoreCase(extension)) {
						cssFiles.add(new File(inputDir, fileName));
					} else if (JS_FILE_EXTENSION.equalsIgnoreCase(extension)) {
						jsFiles.add(new File(inputDir, fileName));
					}
				}
			}
		} catch (Exception e) {
			throw new MojoExecutionException("Error while scanning resources files under the input directory '"
			                                 + inputDir + "'", e);
		}
	}
}
