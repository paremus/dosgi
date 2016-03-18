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
package com.paremus.dosgi.net.message;

import java.util.UUID;

import com.paremus.dosgi.net.serialize.Serializer;

public abstract class AbstractPayloadMessage<M extends MessageType> extends AbstractRSAMessage<M> {
	
	private final Serializer serializer;
	
	public AbstractPayloadMessage(M type, UUID serviceId, int callId,
			Serializer serializer) {
		super(type, serviceId, callId);
		this.serializer = serializer;
	}
	
	public final Serializer getSerializer() {
		return serializer;
	}
}
