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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.Constants.OBJECTCLASS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import com.paremus.dosgi.discovery.cluster.scope.EndpointFilter;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

@RunWith(MockitoJUnitRunner.class)
public class LocalDiscoveryListenerTest {

	private static final UUID LOCAL_UUID = new UUID(123, 456);
	private static final UUID REMOTE_UUID_1 = new UUID(987, 654);
	private static final UUID REMOTE_UUID_2 = new UUID(876, 543);
	private static final String ENDPOINT_1 = new UUID(234, 567).toString();
	private static final String ENDPOINT_2 = new UUID(345, 678).toString();

	public static final String CLUSTER_A = "clusterA";
	public static final String CLUSTER_B = "clusterB";
	
	@Mock
	Bundle clientA;

	@Mock
	Bundle clientB;

	@Mock
	RemoteDiscoveryEndpoint remoteA;

	@Mock
	RemoteDiscoveryEndpoint remoteB;

	@Mock
	EndpointFilter filter;

	private Semaphore sem = new Semaphore(0);
	
	private LocalDiscoveryListener ldl;
	
	private EventExecutorGroup workers;
	
	@Before
	public void setUp() throws Exception {
		
		workers = new DefaultEventExecutorGroup(1);
		
		ldl = new LocalDiscoveryListener(20000, workers);
		
		Mockito.doAnswer(i -> { sem.release(((Integer)i.getArguments()[0])); return null; }).when(remoteA)
			.publishEndpoint(Mockito.any(Integer.class), Mockito.any(EndpointDescription.class), Mockito.anyBoolean());
		Mockito.doAnswer(i -> { sem.release(8 * ((Integer)i.getArguments()[0])); return null; }).when(remoteB)
			.publishEndpoint(Mockito.any(Integer.class), Mockito.any(EndpointDescription.class), Mockito.anyBoolean());
		Mockito.doAnswer(i -> { sem.release(64 * ((Integer)i.getArguments()[0])); return null; }).when(remoteA)
			.revokeEndpoint(Mockito.any(Integer.class), Mockito.any(EndpointDescription.class));
		Mockito.doAnswer(i -> { sem.release(512 * ((Integer)i.getArguments()[0])); return null; }).when(remoteB)
			.revokeEndpoint(Mockito.any(Integer.class), Mockito.any(EndpointDescription.class));
	}
	
	@After
	public void tearDown() throws Exception {
		//This makes sure that all of the tests pick up all of the events
		assertEquals(0, sem.availablePermits());
		ldl.destroy();
		
		workers.shutdownGracefully(10, 1000, TimeUnit.MILLISECONDS).sync();
	}

	@Test
	public void testGetService() {
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		EndpointEventListener listenerB = (EndpointEventListener) ldl.getService(clientB, null);
		
		assertNotSame(listenerA, listenerB);
	}
	
