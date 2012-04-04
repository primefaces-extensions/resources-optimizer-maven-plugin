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

/**
 * Implementation of the interface {@link TokenResolver} to replace JSF based image references #{resource[...]} in CSS files by
 * embedded dataURIs (base64 .
 *
 * @author  Oleg Varaksin / last modified by $Author$
 * @version $Revision$
 * @since   0.5
 */
public class DataUriTokenResolver implements TokenResolver {

	public String resolveToken(final String token) {
		// TODO
		// not forget to check if image exists and less than 32KB
		return "test-dataURI";
	}
}
