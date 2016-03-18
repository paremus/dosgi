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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

class JavaFutureReturnHandler extends BasicReturnHandler {
	
	private final JavaCompletionStageReturnHandler csHandler;
	
	private final PromiseReturnHandler promiseHandler;

	private EventExecutorGroup executor;
	
	@SuppressWarnings("unchecked")
	public JavaFutureReturnHandler(UUID serviceId, Serializer serializer, Future<?> completeFuture, EventExecutorGroup executor) {
		super(serviceId, serializer, completeFuture);
		this.executor = executor;
		csHandler = new JavaCompletionStageReturnHandler(serviceId, serializer, completeFuture, executor);
		promiseHandler = new PromiseReturnHandler(serviceId, serializer, completeFuture,
				o -> (io.netty.util.concurrent.Future<Object>) o);
	}

	@Override
	public Future<?> success(Channel channel, int callId, Object returnValue) {
		if(returnValue instanceof CompletionStage) {
			return csHandler.success(channel, callId, returnValue);
		} else if (returnValue instanceof io.netty.util.concurrent.Future) {
			return promiseHandler.success(channel, callId, returnValue);
		} else {
			return executor.submit(() -> {
					try {
						// TODO use the real timeout
						sendReturn(channel, callId, true,
								((java.util.concurrent.Future<?>) returnValue)
									.get(30, java.util.concurrent.TimeUnit.SECONDS));
					} catch (InterruptedException e) {
						((java.util.concurrent.Future<?>) returnValue).cancel(true);
						sendReturn(channel, callId, false, e);
					} catch (ExecutionException e) {
						sendReturn(channel, callId, false, e.getCause());
					} catch (TimeoutException e) {
						// TODO Auto-generated catch block
						// The client will have given up, so so should we
						((java.util.concurrent.Future<?>) returnValue).cancel(true);
					}
				});
		}
	}
}
