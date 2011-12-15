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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import com.google.common.io.Files;

/**
 * Basis abstract class for Google Closure Compiler / YUI Compressor Optimizers.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public abstract class AbstractOptimizer {

	private static final String AGGREGATED_FILE_EXTENSION = ".aggr";

	private long sizeTotalOriginal = 0;

	private long sizeTotalOptimized = 0;

	public abstract void optimize(final ResourcesSetAdapter rsa, final Log log) throws MojoExecutionException;

	protected File getFileWithSuffix(final String path, final String suffix) throws IOException {
		// get file extension
		String extension = FileUtils.extension(path);
		if (StringUtils.isNotEmpty(extension)) {
			extension = "." + extension;
		}

		// path of file with suffix
		String pathSuffix = FileUtils.removeExtension(path) + suffix + extension;

		// create a new file with suffix
		File outputFile = new File(pathSuffix);
		Files.touch(outputFile);

		return outputFile;
	}

	protected File aggregateFiles(final ResourcesSetAdapter rsa, final Charset cset, final Log log) throws IOException {
		int filesCount = rsa.getFiles().size();
		if (rsa.getAggregation().getPrependedFile() != null) {
			filesCount++;
		}

		if (filesCount > 1) {
			log.info("Aggregation is running ...");
		}

		File outputFile = getOutputFile(rsa);
		if (rsa.getAggregation().getPrependedFile() != null) {
			// write / append to be prepended file into / to the output file
			prependFile(rsa.getAggregation().getPrependedFile(), outputFile, cset, rsa.getEncoding());
		}

		for (File file : rsa.getFiles()) {
			InputStreamReader in = new InputStreamReader(new FileInputStream(file), rsa.getEncoding());
			StringWriter writer = new StringWriter();
			IOUtil.copy(in, writer);

			// write / append compiled content into / to the new file
			Files.append(writer.toString(), outputFile, cset);
			IOUtil.close(in);
		}

		if (filesCount > 1) {
			log.info(filesCount + " files were successfully aggregated.");
		}

		return outputFile;
	}

	protected void deleteFilesIfNecessary(final ResourcesSetAdapter rsa, final Log log) {
		if (rsa.getAggregation().isRemoveIncluded() && rsa.getFiles().size() > 0) {
			for (File file : rsa.getFiles()) {
				if (file.exists() && !file.delete()) {
					log.warn("File " + file.getName() + " could not be deleted after aggregation.");
				}
			}
		}
	}

	protected void renameOutputFileIfNecessary(final ResourcesSetAdapter rsa, final File outputFile) throws IOException {
		if (!rsa.getAggregation().isSubDirMode()) {
			return;
		}

		if (outputFile != null && outputFile.exists()) {
			FileUtils.rename(outputFile, rsa.getAggregation().getOutputFile());
		}
	}

	protected void prependFile(final File prependedFile, final File outputFile, final Charset cset, final String encoding)
	    throws IOException {
		InputStreamReader in = new InputStreamReader(new FileInputStream(prependedFile), encoding);
		StringWriter writer = new StringWriter();
		IOUtil.copy(in, writer);

		// write / append compiled content into / to the new file
		Files.append(writer.toString(), outputFile, cset);
		IOUtil.close(in);
	}

	protected File getOutputFile(final ResourcesSetAdapter rsa) throws IOException {
		File outputFile = rsa.getAggregation().getOutputFile();
		if (rsa.getAggregation().isSubDirMode()) {
			// prevent overwriting of existing CSS or JS file with the same name as the output file
			File aggrFile = new File(FileUtils.removeExtension(outputFile.getCanonicalPath()) + AGGREGATED_FILE_EXTENSION);
			Files.createParentDirs(aggrFile);
			Files.touch(aggrFile);

			return aggrFile;
		}

		Files.createParentDirs(outputFile);
		Files.touch(outputFile);

		return outputFile;
	}

	protected void addToOriginalSize(final File file) {
		sizeTotalOriginal = sizeTotalOriginal + file.length();
	}

	protected void addToOptimizedSize(final File file) {
		sizeTotalOptimized = sizeTotalOptimized + file.length();
	}

	protected void addToOptimizedSize(final long size) {
		sizeTotalOptimized = sizeTotalOptimized + size;
	}

	protected long getTotalOriginalSize() {
		return sizeTotalOriginal;
	}

	protected long getTotalOptimizedSize() {
		return sizeTotalOptimized;
	}
}
