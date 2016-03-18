/*-
 * #%L
 * com.paremus.dosgi.discovery.cluster
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
package com.paremus.dosgi.discovery.cluster.remote;

import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractListenerInterest {
	private static final Logger logger = LoggerFactory.getLogger(AbstractListenerInterest.class);
	
	private List<String> filters;
	
	public AbstractListenerInterest(ServiceReference<?> ref) {
		updateFilters(ref);
	}

	@SuppressWarnings("unchecked")
	public synchronized List<String> updateFilters(ServiceReference<?> ref) {
		List<String> oldFilters = filters;
		Object o = ref.getProperty(ENDPOINT_LISTENER_SCOPE);
		if(o instanceof String) {
			filters = Collections.singletonList((String)o);
		} else if (o instanceof String[]) {
			filters = new ArrayList<>();
			for(String s : (String[]) o) {
				filters.add(s);
			}
		} else if (o instanceof Collection) {
			filters = new ArrayList<>((Collection<String>)o);
		} else {
			logger.warn("The RSA endpoint listener {} does not specify any filters so no endpoints will be passed to it", 
					ref.getProperty("service.id"));
			filters = Collections.emptyList();
		}
		return oldFilters;
	}
	
	public synchronized String isInterested(EndpointDescription e) {
		return filters.stream().filter(f -> {
				try {
					return e.matches(f);
				} catch (IllegalArgumentException iae) {
					logger.warn("The endpoint filter {} is invalid and cannot be matched", f);
					return false;
				}
			}).findFirst().orElse(null);
	}
	
	public abstract void sendEvent(EndpointEvent ee, String filter);
}
