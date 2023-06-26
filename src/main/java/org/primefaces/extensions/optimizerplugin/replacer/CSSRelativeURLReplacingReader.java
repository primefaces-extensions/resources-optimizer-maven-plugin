/*
 * Copyright 2011-2023 PrimeFaces Extensions
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

import com.google.common.primitives.Chars;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reader for replacing relative URLs in CSS url function calls.
 *
 * @author s.golovanov
 */
public class CSSRelativeURLReplacingReader extends AbstractTokenReplacingReader {

    private static final char[] URL_FUNC_CALL_START = "url(".toCharArray();
    private static final int URL_FUNC_CALL_START_LEN = URL_FUNC_CALL_START.length;

    private static final List<char[]> IGNORED_ARGUMENT_PREFIXES = List.of("https:", "http:", "blob:", "#", "data:")
            .stream().map(String::toCharArray).collect(Collectors.toUnmodifiableList());
    private static final char[] TRIM_ARGUMENTS_CHARS = "\t'\"".toCharArray();

    // just need enough of a buffer to check the beginning of a url function call (with some leeway for whitespace)
    private final char[] startChars = new char[20];
    private int argStart;

    public CSSRelativeURLReplacingReader(final Log log, final TokenResolver tokenResolver, final Reader source) {
        super(log, tokenResolver, source, 20);
    }

    @Override
    protected boolean matchTokenStart() throws IOException {
        int countValidChars = readChars(startChars);
        if (countValidChars == -1) {
            return false;
        }

        // first check the char before "url("
        // (what's with the quote? it's because of this: `li::after { content: " - "url(star.gif); }`)
        if (!(startChars[0] == ' ' || startChars[0] == '\t' || startChars[0] == ':' || startChars[0] == '"')) {
            pushbackReader.unread(startChars, 0, countValidChars);
            return false;
        }

        // now "url(" itself (no, it can't have a space before the parenthesis)
        if (!Arrays.equals(URL_FUNC_CALL_START, 0, URL_FUNC_CALL_START_LEN,
                startChars, 1, URL_FUNC_CALL_START_LEN + 1)) {
            pushbackReader.unread(startChars, 0, countValidChars);
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Found CSS url function call: " + String.valueOf(startChars));
        }

        // skip quotes/spaces to find argument start
        argStart = URL_FUNC_CALL_START_LEN + 1;
        while (Chars.contains(TRIM_ARGUMENTS_CHARS, startChars[argStart])) {
            ++argStart;
            if (argStart == startChars.length) {
                pushbackReader.unread(startChars, 0, countValidChars);
                return false;
            }
        }

        // we can't conclusively positively match a relative URL argument - instead we have to match and exclude
        // everything we definitely don't want to replace
        // we're skipping:
        // - already embedded Data URIs;
        // - unresolvable (?) stuff like blobs (how would they get in a static css file? who knows) and SVG fragments
        // ("#" also skips JSF resource URLs which is necessary);
        // - absolute URLs too, because trying to fetch an internet or even a LAN resource is definitely not something
        // that should'be done quietly by default
        for (char[] prefix : IGNORED_ARGUMENT_PREFIXES) {
            if (argStart + prefix.length <= startChars.length &&
                    Arrays.equals(prefix, 0, prefix.length, startChars,
                            argStart, argStart + prefix.length)) {
                if (log.isDebugEnabled()) {
                    log.debug("Matched ignored argument prefix: " + String.valueOf(prefix));
                }
                pushbackReader.unread(startChars, 0, countValidChars);
                return false;
            }
        }
        // additional optimistic check for an SVG fragment, e.g. url(my-file.svg#svg-blur) - in case it happened to fit
        // into the buffer
        if (Chars.contains(startChars, '#')) {
            if (log.isDebugEnabled()) {
                log.debug("Matched ignored SVG fragment argument");
            }
            pushbackReader.unread(startChars, 0, countValidChars);
            return false;
        }

        // unread to the beginning of the argument in preparation for findToken
        pushbackReader.unread(startChars, argStart, startChars.length - argStart);
        return true;
    }

    @Override
    protected boolean findToken() throws IOException {
        boolean cancel = false;
        tokenBuffer.setLength(0);

        int ch = pushbackReader.read();
        // technically there could be parentheses in a quoted URL, or escaped parentheses in unquoted, but let's ignore
        // that for now
        while (ch != ')') {
            if (ch == -1 || ch == '#') {
                // finished reading the file or this turned out to be an SVG fragment
                cancel = true;
                break;
            }
            tokenBuffer.append((char) ch);
            ch = pushbackReader.read();
        }

        if (cancel) {
            resolvedToken = String.valueOf(startChars, 0, argStart) + tokenBuffer;
            return false;
        }
        else {
            // we trimmed argument start above (see TRIM_ARGUMENTS_CHARS), should we trim the end too for symmetry? meh,
            // DataUriTokenResolver would handle it, it only looks weird in debug logs, just a cosmetic issue
            if (log.isDebugEnabled()) {
                log.debug("Extracted CSS url() relative URL argument to resolve: " + tokenBuffer);
            }
            return true;
        }
    }

    @Override
    protected void handleResolverResult() {
        if (resolvedToken != null) {
            // the argument-token was resolved - we need to wrap it back into "*url(" (exactly as it was) and ")"
            resolvedToken = String.valueOf(startChars, 0, URL_FUNC_CALL_START_LEN + 1) + resolvedToken
                    + ')';
        }
        else {
            // the argument-token was not resolved - restore what was read exactly
            resolvedToken = String.valueOf(startChars, 0, argStart) + tokenBuffer + ')';
        }
    }


}
