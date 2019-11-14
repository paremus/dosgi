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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushEvent.EventType;
import org.osgi.util.pushstream.PushEventConsumer;

import com.paremus.dosgi.net.pushstream.PushStreamFactory.DataStream;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

abstract class AbstractPushEventConsumerImpl implements PushEventConsumer<Object>, DataStream {

	protected final AtomicBoolean closed = new AtomicBoolean(false);
	
	long backPressureFutureTime = 0;

	protected final Promise<Void> closeFuture = ImmediateEventExecutor.INSTANCE.newPromise();
	
	private final ToLongFunction<Object> onData;
	private final Consumer<Throwable> onTerminal;
	
	public AbstractPushEventConsumerImpl(ToLongFunction<Object> onData,
			Consumer<Throwable> onTerminal) {
		this.onData = onData;
		this.onTerminal = onTerminal;
	}

	@Override
	public long accept(PushEvent<? extends Object> event) throws Exception {
		if(event.isTerminal()) {
			terminalEvent(event);
			return -1;
		} else if(!closed.get()){
			long localBP = onData.applyAsLong(event.getData());
			return localBP < 0 ? localBP : calculateBackPressure(localBP);
		}
		return -1;
	}

	private long calculateBackPressure(long localBP) {
		
		long remoteBPTime;
		synchronized (this) {
			remoteBPTime = backPressureFutureTime;
		}
		
		long remoteBP;
		if(remoteBPTime == 0) {
			// A special value meaning we have never seen backpressure, or that it was reset
			// for the fast path
			remoteBP = 0;
		} else {
			remoteBP = Math.max(0, TimeUnit.NANOSECONDS.toMillis(remoteBPTime - System.nanoTime()));
			// If we have passed the threshold then reset to zero
			if(remoteBP == 0) {
				synchronized (this) {
					if(backPressureFutureTime == remoteBPTime) {
						backPressureFutureTime = 0;
					}
				}
			}
		}
		
		return Math.max(localBP, remoteBP);
	}

	protected void terminalEvent(PushEvent<? extends Object> event) {
		if(event.getType() == EventType.CLOSE) {
			onTerminal.accept(null);
		} else if (event.getType() == EventType.ERROR) {
			onTerminal.accept(event.getFailure());
		} else {
			onTerminal.accept(new IllegalArgumentException(
					"Received an unknown event type " + event.getType()));
		}
	}

	@Override
	public void asyncBackPressure(long bp) {
		long now = System.nanoTime();
		
		synchronized (this) {
			long remainder = backPressureFutureTime == 0 ? 0 : Math.max(backPressureFutureTime - now, 0);
			
			long updated = now + remainder + TimeUnit.MILLISECONDS.toNanos(bp);
			
			// Never set to zero here, as that always means no back pressure! 
			backPressureFutureTime = updated == 0 ? 1 : updated;
		}
	}

	@Override
	public Future<Void> closeFuture() {
		return closeFuture;
	}
}
