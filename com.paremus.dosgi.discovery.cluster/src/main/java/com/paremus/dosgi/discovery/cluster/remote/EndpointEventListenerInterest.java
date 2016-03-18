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
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

class EndpointEventListenerInterest extends AbstractListenerInterest {
	public final EndpointEventListener listener;
	
	public EndpointEventListenerInterest(EndpointEventListener listener,
			ServiceReference<?> ref) {
		super(ref);
		this.listener = listener;
	}

	@Override
	public void sendEvent(EndpointEvent ee, String filter) {
		listener.endpointChanged(ee, filter);
	}
}
