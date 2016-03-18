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
import com.paremus.entire.viewers.api.Bars;

/**
 * A structure to maintain memory information.
 */
public class FibreMemoryInfo extends struct {
	private static final long	serialVersionUID	= 1L;

	@ADA(
		description = "Initially allocated memory",
		builder = Bars.Marker.class,
		unit = Unit.bytes)
	public long					init;

	@ADA(
		description = "Memory currently used",
		builder = Bars.Value.class,
		unit = Unit.bytes)
	public long					used;

	@ADA(
		description = "Maximum allowed memory",
		builder = Bars.Marker.class,
		unit = Unit.bytes)
	public long					max;

	@ADA(
		name = "comm.",
		description = "Committed memory, means it is not in used but allocated from the OS",
		builder = Bars.Marker.class,
		unit = Unit.bytes)
	public long					committed;
}