	@Test
	public void testRegisterEndpoints() throws Exception{
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);

		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		
		assertTrue("No async registration with remote endpoint A", sem.tryAcquire(1, SECONDS));
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		assertTrue("No async registration with remote endpoint b", sem.tryAcquire(8, 1, SECONDS));
	}

	@Test
	public void testModifyEndpoint() throws Exception{
		
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);

		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		
		assertTrue("No async registration with remote endpoint A", sem.tryAcquire(1, SECONDS));
		Mockito.verify(remoteA).publishEndpoint(1, ed, false);

		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.MODIFIED, ed), null);
		
		assertTrue("No async update with remote endpoint A", sem.tryAcquire(2, 1, SECONDS));
		Mockito.verify(remoteA).publishEndpoint(2, ed, false);

		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.MODIFIED, ed), null);
		
		assertTrue("No async update with remote endpoint A", sem.tryAcquire(3, 1, SECONDS));
		Mockito.verify(remoteA).publishEndpoint(3, ed, false);
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		assertTrue("No async registration with remote endpoint b", sem.tryAcquire(24, 1, SECONDS));
	}
	
	@Test
	public void testRevokeEndpoint() throws Exception{
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);

		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		
		assertTrue("No async registration with remote endpoint A", sem.tryAcquire(1, SECONDS));

		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, ed), null);
		
		assertTrue("No async removal with remote endpoint A", sem.tryAcquire(128, 1, SECONDS));
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		assertFalse("Should not be a registration with remote endpoint b", sem.tryAcquire(1, SECONDS));
	}

	@Test
	public void testUngetRevokesAllEndpoints() throws Exception{
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);
		EndpointDescription ed2 = getTestEndpointDescription(ENDPOINT_2);

		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed2), null);
		
		assertTrue("No async registrations with the remotes", sem.tryAcquire(2 + 16, 1, SECONDS));

		ldl.ungetService(clientA, null, listenerA);
		
		//Two removes with count 2
		assertTrue("No async removal for endpoints", sem.tryAcquire(256 + 2048, 1, SECONDS));
	}

	@Test
	public void testMultipleSponsorsPreventsRevocationOnUnget() throws Exception{
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);
		EndpointDescription ed2 = getTestEndpointDescription(ENDPOINT_2);
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		EndpointEventListener listenerB = (EndpointEventListener) ldl.getService(clientB, null);
		listenerB.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed2), null);
		
		assertTrue("No async registrations with the remotes", sem.tryAcquire(2 + 16, 1, SECONDS));
		
		ldl.ungetService(clientA, null, listenerA);
		
		assertTrue("No async removal for endpoints", sem.tryAcquire(128 + 1024, 1, SECONDS));
		
		Mockito.verify(remoteA, Mockito.never()).revokeEndpoint(Mockito.anyInt(), Mockito.same(ed2));
		Mockito.verify(remoteB, Mockito.never()).revokeEndpoint(Mockito.anyInt(), Mockito.same(ed2));
	}

	@Test
	public void testRepublication() throws Exception{
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);
		EndpointDescription ed2 = getTestEndpointDescription(ENDPOINT_2);
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		EndpointEventListener listenerB = (EndpointEventListener) ldl.getService(clientB, null);
		listenerB.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed2), null);
		
		assertTrue("No async registrations with the remotes", sem.tryAcquire(2 + 16, 1, SECONDS));
		
		ldl.republish(ENDPOINT_2, REMOTE_UUID_1);
		
		assertTrue("No republicationOccurred", sem.tryAcquire(1, SECONDS));
	}

	@Test
	public void testReminder() throws Exception{
		Mockito.doAnswer(i -> { sem.release(4096); return null;}).when(remoteA).sendReminder();
		Mockito.doAnswer(i -> { sem.release(32768); return null;}).when(remoteB).sendReminder();
		ldl.destroy();
		ldl = new LocalDiscoveryListener(2000, workers);
		
		EndpointDescription ed = getTestEndpointDescription(ENDPOINT_1);
		EndpointDescription ed2 = getTestEndpointDescription(ENDPOINT_2);
		
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		EndpointEventListener listenerA = (EndpointEventListener) ldl.getService(clientA, null);
		listenerA.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), null);
		EndpointEventListener listenerB = (EndpointEventListener) ldl.getService(clientB, null);
		listenerB.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed2), null);
		
		assertTrue("No async registrations with the remotes", sem.tryAcquire(2 + 16, 1, SECONDS));
		
		assertTrue("No reminder sent", sem.tryAcquire(4096 + 32768, 3, SECONDS));
		ldl.destroy();
		
	}

	@Test
	public void testRemovalOfRemoteEndpoint() {
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		ldl.removeRemote(CLUSTER_A, REMOTE_UUID_1);
		
		Mockito.verify(remoteA).stopCalling();
	}

	@Test
	public void testRemovalOfAllEndpointsInCluster() {
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		
		ldl.removeRemotesForCluster(CLUSTER_A);
		
		Mockito.verify(remoteA).stopCalling();
		Mockito.verify(remoteB).stopCalling();
	}

	@Test
	public void testSponsoredEndpointNotRemoved() {
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_B, REMOTE_UUID_1, 2345, filter, () -> remoteA);
		
		ldl.removeRemotesForCluster(CLUSTER_A);
		Mockito.verify(remoteA, Mockito.never()).stopCalling();
		ldl.removeRemotesForCluster(CLUSTER_B);
		Mockito.verify(remoteA).stopCalling();
	}

	@Test
	public void testSponsoredEndpointNotRemovedForCluster() {
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_2, 2345, filter, () -> remoteB);
		ldl.updateRemote(CLUSTER_B, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		ldl.removeRemotesForCluster(CLUSTER_A);
		Mockito.verify(remoteA, Mockito.never()).stopCalling();
		Mockito.verify(remoteB).stopCalling();
		ldl.removeRemotesForCluster(CLUSTER_B);
		Mockito.verify(remoteA).stopCalling();
	}

	@Test
	public void testSponsoredEndpointNotRemovedUnlessAllClustersAre() {
		ldl.updateRemote(CLUSTER_A, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		ldl.updateRemote(CLUSTER_B, REMOTE_UUID_1, 1234, filter, () -> remoteA);
		
		ldl.removeRemote(CLUSTER_A, REMOTE_UUID_1);
		Mockito.verify(remoteA, Mockito.never()).stopCalling();
		ldl.removeRemote(CLUSTER_B, REMOTE_UUID_1);
		Mockito.verify(remoteA).stopCalling();
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
