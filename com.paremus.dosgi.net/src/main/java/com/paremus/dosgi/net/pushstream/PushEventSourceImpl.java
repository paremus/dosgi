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

import java.util.function.Consumer;

import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushEventConsumer;
import org.osgi.util.pushstream.PushEventSource;

import com.paremus.dosgi.net.message.AbstractRSAMessage.CacheKey;
import com.paremus.dosgi.net.pushstream.PushStreamFactory.OnConnect;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

class PushEventSourceImpl<T> implements PushEventSource<T> {

	private final CacheKey key;
	
	private final OnConnect<T> onConnect;
	private final Consumer<CacheKey> onClose;
	private final EventExecutor executor;

	public PushEventSourceImpl(CacheKey key, OnConnect<T> onConnect, 
			Consumer<CacheKey> onClose, EventExecutor executor) {
		this.key = key;
		this.onConnect = onConnect;
		this.onClose = onClose;
		this.executor = executor;
	}

	@Override
	public AutoCloseable open(PushEventConsumer<? super T> aec) throws Exception {
		
		Promise<Object> closePromise = executor.newPromise();
		
		onConnect.connect(key, executor, closePromise, t -> {
				try {
					return aec.accept(PushEvent.data(t));
				} catch (Exception e) {
					try {
						aec.accept(PushEvent.error(e));
					} catch (Exception e1) {
						// TODO Auto-generated catch block
					} finally {
						closePromise.trySuccess(null);
					}
				}
				return -1;
			}, t -> {
				try {
					aec.accept(t == null ? PushEvent.close() : PushEvent.error(t));
				} catch (Exception e) {
					try {
						aec.accept(PushEvent.error(e));
					} catch (Exception e1) {
						// TODO Auto-generated catch block
					}
				} finally {
					closePromise.trySuccess(null);
				}
			});
		
		return () -> {
			onClose.accept(key);
			closePromise.trySuccess(null);
		};
	}

}
