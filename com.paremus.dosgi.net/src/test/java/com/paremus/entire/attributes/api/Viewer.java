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
package com.paremus.entire.attributes.api;

import java.lang.reflect.Type;

/**
 * A Viewer represents the server side of a JS viewer. It can be used in the
 * {@link ADA} annotation. When specified in this annotation, the
 * {@link ADBuilder} will instantiate the given class and call
 * {@link #build(AD, Type)} on it. This can then set the different fields of the
 * {@link AD}.
 * 
 */
public abstract class Viewer {

	/**
	 * Method called during building of the {@link AD}
	 * 
	 * @param def the attribute descriptor being build 
	 * @param type the data type
	 * @return returns def
	 */
	public abstract <T extends AD> T build(T def, Type type) throws Exception;

}
