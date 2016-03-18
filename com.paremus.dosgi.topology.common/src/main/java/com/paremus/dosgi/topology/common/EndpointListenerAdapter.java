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

import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class EndpointListenerAdapter implements EndpointEventListener {

	private static final Logger logger = LoggerFactory.getLogger(EndpointListenerAdapter.class);
	
	private final EndpointListener listener;
	
	public EndpointListenerAdapter(EndpointListener listener) {
		this.listener = listener;
	}

	@Override
	public void endpointChanged(EndpointEvent event, String filter) {
		switch(event.getType()) {
		case EndpointEvent.MODIFIED :
				listener.endpointRemoved(event.getEndpoint(), filter);
			case EndpointEvent.ADDED :
				listener.endpointAdded(event.getEndpoint(), filter);
				break;
			case EndpointEvent.MODIFIED_ENDMATCH :
			case EndpointEvent.REMOVED :
				listener.endpointRemoved(event.getEndpoint(), filter);
				break;
			default :
				logger.error("An unknown event type {} occurred for endpoint {}", 
						new Object[] {event.getType(), event.getEndpoint()});
		}
	}

	@Override
	public String toString() {
		return "EventListener wrapping: " + listener.toString(); 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((listener == null) ? 0 : listener.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EndpointListenerAdapter other = (EndpointListenerAdapter) obj;
		if (listener == null) {
			if (other.listener != null)
				return false;
		} else if (!listener.equals(other.listener))
			return false;
		return true;
	}
}
