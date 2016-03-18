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
package com.paremus.dosgi.net.pushstream;

import static org.osgi.framework.ServiceException.REMOTE;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import org.osgi.framework.ServiceException;
import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushStream;

import io.netty.util.Timeout;
import io.netty.util.Timer;

class PushStreamPushEventConsumerImpl extends AbstractPushEventConsumerImpl {

	private final PushStream<Object> stream;
	
	private final Timeout timeout;
	
	public PushStreamPushEventConsumerImpl(ToLongFunction<Object> onData,
			Consumer<Throwable> onTerminal, PushStream<Object> stream, Timer timer) {
		super(onData, onTerminal);
		this.stream = stream;
		timeout = timer.newTimeout(t -> {
				if(closed.compareAndSet(false, true)) {
					closeFuture.tryFailure(new TimeoutException("Stream timed out"));
					stream.close();
				}
			}, 30, TimeUnit.SECONDS);
	}

	protected void terminalEvent(PushEvent<? extends Object> event) {
		if(closed.compareAndSet(false, true)) {
			closeFuture.trySuccess(null);
			super.terminalEvent(event);
		}
	}

	@Override
	public void open() {
		if(!closed.get()) {
			if(timeout.cancel()) {
				stream.forEachEvent(this);
			} else if (!timeout.isCancelled()) {
				super.terminalEvent(PushEvent.error(new ServiceException("The remote PushStream timed out", REMOTE)));
			}
		}
	}

	@Override
	public void close() {
		if(closed.compareAndSet(false, true)) {
			closeFuture.trySuccess(null);
			stream.close();
		}
	}
}
