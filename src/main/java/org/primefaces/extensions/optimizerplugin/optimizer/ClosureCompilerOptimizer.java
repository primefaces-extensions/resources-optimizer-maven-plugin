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
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
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

    @Override
    public void optimize(final ResourcesSetAdapter rsAdapter, final Log log) throws MojoExecutionException {
        ResourcesSetJsAdapter rsa = (ResourcesSetJsAdapter) rsAdapter;
        CompilationLevel compLevel = rsa.getCompilationLevel();
        CompilerOptions options = new CompilerOptions();
        compLevel.setOptionsForCompilationLevel(options);

        WarningLevel warnLevel = rsa.getWarningLevel();
        warnLevel.setOptionsForWarningLevel(options);

        LanguageMode langIn = rsa.getLanguageIn();
        options.setLanguageIn(langIn);

        LanguageMode langOut = rsa.getLanguageOut();
        options.setLanguageOut(langOut);
        Compiler.setLoggingLevel(Level.WARNING);

        try {
            Charset cset = Charset.forName(rsa.getEncoding());

            if (rsa.getAggregation() == null) {
                // no aggregation
                for (File file : rsa.getFiles()) {
                    log.info("Optimize JS file " + file.getName() + " ...");

                    // statistic
                    addToOriginalSize(file);

                    // path of the original file
                    String path = file.getCanonicalPath();

                    String outputFilePath = null;
                    String outputSourceMapDir = null;
                    File sourceMapFile = null;
                    File sourceFile;

                    if (rsa.getSourceMap() != null) {
                        // setup source map
                        outputFilePath = file.getCanonicalPath();
                        outputSourceMapDir = rsa.getSourceMap().getOutputDir();
                        sourceMapFile = setupSourceMapFile(options, rsa.getSourceMap(), outputFilePath);
                        // create an empty file with ...source.js from the original one
                        sourceFile = getFileWithSuffix(path, OUTPUT_FILE_SUFFIX);

                        if (StringUtils.isNotBlank(rsa.getSuffix())) {
                            // rename original file as ...source.js
                            FileUtils.rename(file, sourceFile);
                        } else {
                            // copy content of the original file to the ...source.js
                            FileUtils.copyFile(file, sourceFile);
                        }
                    } else {
                        sourceFile = file;
                    }

                    // compile
                    List<SourceFile> interns = new ArrayList<SourceFile>();
                    interns.add(SourceFile.fromFile(sourceFile, cset));
                    Compiler compiler = compile(log, interns, options, rsa.isFailOnWarning());

                    if (StringUtils.isNotBlank(rsa.getSuffix())) {
                        // write compiled content into the new file
                        File outputFile = getFileWithSuffix(path, rsa.getSuffix());
                        Files.write(compiler.toSource(), outputFile, cset);

                        if (sourceMapFile != null) {
                            // write sourceMappingURL into the minified file
                            writeSourceMappingURL(outputFile, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(), cset, log);
                        }

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

                        if (sourceMapFile != null) {
                            // write sourceMappingURL into the minified file
                            writeSourceMappingURL(file, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(), cset, log);
                        }

                        // statistic
                        addToOptimizedSize(file);
                    }

                    if (outputFilePath != null && sourceMapFile != null) {
                        // write the source map
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), outputSourceMapDir, log);

                        // move the source file to the source map dir
                        moveToSourceMapDir(sourceFile, outputSourceMapDir, log);
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
                    if (rsa.getSourceMap() != null) {
                        // setup source map
                        outputFilePath = outputFile.getCanonicalPath();
                        sourceMapFile = setupSourceMapFile(options, rsa.getSourceMap(), outputFilePath);
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

                    if (outputFilePath != null && sourceMapFile != null) {
                        // write sourceMappingURL into the minified file
                        writeSourceMappingURL(outputFile, sourceMapFile, rsa.getSourceMap().getSourceMapRoot(), cset, log);

                        // write the source map
                        String outputSourceMapDir = rsa.getSourceMap().getOutputDir();
                        Files.touch(sourceMapFile);
                        writeSourceMap(sourceMapFile, outputFilePath, compiler.getSourceMap(), outputSourceMapDir, log);

                        // move the source file
                        moveToSourceMapDir(aggrOutputFile, outputSourceMapDir, log);
                    } else {
                        // delete the temp. aggregated file ...source.js
                        if (aggrOutputFile.exists() && !aggrOutputFile.delete()) {
                            log.warn("Temporary file " + aggrOutputFile.getName() + " could not be deleted.");
                        }
                    }

                    // statistic
                    addToOptimizedSize(sizeBefore - outputFile.length());
                } else {
                    // delete single files if necessary
                    deleteFilesIfNecessary(rsa, log);

                    // rename aggregated file if necessary
                    renameOutputFileIfNecessary(rsa, aggrOutputFile);

                    // statistic
                    addToOptimizedSize(sizeBefore);
                }
            } else {
                // should not happen
                log.error("Wrong plugin's internal state.");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Resources optimization failure: " + e.getLocalizedMessage(), e);
        }
    }

    protected Compiler compile(Log log, List<SourceFile> interns, CompilerOptions options, boolean failOnWarning)
    throws MojoExecutionException {
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

    private File setupSourceMapFile(CompilerOptions options, org.primefaces.extensions.optimizerplugin.model.SourceMap sourceMap,
                                    String outputFilePath) throws IOException {
        // set options for source map
        options.setSourceMapDetailLevel(SourceMap.DetailLevel.valueOf(sourceMap.getDetailLevel()));
        options.setSourceMapFormat(SourceMap.Format.valueOf(sourceMap.getFormat()));

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

    private void writeSourceMap(File sourceMapFile, String sourceFileName, SourceMap sourceMap, String outputDir, Log log) {
        try {
            FileWriter out = new FileWriter(sourceMapFile);
            sourceMap.appendTo(out, sourceFileName);
            out.flush();
            IOUtil.close(out);
        } catch (Exception e) {
            log.error("Failed to write an JavaScript Source Map file for " + sourceFileName, e);
        }

        // move the file
        moveToSourceMapDir(sourceMapFile, outputDir, log);
    }

    private void moveToSourceMapDir(File file, String outputDir, Log log) {
        try {
            String name = file.getName();
            String target = outputDir + name;
            File targetFile = new File(target);
            Files.createParentDirs(targetFile);
            Files.move(file, targetFile);
        } catch (Exception e) {
            log.error("File " + file + " could not be moved to " + outputDir, e);
        }
    }

    private void writeSourceMappingURL(File minifiedFile, File sourceMapFile, String sourceMapRoot, Charset cset, Log log) {
        try {
            // write sourceMappingURL
            String smRoot = (sourceMapRoot != null ? sourceMapRoot : "");
            Files.append(System.getProperty("line.separator"), minifiedFile, cset);
            Files.append("//# sourceMappingURL=" + smRoot + sourceMapFile.getName(), minifiedFile, cset);
        } catch (IOException e) {
            log.error("//# sourceMappingURL for the minified file " + minifiedFile + " could not be written", e);
        }
    }
}
