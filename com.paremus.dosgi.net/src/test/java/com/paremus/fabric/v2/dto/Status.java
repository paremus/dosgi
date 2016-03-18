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

public enum Status {
	UNKNOWN, VALID, WARNING, ERROR, CRITICAL, DISABLED, DELETED;

	public Status escalate(Status next) {
		if (next.compareTo(this) > 0)
			return next;
		else
			return this;
	}

	public Status ifOverrides(Status status) {
		if (this.compareTo(status) >= 0)
			return this;
		else
			return status;
	}
}
