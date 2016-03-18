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
import com.paremus.entire.attributes.api.AD.Unit;
import com.paremus.entire.attributes.api.ADA;

public class FibreFileSystem extends struct {
	private static final long	serialVersionUID	= 1L;
	public String				device;
	public String				mount;
	public String				type;
	public boolean				nodata;
	public long					spaceTotal;
	public long					spaceUsed;

	/**
	 * @formatter:off
	 */
	@ADA(
		description = "Number of bytes written per second",
		unit = Unit.bytes_s)
	public long					writtenAvg;
	@ADA(
		description = "Number of bytes read per second",
		unit = Unit.bytes_s)
	public long					readAvg;
	/**
	 * @formatter:on
	 */
}

