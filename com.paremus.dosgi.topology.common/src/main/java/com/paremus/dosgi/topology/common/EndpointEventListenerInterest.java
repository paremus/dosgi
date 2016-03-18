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

import static org.osgi.framework.Constants.SERVICE_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndpointEventListenerInterest {
	private static final Logger logger = LoggerFactory.getLogger(EndpointEventListenerInterest.class);
	
	private List<String> filters;
	private final EndpointEventListener listener;
	private final ServiceReference<?> listenerRef;
	
	private final Map<EndpointDescription, String> trackedEndpoints = new HashMap<>();
	
	public EndpointEventListenerInterest(EndpointEventListener listener,
			ServiceReference<?> listenerRef, List<String> filters) {
		this.listener = listener;
		this.listenerRef = listenerRef;
		updateFilters(filters);
	}
	
	public void updateFilters(List<String> filters) {
		
		this.filters = new ArrayList<>(filters);
		if(this.filters.isEmpty()) {
			logger.warn("The RSA endpoint listener {} with service id {} from bundle {} does not specify any filters so no endpoints will be passed to it", 
					new Object[] {listener, listenerRef.getProperty(SERVICE_ID), listenerRef.getBundle()});
		}
		trackedEndpoints.keySet().removeIf(ed -> getMatchingFilter(ed) == null);
	}
	
	public void notify(EndpointDescription oldEd, EndpointDescription newEd) {
		if(oldEd == null && newEd != null) {
			String filter = getMatchingFilter(newEd);
			if(filter != null && trackedEndpoints.put(newEd, filter) == null) {
				listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, newEd), filter);
			}
		} else if (oldEd != null && newEd != null) {
			String oldFilter = trackedEndpoints.get(oldEd);
			if(oldFilter != null) {
				String filter = getMatchingFilter(newEd);
				if(filter != null) {
					trackedEndpoints.put(newEd, filter);
					listener.endpointChanged(new EndpointEvent(EndpointEvent.MODIFIED, newEd), filter);
				} else {
					trackedEndpoints.remove(oldEd);
					listener.endpointChanged(new EndpointEvent(EndpointEvent.MODIFIED_ENDMATCH, newEd), oldFilter);
				}
			} else {
				notify(null, newEd);
			}
		} else if (oldEd != null && newEd == null) {
			String oldFilter = trackedEndpoints.remove(oldEd);
			if(oldFilter != null) {
				listener.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, oldEd), oldFilter);
			}
		}
	}

	private String getMatchingFilter(EndpointDescription ed) {
		return filters.stream().filter(ed::matches).findFirst().orElse(null);
	}
}
