/*-
 * #%L
 * com.paremus.dosgi.discovery.cluster.test
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
package com.paremus.dosgi.discovery.cluster.test;

import static java.net.InetAddress.getLoopbackAddress;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.Constants.OBJECTCLASS;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.EndpointEvent;
import org.osgi.service.remoteserviceadmin.EndpointEventListener;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.util.tracker.ServiceTracker;

import com.paremus.cluster.ClusterInformation;
import com.paremus.cluster.listener.Action;
import com.paremus.cluster.listener.ClusterListener;
import com.paremus.net.info.ClusterNetworkInformation;

@RunWith(JUnit4.class)
public class PartitionRecoveryTest {

	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
	private final Semaphore updateSemaphore = new Semaphore(0);
	private final Semaphore removeSemaphore = new Semaphore(0);
	
    @SuppressWarnings("rawtypes")
	private final ServiceFactory listener = new TestListener();

    private ServiceRegistration<?> reg;
    
	private ServiceRegistration<?> rootCIReg;

	private ServiceRegistration<?> rootCNIReg;

	private List<Framework> childFrameworks;
    
	@Before
	public void setup() {
		// This is necessary due to a leak seen in Netty due to the finalization of
		// the buffer pool...
		System.setProperty("io.netty.allocator.type", "unpooled");
	}
	
	@After
	public void tearDown() throws Exception {
		if(childFrameworks != null) {
			childFrameworks.forEach((fw) -> { 
				try{
					fw.stop();
				} catch (BundleException be){}
			});
			childFrameworks.forEach((fw) -> { 
				try{
					fw.waitForStop(2000);
				} catch (Exception e){}
			});
		}
		
		
		ServiceReference<ConfigurationAdmin> ref = context.getServiceReference(ConfigurationAdmin.class);
		if(ref != null) {
			ConfigurationAdmin cm = context.getService(ref);
			if(cm != null) {
				Configuration[] configs = cm.listConfigurations(null);
				if(configs != null) {
					for(Configuration c : configs) {
						c.delete();
					}
				}
			}
		}
		
		try {
			reg.unregister();
		} catch (IllegalStateException ise) {
			
		}
		try {
			rootCIReg.unregister();
		} catch (IllegalStateException ise) {
			
		}
		try {
			rootCNIReg.unregister();
		} catch (IllegalStateException ise) {
			
		}
		knownProps.clear();
		Thread.sleep(100);
	}
	
	private final ConcurrentMap<UUID, ConcurrentMap<String, Map<String, Object>>> knownProps = new ConcurrentHashMap<>();

	private final ConcurrentMap<UUID, Map<String, byte[]>> clusterData = new ConcurrentHashMap<>();

	@SuppressWarnings("rawtypes")
	public class TestListener implements ServiceFactory {
		@SuppressWarnings("unchecked")
		@Override
		public Object getService(Bundle bundle, ServiceRegistration registration) {
			try {
				return Proxy.newProxyInstance(bundle.adapt(BundleWiring.class).getClassLoader(), 
						new Class[] {bundle.loadClass(EndpointEventListener.class.getName())}, 
						(o, m, a) ->{
							;
							Object ed = a[0].getClass().getMethod("getEndpoint").invoke(a[0]);
							Integer type = (Integer) a[0].getClass().getMethod("getType").invoke(a[0]);
							knownProps.compute(UUID.fromString(bundle.getBundleContext().getProperty(Constants.FRAMEWORK_UUID)),
									(k,v) -> {
										ConcurrentMap<String, Map<String, Object>> toReturn;
										toReturn = v == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(v);
										
										try {
											switch(type) {
												case EndpointEvent.MODIFIED :
													updateSemaphore.release();
												case EndpointEvent.ADDED :
													toReturn.put(ed.getClass().getMethod("getId").invoke(ed).toString(),
															(Map<String, Object>) ed.getClass().getMethod("getProperties").invoke(ed));
													break;
												case EndpointEvent.MODIFIED_ENDMATCH :
												case EndpointEvent.REMOVED :
													removeSemaphore.release();
													toReturn.remove(ed.getClass().getMethod("getId").invoke(ed).toString());
											}
										} catch (Exception e) {
											throw new RuntimeException(e);
										}
										return toReturn;
									});
							return null;
						});
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void ungetService(Bundle bundle,
				ServiceRegistration registration, Object service) {
			knownProps.remove(UUID.fromString(context.getProperty(Constants.FRAMEWORK_UUID)));
		}
		
	}
	
    /** 
     * In the simple tests we publish a single endpoint and then simulate a partition
     * There are no new endpoints published and no updates, so we should just see a simple heal
     */
	@Test
	public void testSimpleTotalPartitionRecovery() throws Exception {
		doTestSimplePartitionRecovery(false, true);
	}

	@Test
	public void testSecureSimpleTotalPartitionRecovery() throws Exception {
		doTestSimplePartitionRecovery(true, true);
	}
	
	/** 
	 *  Partial partitions are ones where the recipient cannot see the sender
	 *  but the sender can still see them - they can cause nasty issues!
	 */
	@Test
	public void testSimplePartialPartitionRecovery() throws Exception {
		doTestSimplePartitionRecovery(false, false);
	}
	
	@Test
	public void testSecureSimplePartialPartitionRecovery() throws Exception {
		doTestSimplePartitionRecovery(true, false);
	}

	private void doTestSimplePartitionRecovery(boolean secure, boolean totalPartition) throws Exception {
    	
    	Framework rootFw = context.getBundle(0).adapt(Framework.class);
    	childFrameworks = createFrameworks();
    	Set<UUID> fwIds = Stream.concat(Stream.of(rootFw), childFrameworks.stream())
    			.map(this::getUUID)
    			.collect(toSet());
    	
    	long start;
        
        setupCluster(secure, rootFw, fwIds);
        
        
        EndpointEventListener eel = context.getService(context.getServiceReference(EndpointEventListener.class));
        
        EndpointDescription ed = getTestEndpointDescription("FOO", context);
        
        Map<UUID, Map<String, Map<String, Object>>> expected = childFrameworks.stream().map(this::getUUID)
        		.collect(toMap(identity(), (u) -> Collections.singletonMap(ed.getId(), ed.getProperties())));
        
        start = System.currentTimeMillis();
        eel.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), 
        		"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
        
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
        
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(0, removeSemaphore.availablePermits());
        
        System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
       
        //  Partition time
        
        createPartition(rootFw, totalPartition);
        
        getZoneB().stream().forEach(f -> {
        	expected.put(getUUID(f), Collections.emptyMap());
        });
        
        start = System.currentTimeMillis();
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
        
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(4, removeSemaphore.availablePermits());
        
        System.out.println("Partition propagated after " + (System.currentTimeMillis() - start) + " milliseconds");
        
        //Heal partition
        healPartition(rootFw, totalPartition);
        
        getZoneB().stream().forEach(f -> {
        	expected.put(getUUID(f), Collections.singletonMap(ed.getId(), ed.getProperties()));
        });
        
        
        start = System.currentTimeMillis();
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
        
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(4, removeSemaphore.availablePermits());
        
        System.out.println("Partition healed after " + (System.currentTimeMillis() - start) + " milliseconds");
    }

	
    private void createPartition(Framework rootFw, boolean totalPartition) {
    	Set<Framework> zoneA = getZoneA(rootFw);
        Set<UUID> zoneAIds = zoneA.stream().map(this::getUUID).collect(toSet());

        Set<Framework> zoneB = getZoneB();
        Set<UUID> zoneBIds = zoneB.stream().map(this::getUUID).collect(toSet());
        
        if(totalPartition) {
        	//If a total partition then zone a cannot see zone b
        	zoneA.stream().forEach(f -> notifyClusterListener(f, Action.REMOVED, zoneBIds));
        }
        //The partition is that zone b can no longer see zone a
        zoneB.stream().forEach(f -> {
        	notifyClusterListener(f, Action.REMOVED, zoneAIds);
        });
	}

	private void healPartition(Framework rootFw, boolean totalPartition) {
		
		Set<Framework> zoneA = getZoneA(rootFw);
        Set<UUID> zoneAIds = zoneA.stream().map(this::getUUID).collect(toSet());

        Set<Framework> zoneB = getZoneB();
        Set<UUID> zoneBIds = zoneB.stream().map(this::getUUID).collect(toSet());
        
		if(totalPartition) {
        	zoneA.stream().forEach(f -> notifyClusterListener(f, Action.ADDED, zoneBIds));
        }
        zoneB.stream().forEach(f -> {
        	notifyClusterListener(f, Action.ADDED, zoneAIds);
        });
	}

	/** 
     * In the complex tests we publish a two endpoints and then simulate a partition
     * While the partition is in place we publish a third endpoint in the original zone,
     * a fourth endpoint in the partitioned zone, update one of the original endpoints,
     * and unregister the other original endpoint.
     * 
     * When the partition heals the whole thing should reach a consistent state...
     * 
     */
	@Test
	public void testComplexTotalPartitionRecovery() throws Exception {
		doTestComplexPartitionRecovery(false, true);
	}
	
	@Test
	public void testSecureComplexTotalPartitionRecovery() throws Exception {
		doTestComplexPartitionRecovery(true, true);
	}
	
	@Test
	public void testComplexPartialPartitionRecovery() throws Exception {
		doTestComplexPartitionRecovery(false, false);
	}
	
	@Test
	public void testSecureComplexPartialPartitionRecovery() throws Exception {
		doTestComplexPartitionRecovery(true, false);
	}
	
	private void doTestComplexPartitionRecovery(boolean secure, boolean totalPartition) throws Exception {
		
		Framework rootFw = context.getBundle(0).adapt(Framework.class);
		childFrameworks = createFrameworks();
		Set<UUID> fwIds = Stream.concat(Stream.of(rootFw), childFrameworks.stream())
				.map(this::getUUID)
				.collect(toSet());
		
		setupCluster(secure, rootFw, fwIds);
		
		EndpointEventListener eel = context.getService(context.getServiceReference(EndpointEventListener.class));
		
		EndpointDescription ed = getTestEndpointDescription("FOO", context);
		EndpointDescription ed2 = getTestEndpointDescription("BAR", context);
		
		Map<UUID, Map<String, Map<String, Object>>> expected = childFrameworks.stream().map(this::getUUID)
				.collect(toMap(identity(), (u) -> {
					Map<String, Map<String, Object>> m = new HashMap<>();
					m.put(ed.getId(), ed.getProperties());
					m.put(ed2.getId(), ed2.getProperties());
					return m;
				}));
		
		long start = System.currentTimeMillis();
		eel.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), 
				"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
		
		eel.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed2), 
				"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		assertEquals(0, updateSemaphore.availablePermits());
		assertEquals(0, removeSemaphore.availablePermits());
		
		System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
		
		//  Partition time
		
		createPartition(rootFw, totalPartition);
	        
        getZoneB().stream().forEach(f -> {
        	expected.put(getUUID(f), new HashMap<>());
        });
		
		
		start = System.currentTimeMillis();
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		
		assertEquals(0, updateSemaphore.availablePermits());
		//Four nodes lose two services
		assertEquals(8, removeSemaphore.availablePermits());
		
		System.out.println("Partition propagated after " + (System.currentTimeMillis() - start) + " milliseconds");
		
		//Publish the new endpoint in Zone A
		
		EndpointDescription ed3 = getTestEndpointDescription("BAZ", context);
		
		getZoneA(rootFw).stream()
			.map(this::getUUID)
			.filter(id -> !id.equals(getUUID(rootFw)))
			.forEach(id -> {
				expected.get(id).put(ed3.getId(), ed3.getProperties());
			});
		
		if(!totalPartition) {
			//Zone A can still publish to zone b, so we will see this get published...
			getZoneB().stream()
				.map(this::getUUID)
				.forEach(id -> {
					expected.get(id).put(ed3.getId(), ed3.getProperties());
				});
		}
		
		start = System.currentTimeMillis();
		eel.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed3), 
				"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		assertEquals(0, updateSemaphore.availablePermits());
		assertEquals(8, removeSemaphore.availablePermits());
		
		System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
		
		//Publish the new endpoint in Zone B
		
		HashSet<Framework> zoneB = getZoneB();
		Framework zoneBHost = zoneB.stream().findAny().get();
		
		BundleContext zoneBCtx = zoneBHost.getBundleContext();
    	Object zoneBEel = zoneBCtx.getService(zoneBCtx.getServiceReference(EndpointEventListener.class.getName()));
        
        EndpointDescription ed4 = getTestEndpointDescription("FIZZBUZZ", zoneBHost.getBundleContext());
        Map<String, Object> endpointProps = ed4.getProperties();
		
		zoneB.stream()
			.map(this::getUUID)
			.filter(id -> !id.equals(getUUID(zoneBHost)))
			.forEach(id -> {
				expected.get(id).put(ed4.getId(), endpointProps);
			});
		
		start = System.currentTimeMillis();
		try {
	        Object endpointDesc = zoneBEel.getClass().getClassLoader()
	        		.loadClass(EndpointDescription.class.getName()).getConstructor(Map.class)
	        		.newInstance(endpointProps);
	        
	        Object event = zoneBEel.getClass().getClassLoader()
	        		.loadClass(EndpointEvent.class.getName()).getConstructor(int.class, endpointDesc.getClass())
	        		.newInstance(EndpointEvent.ADDED, endpointDesc);
	        
	        Method endpointChanged = Arrays.stream(zoneBEel.getClass().getMethods())
	        		.filter(m -> m.getName().equals("endpointChanged"))
	        		.findFirst()
	        		.get();
	        endpointChanged.setAccessible(true);
			endpointChanged.invoke(zoneBEel, event, 
	        		"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + getUUID(zoneBHost) + ")");
        
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		assertEquals(0, updateSemaphore.availablePermits());
		assertEquals(8, removeSemaphore.availablePermits());
		
		System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
		
		//Update an original endpoint in Zone A
		
		Map<String, Object> map = new HashMap<>(ed.getProperties());
		map.put("updated", Boolean.TRUE);
		
		EndpointDescription edPrime = new EndpointDescription(map);
		
		getZoneA(rootFw).stream()
			.map(this::getUUID)
			.filter(id -> !id.equals(getUUID(rootFw)))
			.forEach(id -> {
				expected.get(id).put(edPrime.getId(), edPrime.getProperties());
			});
		
		if(!totalPartition) {
			//Zone A can still publish to zone B, so we will see this update published, but it will behave as an add...
			getZoneB().stream()
				.map(this::getUUID)
				.forEach(id -> {
					expected.get(id).put(edPrime.getId(), edPrime.getProperties());
				});
		}
		
		start = System.currentTimeMillis();
		eel.endpointChanged(new EndpointEvent(EndpointEvent.MODIFIED, edPrime), 
				"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		//Only 5 nodes are actually updated
		assertEquals(5, updateSemaphore.availablePermits());
		assertEquals(8, removeSemaphore.availablePermits());
		
		System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");

		//Remove the other original endpoint in Zone A
		
		getZoneA(rootFw).stream()
			.map(this::getUUID)
			.filter(id -> !id.equals(getUUID(rootFw)))
			.forEach(id -> {
				expected.get(id).remove(ed2.getId());
			});
		
		start = System.currentTimeMillis();
		eel.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, ed2), 
				"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		assertEquals(5, updateSemaphore.availablePermits());
		assertEquals(13, removeSemaphore.availablePermits());
		
		System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
		
		//Heal partition
		healPartition(rootFw, totalPartition);
	        
        getZoneB().stream()
	        .map(this::getUUID)
	        .forEach(f -> {
	        	Map<String, Map<String, Object>> x = expected.get(f);
	        	x.put(edPrime.getId(), edPrime.getProperties());
	        	x.put(ed3.getId(), ed3.getProperties());
	        });
        getZoneA(rootFw).stream()
			.map(this::getUUID)
			.filter(id -> !id.equals(getUUID(rootFw)))
			.forEach(f -> {
	        	expected.get(f).put(ed4.getId(), ed4.getProperties());
	        });
        
        expected.put(getUUID(rootFw), Collections.singletonMap(ed4.getId(), ed4.getProperties()));
		
		start = System.currentTimeMillis();
		
		assertTrue(knownProps.toString(), awaitConsensus(expected, start));
		
		assertEquals(5, updateSemaphore.availablePermits());
		assertEquals(13, removeSemaphore.availablePermits());
		
		System.out.println("Partition healed after " + (System.currentTimeMillis() - start) + " milliseconds");
	}

	private boolean awaitConsensus(Map<UUID, Map<String, Map<String, Object>>> expected, long start)
			throws InterruptedException {
		boolean finished = false;
		outer: while(System.currentTimeMillis() - start < 20000) {
			Thread.sleep(100);
			for(UUID key : expected.keySet()) {
				if(!knownProps.containsKey(key))
					continue outer;
				if(!expected.get(key).keySet().equals(knownProps.get(key).keySet()))
					continue outer;
			}
			finished = true;
			break outer;
		};
		return finished;
	}

	private HashSet<Framework> getZoneB() {
		return new HashSet<>(Arrays.asList(childFrameworks.get(0), childFrameworks.get(2), 
				childFrameworks.get(4), childFrameworks.get(6)));
	}

	private HashSet<Framework> getZoneA(Framework rootFw) {
		return new HashSet<>(Arrays.asList(rootFw, childFrameworks.get(1),
				childFrameworks.get(3), childFrameworks.get(5), childFrameworks.get(7),
				childFrameworks.get(8)));
	}

	private void setupCluster(boolean secure, Framework rootFw, Set<UUID> fwIds) throws InterruptedException {
		reg = configureFramework(rootFw, fwIds, secure, 3600);
		
		childFrameworks.stream().forEach(f -> configureFramework(f, fwIds, secure, 3600));
		
		long start = System.currentTimeMillis();
		while(System.currentTimeMillis() - start < 20000) {
			int size = clusterData.size();
			System.out.println("Cluster is " + size + " members.");
			if(size == 10) {
				break;
			}
			Thread.sleep(100);
		}
		
		assertEquals(10, clusterData.size());
		
		notifyClusterListener(rootFw, Action.ADDED, fwIds);
		childFrameworks.stream().forEach(f -> notifyClusterListener(f, Action.ADDED, fwIds));
	}

	private EndpointDescription getTestEndpointDescription(String endpointId, BundleContext fwContext) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();

        // required
        m.put(OBJECTCLASS, new String[]{"com.acme.HelloService", "some.other.Service"});
        m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, fwContext.getProperty(Constants.FRAMEWORK_UUID));
        m.put(RemoteConstants.ENDPOINT_ID, endpointId);
        m.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.valueOf(42));
        m.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");

        return new EndpointDescription(m);
	}    

	private ServiceRegistration<?> configureFramework(Framework f, Set<UUID> knownIds, 
			boolean secure, long expiry) {
		
		registerClusterInformationAndNetworkInformation(f.getBundleContext(), getUUID(f), knownIds, secure);
    	
		try {
	    	@SuppressWarnings({ "unchecked", "rawtypes" })
			ServiceTracker tracker = new ServiceTracker(f.getBundleContext(), ConfigurationAdmin.class.getName(), null);
	    	tracker.open();
	    	Object cm = tracker.waitForService(2000);
	    	
	    	Method m = cm.getClass().getMethod("createFactoryConfiguration", String.class, String.class);
	    	Object encodingConfig = m.invoke(cm, "com.paremus.netty.tls", "?");
	    	Object discoveryConfig = m.invoke(cm, "com.paremus.dosgi.discovery.cluster", "?");
	    	
	    	m = encodingConfig.getClass().getMethod("update", Dictionary.class);
	    	
	    	m.invoke(encodingConfig, getEncodingConfig(secure, expiry));
	    	m.invoke(discoveryConfig, getDiscoveryConfig());
	    	tracker.close();
	    	
	    	String filter = "(!("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + getUUID(f) + "))";
	    	Hashtable<String, Object> props = new Hashtable<>();
	    	props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, new String[] {filter});
	    	props.put(Constants.SERVICE_RANKING, -1);
	    	return f.getBundleContext().registerService(EndpointEventListener.class.getName(), listener, props);
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }
	
	@SuppressWarnings("rawtypes")
	private class ClusterInformationServiceFactory implements ServiceFactory, InvocationHandler {

		private final Set<UUID> knownIds;
		private final UUID localId;
		private final boolean secure;
		
		public ClusterInformationServiceFactory(Set<UUID> knownIds, UUID localId, boolean secure) {
			super();
			this.knownIds = knownIds;
			this.localId = localId;
			this.secure = secure;
		}

		@Override
		public Object getService(Bundle bundle, ServiceRegistration registration) {
			try {
				return Proxy.newProxyInstance(new ClassLoader(bundle.adapt(BundleWiring.class).getClassLoader()){}, 
						new Class[] {bundle.loadClass(ClusterInformation.class.getName())}, this);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch(method.getName()) {
				case "getKnownMembers" :
					return new HashSet<UUID>(knownIds);
				case "getMemberHosts" :
					return knownIds.stream()
							.collect(toMap(identity(), i -> getLoopbackAddress()));
				case "getClusterName" :
					return "clusterOne";
				case "getAddressFor" :
					return getLoopbackAddress();
				case "getCertificateFor" :
					return secure ? getCertificate() : null;
				case "getLocalUUID" :
					return localId;
				case "getMemberAttributes" :
					return new HashMap<String, byte[]>(clusterData.getOrDefault((UUID)args[0], new HashMap<>()));
				case "getMemberAttribute" :
					return clusterData.getOrDefault((UUID)args[0], new HashMap<>()).get((String)args[1]);
				case "updateAttribute" :
					Map<String, byte[]> data = clusterData.computeIfAbsent(localId, i -> new ConcurrentHashMap<>());
					if(args[1] == null) {
						data.remove((String)args[0]);
					} else {
						data.put((String)args[0], (byte[])args[1]);
					}
					return null;
				case "equals" :
					if(Proxy.isProxyClass(args[0].getClass())) {
						InvocationHandler ih = Proxy.getInvocationHandler(args[0]);
						return ih == this;
					}
					return false;
				default :
					throw new UnsupportedOperationException("Method " + method + " not known");
			}
		}
		
	}
	
	@SuppressWarnings("rawtypes")
	private class ClusterNetworkInformationServiceFactory implements ServiceFactory, InvocationHandler {
		
		private final UUID localId;
		
		public ClusterNetworkInformationServiceFactory(UUID localId) {
			super();
			this.localId = localId;
		}

		@Override
		public Object getService(Bundle bundle, ServiceRegistration registration) {
			try {
				Class<?> cniClass = bundle.loadClass(ClusterNetworkInformation.class.getName());
				return Proxy.newProxyInstance(new ClassLoader(cniClass.getClassLoader()){}, 
						new Class[] {cniClass}, this);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch(method.getName()) {
				case "isFirewalled" :
					return false;
				case "getLocalUUID" :
					return localId;
				case "getClusterName" :
					return "clusterOne";
				case "getBindAddress" :
				case "getFibreAddress" :
					return getLoopbackAddress();
				case "getAddressFor" : 
				default :
					throw new UnsupportedOperationException("Method " + method + " not known");
			}
		}
	}
	
	private void registerClusterInformationAndNetworkInformation(BundleContext ctx, UUID uuid,
			Set<UUID> knownIds, boolean secure) {
		
		ServiceRegistration<?> reg = ctx.registerService(ClusterInformation.class.getName(), 
				new ClusterInformationServiceFactory(knownIds, uuid, secure), 
				new Hashtable<>(Collections.singletonMap("cluster.name", "clusterOne")));
		
		if(ctx.getProperty(Constants.FRAMEWORK_UUID).equals(context.getProperty(Constants.FRAMEWORK_UUID))) {
			rootCIReg = reg;
		}
		
		reg = ctx.registerService(ClusterNetworkInformation.class.getName(), 
				new ClusterNetworkInformationServiceFactory(uuid), 
				new Hashtable<>(Collections.singletonMap("cluster.name", "clusterOne")));
		
		if(ctx.getProperty(Constants.FRAMEWORK_UUID).equals(context.getProperty(Constants.FRAMEWORK_UUID))) {
			rootCNIReg = reg;
		}
	}

	private Dictionary<String, Object> getEncodingConfig(boolean secure, long expiry) {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		
		if(secure) {
			String testResources = context.getProperty("test.resources");
			
			config.put("keystore.location", testResources + "etc/testSign.keystore");
			config.put("keystore.type", "jks");
			config.put(".keystore.password", "testingSign");
			config.put(".keystore.key.password", "signing");

			config.put("truststore.location", testResources + "etc/test.truststore");
			config.put("truststore.type", "jks");
			config.put(".truststore.password", "paremus");
		} else {
			config.put("insecure", true);
		}
		
		return config;
	}

	private Certificate getCertificate() {
		try {
			KeyStore ks = KeyStore.getInstance("jks");
			String testResources = context.getProperty("test.resources");
			ks.load(new FileInputStream(testResources + "etc/testSign.keystore"), "testingSign".toCharArray());
			return ks.getCertificate("testsigningcert");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Dictionary<String, ?> getDiscoveryConfig() {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		config.put("bind.address", "127.0.0.1");
		config.put("root.cluster", "clusterOne");
		config.put("infra", "false");
		return config;
	}

	private List<Framework> createFrameworks() throws BundleException {
		FrameworkFactory ff = ServiceLoader.load(FrameworkFactory.class, 
				BundleContext.class.getClassLoader()).iterator().next();
    	
    	List<String> locations = new ArrayList<>();
    	
    	for(Bundle b : context.getBundles()) {
    		if(b.getSymbolicName().equals("org.apache.felix.configadmin") ||
    				b.getSymbolicName().equals("org.apache.felix.scr") ||
    				b.getSymbolicName().equals("com.paremus.cluster.api") ||
    				b.getSymbolicName().equals("com.paremus.dosgi.api") ||
    				b.getSymbolicName().equals("com.paremus.gossip.netty") ||
    				b.getSymbolicName().equals("com.paremus.license") ||
    				b.getSymbolicName().equals("com.paremus.netty.tls") ||
    				b.getSymbolicName().equals("com.paremus.dosgi.discovery.cluster") ||
    				b.getSymbolicName().equals("org.osgi.service.remoteserviceadmin") ||
    				b.getSymbolicName().startsWith("bc") ||
    				b.getSymbolicName().startsWith("io.netty") ||
    				b.getSymbolicName().startsWith("org.osgi.util") ||
    				b.getSymbolicName().startsWith("slf4j") ||
    				b.getSymbolicName().startsWith("ch.qos.logback")) {
    			locations.add(b.getLocation());
    		}
    	}
    	
    	List<Framework> clusterOne = new ArrayList<Framework>();
    	
        for(int i = 1; i < 10; i++) {
        	Map<String, String> fwConfig = new HashMap<>();
        	fwConfig.put(Constants.FRAMEWORK_STORAGE, new File(context.getDataFile(""), "ClusterOne" + i).getAbsolutePath());
        	fwConfig.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        	Framework f = ff.newFramework(fwConfig);
        	clusterOne.add(f);
        	f.init();
        	for(String s : locations) {
        		f.getBundleContext().installBundle(s);
        	}
        	f.start();
        	f.adapt(FrameworkWiring.class).resolveBundles(Collections.emptySet());
        	for(Bundle b : f.getBundleContext().getBundles()) {
        		if(b.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
        			b.start();
        		}
        	}
        }
		return clusterOne;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void notifyClusterListener(Framework f, Action a, Set<UUID> fwIds) {
		BundleContext ctx = f.getBundleContext();
		ServiceReference<?> listenerRef = ctx.getServiceReference(ClusterListener.class.getName());
		Object listener = ctx.getService(listenerRef);
		ctx = listenerRef.getBundle().getBundleContext();
		Object ci = ctx.getService(ctx.getServiceReference(ClusterInformation.class.getName()));
		
		Object action;
		try {
			action = Enum.valueOf((Class<? extends Enum>) listenerRef.getBundle().loadClass(Action.class.getName()), a.name());
		} catch (ClassNotFoundException e1) {
			throw new IllegalArgumentException(e1);
		}
		
		Method m = Arrays.stream(listener.getClass().getMethods())
				.filter(x -> "clusterEvent".equals(x.getName()))
				.findFirst().get();
		
		m.setAccessible(true);
		
		fwIds.stream().forEach(id -> {
			try {
				m.invoke(listener, ci, action, id, a == Action.ADDED ? clusterData.get(id).keySet() : new HashSet<>(),
						 a == Action.REMOVED ? clusterData.get(id).keySet() : new HashSet<>(), new HashSet<>());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
 		
	}

	private UUID getUUID(Framework f) {
		return UUID.fromString(f.getBundleContext().getProperty(Constants.FRAMEWORK_UUID));
	}
}
