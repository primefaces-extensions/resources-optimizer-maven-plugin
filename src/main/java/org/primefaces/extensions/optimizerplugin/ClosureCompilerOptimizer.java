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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import com.google.common.io.Files;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.WarningLevel;

/**
 * Class for Google Closure Compiler doing JavaScript optimization.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.1
 */
public class ClosureCompilerOptimizer extends AbstractOptimizer {

	// Note: empty externs, probably CommandLineRunner.getDefaultExterns() would be better
	private static final List<JSSourceFile> EXTERNS_EMPTY = new ArrayList<JSSourceFile>();

	private static final String OPTIMIZED_FILE_EXTENSION = ".optjs";

	@Override
	public void optimize(final ResourcesSetAdapter rsa, final Log log) throws MojoExecutionException {
		CompilationLevel compLevel = rsa.getCompilationLevel();
		CompilerOptions options = new CompilerOptions();
		compLevel.setOptionsForCompilationLevel(options);

		WarningLevel warnLevel = rsa.getWarningLevel();
		warnLevel.setOptionsForWarningLevel(options);
		com.google.javascript.jscomp.Compiler.setLoggingLevel(Level.WARNING);

		try {
			Charset cset = Charset.forName(rsa.getEncoding());

			if (rsa.getAggregation() == null) {
				// no aggregation
				for (File file : rsa.getFiles()) {
					log.info("Optimize JS file " + file.getName() + " ...");
					addToOriginalSize(file);

					JSSourceFile jsSourceFile = JSSourceFile.fromFile(file, cset);
					List<JSSourceFile> interns = new ArrayList<JSSourceFile>();
					interns.add(jsSourceFile);

					// compile
					Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

					// generate output
					String path = file.getCanonicalPath();
					if (StringUtils.isNotBlank(rsa.getSuffix())) {
						// write compiled content into the new file
						File outputFile = getFileWithSuffix(path, rsa.getSuffix());
						Files.write(compiler.toSource(), outputFile, cset);

						// statistic
						addToOptimizedSize(outputFile);
					} else {
						// path of temp. file
						String pathOptimized = FileUtils.removeExtension(path) + OPTIMIZED_FILE_EXTENSION;

						// create a new temp. file
						File outputFile = new File(pathOptimized);
						Files.touch(outputFile);

						// write compiled content into the new file and rename it (overwrite the original file)
						Files.write(compiler.toSource(), outputFile, cset);
						FileUtils.rename(outputFile, file);

						// statistic
						addToOptimizedSize(file);
					}
				}
			} else if (rsa.getAggregation().getOutputFile() != null) {
				// aggregation to one output file
				File outputFile;

				if (!rsa.getAggregation().isWithoutCompress()) {
					// with compressing before aggregation
					List<JSSourceFile> interns = new ArrayList<JSSourceFile>();
					for (File file : rsa.getFiles()) {
						log.info("Optimize JS file " + file.getName() + " ...");
						addToOriginalSize(file);

						interns.add(JSSourceFile.fromFile(file, cset));
					}

					// compile
					Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

					int filesCount = rsa.getFiles().size();
					if (rsa.getAggregation().getPrependedFile() != null) {
						filesCount++;
					}

					if (filesCount > 1) {
						log.info("Aggregation is running ...");
					}

					// get right output file
					outputFile = getOutputFile(rsa);

					long sizeBefore = outputFile.length();

					if (rsa.getAggregation().getPrependedFile() != null) {
						// write / append to be prepended file into / to the output file
						prependFile(rsa.getAggregation().getPrependedFile(), outputFile, cset, rsa.getEncoding());
					}

					// write / append compiled content into / to the output file
					Files.append(compiler.toSource(), outputFile, cset);

					// statistic
					addToOptimizedSize(outputFile.length() - sizeBefore);

					if (filesCount > 1) {
						log.info(filesCount + " files were successfully aggregated.");
					}
				} else {
					// only aggregation without compressing
					outputFile = aggregateFiles(rsa, cset, log);
				}

				// delete single files if necessary
				deleteFilesIfNecessary(rsa, log);

				// rename aggregated file if necessary
				renameOutputFileIfNecessary(rsa, outputFile);
			} else {
				// should not happen
				log.error("Wrong plugin's internal state.");
			}
		} catch (Exception e) {
			throw new MojoExecutionException("Resources optimization failure: " + e.getLocalizedMessage(), e);
		}
	}

	protected Compiler compile(final Log log, final List<JSSourceFile> interns, final CompilerOptions options,
	                           final boolean failOnWarning) throws MojoExecutionException {
		// compile
		com.google.javascript.jscomp.Compiler compiler = new Compiler();
		Result result = compiler.compile(EXTERNS_EMPTY, interns, options);

		// evaluate result
		evalResult(result, log, failOnWarning);

		return compiler;
	}

	protected void evalResult(final Result result, final Log log, final boolean failOnWarning) throws MojoExecutionException {
		if (result.warnings != null) {
			for (JSError warning : result.warnings) {
				log.warn(warning.toString());
			}
		}

		if (result.warnings != null && result.warnings.length > 0 && failOnWarning) {
			throw new MojoExecutionException("Resources optimization failure. Please fix warnings and try again.");
		}

		if (result.errors != null) {
			for (JSError error : result.errors) {
				log.error(error.toString());
			}
		}

		if (result.errors != null && result.errors.length > 0) {
			throw new MojoExecutionException("Resources optimization failure. Please fix errors and try again.");
		}

		if (!result.success) {
			throw new MojoExecutionException("Resources optimization failure. Please fix errors and try again.");
		}
	}
}
