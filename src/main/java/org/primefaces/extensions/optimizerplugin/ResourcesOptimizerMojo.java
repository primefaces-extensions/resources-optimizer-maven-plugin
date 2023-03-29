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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.WarningLevel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.primefaces.extensions.optimizerplugin.model.Aggregation;
import org.primefaces.extensions.optimizerplugin.model.ResourcesSet;
import org.primefaces.extensions.optimizerplugin.model.SourceMap;
import org.primefaces.extensions.optimizerplugin.optimizer.ClosureCompilerOptimizer;
import org.primefaces.extensions.optimizerplugin.optimizer.YuiCompressorOptimizer;
import org.primefaces.extensions.optimizerplugin.replacer.DataUriTokenResolver;
import org.primefaces.extensions.optimizerplugin.util.ResourcesScanner;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetAdapter;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetCssAdapter;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetJsAdapter;

/**
 * Entry point for this plugin.
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
@Mojo(name = "optimize",
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        threadSafe = true)
public class ResourcesOptimizerMojo extends AbstractMojo {

    private static final String[] DEFAULT_INCLUDES = {"**/*.css", "**/*.js"};

    private static final String[] DEFAULT_EXCLUDES = {};

    /**
     * Input directory
     */
    @Parameter(defaultValue = "${project.build.directory}/webapp")
    private File inputDir;

    /**
     * Images directories according to JSF spec.
     */
    @Parameter(defaultValue = "${project.basedir}${file.separator}src${file.separator}main${file.separator}webapp${file.separator}resources,${project.basedir}${file.separator}src${file.separator}main${file.separator}resources${file.separator}META-INF${file.separator}resources")
    private String imagesDir;

    /**
     * Compilation level for Google Closure Compiler.
     */
    @Parameter(defaultValue = "SIMPLE_OPTIMIZATIONS")
    private String compilationLevel;

    /**
     * Warning level for Google Closure Compiler.
     */
    @Parameter(defaultValue = "QUIET")
    private String warningLevel;

    /**
     * Encoding to read files.
     */
    @Parameter(defaultValue = "UTF-8", required = true)
    private String encoding;

    /**
     * Flag whether this plugin must stop/fail on warnings.
     */
    @Parameter()
    private boolean failOnWarning;

    /**
     * Flag whether to add 'use strict'; to JS files.
     */
    @Parameter()
    private boolean emitUseStrict;

    /**
     * Suffix for compressed / merged files.
     */
    @Parameter()
    private String suffix;

    /**
     * Flag if images referenced in CSS files (size < 32KB) should be converted to data URIs.
     */
    @Parameter()
    private boolean useDataUri;

    /**
     * Files to be included. Files selectors follow patterns specified in {@link org.codehaus.plexus.util.DirectoryScanner}.
     */
    @Parameter()
    private String[] includes;

    /**
     * Files to be excluded. Files selectors follow patterns specified in {@link org.codehaus.plexus.util.DirectoryScanner}.
     */
    @Parameter()
    private String[] excludes;

    /**
     * Configuration for aggregations.
     */
    @Parameter()
    private Aggregation[] aggregations;

    /**
     * Configuration for source maps.
     */
    @Parameter()
    private SourceMap sourceMap;

    /**
     * Default output directory for created source maps and original source files. This value is used internally in this class.
     */
    @Parameter(defaultValue = "${project.build.directory}${file.separator}sourcemap${file.separator}")
    private String smapOutputDir;

    /**
     * Language mode for input javascript.
     */
    @Parameter(defaultValue = "ECMASCRIPT3")
    private String languageIn;

    /**
     * Language mode for output javascript.
     */
    @Parameter(defaultValue = "NO_TRANSPILE")
    private String languageOut;

    /**
     * Flag whether plugin execution should be skipped.
     */
    @Parameter
    private boolean skip;


    /**
     * Compile sets.
     *
     * @parameter
     */
    private List<ResourcesSet> resourcesSets;

    private DataUriTokenResolver dataUriTokenResolver;

    private long originalFilesSize = 0;

    private long optimizedFilesSize = 0;

    boolean resFound = false;

    /**
     * Executes Mojo.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            getLog().info("Skipping the execution.");
            return;
        }

        // getLog().info("Optimization of resources is started ...");

        try {
            if (resourcesSets == null || resourcesSets.isEmpty()) {
                final String[] incls = (includes != null && includes.length > 0) ? includes : DEFAULT_INCLUDES;
                final String[] excls = (excludes != null && excludes.length > 0) ? excludes : DEFAULT_EXCLUDES;

                final Aggregation[] aggrs;
                if (aggregations == null || aggregations.length < 1) {
                    aggrs = new Aggregation[1];
                    aggrs[0] = null;
                } else {
                    aggrs = aggregations;
                }

                for (Aggregation aggr : aggrs) {
                    aggr = checkAggregation(aggr) ? null : aggr;

                    // evaluate inputDir
                    final File dir = (aggr != null && aggr.getInputDir() != null) ? aggr.getInputDir() : inputDir;

                    // prepare CSS und JavaScript files
                    final ResourcesScanner scanner = new ResourcesScanner();
                    scanner.scan(dir, incls, excls);

                    if (aggr != null && aggr.getOutputFile() == null) {
                        // subDirMode = true ==> aggregation for each subfolder
                        final File[] files = dir.listFiles();
                        if (files != null) {
                            for (final File file : files) {
                                if (file.isDirectory()) {
                                    final ResourcesScanner subDirScanner = new ResourcesScanner();
                                    subDirScanner.scan(file, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);

                                    final Set<File> subDirCssFiles = filterSubDirFiles(scanner.getCssFiles(), subDirScanner.getCssFiles());
                                    if (!subDirCssFiles.isEmpty()) {
                                        final DataUriTokenResolver dataUriTokenResolver = (useDataUri ? getDataUriTokenResolver() : null);

                                        // handle CSS files
                                        processCssFiles(file, subDirCssFiles, dataUriTokenResolver,
                                                getSubDirAggregation(file, aggr, ResourcesScanner.CSS_FILE_EXTENSION),
                                                null);
                                    }

                                    final Set<File> subDirJsFiles = filterSubDirFiles(scanner.getJsFiles(), subDirScanner.getJsFiles());
                                    if (!subDirJsFiles.isEmpty()) {
                                        // handle JavaScript files
                                        processJsFiles(file, subDirJsFiles,
                                                getSubDirAggregation(file, aggr, ResourcesScanner.JS_FILE_EXTENSION),
                                                getCompilationLevel(compilationLevel), getWarningLevel(warningLevel),
                                                resolveSourceMap(null), null, getLanguageIn(languageIn), getLanguageOut(languageOut), emitUseStrict);
                                    }
                                }
                            }
                        }
                    } else {
                        if (!scanner.getCssFiles().isEmpty()) {
                            final DataUriTokenResolver dataUriTokenResolver = (useDataUri ? getDataUriTokenResolver() : null);

                            // handle CSS files
                            processCssFiles(dir, scanner.getCssFiles(), dataUriTokenResolver, aggr, suffix);
                        }

                        if (!scanner.getJsFiles().isEmpty()) {
                            // handle JavaScript files
                            processJsFiles(dir, scanner.getJsFiles(), aggr, getCompilationLevel(compilationLevel),
                                    getWarningLevel(warningLevel), resolveSourceMap(null), suffix,
                                    getLanguageIn(languageIn), getLanguageOut(languageOut), emitUseStrict);
                        }
                    }
                }
            } else {
                for (final ResourcesSet rs : resourcesSets) {
                    // iterate over all resources sets
                    final String[] incls;
                    if (rs.getIncludes() != null && rs.getIncludes().length > 0) {
                        incls = rs.getIncludes();
                    } else if (includes != null && includes.length > 0) {
                        incls = includes;
                    } else {
                        incls = DEFAULT_INCLUDES;
                    }

                    final String[] excls;
                    if (rs.getExcludes() != null && rs.getExcludes().length > 0) {
                        excls = rs.getExcludes();
                    } else if (excludes != null && excludes.length > 0) {
                        excls = excludes;
                    } else {
                        excls = DEFAULT_EXCLUDES;
                    }

                    final Aggregation[] aggrs;
                    if (rs.getAggregations() == null || rs.getAggregations().length < 1) {
                        if (aggregations == null || aggregations.length < 1) {
                            aggrs = new Aggregation[1];
                            aggrs[0] = null;
                        } else {
                            aggrs = aggregations;
                        }
                    } else {
                        aggrs = rs.getAggregations();
                    }

                    for (Aggregation aggr : aggrs) {
                        aggr = checkAggregation(aggr) ? null : aggr;

                        // evaluate inputDir
                        File dir = (aggr != null && aggr.getInputDir() != null) ? aggr.getInputDir() : rs.getInputDir();
                        if (dir == null) {
                            dir = inputDir;
                        }

                        // prepare CSS und JavaScript files
                        final ResourcesScanner scanner = new ResourcesScanner();
                        scanner.scan(dir, incls, excls);

                        if (aggr != null && aggr.getOutputFile() == null) {
                            // subDirMode = true ==> aggregation for each subfolder
                            final File[] files = dir.listFiles();
                            if (files != null) {
                                for (final File file : files) {
                                    if (file.isDirectory()) {
                                        final ResourcesScanner subDirScanner = new ResourcesScanner();
                                        subDirScanner.scan(file, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);

                                        final Set<File> subDirCssFiles = filterSubDirFiles(scanner.getCssFiles(), subDirScanner.getCssFiles());
                                        if (!subDirCssFiles.isEmpty()) {
                                            final DataUriTokenResolver dataUriTokenResolver = (useDataUri || rs.isUseDataUri() ?
                                                    getDataUriTokenResolver() :
                                                    null);

                                            // handle CSS files
                                            processCssFiles(file, subDirCssFiles, dataUriTokenResolver,
                                                    getSubDirAggregation(file, aggr, ResourcesScanner.CSS_FILE_EXTENSION),
                                                    null);
                                        }

                                        final Set<File> subDirJsFiles = filterSubDirFiles(scanner.getJsFiles(), subDirScanner.getJsFiles());
                                        if (!subDirJsFiles.isEmpty()) {
                                            // handle JavaScript files
                                            processJsFiles(file, subDirJsFiles,
                                                    getSubDirAggregation(file, aggr, ResourcesScanner.JS_FILE_EXTENSION),
                                                    resolveCompilationLevel(rs), resolveWarningLevel(rs),
                                                    resolveSourceMap(rs), null, resolveLanguageIn(rs), resolveLanguageOut(rs), emitUseStrict);
                                        }
                                    }
                                }
                            }
                        } else {
                            if (!scanner.getCssFiles().isEmpty()) {
                                final DataUriTokenResolver dataUriTokenResolver = (useDataUri || rs.isUseDataUri() ? getDataUriTokenResolver() : null);

                                // handle CSS files
                                processCssFiles(dir, scanner.getCssFiles(), dataUriTokenResolver, aggr, suffix);
                            }

                            if (!scanner.getJsFiles().isEmpty()) {
                                // handle JavaScript files
                                processJsFiles(dir, scanner.getJsFiles(), aggr, resolveCompilationLevel(rs),
                                        resolveWarningLevel(rs), resolveSourceMap(rs), suffix,
                                        resolveLanguageIn(rs), resolveLanguageOut(rs), emitUseStrict);
                            }
                        }
                    }
                }
            }
        } catch (final MojoExecutionException e) {
            throw e;
        } catch (final Exception e) {
            throw new MojoExecutionException("Error while executing the mojo " + getClass(), e);
        }

        if (!resFound) {
            getLog().info("No resources found for optimization.");

            return;
        }

        // getLog().info("Optimization of resources has been finished successfully.");
        outputStatistic();
    }

    private void processCssFiles(final File inputDir, final Set<File> cssFiles, final DataUriTokenResolver dataUriTokenResolver,
                                 final Aggregation aggr, final String suffix) throws MojoExecutionException {
        resFound = true;
        final ResourcesSetAdapter rsa = new ResourcesSetCssAdapter(
                inputDir, cssFiles, dataUriTokenResolver, aggr, encoding, failOnWarning, suffix);

        final YuiCompressorOptimizer yuiOptimizer = new YuiCompressorOptimizer();
        yuiOptimizer.optimize(rsa, getLog());

        originalFilesSize += yuiOptimizer.getTotalOriginalSize();
        optimizedFilesSize += yuiOptimizer.getTotalOptimizedSize();
    }

    private void processJsFiles(final File inputDir, final Set<File> jsFiles, final Aggregation aggr, final CompilationLevel compilationLevel,
                                final WarningLevel warningLevel, final SourceMap sourceMap, final String suffix,
                                final LanguageMode languageIn, final LanguageMode languageOut, boolean emitUseStrict) throws MojoExecutionException {
        resFound = true;
        final ResourcesSetAdapter rsa = new ResourcesSetJsAdapter(
                inputDir, jsFiles, aggr, compilationLevel, warningLevel, sourceMap, encoding,
                failOnWarning, suffix, languageIn, languageOut, emitUseStrict);

        final ClosureCompilerOptimizer closureOptimizer = new ClosureCompilerOptimizer();
        closureOptimizer.optimize(rsa, getLog());

        originalFilesSize += closureOptimizer.getTotalOriginalSize();
        optimizedFilesSize += closureOptimizer.getTotalOptimizedSize();
    }

    private boolean checkAggregation(final Aggregation aggregation) throws MojoExecutionException {
        if (aggregation == null) {
            return true;
        }

        if (aggregation.isSubDirMode() && aggregation.getOutputFile() != null) {
            final String errMsg = "At least one aggregation tag is ambiguous because both " +
                    "'subDirMode' and 'outputFile' were set";
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
                getLog().warn("Using 'outputFile' as aggregation");
                aggregation.setSubDirMode(false);
            }

            return false;
        }

        if (!aggregation.isSubDirMode() && aggregation.getOutputFile() == null) {
            final String errMsg = "An aggregation tag is available, but no valid aggregation was configured. " +
                    "Check 'subDirMode' and 'outputFile'";
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
            }

            return true;
        }

        return false;
    }

    private Aggregation getSubDirAggregation(final File dir, final Aggregation aggr, final String fileExtension) {
        final Aggregation subDirAggr = new Aggregation();
        subDirAggr.setPrependedFile(aggr.getPrependedFile());
        subDirAggr.setRemoveIncluded(aggr.isRemoveIncluded());
        subDirAggr.setRemoveEmptyDirectories(aggr.isRemoveEmptyDirectories());
        subDirAggr.setWithoutCompress(aggr.isWithoutCompress());
        subDirAggr.setSubDirMode(true);

        final File outputFile = new File(dir, dir.getName() + "." + fileExtension);
        subDirAggr.setOutputFile(outputFile);

        return subDirAggr;
    }

    private CompilationLevel resolveCompilationLevel(final ResourcesSet rs) throws MojoExecutionException {
        final CompilationLevel compLevel;
        if (rs.getCompilationLevel() != null) {
            compLevel = getCompilationLevel(rs.getCompilationLevel());
        } else {
            compLevel = getCompilationLevel(compilationLevel);
        }

        return compLevel;
    }

    private WarningLevel resolveWarningLevel(final ResourcesSet rs) throws MojoExecutionException {
        final WarningLevel warnLevel;
        if (rs.getWarningLevel() != null) {
            warnLevel = getWarningLevel(rs.getWarningLevel());
        } else {
            warnLevel = getWarningLevel(warningLevel);
        }

        return warnLevel;
    }

    private SourceMap resolveSourceMap(final ResourcesSet rs) {
        final SourceMap smap;
        if (rs != null && rs.getSourceMap() != null) {
            smap = rs.getSourceMap();
        } else {
            smap = sourceMap;
        }

        if (smap == null || !smap.isCreate()) {
            return null;
        }

        // set defaults
        if (smap.getOutputDir() == null) {
            smap.setOutputDir(smapOutputDir);
        }

        if (smap.getDetailLevel() == null) {
            smap.setDetailLevel(com.google.javascript.jscomp.SourceMap.DetailLevel.ALL.name());
        }

        if (smap.getFormat() == null) {
            smap.setFormat(com.google.javascript.jscomp.SourceMap.Format.V3.name());
        }

        return smap;
    }

    private LanguageMode resolveLanguageIn(final ResourcesSet rs) throws MojoExecutionException {
        final LanguageMode langIn;
        if (rs.getLanguageIn() != null) {
            langIn = getLanguageIn(rs.getLanguageIn());
        } else {
            langIn = getLanguageIn(languageIn);
        }

        return langIn;
    }

    private LanguageMode resolveLanguageOut(final ResourcesSet rs) throws MojoExecutionException {
        final LanguageMode langOut;
        if (rs.getLanguageOut() != null) {
            langOut = getLanguageOut(rs.getLanguageOut());
        } else {
            langOut = getLanguageIn(languageOut);
        }

        return langOut;
    }

    private CompilationLevel getCompilationLevel(final String compilationLevel) throws MojoExecutionException {
        try {
            return CompilationLevel.valueOf(compilationLevel);
        } catch (final Exception e) {
            final String errMsg = "Compilation level '" + compilationLevel + "' is wrong. Valid constants are: " +
                    "'WHITESPACE_ONLY', 'SIMPLE_OPTIMIZATIONS', 'ADVANCED_OPTIMIZATIONS'";
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
                getLog().warn("Using 'SIMPLE_OPTIMIZATIONS' as compilation level");

                return CompilationLevel.SIMPLE_OPTIMIZATIONS;
            }
        }
    }

    private WarningLevel getWarningLevel(final String warningLevel) throws MojoExecutionException {
        try {
            return WarningLevel.valueOf(warningLevel);
        } catch (final Exception e) {
            final String errMsg = "Warning level '" + warningLevel + "' is wrong. Valid constants are: 'QUIET', 'DEFAULT', 'VERBOSE'";
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
                getLog().warn("Using 'QUIET' as warning level");

                return WarningLevel.QUIET;
            }
        }
    }

    private LanguageMode getLanguageIn(final String languageIn) throws MojoExecutionException {
        try {
            return LanguageMode.valueOf(languageIn);
        } catch (final Exception e) {
            final String errMsg = "Input language spec'" + languageIn + "' is wrong. Valid constants are: " + Arrays.toString(LanguageMode.values());
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
                getLog().warn("Using 'QUIET' as warning level");

                return LanguageMode.ECMASCRIPT3;
            }
        }
    }

    private LanguageMode getLanguageOut(final String languageOut) throws MojoExecutionException {
        try {
            return LanguageMode.valueOf(languageOut);
        } catch (final Exception e) {
            final String errMsg = "Output language spec'" + languageOut + "' is wrong. Valid constants are: " + Arrays.toString(LanguageMode.values());
            if (failOnWarning) {
                throw new MojoExecutionException(errMsg);
            } else {
                getLog().warn(errMsg);
                getLog().warn("Using 'QUIET' as warning level");

                return LanguageMode.NO_TRANSPILE;
            }
        }
    }

    private DataUriTokenResolver getDataUriTokenResolver() {
        if (dataUriTokenResolver != null) {
            return dataUriTokenResolver;
        }

        final String[] arrImagesDir = imagesDir.split(",");
        final File[] fileImagesDir = new File[arrImagesDir.length];
        for (int i = 0; i < arrImagesDir.length; i++) {
            final File file = new File(arrImagesDir[i]);
            if (file.isDirectory()) {
                getLog().info("Data URI Directory: " + file);
                fileImagesDir[i] = new File(arrImagesDir[i]);
            }
        }

        dataUriTokenResolver = new DataUriTokenResolver(getLog(), fileImagesDir);

        return dataUriTokenResolver;
    }

    private Set<File> filterSubDirFiles(final Set<File> resSetFiles, final Set<File> subDirFiles) {
        final Set<File> filteredFiles = new LinkedHashSet<>();

        if (subDirFiles == null || subDirFiles.isEmpty() || resSetFiles == null || resSetFiles.isEmpty()) {
            return filteredFiles;
        }

        for (final File subDirFile : subDirFiles) {
            if (resSetFiles.contains(subDirFile)) {
                filteredFiles.add(subDirFile);
            }
        }

        return filteredFiles;
    }

    private void outputStatistic() {
        final String originalSizeTotal;
        final String optimizedSizeTotal;
        final long oneMB = 1024 * 1024;

        if (originalFilesSize <= 1024) {
            originalSizeTotal = originalFilesSize + " Bytes";
        } else if (originalFilesSize <= oneMB) {
            originalSizeTotal = round((double) originalFilesSize / 1024, 3) + " KB";
        } else {
            originalSizeTotal = round((double) originalFilesSize / oneMB, 3) + " MB";
        }

        if (optimizedFilesSize <= 1024) {
            optimizedSizeTotal = optimizedFilesSize + " Bytes";
        } else if (optimizedFilesSize <= oneMB) {
            optimizedSizeTotal = round((double) optimizedFilesSize / 1024, 3) + " KB";
        } else {
            optimizedSizeTotal = round((double) optimizedFilesSize / oneMB, 3) + " MB";
        }

        if (originalFilesSize > 0) {
            getLog().info("=== Statistic ===========================================");
            getLog().info("Size before optimization = " + originalSizeTotal);
            getLog().info("Size after optimization = " + optimizedSizeTotal);
            getLog().info("Optimized resources have " + round(((optimizedFilesSize * 100.0) / originalFilesSize), 2)
                    + "% of original size");
            getLog().info("=========================================================");
        }
    }

    private double round(final double value, final int places) {
        final double roundedValue;
        final double factor = Math.pow(10.0, places);
        final double temp = Math.round(value * factor * factor) / factor;
        roundedValue = Math.round(temp) / factor;

        return roundedValue;
    }
}
