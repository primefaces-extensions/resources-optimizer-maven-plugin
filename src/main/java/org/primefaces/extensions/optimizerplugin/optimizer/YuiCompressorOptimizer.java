/**
 * Copyright 2011-2023 PrimeFaces Extensions
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.primefaces.extensions.optimizerplugin.optimizer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.primefaces.extensions.optimizerplugin.replacer.CSSRelativeURLReplacingReader;
import org.primefaces.extensions.optimizerplugin.replacer.DataUriTokenResolver;
import org.primefaces.extensions.optimizerplugin.replacer.FixedMarkerTokenReplacingReader;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetAdapter;
import org.primefaces.extensions.optimizerplugin.util.ResourcesSetCssAdapter;

import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

/**
 * Class for YUI Compressor doing CSS optimization.
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class YuiCompressorOptimizer extends AbstractOptimizer {

    private static final String OPTIMIZED_FILE_EXTENSION = ".optcss";

    private static final String JSF_RESOURCE_DATA_URI_START_MARKER = "#{resource[";

    private static final String JSF_RESOURCE_DATA_URI_END_MARKER = "]}";

    public YuiCompressorOptimizer(Log log) {
        super(log);
    }

    @Override
    public void optimize(final ResourcesSetAdapter rsAdapter) throws MojoExecutionException {
        ResourcesSetCssAdapter rsa = (ResourcesSetCssAdapter) rsAdapter;
        Reader in = null;
        OutputStreamWriter out = null;

        try {
            if (rsa.getAggregation() == null) {
                // no aggregation
                for (File file : rsa.getFiles()) {
                    log.info("Optimize CSS file " + file.getName() + " ...");

                    // statistic
                    addToOriginalSize(file);

                    in = getReader(rsa, file);

                    // generate output
                    String path = file.getCanonicalPath();
                    if (StringUtils.isNotBlank(rsa.getSuffix())) {
                        // create a new output stream
                        File outputFile = getFileWithSuffix(path, rsa.getSuffix());
                        out = new OutputStreamWriter(new FileOutputStream(outputFile), rsa.getEncoding());

                        // compress and write compressed content into the new file
                        CssCompressor compressor = new CssCompressor(in);
                        compressor.compress(out, 500);
                        closeStreams(in, out);

                        // statistic
                        addToOptimizedSize(outputFile);
                    }
                    else {
                        // path of temp. file
                        String pathOptimized = FileUtils.removeExtension(path) + OPTIMIZED_FILE_EXTENSION;

                        // create a new temp. file and output stream
                        File outputFile = new File(pathOptimized);
                        Files.touch(outputFile);
                        out = new OutputStreamWriter(new FileOutputStream(outputFile), rsa.getEncoding());

                        // compress and write compressed content into the new file
                        CssCompressor compressor = new CssCompressor(in);
                        compressor.compress(out, 500);
                        closeStreams(in, out);

                        // rename the new file (overwrite the original file)
                        FileUtils.rename(outputFile, file);

                        // statistic
                        addToOptimizedSize(file);
                    }
                }
            }
            else if (rsa.getAggregation().getOutputFile() != null) {
                // aggregation to one output file
                File outputFile;
                Charset cset = Charset.forName(rsa.getEncoding());

                if (!rsa.getAggregation().isWithoutCompress()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(baos, rsa.getEncoding());

                    // with compressing before aggregation
                    for (File file : rsa.getFiles()) {
                        log.info("Optimize CSS file " + file.getName() + " ...");

                        // statistic
                        addToOriginalSize(file);

                        // create reader for the current file
                        in = getReader(rsa, file);

                        // compress and write compressed content into the output stream
                        CssCompressor compressor = new CssCompressor(in);
                        compressor.compress(osw, 500);

                        // close stream
                        closeStream(in);
                    }

                    // close stream
                    closeStream(osw);

                    if (rsa.getAggregation().getPrependedFile() != null) {
                        // statistic
                        addToOriginalSize(rsa.getAggregation().getPrependedFile());
                    }

                    // get right output file
                    outputFile = getOutputFile(rsa);

                    long sizeBefore = outputFile.length();

                    if (rsa.getAggregation().getPrependedFile() != null) {
                        // write / append to be prepended file into / to the output file
                        prependFile(rsa.getAggregation().getPrependedFile(), outputFile, cset, rsa);
                    }

                    // write / append compiled content into / to the output file
                    Files.asCharSink(outputFile, cset, FileWriteMode.APPEND).write(baos.toString(rsa.getEncoding()));

                    // statistic
                    addToOptimizedSize(outputFile.length() - sizeBefore);
                }
                else {
                    // only aggregation without compressing
                    outputFile = aggregateFiles(rsa, cset, false);

                    // statistic
                    long size = addToOriginalSize(outputFile);
                    addToOptimizedSize(size);
                }

                // delete single files if necessary
                deleteFilesIfNecessary(rsa);
                deleteDirectoryIfNecessary(rsa);

                // rename aggregated file if necessary
                renameOutputFileIfNecessary(rsa, outputFile);
            }
            else {
                // should not happen
                log.error("Wrong plugin's internal state.");
            }
        }
        catch (Exception e) {
            throw new MojoExecutionException("Resources optimization failure: " + e.getLocalizedMessage(), e);
        }
        finally {
            closeStreams(in, out);
        }
    }

    @Override
    protected Reader getReader(ResourcesSetAdapter rsAdapter, File file)
                throws FileNotFoundException, UnsupportedEncodingException {
        ResourcesSetCssAdapter rsa = (ResourcesSetCssAdapter) rsAdapter;

        Reader reader = super.getReader(rsa, file);

        // only use Data URI's if toke resolver is set
        if (rsa.getProjectDataUriTokenResolver() != null) {
            reader = new FixedMarkerTokenReplacingReader(log, rsa.getProjectDataUriTokenResolver(), reader,
                    JSF_RESOURCE_DATA_URI_START_MARKER, JSF_RESOURCE_DATA_URI_END_MARKER);

            // this needs a resolver relative to current CSS file directory
            File fileParentDir = new File(file.getParent());
            DataUriTokenResolver fileRelativeResolver = new DataUriTokenResolver(log, List.of(fileParentDir));
            reader = new CSSRelativeURLReplacingReader(log, fileRelativeResolver, reader);
        }

        return reader;
    }

    protected void closeStreams(Reader in, Writer out) {
        closeStream(in);
        closeStream(out);
    }

    private void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException var2) {
                // exit it
            }
        }
    }
}