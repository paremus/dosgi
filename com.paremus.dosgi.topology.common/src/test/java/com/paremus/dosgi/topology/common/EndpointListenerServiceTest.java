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

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.ADDED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.REMOVED;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;

@RunWith(MockitoJUnitRunner.class)
public class EndpointListenerServiceTest {

	public static final String FILTER = "FOO";
	
	@Mock
	Bundle b;
	
	@Mock
	EndpointDescription ed;
	
	EndpointListenerService listenerService;
	
	@Before
	public void setUp() {
		
		listenerService = mock(EndpointListenerService.class, withSettings().useConstructor(b)
				.defaultAnswer(Answers.CALLS_REAL_METHODS));
		
	}
	
	@Test
	public void testEndpointAdded() {
		listenerService.endpointAdded(ed, FILTER);
		
		verify(listenerService).handleEvent(same(b), argThat(isEventWith(ADDED, ed)), eq(FILTER));
	}

	@Test
	public void testEndpointRemoved() {
		listenerService.endpointRemoved(ed, FILTER);
		
		verify(listenerService).handleEvent(same(b), argThat(isEventWith(REMOVED, ed)), eq(FILTER));
	}

	@Test
	public void testEndpointChanged() {
		
		EndpointEvent event = new EndpointEvent(ADDED, ed);
		listenerService.endpointChanged(event, FILTER);
		
		verify(listenerService).handleEvent(same(b), same(event), eq(FILTER));
	}
	
	@Test
	public void testMultipleUseAsListener() {
		
		listenerService.endpointAdded(ed, FILTER);
		listenerService.endpointRemoved(ed, FILTER);
		
		InOrder inOrder = Mockito.inOrder(listenerService);
		inOrder.verify(listenerService).handleEvent(same(b), argThat(isEventWith(ADDED, ed)), eq(FILTER));
		inOrder.verify(listenerService).handleEvent(same(b), argThat(isEventWith(REMOVED, ed)), eq(FILTER));
	}

	@Test
	public void testMultipleUseAsEventListener() {
		
		listenerService.endpointChanged(new EndpointEvent(ADDED, ed), FILTER);
		listenerService.endpointChanged(new EndpointEvent(REMOVED, ed), FILTER);
		
		InOrder inOrder = Mockito.inOrder(listenerService);
		inOrder.verify(listenerService).handleEvent(same(b), argThat(isEventWith(ADDED, ed)), eq(FILTER));
		inOrder.verify(listenerService).handleEvent(same(b), argThat(isEventWith(REMOVED, ed)), eq(FILTER));
	}
	
	@Test
	public void testMixedUseEventListener() {
		
		EndpointEvent event = new EndpointEvent(ADDED, ed);
		listenerService.endpointChanged(event, FILTER);

		try {
			listenerService.endpointRemoved(ed, FILTER);
			fail("Should throw an Exception");
		} catch (IllegalStateException e) {
			
		}
	}

	@Test
	public void testMixedUseListenerEvent() {
		
		listenerService.endpointAdded(ed, FILTER);
		
		try {
			listenerService.endpointChanged(new EndpointEvent(REMOVED, ed), FILTER);
			fail("Should throw an Exception");
		} catch (IllegalStateException e) {
			
		}
	}
	
	
	private ArgumentMatcher<EndpointEvent> isEventWith(int eventType, EndpointDescription ed) {
		return new ArgumentMatcher<EndpointEvent>() {

				@Override
				public boolean matches(EndpointEvent argument) {
					return argument.getType() == eventType && argument.getEndpoint().equals(ed);
				}
			};
	}

}
