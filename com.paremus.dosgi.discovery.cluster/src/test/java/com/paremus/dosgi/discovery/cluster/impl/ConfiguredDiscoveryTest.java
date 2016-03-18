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
package com.paremus.dosgi.discovery.cluster.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;

import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfiguredDiscoveryTest {

	private static final UUID LOCAL_UUID = new UUID(123, 456);
	
	@Mock
	Config config;

	@Before
	public void setUp() {
		Mockito.when(config.additional_filters()).thenReturn(new String[0]);
		Mockito.when(config.local_id_filter_extension()).thenReturn("");
	}
	
	@Test
	public void testDefaultConfig() {
		Hashtable<String, Object> filters = ConfiguredDiscovery.getFilters(config, LOCAL_UUID);
		
		assertTrue(filters.containsKey(ENDPOINT_LISTENER_SCOPE));
		
		@SuppressWarnings("unchecked")
		List<String> filterList = (List<String>) filters.get(ENDPOINT_LISTENER_SCOPE);
		
		assertEquals(1, filterList.size());
		assertEquals("(endpoint.framework.uuid=" + LOCAL_UUID + ")", filterList.get(0));
	}

	@Test
	public void testFilterExtensionConfig() {
		
		Mockito.when(config.local_id_filter_extension()).thenReturn("(foo=bar)");
		
		Hashtable<String, Object> filters = ConfiguredDiscovery.getFilters(config, LOCAL_UUID);
		
		assertTrue(filters.containsKey(ENDPOINT_LISTENER_SCOPE));
		
		@SuppressWarnings("unchecked")
		List<String> filterList = (List<String>) filters.get(ENDPOINT_LISTENER_SCOPE);
		
		assertEquals(1, filterList.size());
		assertEquals("(&(foo=bar)(endpoint.framework.uuid=" + LOCAL_UUID + "))", filterList.get(0));
	}

	@Test
	public void testFilterExtensionPlaceholderConfig() {
		
		Mockito.when(config.local_id_filter_extension()).thenReturn("(foo=${LOCAL_FW_UUID})");
		
		Hashtable<String, Object> filters = ConfiguredDiscovery.getFilters(config, LOCAL_UUID);
		
		assertTrue(filters.containsKey(ENDPOINT_LISTENER_SCOPE));
		
		@SuppressWarnings("unchecked")
		List<String> filterList = (List<String>) filters.get(ENDPOINT_LISTENER_SCOPE);
		
		assertEquals(1, filterList.size());
		assertEquals("(&(foo=" + LOCAL_UUID + ")(endpoint.framework.uuid=" + LOCAL_UUID + "))", filterList.get(0));
	}

	@Test
	public void testAdditionalFilterConfig() {
		
		Mockito.when(config.additional_filters()).thenReturn(new String[] {"(foo=bar)", "(fizz=buzz)"});
		
		Hashtable<String, Object> filters = ConfiguredDiscovery.getFilters(config, LOCAL_UUID);
		
		assertTrue(filters.containsKey(ENDPOINT_LISTENER_SCOPE));
		
		@SuppressWarnings("unchecked")
		List<String> filterList = (List<String>) filters.get(ENDPOINT_LISTENER_SCOPE);
		
		assertEquals(3, filterList.size());
		assertEquals("(endpoint.framework.uuid=" + LOCAL_UUID + ")", filterList.get(0));
		assertEquals("(foo=bar)", filterList.get(1));
		assertEquals("(fizz=buzz)", filterList.get(2));
	}

	@Test
	public void testAdditionalFilterPlaceholderConfig() {
		
		Mockito.when(config.additional_filters()).thenReturn(new String[] {"(foo=${LOCAL_FW_UUID})", "(fizz=${LOCAL_FW_UUID})"});
		
		Hashtable<String, Object> filters = ConfiguredDiscovery.getFilters(config, LOCAL_UUID);
		
		assertTrue(filters.containsKey(ENDPOINT_LISTENER_SCOPE));
		
		@SuppressWarnings("unchecked")
		List<String> filterList = (List<String>) filters.get(ENDPOINT_LISTENER_SCOPE);
		
		assertEquals(3, filterList.size());
		assertEquals("(endpoint.framework.uuid=" + LOCAL_UUID + ")", filterList.get(0));
		assertEquals("(foo=" + LOCAL_UUID + ")", filterList.get(1));
		assertEquals("(fizz=" + LOCAL_UUID + ")", filterList.get(2));
	}

}
