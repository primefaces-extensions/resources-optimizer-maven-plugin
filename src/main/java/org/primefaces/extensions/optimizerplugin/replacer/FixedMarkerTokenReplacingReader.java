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

package org.primefaces.extensions.optimizerplugin.replacer;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

/**
 * Finds and replaces tokens enclosed in known fixed length markers.
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class FixedMarkerTokenReplacingReader extends AbstractTokenReplacingReader {

    private final String tokenStartMarker;
    private final String tokenEndMarker;
    private final char[] tokenStartMarkerChars;
    private final char[] tokenEndMarkerChars;
    private final char[] tmpTokenStartMarkerChars;
    private final char[] tmpTokenEndMarkerChars;

    public FixedMarkerTokenReplacingReader(final Log log, final TokenResolver resolver, final Reader source,
            final String tokenStartMarker, final String tokenEndMarker) {
        super(log, resolver, source, Math.max(tokenStartMarker.length(), tokenEndMarker.length()));
        this.tokenStartMarker = tokenStartMarker;
        this.tokenEndMarker = tokenEndMarker;
        tokenStartMarkerChars = tokenStartMarker.toCharArray();
        tokenEndMarkerChars = tokenEndMarker.toCharArray();
        tmpTokenStartMarkerChars = new char[tokenStartMarker.length()];
        tmpTokenEndMarkerChars = new char[tokenEndMarker.length()];
    }

    @Override
    protected boolean matchTokenStart() throws IOException {
        // read proper number of chars into a temp. char array in order to find token start marker
        int countValidChars = readChars(tmpTokenStartMarkerChars);

        if (Arrays.equals(tmpTokenStartMarkerChars, tokenStartMarkerChars)) {
            return true;
        }
        else {
            if (countValidChars > 0) {
                pushbackReader.unread(tmpTokenStartMarkerChars, 0, countValidChars);
            }
            return false;
        }
    }

    @Override
    protected boolean findToken() throws IOException {
        // found start of token, read proper number of chars into a temp. char array in order to find token end marker
        boolean endOfSource = false;
        tokenBuffer.setLength(0);
        int countValidChars = readChars(tmpTokenEndMarkerChars);

        while (!Arrays.equals(tmpTokenEndMarkerChars, tokenEndMarkerChars)) {
            if (countValidChars == -1) {
                // end of source and no token end marker was found
                endOfSource = true;

                break;
            }

            tokenBuffer.append(tmpTokenEndMarkerChars[0]);

            pushbackReader.unread(tmpTokenEndMarkerChars, 0, countValidChars);
            if (pushbackReader.read() == -1) {
                // end of source and no token end marker was found
                endOfSource = true;

                break;
            }

            countValidChars = readChars(tmpTokenEndMarkerChars);
        }

        if (endOfSource) {
            resolvedToken = tokenStartMarker + tokenBuffer;
            return false;
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Extracted " + tokenStartMarker + tokenEndMarker + "-demarcated token to resolve: "
                        + tokenBuffer);
            }
            return true;
        }
    }

    @Override
    protected void handleResolverResult() {
        if (resolvedToken == null) {
            // token was not resolved
            resolvedToken = tokenStartMarker + tokenBuffer + tokenEndMarker;
        }
    }

}
