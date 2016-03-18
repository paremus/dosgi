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
package com.paremus.fabric.v1.dto;

import com.paremus.dto.api.struct;
import com.paremus.entire.attributes.api.ADA;

/**
 * Identifies an asset, like a machine, a CPU, a VM, etc. An asset has a name, a
 * version, and a vendor.
 * 
 */
public class Asset extends struct {
	private static final long	serialVersionUID	= 1L;

	@ADA(
		name = " ",
		description = "Name of this asset")
	public String	name;

	@ADA(
		name = "Version",
		description = "Version of this asset")
	public String	version;

	@ADA(
		name = "Vendor",
		description = "Vendor of this asset")
	public String	vendor;
}
