/*-
 * #%L
 * com.paremus.dosgi.net
 * %%
 * Copyright (C) 2016 - 2019 Paremus Ltd
 * %%
 * Licensed under the Fair Source License, Version 0.9 (the "License");
 * 
 * See the NOTICE.txt file distributed with this work for additional 
 * information regarding copyright ownership. You may not use this file 
 * except in compliance with the License. For usage restrictions see the 
 * LICENSE.txt file distributed with this work
 * #L%
 */
package org.osgi.framework;

import java.util.HashMap;
import java.util.Map;

public class FrameworkUtil {

	private static final Map<Class<?>, Bundle> mapping = new HashMap<>();

	private static final Map<String, Filter> filters = new HashMap<>();
	
	public static void clear() {
		mapping.clear();
		filters.clear();
	}
	
	public static void registerBundleFor(Class<?> clazz, Bundle bundle) {
		mapping.put(clazz, bundle);
	}
	
	public static Bundle getBundle(Class<?> clazz) {
		return mapping.get(clazz);
	}

	public static Filter createFilter(String filter) throws InvalidSyntaxException {
		Filter f = filters.get(filter);
		if(f == null) {
			throw new InvalidSyntaxException("No filter defined for string", filter);
		}
		return f;
	}
}
