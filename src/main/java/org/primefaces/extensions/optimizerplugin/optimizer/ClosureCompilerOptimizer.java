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

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetAdapter;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetJsAdapter;

import com.google.common.collect.ImmutableList;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.WarningLevel;

/**
 * Class for Google Closure Compiler doing JavaScript optimization.
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class ClosureCompilerOptimizer extends AbstractOptimizer {

    private static final List<SourceFile> EXTERNS_EMPTY = new ArrayList<>();

    private static final String OPTIMIZED_FILE_EXTENSION = ".optjs";

    private static final String SOURCE_MAP_FILE_EXTENSION = ".map";

    @Override
    public void optimize(final ResourcesSetAdapter rsAdapter, final Log log) throws MojoExecutionException {
        final ResourcesSetJsAdapter rsa = (ResourcesSetJsAdapter) rsAdapter;
        final CompilationLevel compLevel = rsa.getCompilationLevel();
        final CompilerOptions options = new CompilerOptions();
        compLevel.setOptionsForCompilationLevel(options);

        final WarningLevel warnLevel = rsa.getWarningLevel();
        warnLevel.setOptionsForWarningLevel(options);

        final LanguageMode langIn = rsa.getLanguageIn();
        options.setLanguageIn(langIn);

        final LanguageMode langOut = rsa.getLanguageOut();
        options.setLanguageOut(langOut);
        Compiler.setLoggingLevel(Level.WARNING);

        try {
            final Charset cset = Charset.forName(rsa.getEncoding());

            if (rsa.getAggregation() == null) {
                // no aggregation
                for (final File file : rsa.getFiles()) {
                    log.info("Optimize JS file " + file.getName() + " ...");

                    // statistic
                    addToOriginalSize(file);

                    // path of the original file
                    final String path = file.getCanonicalPath();

                    String outputFilePath = null;
                    String outputSourceMapDir = null;
                    File sourceMapFile = null;
                    final File sourceFile;

                    if (rsa.getSourceMap() != null) {
                        // setup source map
                        outputFilePath = file.getCanonicalPath();
                        outputSourceMapDir = rsa.getSourceMap().getOutputDir();
                        sourceMapFile = setupSourceMapFile(options, rsa.getSourceMap(), outputFilePath);
                        // create an empty file with ...source.js from the original one
                        sourceFile = getFileWithSuffix(path, AbstractOptimizer.OUTPUT_FILE_SUFFIX);

                        if (StringUtils.isNotBlank(rsa.getSuffix())) {
                            // rename original file as ...source.js
                            FileUtils.rename(file, sourceFile);
                        }
                        else {
                            // copy content of the original file to the ...source.js
                            FileUtils.copyFile(file, sourceFile);
                        }
                    }
                    else {
                        sourceFile = file;
                    }

                    // compile
                    final List<SourceFile> interns = new ArrayList<>();
                    interns.add(SourceFile.fromPath(sourceFile.toPath(), cset));
                    final Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

                    if (StringUtils.isNotBlank(rsa.getSuffix())) {
                        // write compiled content into the new file
                        final File outputFile = getFileWithSuffix(path, rsa.getSuffix());
                        Files.asCharSink(outputFile, cset).write(compiler.toSource());

                        if (sourceMapFile != null) {
                            // write sourceMappingURL into the minified file
                            writeSourceMappingURL(outputFile, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(),
                                        cset, log);
                        }

                        // statistic
                        addToOptimizedSize(outputFile);
                    }
                    else {
                        // path of temp. file
                        final String pathOptimized = FileUtils.removeExtension(path) + OPTIMIZED_FILE_EXTENSION;

                        // create a new temp. file
                        final File outputFile = new File(pathOptimized);
                        Files.touch(outputFile);

                        // write compiled content into the new file and rename it (overwrite the original file)
                        Files.asCharSink(outputFile, cset).write(compiler.toSource());
                        FileUtils.rename(outputFile, file);

                        if (sourceMapFile != null) {
                            // write sourceMappingURL into the minified file
                            writeSourceMappingURL(file, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(), cset,
                                        log);
                        }

                        // statistic
                        addToOptimizedSize(file);
                    }

                    if (outputFilePath != null) {
                        // write the source map
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), outputSourceMapDir, log);

                        // move the source file to the source map dir
                        moveToSourceMapDir(sourceFile, outputSourceMapDir, log);
                    }
                }
            }
            else if (rsa.getAggregation().getOutputFile() != null) {
                // aggregation to one output file
                final File outputFile = rsa.getAggregation().getOutputFile();
                final File aggrOutputFile = aggregateFiles(rsa, cset, true);

                // statistic
                final long sizeBefore = addToOriginalSize(aggrOutputFile);

                if (!rsa.getAggregation().isWithoutCompress()) {
                    // compressing
                    for (final File file : rsa.getFiles()) {
                        log.info("Optimize JS file " + file.getName() + " ...");
                    }

                    String outputFilePath = null;
                    File sourceMapFile = null;
                    if (rsa.getSourceMap() != null) {
                        // setup source map
                        outputFilePath = outputFile.getCanonicalPath();
                        sourceMapFile = setupSourceMapFile(options, rsa.getSourceMap(), outputFilePath);
                    }

                    // compile
                    final List<SourceFile> interns = new ArrayList<>();
                    interns.add(SourceFile.fromPath(aggrOutputFile.toPath(), cset));
                    final Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

                    // delete single files if necessary
                    deleteFilesIfNecessary(rsa, log);
                    deleteDirectoryIfNecessary(rsa, log);

                    // write the compiled content into a new file
                    Files.touch(outputFile);
                    Files.asCharSink(outputFile, cset).write(compiler.toSource());

                    if (outputFilePath != null) {
                        // write sourceMappingURL into the minified file
                        writeSourceMappingURL(outputFile, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(), cset,
                                    log);

                        // write the source map
                        final String outputSourceMapDir = rsa.getSourceMap().getOutputDir();
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), outputSourceMapDir, log);

                        // move the source file
                        moveToSourceMapDir(aggrOutputFile, outputSourceMapDir, log);
                    }
                    else {
                        // delete the temp. aggregated file ...source.js
                        if (aggrOutputFile.exists() && !aggrOutputFile.delete()) {
                            log.warn("Temporary file " + aggrOutputFile.getName() + " could not be deleted.");
                        }
                    }

                    // statistic
                    addToOptimizedSize(sizeBefore - outputFile.length());
                }
                else {
                    // delete single files if necessary
                    deleteFilesIfNecessary(rsa, log);
                    deleteDirectoryIfNecessary(rsa, log);

                    // rename aggregated file if necessary
                    renameOutputFileIfNecessary(rsa, aggrOutputFile);

                    // statistic
                    addToOptimizedSize(sizeBefore);
                }
            }
            else {
                // should not happen
                log.error("Wrong plugin's internal state.");
            }
        }
        catch (final Exception e) {
            throw new MojoExecutionException("Resources optimization failure: " + e.getLocalizedMessage(), e);
        }
    }

    protected Compiler compile(final Log log, final List<SourceFile> interns, final CompilerOptions options,
                final boolean failOnWarning)
                throws MojoExecutionException {
        // compile
        final Compiler compiler = new Compiler();
        final Result result = compiler.compile(EXTERNS_EMPTY, interns, options);

        // evaluate result
        evalResult(result, log, failOnWarning);

        return compiler;
    }

    protected void evalResult(final Result result, final Log log, final boolean failOnWarning)
                throws MojoExecutionException {
        if (result.warnings != null) {
            for (final JSError warning : result.warnings) {
                log.warn(warning.toString());
            }
        }

        if (result.warnings != null && result.warnings.size() > 0 && failOnWarning) {
            throw new MojoExecutionException("Resources optimization failure. Please fix warnings and try again.");
        }

        if (result.errors != null) {
            for (final JSError error : result.errors) {
                log.error(error.toString());
            }
        }

        if (result.errors != null && result.errors.size() > 0) {
            throw new MojoExecutionException("Resources optimization failure. Please fix errors and try again.");
        }

        if (!result.success) {
            throw new MojoExecutionException("Resources optimization failure. Please fix errors and try again.");
        }
    }

    private File setupSourceMapFile(final CompilerOptions options,
                final org.primefaces.extensions.optimizerplugin.model.SourceMap sourceMap,
                final String outputFilePath) throws IOException {
        // set options for source map
        options.setSourceMapDetailLevel(SourceMap.DetailLevel.valueOf(sourceMap.getDetailLevel()));
        options.setSourceMapFormat(SourceMap.Format.valueOf(sourceMap.getFormat()));

        final File sourceMapFile = new File(outputFilePath + SOURCE_MAP_FILE_EXTENSION);
        options.setSourceMapOutputPath(sourceMapFile.getCanonicalPath());

        String prefix = outputFilePath.substring(0, outputFilePath.lastIndexOf(File.separator) + 1);
        // Replace backslashes (the file separator used on Windows systems).
        // This is needed due to the same code in SourceMap.java
        if (File.separatorChar == '\\') {
            prefix = prefix.replace('\\', '/');
        }

        final List<SourceMap.PrefixLocationMapping> sourceMapLocationMappings =
                    ImmutableList.of(new SourceMap.PrefixLocationMapping(prefix, ""));
        options.setSourceMapLocationMappings(sourceMapLocationMappings);

        return sourceMapFile;
    }

    private void writeSourceMap(final File sourceMapFile, final String sourceFileName, final SourceMap sourceMap,
                final String outputDir, final Log log) {
        try {
            final FileWriter out = new FileWriter(sourceMapFile);
            sourceMap.appendTo(out, sourceFileName);
            out.flush();
            IOUtil.close(out);
        }
        catch (final Exception e) {
            log.error("Failed to write an JavaScript Source Map file for " + sourceFileName, e);
        }

        // move the file
        moveToSourceMapDir(sourceMapFile, outputDir, log);
    }

    private void moveToSourceMapDir(final File file, final String outputDir, final Log log) {
        try {
            final String name = file.getName();
            final String target = outputDir + name;
            final File targetFile = new File(target);
            if (!file.equals(targetFile)) {
                Files.createParentDirs(targetFile);
                Files.move(file, targetFile);
            }
        }
        catch (final Exception e) {
            log.error("File " + file + " could not be moved to " + outputDir, e);
        }
    }

    private void writeSourceMappingURL(final File minifiedFile, final File sourceMapFile, final String sourceMapRoot,
                final Charset cset, final Log log) {
        try {
            // write sourceMappingURL
            final String smRoot = (sourceMapRoot != null ? sourceMapRoot : "");
            Files.asCharSink(minifiedFile, cset, FileWriteMode.APPEND).write(System.getProperty("line.separator"));
            Files.asCharSink(minifiedFile, cset, FileWriteMode.APPEND)
                        .write("//# sourceMappingURL=" + smRoot + sourceMapFile.getName());
        }
        catch (final IOException e) {
            log.error("//# sourceMappingURL for the minified file " + minifiedFile + " could not be written", e);
        }
    }
}
