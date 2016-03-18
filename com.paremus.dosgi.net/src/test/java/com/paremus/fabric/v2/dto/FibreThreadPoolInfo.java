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
package com.paremus.fabric.v2.dto;

import com.paremus.dto.api.struct;
import com.paremus.entire.attributes.api.ADA;

public class FibreThreadPoolInfo extends struct {
	private static final long	serialVersionUID	= 1L;
	public String				name;
	@ADA(
		description = "Number of threads currently active")
	public int					active;
	public int					max;
	public long					queueDepth;
	@ADA(
		description = "Number of threads created in the last minute")
	public long					createdAvg;
	public int					min;

}

