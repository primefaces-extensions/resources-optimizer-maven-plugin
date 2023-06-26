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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Objects;

/**
 * Reader for inplace token replacements. It does not use as much memory as the String.replace() method. Got the idea from
 * <a href="http://tutorials.jenkov.com/java-howto/replace-strings-in-streams-arrays-files.html">...</a>
 *
 * @author Oleg Varaksin (ovaraksin@googlemail.com)
 */
public abstract class AbstractTokenReplacingReader extends Reader {

    protected final Log log;

    protected final PushbackReader pushbackReader;
    private final TokenResolver tokenResolver;
    protected final StringBuilder tokenBuffer = new StringBuilder();
    protected String resolvedToken = null;
    protected int resolvedTokenIndex = 0;

    protected AbstractTokenReplacingReader(final Log log, final TokenResolver resolver, final Reader source,
            final int pushbackBufferSize) {
        this.log = log;
        pushbackReader = new PushbackReader(source, pushbackBufferSize);
        tokenResolver = Objects.requireNonNull(resolver, "Token resolver is null");
    }

    /**
     * Try to match token start at the reader's current position. Unread what was read if failed to match.
     *
     * @return true if matched token start
     * @throws IOException
     */
    protected abstract boolean matchTokenStart() throws IOException;

    /**
     * Find (sub)token to be replaced and put it into {@link #tokenBuffer}. Set {@link #resolvedToken} to everything
     * that was read on this iteration if not found.
     *
     * @return true if found token to resolve with {@link #tokenResolver}
     * @throws IOException
     */
    protected abstract boolean findToken() throws IOException;

    /**
     * If {@link #tokenResolver} successfully resolved the token then this is where you could wrap
     * the result in {@link #resolvedToken} into something if necessary.
     * If failed to resolve (resolvedToken == null) then set {@link #resolvedToken} to everything that was read on this
     * iteration.
     */
    protected abstract void handleResolverResult();

    @Override
    public final int read() throws IOException {
        // return chars from a previously found/resolved token
        if (resolvedToken != null) {
            if (resolvedTokenIndex < resolvedToken.length()) {
                return resolvedToken.charAt(resolvedTokenIndex++);
            }
            else {
                resolvedToken = null;
                resolvedTokenIndex = 0;
            }
        }

        if (!matchTokenStart()) {
            return pushbackReader.read();
        }

        if (findToken()) {
            // try to resolve token
            resolvedToken = tokenResolver.resolveToken(tokenBuffer.toString());
            handleResolverResult();
        }

        return resolvedToken.charAt(resolvedTokenIndex++);
    }

    protected int readChars(final char[] tmpChars) throws IOException {
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
    public final int read(@Nonnull final char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public final int read(@Nonnull final char[] cbuf, final int off, final int len) throws IOException {
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
    public final void close() throws IOException {
        pushbackReader.close();
    }

    @Override
    public final boolean ready() throws IOException {
        return pushbackReader.ready();
    }

    @Override
    public final boolean markSupported() {
        return false;
    }

    @Override
    public final int read(@Nonnull final CharBuffer target) {
        throw new UnsupportedOperationException("Method int read(CharBuffer target) is not supported");
    }

    @Override
    public final long skip(final long n) {
        throw new UnsupportedOperationException("Method long skip(long n) is not supported");
    }

    @Override
    public final void mark(final int readAheadLimit) {
        throw new UnsupportedOperationException("Method void mark(int readAheadLimit) is not supported");
    }

    @Override
    public final void reset() {
        throw new UnsupportedOperationException("Method void reset() is not supported");
    }

}
