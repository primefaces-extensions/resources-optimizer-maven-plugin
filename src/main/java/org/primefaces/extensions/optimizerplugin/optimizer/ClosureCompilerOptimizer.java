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

package org.primefaces.extensions.optimizerplugin.optimizer;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.WarningLevel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetAdapter;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetJsAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Class for Google Closure Compiler doing JavaScript optimization.
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class ClosureCompilerOptimizer extends AbstractOptimizer {

    // Note: empty externs, probably CommandLineRunner.getDefaultExterns() would be better
    private static final List<SourceFile> EXTERNS_EMPTY = new ArrayList<SourceFile>();

    private static final String OPTIMIZED_FILE_EXTENSION = ".optjs";

    private static final String SOURCE_MAP_FILE_EXTENSION = ".map";

    private String sourceMapDir;

    @Override
    public void optimize(final ResourcesSetAdapter rsAdapter, final Log log) throws MojoExecutionException {
        ResourcesSetJsAdapter rsa = (ResourcesSetJsAdapter) rsAdapter;
        CompilationLevel compLevel = rsa.getCompilationLevel();
        CompilerOptions options = new CompilerOptions();
        compLevel.setOptionsForCompilationLevel(options);

        WarningLevel warnLevel = rsa.getWarningLevel();
        warnLevel.setOptionsForWarningLevel(options);
        Compiler.setLoggingLevel(Level.WARNING);

        try {
            Charset cset = Charset.forName(rsa.getEncoding());

            if (rsa.getAggregation() == null) {
                // no aggregation
                for (File file : rsa.getFiles()) {
                    log.info("Optimize JS file " + file.getName() + " ...");

                    // statistic
                    addToOriginalSize(file);

                    String outputFilePath = null;
                    File sourceMapFile = null;
                    if (rsa.isCreateSourceMap()) {
                        // setup source map
                        outputFilePath = file.getCanonicalPath();
                        sourceMapFile = setupSourceMapFile(options, outputFilePath);
                    }

                    // compile
                    List<SourceFile> interns = new ArrayList<SourceFile>();
                    interns.add(SourceFile.fromFile(file, cset));
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

                    if (outputFilePath != null && sourceMapFile != null) {
                        // write the source map
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), log);
                    }
                }
            } else if (rsa.getAggregation().getOutputFile() != null) {
                // aggregation to one output file
                File outputFile = rsa.getAggregation().getOutputFile();
                File aggrOutputFile = aggregateFiles(rsa, cset, true);

                // statistic
                long sizeBefore = addToOriginalSize(aggrOutputFile);

                if (!rsa.getAggregation().isWithoutCompress()) {
                    // compressing
                    for (File file : rsa.getFiles()) {
                        log.info("Optimize JS file " + file.getName() + " ...");
                    }

                    String outputFilePath = null;
                    File sourceMapFile = null;
                    if (rsa.isCreateSourceMap()) {
                        // setup source map
                        outputFilePath = outputFile.getCanonicalPath();
                        sourceMapFile = setupSourceMapFile(options, outputFilePath);
                    }

                    // compile
                    List<SourceFile> interns = new ArrayList<SourceFile>();
                    interns.add(SourceFile.fromFile(aggrOutputFile, cset));
                    Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

                    // delete single files if necessary
                    deleteFilesIfNecessary(rsa, log);

                    // write the compiled content into a new file
                    Files.touch(outputFile);
                    Files.write(compiler.toSource(), outputFile, cset);

                    // statistic
                    addToOptimizedSize(sizeBefore - outputFile.length());

                    if (outputFilePath != null && sourceMapFile != null) {
                        // write the source map
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), log);

                        // move the source file
                        moveToSourceMapDir(aggrOutputFile, log);
                    } else {
                        // delete the temp. aggregated file ...source.js
                        if (aggrOutputFile.exists() && !aggrOutputFile.delete()) {
                            log.warn("Temporary file " + aggrOutputFile.getName() + " could not be deleted.");
                        }
                    }
                } else {
                    // statistic
                    addToOptimizedSize(sizeBefore);

                    // delete single files if necessary
                    deleteFilesIfNecessary(rsa, log);

                    // rename aggregated file if necessary
                    renameOutputFileIfNecessary(rsa, aggrOutputFile);
                }
            } else {
                // should not happen
                log.error("Wrong plugin's internal state.");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Resources optimization failure: " + e.getLocalizedMessage(), e);
        }
    }

    public void setSourceMapDir(String sourceMapDir) {
        this.sourceMapDir = sourceMapDir;
    }

    protected Compiler compile(Log log, List<SourceFile> interns, CompilerOptions options,
                               boolean failOnWarning) throws MojoExecutionException {
        // compile
        Compiler compiler = new Compiler();
        Result result = compiler.compile(EXTERNS_EMPTY, interns, options);

        // evaluate result
        evalResult(result, log, failOnWarning);

        return compiler;
    }

    protected void evalResult(Result result, Log log, boolean failOnWarning) throws MojoExecutionException {
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

    private File setupSourceMapFile(CompilerOptions options, String outputFilePath) throws IOException {
        // set options for source map
        options.setSourceMapDetailLevel(SourceMap.DetailLevel.ALL);
        options.setSourceMapFormat(SourceMap.Format.V3);

        File sourceMapFile = new File(outputFilePath + SOURCE_MAP_FILE_EXTENSION);
        options.setSourceMapOutputPath(sourceMapFile.getCanonicalPath());

        String prefix = outputFilePath.substring(0, outputFilePath.lastIndexOf(File.separator) + 1);
        // Replace backslashes (the file separator used on Windows systems).
        // This is needed due to the same code in SourceMap.java
        if (File.separatorChar == '\\') {
            prefix = prefix.replace('\\', '/');
        }

        List<SourceMap.LocationMapping> sourceMapLocationMappings =
                ImmutableList.of(new SourceMap.LocationMapping(prefix, ""));
        options.setSourceMapLocationMappings(sourceMapLocationMappings);

        return sourceMapFile;
    }

    private void writeSourceMap(File sourceMapFile, String sourceFileName, SourceMap sourceMap, Log log) {
        try {
            FileWriter out = new FileWriter(sourceMapFile);
            sourceMap.appendTo(out, sourceFileName);
            out.flush();
            IOUtil.close(out);
        } catch (Exception e) {
            log.error("Failed to write an JavaScript Source Map file for " + sourceFileName, e);
        }

        // move the file
        moveToSourceMapDir(sourceMapFile, log);
    }

    private void moveToSourceMapDir(File sourceMapFile, Log log) {
        try {
            String name = sourceMapFile.getName();
            String target = sourceMapDir + name;
            File targetFile = new File(target);
            Files.createParentDirs(targetFile);
            Files.move(sourceMapFile, targetFile);
        } catch (Exception e) {
            log.error("File " + sourceMapFile + " could not be moved to " + sourceMapDir, e);
        }
    }
}
