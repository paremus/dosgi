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

import static org.junit.Assert.assertEquals;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.ADDED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.MODIFIED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.REMOVED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointListener;

@SuppressWarnings("deprecation")
@RunWith(MockitoJUnitRunner.class)
public class EndpointListenerAdapterTest {

	private static final String FILTER = "foo";
	
	@Mock
	EndpointListener listener;
	
	@Mock
	EndpointDescription ed;
	
	@Test
	public void testAdd() {
		EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);

		adapter.endpointChanged(new EndpointEvent(ADDED, ed), FILTER);
		
		Mockito.verify(listener).endpointAdded(ed, FILTER);
		Mockito.verifyNoMoreInteractions(listener);
		
	}

	@Test
	public void testRemove() {
		EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
		
		adapter.endpointChanged(new EndpointEvent(REMOVED, ed), FILTER);
		
		Mockito.verify(listener).endpointRemoved(ed, FILTER);
		Mockito.verifyNoMoreInteractions(listener);
		
	}
	
	@Test
	public void testModified() {
		EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
		
		adapter.endpointChanged(new EndpointEvent(MODIFIED, ed), FILTER);
		
		InOrder inOrder = Mockito.inOrder(listener);
		inOrder.verify(listener).endpointRemoved(ed, FILTER);
		inOrder.verify(listener).endpointAdded(ed, FILTER);
		inOrder.verifyNoMoreInteractions();
		
	}

	@Test
	public void testEquals() {
		EndpointListenerAdapter adapter = new EndpointListenerAdapter(listener);
		EndpointListenerAdapter adapter2 = new EndpointListenerAdapter(listener);
		
		assertEquals(adapter, adapter2);
		
	}
}
