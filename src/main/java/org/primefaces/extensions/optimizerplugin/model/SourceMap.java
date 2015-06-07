package org.primefaces.extensions.optimizerplugin.model;

/*
 * Class representing a source map configuration.
 * 
 * Author: Oleg Varaksin (ovaraksin@googlemail.com)
 * Created on: 07.06.2015
 */
public class SourceMap {

    /**
     * Path to the location of source maps and original source files.
     * The path is prepended to the file name in the source mapping URL declaration //# sourceMappingURL
     * which is appended to all minified files of corresponding resource set.
     *
     * @parameter
     */
    private String sourceMapRoot;

    /**
     * Output directory for created source maps and original source files.
     *
     * @parameter
     */
    private String outputDir;

    /**
     * Source maps details level as String. See {@link com.google.javascript.jscomp.SourceMap.DetailLevel}.
     *
     * @parameter
     */
    private String detailLevel;

    /**
     * Source maps format as String. See {@link com.google.javascript.jscomp.SourceMap.Format}.
     *
     * @parameter
     */
    private String format;

    /**
     * Boolean flag if the source map should be created.
     * 
     * @parameter 
     */
    private boolean create = true;

    public String getSourceMapRoot() {
        return sourceMapRoot;
    }

    public void setSourceMapRoot(String sourceMapRoot) {
        this.sourceMapRoot = sourceMapRoot;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDetailLevel() {
        return detailLevel;
    }

    public void setDetailLevel(String detailLevel) {
        this.detailLevel = detailLevel;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }
}
