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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushEventSource;

import io.netty.util.Timeout;
import io.netty.util.Timer;

class PushEventSourcePushEventConsumerImpl extends AbstractPushEventConsumerImpl {

	private static final AutoCloseable MARKER = () -> {};
	
	private final PushEventSource<Object> source;
	
	private final Timer timer;
	
	private Timeout timeout;
	
	private AtomicReference<AutoCloseable> connection = new AtomicReference<AutoCloseable>(null);
	
	public PushEventSourcePushEventConsumerImpl(ToLongFunction<Object> onData,
			Consumer<Throwable> onTerminal, PushEventSource<Object> source, Timer timer) {
		super(onData, onTerminal);
		this.source = source;
		this.timer = timer;
		synchronized (timer) {
			timeout = timer.newTimeout(this::timeout , 30, TimeUnit.SECONDS);
		}
	}
	
	private void timeout(Timeout t) {
		closed.set(true);
		internalClose();
		closeFuture.tryFailure(new TimeoutException("Stream timed out"));
	}
	
	protected void terminalEvent(PushEvent<? extends Object> event) {
		if(connection.getAndSet(null) != null) {
			super.terminalEvent(event);
		}
	}

	@Override
	public void open() {
		synchronized (this) {
			if(!timeout.cancel()) {
				return;
			}
		}
		if(!closed.get()) {
			if(connection.compareAndSet(null, MARKER)) {
				AutoCloseable open = null;
				try {
					open = source.open(this);
				} catch (Exception e) {
					closed.set(true);
					terminalEvent(PushEvent.error(e));
					internalClose();
					closeFuture.trySuccess(null);
				}
				// this can only happen due to an overlapping close
				if(!connection.compareAndSet(MARKER, open) && open != null) {
					try {
						open.close();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} else {
			
		}
	}

	@Override
	public void close() {
		internalClose();
		synchronized (this) {
			if(timeout.isCancelled()) {
				timeout = timer.newTimeout(this::timeout , 30, TimeUnit.SECONDS);
			}
		}
	}

	private void internalClose() {
		AutoCloseable conn = connection.getAndSet(null);
		if(conn != null) {
			try {
				conn.close();
			} catch (Exception e) {
				
			}
		}
	}
}
