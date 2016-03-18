/*-
 * #%L
 * com.paremus.dosgi.topology.common
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
package com.paremus.dosgi.topology.common;

import static org.osgi.service.remoteserviceadmin.EndpointEvent.ADDED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.REMOVED;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;

@SuppressWarnings("deprecation")
public abstract class EndpointListenerService implements EndpointEventListener, EndpointListener {

	private static enum ListenerType {
		LISTENER, EVENT_LISTENER;
	}
	
	private final Bundle client;

	private final AtomicReference<ListenerType> typeWatcher = new AtomicReference<>();

	public EndpointListenerService(Bundle client) {
		this.client = client;
	}

	@Override
	public void endpointChanged(EndpointEvent event, String filter) {
		checkEventListener();
		handleEvent(client, event, filter);
	}

	private void checkEventListener() {
		if (typeWatcher
				.updateAndGet(old -> old == null ? ListenerType.EVENT_LISTENER : old) != ListenerType.EVENT_LISTENER) {
			throw new IllegalStateException("An RSA 1.1 EndpointEventListener must not be "
					+ "called in addition to an EndpointListener from the same bundle");
		}
	}

	@Override
	public void endpointRemoved(EndpointDescription endpoint, String matchedFilter) {
		checkListener();
		handleEvent(client, new EndpointEvent(REMOVED, endpoint), matchedFilter);
	}

	@Override
	public void endpointAdded(EndpointDescription endpoint, String matchedFilter) {
		checkListener();
		handleEvent(client, new EndpointEvent(ADDED, endpoint), matchedFilter);
	}

	private void checkListener() {
		if (typeWatcher.updateAndGet(old -> old == null ? ListenerType.LISTENER : old) != ListenerType.LISTENER) {
			throw new IllegalStateException("An RSA 1.1 EndpointListener must not be "
					+ "called in addition to an EndpointEventListener from the same bundle");
		}
	}
	
	protected abstract void handleEvent(Bundle client, EndpointEvent event, String filter);

}
