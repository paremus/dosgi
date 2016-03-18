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
import java.util.function.Function;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

class PromiseReturnHandler extends BasicReturnHandler {
	
	private final Function<Object, Future<Object>> toNettyFuture;
	
	public PromiseReturnHandler(UUID serviceId, Serializer serializer, Future<?> completeFuture,
			Function<Object, Future<Object>> toNettyFuture) {
		super(serviceId, serializer, completeFuture);
		this.toNettyFuture = toNettyFuture;
	}

	@Override
	public Future<?> success(Channel channel, int callId, Object returnValue) {
		return toNettyFuture.apply(returnValue)
			.addListener(f -> asyncResponse(channel, callId, f));
	}

	private void asyncResponse(Channel channel, int callId, Future<? super Object> f) {
		if(f.isSuccess()) {
			sendReturn(channel, callId, true, f.getNow());
		} else {
			sendReturn(channel, callId, false, f.cause());
		}
	}
}
