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
package com.paremus.dosgi.net.server;

import java.util.UUID;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

class BasicReturnHandler implements ReturnHandler {

	protected final UUID serviceId;
	protected final Serializer serializer;
	private Future<?> completeFuture;
	
	public BasicReturnHandler(UUID serviceId, Serializer serializer, Future<?> completeFuture) {
		this.serviceId = serviceId;
		this.serializer = serializer;
		this.completeFuture = completeFuture;
	}

	@Override
	public Future<?> success(Channel channel, int callId, Object returnValue) {
		sendReturn(channel, callId, true, returnValue);
		return completeFuture;
	}

	@Override
	public Future<?> failure(Channel channel, int callId, Throwable failure) {
		sendReturn(channel, callId, false, failure);
		return completeFuture;
	}
	
	protected void sendReturn(Channel channel, int callId, boolean successful, Object o) {
		channel.writeAndFlush(
				new MethodCompleteResponse(successful, serviceId, callId, serializer, o), 
				channel.voidPromise());
	}
}
