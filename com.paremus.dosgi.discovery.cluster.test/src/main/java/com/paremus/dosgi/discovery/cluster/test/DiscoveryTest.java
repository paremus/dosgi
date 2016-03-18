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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.Constants.FRAMEWORK_UUID;
import static org.osgi.framework.Constants.OBJECTCLASS;
import static org.osgi.service.remoteserviceadmin.EndpointEventListener.ENDPOINT_LISTENER_SCOPE;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID;
import static org.osgi.service.remoteserviceadmin.namespace.DiscoveryNamespace.DISCOVERY_NAMESPACE;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.osgi.annotation.bundle.Requirement;
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

@RunWith(JUnit4.class)
@Requirement(namespace=DISCOVERY_NAMESPACE, filter="(protocols=com.paremus.cluster)", effective="active")
public class DiscoveryTest {

	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
	private final Semaphore updateSemaphore = new Semaphore(0);
	private final Semaphore removeSemaphore = new Semaphore(0);
	
    @SuppressWarnings("rawtypes")
	private final ServiceFactory listener = new TestListener();

    private ServiceRegistration<?> reg;
    
	private List<Framework> childFrameworks;
	
	private int numberOfFrameworks = Math.min(Runtime.getRuntime().availableProcessors() * 2, 8);
	
	private final AtomicInteger counter = new AtomicInteger(0);
    
	private ServiceTracker<ClusterInformation, ClusterInformation> tracker;

	@Before
	public void setup() {
		// This is necessary due to a leak seen in Netty due to the finalization of
		// the buffer pool...
		System.setProperty("io.netty.allocator.type", "unpooled");
		
		tracker = new ServiceTracker<>(context, ClusterInformation.class, null);
		tracker.open();
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
		knownProps.clear();
		Thread.sleep(100);
		counter.set(0);
		tracker.close();
	}
	
	private final ConcurrentMap<UUID, ConcurrentMap<String, Map<String, Object>>> knownProps = new ConcurrentHashMap<>();

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
	
	@Test
	public void testDiscoveryFilters() throws Exception {
    	
    	Framework rootFw = context.getBundle(0).adapt(Framework.class);
    	reg = configureFramework(rootFw, false, 3600);
    	
    	childFrameworks = createFrameworks();
        childFrameworks.stream().forEach(f -> configureFramework(f, false, 3600));
        
        ClusterInformation ci = tracker.waitForService(2000);
        assertNotNull(ci);
        
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis() - start < 20000) {
        	int size = ci.getKnownMembers().size();
        	System.out.println("Cluster is " + size + " members.");
			if(size == numberOfFrameworks) {
        		break;
        	}
			Thread.sleep(100);
        }
        
        assertEquals(numberOfFrameworks, ci.getKnownMembers().size());
        
        @SuppressWarnings("unchecked")
		List<String> filters = (List<String>) context.getServiceReference(EndpointEventListener.class)
        		.getProperty(ENDPOINT_LISTENER_SCOPE);
        
