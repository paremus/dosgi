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

import com.paremus.entire.viewers.api.Bars;


/**
 * Annotations cannot handle recursion ... So an {@link ADA} is used for the
 * specific attributes of an attribute and the Sub annotation allows one to go
 * one level deeper. See {@link Bars} and {@link Summary} how this can be used.
 * 
 */
public @interface Sub {
	ADA[] value();
}
