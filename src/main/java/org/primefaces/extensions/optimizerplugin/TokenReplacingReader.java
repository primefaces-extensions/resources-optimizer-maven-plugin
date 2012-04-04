/*
 * Copyright 2011-2012 PrimeFaces Extensions.
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

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * Reader for inplace token replacements. It does not use as much memory as the String.replace() method. Got the idea from
 * http://tutorials.jenkov.com/java-howto/replace-strings-in-streams-arrays-files.html
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.5
 */
public class TokenReplacingReader extends Reader {

	private String tokenStartMarker;
	private String tokenEndMarker;
	private char[] tokenStartMarkerChars;
	private char[] tokenEndMarkerChars;
	private PushbackReader pushbackReader;
	private TokenResolver tokenResolver;
	private StringBuilder tokenBuffer = new StringBuilder();
	private String resolvedToken = null;
	private int resolvedTokenIndex = 0;

	public TokenReplacingReader(final TokenResolver resolver, final Reader source, final String tokenStartMarker,
	                            final String tokenEndMarker) {
		if (resolver == null) {
			throw new IllegalArgumentException("TokenResolver is null");
		}

		if (tokenStartMarker == null || tokenEndMarker == null) {
			throw new IllegalArgumentException("Token start or end marker is null");
		}

		this.tokenStartMarker = tokenStartMarker;
		this.tokenEndMarker = tokenEndMarker;
		this.tokenStartMarkerChars = tokenStartMarker.toCharArray();
		this.tokenEndMarkerChars = tokenEndMarker.toCharArray();
		this.pushbackReader = new PushbackReader(source, Math.max(tokenStartMarker.length(), tokenEndMarker.length()));
		this.tokenResolver = resolver;
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

		char[] cbuf = new char[tokenStartMarker.length()];
		pushbackReader.read(cbuf);

		if (!Arrays.equals(cbuf, tokenStartMarker.toCharArray())) {
			pushbackReader.unread(cbuf);

			return pushbackReader.read();
		}

		// found start of token
		tokenBuffer.delete(0, tokenBuffer.length());

		// TODO
		return 0;
	}

	@Override
	public int read(char[] cbuf) throws IOException {
		return read(cbuf, 0, cbuf.length);
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int charsRead = 0;
		for (int i = 0; i < len; i++) {
			int nextChar = read();
			if (nextChar == -1) {
				charsRead = i;
				if (charsRead == 0) {
					charsRead = -1;
				}

				break;
			}

			cbuf[off + i] = (char) nextChar;
		}

		return charsRead;
	}

	@Override
	public void close() throws IOException {
		this.pushbackReader.close();
	}

	@Override
	public boolean ready() throws IOException {
		return this.pushbackReader.ready();
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read(CharBuffer target) throws IOException {
		throw new UnsupportedOperationException("Method int read(CharBuffer target) is not supported");
	}

	@Override
	public long skip(long n) throws IOException {
		throw new UnsupportedOperationException("Method long skip(long n) is not supported");
	}

	@Override
	public void mark(int readAheadLimit) throws IOException {
		throw new UnsupportedOperationException("Method void mark(int readAheadLimit) is not supported");
	}

	@Override
	public void reset() throws IOException {
		throw new UnsupportedOperationException("Method void reset() is not supported");
	}
}
