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
package com.paremus.dosgi.net.client;

import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractPayloadMessage;
import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public abstract class AbstractClientInvocationWithResult extends AbstractPayloadMessage<ClientMessageType> {
	
	public AbstractClientInvocationWithResult(ClientMessageType calltype, UUID serviceId, int callId, 
			Serializer serializer) {
		super(calltype, serviceId, callId, serializer);
	}

	public abstract long getTimeout();
	
	public abstract void fail(Throwable e);
	
	public abstract void fail(ByteBuf o) throws Exception;

	public abstract void data(ByteBuf o) throws Exception;
	
	public abstract void addCompletionListener(GenericFutureListener<Future<Object>> listener);

}
