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

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

import javax.annotation.Nonnull;

/**
 * Reader for inplace token replacements. It does not use as much memory as the String.replace() method. Got the idea from
 * http://tutorials.jenkov.com/java-howto/replace-strings-in-streams-arrays-files.html
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class TokenReplacingReader extends Reader {

    private final String tokenStartMarker;
    private final String tokenEndMarker;
    private final char[] tokenStartMarkerChars;
    private final char[] tokenEndMarkerChars;
    private final char[] tmpTokenStartMarkerChars;
    private final char[] tmpTokenEndMarkerChars;
    private final PushbackReader pushbackReader;
    private final TokenResolver tokenResolver;
    private final StringBuilder tokenBuffer = new StringBuilder();
    private String resolvedToken = null;
    private int resolvedTokenIndex = 0;

    public TokenReplacingReader(final TokenResolver resolver, final Reader source, final String tokenStartMarker,
                final String tokenEndMarker) {
        if (resolver == null) {
            throw new IllegalArgumentException("Token resolver is null");
        }

        if ((tokenStartMarker == null || tokenStartMarker.length() < 1)
                    || (tokenEndMarker == null || tokenEndMarker.length() < 1)) {
            throw new IllegalArgumentException("Token start / end marker is null or empty");
        }

        this.tokenStartMarker = tokenStartMarker;
        this.tokenEndMarker = tokenEndMarker;
        tokenStartMarkerChars = tokenStartMarker.toCharArray();
        tokenEndMarkerChars = tokenEndMarker.toCharArray();
        tmpTokenStartMarkerChars = new char[tokenStartMarker.length()];
        tmpTokenEndMarkerChars = new char[tokenEndMarker.length()];
        pushbackReader = new PushbackReader(source, Math.max(tokenStartMarker.length(), tokenEndMarker.length()));
        tokenResolver = resolver;
    }

    @Override
    public int read() throws IOException {
        if (resolvedToken != null) {
            if (resolvedTokenIndex < resolvedToken.length()) {
                return resolvedToken.charAt(resolvedTokenIndex++);
            }

            if (resolvedTokenIndex == resolvedToken.length()) {
                resolvedToken = null;
                resolvedTokenIndex = 0;
            }
        }

        // read proper number of chars into a temp. char array in order to find token start marker
        int countValidChars = readChars(tmpTokenStartMarkerChars);

        if (!Arrays.equals(tmpTokenStartMarkerChars, tokenStartMarkerChars)) {
            if (countValidChars > 0) {
                pushbackReader.unread(tmpTokenStartMarkerChars, 0, countValidChars);
            }

            return pushbackReader.read();
        }

        // found start of token, read proper number of chars into a temp. char array in order to find token end marker
        boolean endOfSource = false;
        tokenBuffer.delete(0, tokenBuffer.length());
        countValidChars = readChars(tmpTokenEndMarkerChars);

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
        }
        else {
            // try to resolve token
            resolvedToken = tokenResolver.resolveToken(tokenBuffer.toString());
            if (resolvedToken == null) {
                // token was not resolved
                resolvedToken = tokenStartMarker + tokenBuffer + tokenEndMarker;
            }
        }

        return resolvedToken.charAt(resolvedTokenIndex++);
    }

    private int readChars(final char[] tmpChars) throws IOException {
        int countValidChars = -1;
        final int length = tmpChars.length;
        int data = pushbackReader.read();

        for (int i = 0; i < length; i++) {
            if (data != -1) {
                tmpChars[i] = (char) data;
                countValidChars = i + 1;
                if (i + 1 < length) {
                    data = pushbackReader.read();
                }
            }
            else {
                // reset to java default value for char
                tmpChars[i] = '\u0000';
            }
        }

        return countValidChars;
    }

    @Override
    public int read(@Nonnull final char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(@Nonnull final char[] cbuf, final int off, final int len) throws IOException {
        int charsRead = 0;
        for (int i = 0; i < len; i++) {
            final int nextChar = read();
            if (nextChar == -1) {
                charsRead = i;
                if (charsRead == 0) {
                    charsRead = -1;
                }

                break;
            }
            else {
                charsRead = i + 1;
            }

            cbuf[off + i] = (char) nextChar;
        }

        return charsRead;
    }

    @Override
    public void close() throws IOException {
        pushbackReader.close();
    }

    @Override
    public boolean ready() throws IOException {
        return pushbackReader.ready();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read(@Nonnull final CharBuffer target) {
        throw new UnsupportedOperationException("Method int read(CharBuffer target) is not supported");
    }

    @Override
    public long skip(final long n) {
        throw new UnsupportedOperationException("Method long skip(long n) is not supported");
    }

    @Override
    public void mark(final int readAheadLimit) {
        throw new UnsupportedOperationException("Method void mark(int readAheadLimit) is not supported");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Method void reset() is not supported");
    }
}