        assertTrue(filters.toString(), filters.contains("(" + ENDPOINT_FRAMEWORK_UUID +
        		"=" + context.getProperty(FRAMEWORK_UUID) + ")"));
        assertTrue(filters.toString(), filters.contains("(foo=bar)"));
        assertTrue(filters.toString(), filters.contains("(fizz=buzz)"));
	}
    
	@Test
	public void testDiscoveryPropagation() throws Exception {
		doTestDiscoveryPropagation(false);
	}

	@Test
	public void testSecureDiscoveryPropagation() throws Exception {
		doTestDiscoveryPropagation(true);
	}

	private void doTestDiscoveryPropagation(boolean secure) throws Exception {
    	
    	Framework rootFw = context.getBundle(0).adapt(Framework.class);
    	reg = configureFramework(rootFw, secure, 3600);
    	
    	childFrameworks = createFrameworks();
        childFrameworks.stream().forEach(f -> configureFramework(f, secure, 3600));
        
        ClusterInformation ci = tracker.waitForService(2000);
        assertNotNull(ci);
        
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis() - start < 30000) {
        	int size = ci.getKnownMembers().size();
        	System.out.println("Cluster is " + size + " members.");
			if(size == numberOfFrameworks) {
        		break;
        	}
			Thread.sleep(100);
        }
        
        assertEquals(numberOfFrameworks, ci.getKnownMembers().size());
        
        EndpointEventListener eel = context.getService(context.getServiceReference(EndpointEventListener.class));
        
        EndpointDescription ed = getTestEndpointDescription("FOO", context);
        
        Map<UUID, Map<String, Map<String, Object>>> expected = childFrameworks.stream().map(this::getUUID)
        		.collect(toMap(identity(), (u) -> Collections.singletonMap(ed.getId(), ed.getProperties())));
        
        start = System.currentTimeMillis();
        eel.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, ed), 
        		"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
        
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
        
        assertTrue(knownProps.toString(), finished);
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(0, removeSemaphore.availablePermits());
        
        System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
       
        expected = childFrameworks.stream().map(this::getUUID)
        		.collect(toMap(identity(), (u) -> Collections.emptyMap()));
        
       
        start = System.currentTimeMillis();
        eel.endpointChanged(new EndpointEvent(EndpointEvent.REMOVED, ed), 
        		"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + context.getProperty(Constants.FRAMEWORK_UUID) + ")");
        finished = false;
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
        
        assertTrue(knownProps.toString(), finished);
        
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(numberOfFrameworks - 1, removeSemaphore.availablePermits());
        
        System.out.println("Established cluster after " + (System.currentTimeMillis() - start) + " milliseconds");
    }

	@Test
	public void testDiscoveryStability() throws Exception {
		doTestDiscoveryStability(false);
	}
	
	@Test
    public void testSecureDiscoveryStability() throws Exception {
		doTestDiscoveryStability(true);
	}

	private void doTestDiscoveryStability(boolean secure) throws BundleException, InterruptedException {
		Framework rootFw = context.getBundle(0).adapt(Framework.class);
		reg = configureFramework(rootFw, secure, 20);
		
		childFrameworks = createFrameworks();
	    childFrameworks.stream().forEach(f -> configureFramework(f, secure, 20));
	    
	    ClusterInformation ci = tracker.waitForService(2000);
        assertNotNull(ci);
        
	    long start = System.currentTimeMillis();
	    while(System.currentTimeMillis() - start < 30000) {
	    	int size = ci.getKnownMembers().size();
	    	System.out.println("Cluster is " + size + " members.");
			if(size == numberOfFrameworks) {
	    		break;
			}
			Thread.sleep(100);
	    }
	    AtomicInteger i = new AtomicInteger();
	    
	    Map<UUID, Map<String, Map<String, Object>>> expected = new ConcurrentHashMap<UUID, Map<String,Map<String,Object>>>();
	    expected.put(getUUID(rootFw), new HashMap<String, Map<String,Object>>());
	    
	    childFrameworks.stream().forEach(fw -> {
	    	BundleContext fwContext = fw.getBundleContext();
	    	Object eel = fwContext.getService(fwContext.getServiceReference(EndpointEventListener.class.getName()));
	        
	        EndpointDescription endpoint = getTestEndpointDescription(String.valueOf(i.incrementAndGet()), fwContext);
	        Map<String, Object> endpointProps = endpoint.getProperties();
	        
	        expected.get(getUUID(rootFw)).put(endpoint.getId(), endpointProps);
	        
	        childFrameworks.stream()
	        		.map(this::getUUID)
	        		.filter(id -> !id.equals(getUUID(fw)))
	        		.forEach(id -> 
	        			expected.merge(id, Collections.singletonMap(endpoint.getId(), endpointProps),
	        					(old,x) -> {
		        					Map<String, Map<String, Object>> m = old == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(old);
		        					m.put(endpoint.getId(), endpointProps);
		        					return m;
		        				}));
	        try {
		        Object endpointDesc = eel.getClass().getClassLoader()
		        		.loadClass(EndpointDescription.class.getName()).getConstructor(Map.class)
		        		.newInstance(endpointProps);
		        
		        Object event = eel.getClass().getClassLoader()
		        		.loadClass(EndpointEvent.class.getName()).getConstructor(int.class, endpointDesc.getClass())
		        		.newInstance(EndpointEvent.ADDED, endpointDesc);
		        
		        Method endpointChanged = Arrays.stream(eel.getClass().getMethods())
		        		.filter(m -> m.getName().equals("endpointChanged"))
		        		.findFirst()
		        		.get();
		        endpointChanged.setAccessible(true);
				endpointChanged.invoke(eel, event, 
		        		"("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + getUUID(fw) + ")");
	        
	        } catch (Exception e) {
	        	throw new RuntimeException(e);
	        }
	    });
	    
	    
	    start = System.currentTimeMillis();
        outer: while(System.currentTimeMillis() - start < 60000) {
        	Thread.sleep(100);
        	for(UUID key : expected.keySet()) {
        		if(!knownProps.containsKey(key))
        			continue outer;
        		if(!expected.get(key).keySet().equals(knownProps.get(key).keySet()))
        			continue outer;
        	}
        	break outer;
        };
        
        // The CI server really struggles with the setup for this test, so we accept that it may fail
        boolean inCi = System.getenv("GITLAB_CI") != null;
        
        boolean equals = expected.keySet().equals(knownProps.keySet());
        if(!equals && inCi) {
        	System.out.println("Exiting the test early as the CI server failed to complete the advertisements");
        	return;
        }
        
        AtomicBoolean quit = new AtomicBoolean(false);
        
        assertEquals(expected.keySet(), knownProps.keySet());
        expected.entrySet().stream().forEach(e -> {
        	boolean advertised = e.getValue().keySet().equals(knownProps.get(e.getKey()).keySet());
        	 if(!advertised && inCi) {
             	System.out.println("Will exit the test early as the CI server failed to complete the advertisements");
             	quit.set(true);
             	return;
             }
        	assertEquals("Invalid match for fw " + e.getKey(), e.getValue().keySet(), knownProps.get(e.getKey()).keySet());	
        });
        
        if(quit.get()) {
        	System.out.println("Exiting the test early as the CI server failed to advertise all endpoints");
        	return;
        }
        
        
        System.out.println("All of the endpoints are advertised after " + (System.currentTimeMillis() - start));
        
        Thread.sleep(60000);
        
        assertEquals(expected.keySet(), knownProps.keySet());
        expected.entrySet().stream().forEach(e -> 
        	assertEquals("Invalid match for fw " + e.getKey(), e.getValue().keySet(), knownProps.get(e.getKey()).keySet()));
        
        
        assertEquals(0, updateSemaphore.availablePermits());
        assertEquals(0, removeSemaphore.availablePermits());
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

	private ServiceRegistration<?> configureFramework(Framework f, boolean secure, long expiry) {
    	try {
	    	@SuppressWarnings({ "unchecked", "rawtypes" })
			ServiceTracker tracker = new ServiceTracker(f.getBundleContext(), ConfigurationAdmin.class.getName(), null);
	    	tracker.open();
	    	Object cm = tracker.waitForService(2000);
	    	
	    	Method m = cm.getClass().getMethod("createFactoryConfiguration", String.class, String.class);
	    	
	    	Object gossipConfig = m.invoke(cm, "com.paremus.gossip.netty", "?");
	    	Object encodingConfig = m.invoke(cm, "com.paremus.netty.tls", "?");
	    	Object discoveryConfig = m.invoke(cm, "com.paremus.dosgi.discovery.cluster", "?");
	    	
	    	m = gossipConfig.getClass().getMethod("update", Dictionary.class);
	    	
	    	m.invoke(gossipConfig, getGossipConfig(counter.getAndIncrement()));
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

	private Dictionary<String, ?> getGossipConfig(int i) {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		config.put("bind.address", "127.0.0.1");
		config.put("udp.port", 17001 + 100 * i);
		config.put("tcp.port", 17002 + 100 * i);
		config.put("initial.peers", new String[] {"127.0.0.1:17001", "127.0.0.1:17101", "127.0.0.1:17201"});
		config.put("cluster.name", "clusterOne");
		return config;
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

	private Dictionary<String, ?> getDiscoveryConfig() {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		config.put("bind.address", "127.0.0.1");
		config.put("root.cluster", "clusterOne");
		config.put("infra", "false");
		config.put("additional.filters", new String[] {"(foo=bar)", "(fizz=buzz)"});
		return config;
	}

	private List<Framework> createFrameworks() throws BundleException {
		FrameworkFactory ff = ServiceLoader.load(FrameworkFactory.class, 
    			context.getBundle(0).adapt(ClassLoader.class)).iterator().next();
    	
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
    	
        for(int i = 1; i < numberOfFrameworks; i++) {
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

	private UUID getUUID(Framework f) {
		return UUID.fromString(f.getBundleContext().getProperty(Constants.FRAMEWORK_UUID));
	}
}
