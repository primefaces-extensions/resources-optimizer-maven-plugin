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
import java.util.Set;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.WarningLevel;

/**
 * Container class containing all needed infos for a resource set.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public class ResourcesSetAdapter {

	private File inputDir;

	private Set<File> files;

	private CompilationLevel compilationLevel;

	private WarningLevel warningLevel;

	private Aggregation aggregation;

	private String encoding;

	private boolean failOnWarning;

	private String suffix;

	public ResourcesSetAdapter(final File inputDir, final Set<File> files, final CompilationLevel compilationLevel,
	                           final WarningLevel warningLevel, final Aggregation aggregation, final String encoding,
	                           final boolean failOnWarning, final String suffix) {
		this.inputDir = inputDir;
		this.files = files;
		this.compilationLevel = compilationLevel;
		this.warningLevel = warningLevel;
		this.aggregation = aggregation;
		this.encoding = encoding;
		this.failOnWarning = failOnWarning;
		this.suffix = suffix;
	}

	public File getInputDir() {
		return inputDir;
	}

	public Set<File> getFiles() {
		return files;
	}

	public CompilationLevel getCompilationLevel() {
		return compilationLevel;
	}

	public WarningLevel getWarningLevel() {
		return warningLevel;
	}

	public Aggregation getAggregation() {
		return aggregation;
	}

	public String getEncoding() {
		return encoding;
	}

	public boolean isFailOnWarning() {
		return failOnWarning;
	}

	public String getSuffix() {
		return suffix;
	}
}
