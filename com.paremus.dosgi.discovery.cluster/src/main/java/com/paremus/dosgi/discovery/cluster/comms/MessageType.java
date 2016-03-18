/*-
 * #%L
 * com.paremus.dosgi.discovery.cluster
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
package com.paremus.dosgi.discovery.cluster.comms;

public enum MessageType {
	ANNOUNCEMENT, REVOCATION, ACKNOWLEDGMENT, REMINDER, REQUEST_REANNOUNCEMENT;

	public short code() {
		return (short) ordinal();
	}
	
	public static MessageType valueOf(short code) {
		MessageType[] values = MessageType.values();
		if(code < 0 || code >= values.length) {
			throw new IllegalArgumentException("Not a valid MessageType code " + code);
		}
		return values[code];
	}
}
