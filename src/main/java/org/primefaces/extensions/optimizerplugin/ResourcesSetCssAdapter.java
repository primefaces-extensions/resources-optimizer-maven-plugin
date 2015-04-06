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
import java.util.Set;

/**
 * Container class containing all needed infos for a resource set describing CSS files.
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.5
 */
public class ResourcesSetCssAdapter extends ResourcesSetAdapter {

	private DataUriTokenResolver dataUriTokenResolver;

	public ResourcesSetCssAdapter(final File inputDir, final Set<File> files, final DataUriTokenResolver dataUriTokenResolver,
	                              final Aggregation aggregation, final String encoding, final boolean failOnWarning,
	                              final String suffix) {
		super(inputDir, files, aggregation, encoding, failOnWarning, suffix);
		this.dataUriTokenResolver = dataUriTokenResolver;
	}

	public DataUriTokenResolver getDataUriTokenResolver() {
		return dataUriTokenResolver;
	}
}
