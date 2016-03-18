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
package com.paremus.dosgi.discovery.cluster.local;

import static java.net.InetAddress.getLoopbackAddress;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.eq;
import static org.osgi.framework.Constants.OBJECTCLASS;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import com.paremus.dosgi.discovery.cluster.comms.SocketComms;
import com.paremus.dosgi.discovery.cluster.scope.EndpointFilter;

@RunWith(MockitoJUnitRunner.class)
public class RemoteDiscoveryEndpointTest {

	private static final UUID LOCAL_UUID = new UUID(123, 456);
	private static final UUID REMOTE_UUID_1 = new UUID(987, 654);
	private static final String ENDPOINT_1 = new UUID(234, 567).toString();
	private static final String ENDPOINT_2 = new UUID(345, 678).toString();

	public static final String CLUSTER_A = "clusterA";
	public static final String CLUSTER_B = "clusterB";
	
	@Mock
	SocketComms comms;

	@Mock
	EndpointFilter filter;

	@Test
	public void testOpen() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter);
		Mockito.verify(comms, Mockito.never())
			.newDiscoveryEndpoint(REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));
	
		rde.open();
		
		Mockito.verify(comms).newDiscoveryEndpoint(REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
	}

	@Test
	public void testStopCalling() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter);
		
		rde.stopCalling();
		
		Mockito.verify(comms).stopCalling(REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
	}
	
	@Test
	public void testOnlyPublishedIfAccepted() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.verify(comms, Mockito.never()).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 
				1, REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));

		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(true);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.verify(comms).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 
				1, REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));
	}

	@Test
	public void testIgnoreRepeatPublicationsUnlessForced() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(true);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.verify(comms).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 
				1, REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));
		
		rde.publishEndpoint(2, getTestEndpointDescription(ENDPOINT_1), false);
		rde.publishEndpoint(2, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.verify(comms).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 
				2, REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));

		rde.publishEndpoint(2, getTestEndpointDescription(ENDPOINT_1), true);
		
		Mockito.verify(comms, Mockito.times(2)).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 
				2, REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));

	}

	@Test
	public void testRevokeEndpoint() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(true);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		rde.revokeEndpoint(2, getTestEndpointDescription(ENDPOINT_1));
		
		Mockito.verify(comms).revokeEndpoint(ENDPOINT_1, 2, REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
		
	}

	@Test
	public void testRevokeNotAcceptedEndpoint() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(false);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		rde.revokeEndpoint(2, getTestEndpointDescription(ENDPOINT_1));
		
		Mockito.verify(comms, Mockito.never()).revokeEndpoint(ENDPOINT_1, 2, REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
		
	}
	
	@Test
	public void testFilterChangeDoesNotRevokeEndpoint() {
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(true);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.when(filter.accept(eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(false);
		
		rde.update(1234, filter);
		
		Mockito.verify(comms, Mockito.never()).revokeEndpoint(Mockito.eq(ENDPOINT_1), 
				Mockito.anyInt(), Mockito.any(UUID.class), Mockito.any(InetSocketAddress.class));
	}

	@Test
	public void testPortChangeResetsComms() {
		Mockito.when(filter.accept(Mockito.any(EndpointDescription.class), anySet())).thenReturn(true);
		
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 
		
		rde.open();
		rde.update(1234, filter);
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		Mockito.verify(comms, Mockito.never()).stopCalling(REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
		Mockito.verify(comms).publishEndpoint(getTestEndpointDescription(ENDPOINT_1), 1,
				REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));

		rde.update(2345, filter);
		
		Mockito.verify(comms).stopCalling(REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));
		Mockito.verify(comms).newDiscoveryEndpoint(REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 2345));
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_2), false);
		
		Mockito.verify(comms).publishEndpoint(getTestEndpointDescription(ENDPOINT_2), 1,
				REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 2345));
		
	}
	
	@Test
	public void testSendEmptyReminders() throws Exception{
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		rde.sendReminder();
		
		Mockito.verify(comms, Mockito.never()).sendReminder(Mockito.anyCollection(), 
				Mockito.anyInt(), Mockito.any(UUID.class), Mockito.any(InetSocketAddress.class));
	}
	
	@Test
	public void testSendReminders() throws Exception{
		RemoteDiscoveryEndpoint rde = new RemoteDiscoveryEndpoint(REMOTE_UUID_1, CLUSTER_A, emptySet(), comms, 
				getLoopbackAddress(), 1234, filter); 	
		
		Mockito.when(filter.accept(Mockito.eq(getTestEndpointDescription(ENDPOINT_1)), anySet())).thenReturn(true);
		Mockito.when(filter.accept(Mockito.eq(getTestEndpointDescription(ENDPOINT_2)), anySet())).thenReturn(true);
		
		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_1), false);
		
		rde.sendReminder();
		
		Mockito.verify(comms).sendReminder(Collections.singleton(ENDPOINT_1), 1, REMOTE_UUID_1, 
				new InetSocketAddress(getLoopbackAddress(), 1234));

		rde.publishEndpoint(1, getTestEndpointDescription(ENDPOINT_2), false);
		
		rde.sendReminder();
		
		Mockito.verify(comms).sendReminder(new HashSet<>(asList(ENDPOINT_1, ENDPOINT_2)), 2, 
				REMOTE_UUID_1, new InetSocketAddress(getLoopbackAddress(), 1234));
	}

	private EndpointDescription getTestEndpointDescription(String endpointId) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();

        // required
        m.put(OBJECTCLASS, new String[]{"com.acme.HelloService", "some.other.Service"});
        m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, LOCAL_UUID.toString());
        m.put(RemoteConstants.ENDPOINT_ID, endpointId);
        m.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.valueOf(42));
        m.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");

        return new EndpointDescription(m);
	}
}
