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
import com.paremus.entire.attributes.api.AD;
import com.paremus.entire.attributes.api.ADA;

public class FibreNetworkInterface extends struct {
	private static final long	serialVersionUID	= 1L;
	public String				name;
	public String				type;
	public String				address;
	public String				netmask;
	public String				macAddress;
	public boolean				active;

	/**
	 * @formatter:off
	 */
	@ADA(
		description = "Link speed",
		unit = AD.Unit.bytes_s)
	public long					speedMax;

	@ADA(
		description = "Number of bytes written per second",
		unit = AD.Unit.bytes_s)
	public long					writtenAvg;
	@ADA(
		description = "Number of bytes read per second",
		unit = AD.Unit.bytes_s)
	public long					readAvg;
	public long					writeAvg;
	public long					readErrorsAvg;
	public long					writeErrorsAvg;
	/**
	 * @formatter:on
	 */
}

