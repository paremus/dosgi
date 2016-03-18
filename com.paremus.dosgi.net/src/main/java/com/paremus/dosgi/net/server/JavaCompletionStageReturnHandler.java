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
import java.util.concurrent.CompletionStage;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

class JavaCompletionStageReturnHandler extends BasicReturnHandler {
	
	private EventExecutorGroup executor;

	public JavaCompletionStageReturnHandler(UUID serviceId, Serializer serializer, Future<?> completeFuture,
			EventExecutorGroup executor) {
		super(serviceId, serializer, completeFuture);
		this.executor = executor;
	}

	@Override
	public Future<?> success(Channel channel, int callId, Object returnValue) {
		Promise<Object> p = executor.next().newPromise();
		((CompletionStage<?>) returnValue).whenComplete(
				(r,t) -> asyncResponse(channel, callId, r, t, p));
		p.addListener(f -> {
				if(f.isCancelled() && returnValue instanceof java.util.concurrent.Future) {
					((java.util.concurrent.Future<?>) returnValue).cancel(true);
				}
			});
		return p;
	}

	private void asyncResponse(Channel channel, int callId, Object r, Throwable t, Promise<Object> p) {
		if(t == null) {
			sendReturn(channel, callId, true, r);
		} else {
			sendReturn(channel, callId, false, t);
		}
		p.trySuccess(null);
	}
}
