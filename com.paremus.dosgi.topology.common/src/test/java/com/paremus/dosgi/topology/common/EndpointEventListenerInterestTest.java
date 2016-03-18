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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.ADDED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.MODIFIED;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.MODIFIED_ENDMATCH;
import static org.osgi.service.remoteserviceadmin.EndpointEvent.REMOVED;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.ServiceReference;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;

@RunWith(MockitoJUnitRunner.class)
public class EndpointEventListenerInterestTest {

	private static final String FILTER = "foo";
	
	private static final List<String> filters = Arrays.asList(FILTER, "bar");
	
	@Mock
	EndpointEventListener listener;
	
	@Mock
	ServiceReference<?> ref;
	
	@Mock
	EndpointDescription edA, edB;
	
	@Test
	public void testNewAdd() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		when(edA.matches(FILTER)).thenReturn(true);
		
		interest.notify(null, edA);
		
		verify(listener).endpointChanged(argThat(isEventWith(ADDED, edA)), eq(FILTER));
		verifyNoMoreInteractions(listener);
	}

	@Test
	public void testNewAddNoMatch() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		interest.notify(null, edA);
		
		verifyZeroInteractions(listener);
	}
	
	@Test
	public void testNoMatchToMatchUnseen() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);

		when(edB.matches(FILTER)).thenReturn(true);
		
		interest.notify(edA, edB);
		
		verify(listener).endpointChanged(argThat(isEventWith(ADDED, edB)), eq(FILTER));
		verifyNoMoreInteractions(listener);
	}

	@Test
	public void testMatchToMatchSeen() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		when(edA.matches(FILTER)).thenReturn(true);
		when(edB.matches(FILTER)).thenReturn(true);
		
		interest.notify(null, edA);
		interest.notify(edA, edB);
		
		InOrder inOrder = Mockito.inOrder(listener);
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(ADDED, edA)), eq(FILTER));
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(MODIFIED, edB)), eq(FILTER));
		inOrder.verifyNoMoreInteractions();
	}

	@Test
	public void testMatchToNoMatchSeen() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		when(edA.matches(FILTER)).thenReturn(true);
		
		interest.notify(null, edA);
		interest.notify(edA, edB);
		
		InOrder inOrder = Mockito.inOrder(listener);
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(ADDED, edA)), eq(FILTER));
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(MODIFIED_ENDMATCH, edB)), eq(FILTER));
		inOrder.verifyNoMoreInteractions();
	}

	@Test
	public void testNoMatchToNullUneen() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		interest.notify(null, edA);
		interest.notify(edA, null);
		
		verifyZeroInteractions(listener);
	}
	
	@Test
	public void testMatchToNullSeen() {
		
		EndpointEventListenerInterest interest = new EndpointEventListenerInterest(listener, ref, filters);
		
		when(edA.matches(FILTER)).thenReturn(true);
		
		interest.notify(null, edA);
		interest.notify(edA, null);
		
		InOrder inOrder = Mockito.inOrder(listener);
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(ADDED, edA)), eq(FILTER));
		inOrder.verify(listener).endpointChanged(argThat(isEventWith(REMOVED, edA)), eq(FILTER));
		inOrder.verifyNoMoreInteractions();
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
