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
package com.paremus.dosgi.net.activator;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;

public class RSAExecutorGroup extends MultithreadEventExecutorGroup {

	public RSAExecutorGroup(int nThreads, ThreadFactory threadFactory, int maxQueueDepth) {
		super(nThreads, new ThreadPoolExecutor(nThreads, nThreads, 0, TimeUnit.SECONDS, 
				maxQueueDepth < 0 ? new LinkedBlockingQueue<>() : new ArrayBlockingQueue<>(maxQueueDepth)));
	}

	@Override
	protected EventExecutor newChild(Executor executor, Object... arg1) throws Exception {
		return new DefaultEventExecutor(this, executor);
	}
}
