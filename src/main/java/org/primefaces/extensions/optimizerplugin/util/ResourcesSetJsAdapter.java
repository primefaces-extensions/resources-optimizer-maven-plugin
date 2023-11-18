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

package org.primefaces.extensions.optimizerplugin.util;

import java.io.File;
import java.util.Set;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.WarningLevel;
import org.primefaces.extensions.optimizerplugin.model.Aggregation;
import org.primefaces.extensions.optimizerplugin.model.SourceMap;

/**
 * Container class containing all needed infos for a resource set describing JavaScript files..
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class ResourcesSetJsAdapter extends ResourcesSetAdapter {

    private final CompilationLevel compilationLevel;

    private final WarningLevel warningLevel;

    private final SourceMap sourceMap;

    private final LanguageMode languageIn;

    private final LanguageMode languageOut;

    private final boolean emitUseStrict;
    private boolean processCommonJSModules;

    public ResourcesSetJsAdapter(File inputDir, Set<File> files, Aggregation aggregation, CompilationLevel compilationLevel,
                WarningLevel warningLevel, SourceMap sourceMap, String encoding, boolean failOnWarning,
                String suffix, LanguageMode languageIn, LanguageMode languageOut, boolean emitUseStrict, boolean processCommonJSModules) {
        super(inputDir, files, aggregation, encoding, failOnWarning, suffix);
        this.compilationLevel = compilationLevel;
        this.warningLevel = warningLevel;
        this.sourceMap = sourceMap;
        this.languageIn = languageIn;
        this.languageOut = languageOut;
        this.emitUseStrict = emitUseStrict;
        this.processCommonJSModules = processCommonJSModules;
    }

    public CompilationLevel getCompilationLevel() {
        return compilationLevel;
    }

    public WarningLevel getWarningLevel() {
        return warningLevel;
    }

    public SourceMap getSourceMap() {
        return sourceMap;
    }

    public LanguageMode getLanguageIn() {
        return languageIn;
    }

    public LanguageMode getLanguageOut() {
        return languageOut;
    }

    public boolean isEmitUseStrict() {
        return emitUseStrict;
    }

    public boolean isProcessCommonJSModules() {
        return processCommonJSModules;
    }
}