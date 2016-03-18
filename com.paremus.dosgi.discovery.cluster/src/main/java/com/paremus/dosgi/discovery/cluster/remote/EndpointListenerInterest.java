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

import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
class EndpointListenerInterest extends AbstractListenerInterest {
	
	private static final Logger logger = LoggerFactory.getLogger(EndpointListenerInterest.class);
	
	public final EndpointListener listener;
	
	public EndpointListenerInterest(EndpointListener listener,
			ServiceReference<?> ref) {
		super(ref);
		this.listener = listener;
	}

	@Override
	public void sendEvent(EndpointEvent ee, String filter) {
		EndpointDescription endpoint = ee.getEndpoint();
		switch(ee.getType()) {
			case EndpointEvent.MODIFIED :
				if(logger.isDebugEnabled()) { 
					logger.debug("EndpointListener services are unable to handle modification, removing and re-adding the endpoint {}", endpoint.getId()); 
				}
				listener.endpointRemoved(endpoint, filter);
			case EndpointEvent.ADDED :
				listener.endpointAdded(endpoint, filter);
				break;
			case EndpointEvent.MODIFIED_ENDMATCH :
			case EndpointEvent.REMOVED :
				listener.endpointRemoved(endpoint, filter);
		}
	}
}
