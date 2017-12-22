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

import com.google.common.io.Files;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implementation of the interface {@link TokenResolver} to replace JSF based image references #{resource[...]} in CSS files by
 * embedded dataURIs (encoded base64 string).
 *
 * @author  Oleg Varaksin (ovaraksin@googlemail.com)
 */
public class DataUriTokenResolver implements TokenResolver {

	/** directories where to look for images from */
	private File[] imagesDir;
	private Log log;

	private static Pattern pattern = Pattern.compile("[\\s'\":/\\\\]+");

	private static final int SIZE_LIMIT = 32 * 1024;

	private static final Map<String, String> supportedTypes = new HashMap<String, String>();

	static {
		supportedTypes.put("gif", "image/gif");
		supportedTypes.put("jpg", "image/jpeg");
		supportedTypes.put("jpeg", "image/jpeg");
		supportedTypes.put("png", "image/png");
		supportedTypes.put("svg", "image/svg+xml");
	}

	public DataUriTokenResolver(Log log, final File[] imagesDir) {
		this.imagesDir = imagesDir;
		this.log = log;
	}

	public String resolveToken(final String token) throws IOException {
		if (token == null || token.length() < 1) {
			return token;
		}

		String[] pathParts = pattern.split(token);
		if (pathParts == null || pathParts.length < 1) {
			return null;
		}

		final String separator = System.getProperty("file.separator");
		StringBuilder sb = new StringBuilder();
		sb.append(separator);

		for (int i = 0; i < pathParts.length; i++) {
			if (StringUtils.isNotBlank(pathParts[i])) {
				sb.append(pathParts[i]);
				if (i + 1 < pathParts.length) {
					sb.append(separator);
				}
			}
		}

		String path = sb.toString();
		if (path.length() == 1) {
			return null;
		}

		if (path.endsWith(separator)) {
			path = path.substring(0, path.length() - 1);
		}

		// build image full path and check if image exists and has supported mime-type
		boolean found = false;
		File imageFile = null;
		String extension = null;
		for (File imageDir : imagesDir) {
			String fullPath = imageDir.getCanonicalPath() + path;

			imageFile = new File(fullPath);
			if (!imageFile.exists()) {
				// file doesn't exist
				continue;
			}

			extension = FileUtils.extension(fullPath);
			if (extension == null || !supportedTypes.containsKey(extension)) {
				// not supported image mime-type
				continue;
			}

			found = true;

			break;
		}

		if (!found) {
			return null;
		}

		log.info("Data URI conversion for: " + imageFile);
		// generate dataURI
		final byte[] bytes = Files.toByteArray(imageFile);
		StringBuilder dataUriBilder = new StringBuilder();
		dataUriBilder.append("data:");
		dataUriBilder.append(supportedTypes.get(extension));
		dataUriBilder.append(";base64,");
		dataUriBilder.append(new String(Base64.encodeBase64(bytes)));

		String dataUri = dataUriBilder.toString();

		// Check if the size of dataURI is limited to 32KB (because IE8 has a 32KB limitation)
		boolean exceedLimit = dataUri.length() >= SIZE_LIMIT;
		if (exceedLimit) {
			return null;
		}

		return dataUri;
	}
}
