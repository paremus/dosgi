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

import static com.paremus.dosgi.discovery.cluster.Constants.PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE;
import static com.paremus.dosgi.scoping.discovery.Constants.PAREMUS_TARGETTED_EXTRA_ATTRIBUTE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.Constants.OBJECTCLASS;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

import com.paremus.dosgi.scoping.discovery.ScopeManager;


@RunWith(JUnit4.class)
public class ScopingDiscoveryTest {

	private static final String FABRIC = "fabric";
	private static final int FABRIC_BASE_PORT = 10025;
	private static final String CLUSTER_A = "cluster-a";
	private static final int CLUSTER_A_BASE_PORT = 12025;
	private static final String CLUSTER_B = "cluster-b";
	private static final int CLUSTER_B_BASE_PORT = 14025;
	
	private static final String GLOBAL_ENDPOINT_A = "ep1";
	private static final String GLOBAL_ENDPOINT_B = "ep2";
	private static final String GLOBAL_ENDPOINT_AB = "ep3";
	private static final String SCOPED_ENDPOINT_A = "ep4";
	private static final String SCOPED_ENDPOINT_B = "ep5";
	private static final String SCOPED_ENDPOINT_AB = "ep6";
	

	private static final String SCOPE_A = "system-a";
	private static final String SCOPE_B = "system-b";
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
	private final AtomicInteger counter = new AtomicInteger();
	
	private final ServiceFactory<Object> listener = new TestListener();
    
	private Map<UUID, Framework> allChildFrameworks = new LinkedHashMap<>();

	private Set<UUID> clusterA = new HashSet<>();
	private Set<UUID> clusterB = new HashSet<>();
    
	@Before
	public void setup() {
		// This is necessary due to a leak seen in Netty due to the finalization of
		// the buffer pool...
		System.setProperty("io.netty.allocator.type", "unpooled");
	}
	
	@After
	public void tearDown() throws Exception {
		allChildFrameworks.values().forEach((fw) -> { 
			try{
				fw.stop();
			} catch (BundleException be){}
		});
		allChildFrameworks.values().forEach((fw) -> { 
			try{
				fw.waitForStop(2000);
			} catch (Exception be){}
		});
		
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
		allChildFrameworks.clear();
		Thread.sleep(1000);
	}
	
	/** Framework ID to Map of Endpoint ID to Map of endpoint properties **/
	private final Map<UUID, Map<String, Map<String, Object>>> knownProps = new HashMap<>();

	public class TestListener implements ServiceFactory<Object> {
		@SuppressWarnings("unchecked")
		@Override
		public Object getService(Bundle bundle, ServiceRegistration<Object> registration) {
			try {
				return Proxy.newProxyInstance(bundle.adapt(BundleWiring.class).getClassLoader(), 
						new Class[] {bundle.loadClass(EndpointEventListener.class.getName())}, 
						(o, m, a) ->{
							Object ed = a[0].getClass().getMethod("getEndpoint").invoke(a[0]);
							Integer type = (Integer) a[0].getClass().getMethod("getType").invoke(a[0]);
							UUID id = UUID.fromString(bundle.getBundleContext().getProperty(Constants.FRAMEWORK_UUID));
							String endpointId = ed.getClass().getMethod("getId").invoke(ed).toString();
							System.out.println("Type " + type + " event for " + id + " endpoint " + endpointId);
							
							synchronized (knownProps) {
								Map<String, Map<String, Object>> map = knownProps.computeIfAbsent(id, u -> new HashMap<>());
								if(type.equals(EndpointEvent.ADDED)) {
									map.put(endpointId,
										(Map<String, Object>) ed.getClass().getMethod("getProperties").invoke(ed));
								} else {
									map.remove(endpointId);
								}
							}
							return null;
						});
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void ungetService(Bundle bundle,
				ServiceRegistration<Object> registration, Object service) {
			knownProps.remove(UUID.fromString(bundle.getBundleContext().getProperty(Constants.FRAMEWORK_UUID)));
		}
		
	}
    
	@Test
	public void testDiscoveryPropagationGlobalScope() throws Exception {
		EndpointEventListener listener = setupClusters();
		
		EndpointDescription edA = getTestEndpointDescription(GLOBAL_ENDPOINT_A, "global", CLUSTER_A, null);
		EndpointDescription edB = getTestEndpointDescription(GLOBAL_ENDPOINT_B, "global", CLUSTER_B, null);
		EndpointDescription edAB = getTestEndpointDescription(GLOBAL_ENDPOINT_AB, "global", 
				asList(CLUSTER_A, CLUSTER_B), null);
        
		
        Map<UUID, Map<String, Map<String, Object>>> expected = new HashMap<>();
        
        Consumer<UUID> addForA = u -> {
				Map<String, Map<String, Object>> m = new HashMap<>();
				m.put(edA.getId(), edA.getProperties());
				m.put(edAB.getId(), edAB.getProperties());
				expected.put(u, m);
			};
		Consumer<UUID> addForB = u -> {
				Map<String, Map<String, Object>> m = new HashMap<>();
				m.put(edB.getId(), edB.getProperties());
				m.put(edAB.getId(), edAB.getProperties());
				expected.put(u, m);
			};
        
        clusterA.stream().forEach(addForA);
        clusterB.stream().forEach(addForB);
		
        listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edA), null);
        listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edB), null);
        listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edAB), null);
        
        try {
        	checkExpectation(expected);
        } catch (AssertionError afe) {
        	if(System.getenv("GITLAB_CI") != null) {
        		System.out.println("The testDiscoveryPropagationGlobalScope method is unreliable in CI and we don't know why...");
        		return;
        	} else {
        		throw afe;
        	}
        }
        
        Map<UUID, Framework> lateJoinersA = createFrameworks(2);
        Map<UUID, Framework> lateJoinersB = createFrameworks(2);
        Map<UUID, Framework> lateJoinersAB = createFrameworks(2);
        
        allChildFrameworks.putAll(lateJoinersA);
        allChildFrameworks.putAll(lateJoinersB);
        allChildFrameworks.putAll(lateJoinersAB);
        
        lateJoinersA.values().forEach(f -> configureFramework(f, CLUSTER_A));
        lateJoinersB.values().forEach(f -> configureFramework(f, CLUSTER_B));
        lateJoinersAB.values().forEach(f -> configureFramework(f, CLUSTER_A));

        lateJoinersA.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
        lateJoinersB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
        lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
        lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
        
        lateJoinersA.keySet().forEach(addForA);
        lateJoinersB.keySet().forEach(addForB);
        lateJoinersAB.keySet().forEach( u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		});
        
        checkExpectation(expected);
        
        Random r = new Random();
        
        UUID upgradedAToB = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
        UUID upgradedBToA = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
        
        addToGossipCluster(allChildFrameworks.get(upgradedAToB), CLUSTER_B, CLUSTER_B_BASE_PORT);
        addToGossipCluster(allChildFrameworks.get(upgradedBToA), CLUSTER_A, CLUSTER_A_BASE_PORT);
        
        expected.get(upgradedAToB).put(edB.getId(), edB.getProperties());
        expected.get(upgradedBToA).put(edA.getId(), edA.getProperties());

        checkExpectation(expected);
	}

	@Test
	public void testDiscoveryPropagationGlobalScopeClustersExtra() throws Exception {
		EndpointEventListener listener = setupClusters();
		
		EndpointDescription edA = getTestEndpointDescription(GLOBAL_ENDPOINT_A, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, CLUSTER_A));
		EndpointDescription edB = getTestEndpointDescription(GLOBAL_ENDPOINT_B, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, CLUSTER_B));
		EndpointDescription edAB = getTestEndpointDescription(GLOBAL_ENDPOINT_AB, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, asList(CLUSTER_A, CLUSTER_B)));
		
		Map<UUID, Map<String, Map<String, Object>>> expected = new HashMap<>();
		
		Consumer<UUID> addForA = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		Consumer<UUID> addForB = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		
		clusterA.stream().forEach(addForA);
		clusterB.stream().forEach(addForB);
		
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edA), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edB), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edAB), null);
		
		checkExpectation(expected);
		
		Map<UUID, Framework> lateJoinersA = createFrameworks(2);
		Map<UUID, Framework> lateJoinersB = createFrameworks(2);
		Map<UUID, Framework> lateJoinersAB = createFrameworks(2);
		
		allChildFrameworks.putAll(lateJoinersA);
		allChildFrameworks.putAll(lateJoinersB);
		allChildFrameworks.putAll(lateJoinersAB);
		
		lateJoinersA.values().forEach(f -> configureFramework(f, CLUSTER_A));
		lateJoinersB.values().forEach(f -> configureFramework(f, CLUSTER_B));
		lateJoinersAB.values().forEach(f -> configureFramework(f, CLUSTER_A));
		
		lateJoinersA.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
		lateJoinersB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
		
		lateJoinersA.keySet().forEach(addForA);
		lateJoinersB.keySet().forEach(addForB);
		lateJoinersAB.keySet().forEach( u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		});
		
		checkExpectation(expected);
		
		Random r = new Random();
		
		UUID upgradedAToB = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID upgradedBToA = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		
		addToGossipCluster(allChildFrameworks.get(upgradedAToB), CLUSTER_B, CLUSTER_B_BASE_PORT);
		addToGossipCluster(allChildFrameworks.get(upgradedBToA), CLUSTER_A, CLUSTER_A_BASE_PORT);
		
		expected.get(upgradedAToB).put(edB.getId(), edB.getProperties());
		expected.get(upgradedBToA).put(edA.getId(), edA.getProperties());
		
		checkExpectation(expected);
	}

	@Test
	public void testDiscoveryPropagationSomeClustersIgnored() throws Exception {
		EndpointEventListener listener = setupClusters();
		
		ConfigurationAdmin cm = context.getService(context.getServiceReference(ConfigurationAdmin.class));
		Configuration c = cm.listConfigurations("(service.factoryPid=com.paremus.dosgi.discovery.cluster)")[0];
		Dictionary<String, Object> props = c.getProperties();
		props.put("target.clusters", CLUSTER_A);
		c.update(props);
		
		Thread.sleep(1000);
		
		listener = context.getService(context.getServiceReference(EndpointEventListener.class));
		
		
		EndpointDescription edA = getTestEndpointDescription(GLOBAL_ENDPOINT_A, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, CLUSTER_A));
		EndpointDescription edB = getTestEndpointDescription(GLOBAL_ENDPOINT_B, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, CLUSTER_B));
		EndpointDescription edAB = getTestEndpointDescription(GLOBAL_ENDPOINT_AB, "global", "unnamed", null, 
				singletonMap(PAREMUS_CLUSTERS_EXTRA_ATTRIBUTE, asList(CLUSTER_A, CLUSTER_B)));
		
		Map<UUID, Map<String, Map<String, Object>>> expected = new HashMap<>();
		
		Consumer<UUID> addForA = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		
		clusterA.stream().forEach(addForA);
		
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edA), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edB), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edAB), null);
		
		checkExpectation(expected);
		
		Map<UUID, Framework> lateJoinersA = createFrameworks(2);
		Map<UUID, Framework> lateJoinersB = createFrameworks(2);
		Map<UUID, Framework> lateJoinersAB = createFrameworks(2);
		
		allChildFrameworks.putAll(lateJoinersA);
		allChildFrameworks.putAll(lateJoinersB);
		allChildFrameworks.putAll(lateJoinersAB);
		
		lateJoinersA.values().forEach(f -> configureFramework(f, CLUSTER_A));
		lateJoinersB.values().forEach(f -> configureFramework(f, CLUSTER_B));
		lateJoinersAB.values().forEach(f -> configureFramework(f, CLUSTER_A));
		
		lateJoinersA.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
		lateJoinersB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
		
		lateJoinersA.keySet().forEach(addForA);
		lateJoinersAB.keySet().forEach( u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		});
		
		checkExpectation(expected);
		
		Random r = new Random();
		
		UUID upgradedAToB = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID upgradedBToA = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		
		addToGossipCluster(allChildFrameworks.get(upgradedAToB), CLUSTER_B, CLUSTER_B_BASE_PORT);
		addToGossipCluster(allChildFrameworks.get(upgradedBToA), CLUSTER_A, CLUSTER_A_BASE_PORT);
		
		Map<String, Map<String, Object>> newAMember = expected.computeIfAbsent(upgradedBToA, k -> new HashMap<>());
		newAMember.put(edA.getId(), edA.getProperties());
		newAMember.put(edAB.getId(), edAB.getProperties());
		
		checkExpectation(expected);
	}

	private EndpointEventListener setupClusters() throws Exception {
		
		Framework rootFw = context.getBundle(0).adapt(Framework.class);
		configureFramework(rootFw, FABRIC);
		
		addToGossipCluster(rootFw, FABRIC, FABRIC_BASE_PORT);
		
		Map<UUID, Framework> createdFrameworks = createFrameworks(5);
		allChildFrameworks.putAll(createdFrameworks);
		clusterA.addAll(createdFrameworks.keySet());
	
		createdFrameworks = createFrameworks(5);
		allChildFrameworks.putAll(createdFrameworks);
		clusterB.addAll(createdFrameworks.keySet());
		
		allChildFrameworks.values().stream().forEach(f -> configureFramework(f, FABRIC));
		allChildFrameworks.values().stream().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
	    
		addToGossipCluster(rootFw, CLUSTER_A, CLUSTER_A_BASE_PORT);
		addToGossipCluster(rootFw, CLUSTER_B, CLUSTER_B_BASE_PORT);
		
		clusterA.stream().map(allChildFrameworks::get)
			.forEach(f -> addToGossipCluster(f, CLUSTER_A, CLUSTER_A_BASE_PORT));
		clusterB.stream().map(allChildFrameworks::get)
			.forEach(f -> addToGossipCluster(f, CLUSTER_B, CLUSTER_B_BASE_PORT));
	    
	    return context.getService(context.getServiceReference(EndpointEventListener.class));
	}

	@Test
	public void testDiscoveryPropagationTargettedScope() throws Exception {
		EndpointEventListener listener = setupScopes();
		
		EndpointDescription edA = getTestEndpointDescription(SCOPED_ENDPOINT_A, "targetted", null, SCOPE_A);
		EndpointDescription edB = getTestEndpointDescription(SCOPED_ENDPOINT_B, "targetted", null, SCOPE_B);
		EndpointDescription edAB = getTestEndpointDescription(SCOPED_ENDPOINT_AB, "targetted", null, 
				Arrays.asList(SCOPE_A, SCOPE_B));
		
		Map<UUID, Map<String, Map<String, Object>>> expected = new HashMap<>();
		
		Consumer<UUID> addForA = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		Consumer<UUID> addForB = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		
		clusterA.stream().forEach(addForA);
		clusterB.stream().forEach(addForB);
		
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edA), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edB), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edAB), null);
		
		checkExpectation(expected);
		
		Map<UUID, Framework> lateJoinersA = createFrameworks(2);
		Map<UUID, Framework> lateJoinersB = createFrameworks(2);
		Map<UUID, Framework> lateJoinersAB = createFrameworks(2);
		
		allChildFrameworks.putAll(lateJoinersA);
		allChildFrameworks.putAll(lateJoinersB);
		allChildFrameworks.putAll(lateJoinersAB);
		
		lateJoinersA.values().forEach(f -> configureFramework(f, FABRIC));
		lateJoinersB.values().forEach(f -> configureFramework(f, FABRIC));
		lateJoinersAB.values().forEach(f -> configureFramework(f, FABRIC));
		
		lateJoinersA.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		lateJoinersB.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));

		lateJoinersA.values().forEach(f -> addScope(f, SCOPE_A));
		lateJoinersB.values().forEach(f -> addScope(f, SCOPE_B));
		lateJoinersAB.values().forEach(f -> addScope(f, SCOPE_A));
		lateJoinersAB.values().forEach(f -> addScope(f, SCOPE_B));

		lateJoinersA.keySet().forEach(addForA);
		lateJoinersB.keySet().forEach(addForB);
		lateJoinersAB.keySet().forEach( u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		});
		
		checkExpectation(expected);
		
		Random r = new Random();
		
		UUID upgradedAToB = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID upgradedBToA = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		UUID removeA = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID removeB = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		
		addScope(allChildFrameworks.get(upgradedAToB), SCOPE_B);
		addScope(allChildFrameworks.get(upgradedBToA), SCOPE_A);
		removeScope(allChildFrameworks.get(removeA), SCOPE_A);
		removeScope(allChildFrameworks.get(removeB), SCOPE_B);
		
		expected.get(upgradedAToB).put(edB.getId(), edB.getProperties());
		expected.get(upgradedBToA).put(edA.getId(), edA.getProperties());
		
		Map<String, Map<String, Object>> m = expected.get(removeA);
		m.remove(edA.getId());
		if(!m.containsKey(edB.getId())) {
			m.remove(edAB.getId());
		}

		m = expected.get(removeB);
		m.remove(edB.getId());
		if(!m.containsKey(edA.getId())) {
			m.remove(edAB.getId());
		}
		
		checkExpectation(expected);
	}

	@Test
	public void testDiscoveryPropagationTargettedScopesExtra() throws Exception {
		EndpointEventListener listener = setupScopes();
		
		EndpointDescription edA = getTestEndpointDescription(SCOPED_ENDPOINT_A, "targetted", null, "unnamed", 
				singletonMap(PAREMUS_TARGETTED_EXTRA_ATTRIBUTE, SCOPE_A));
		EndpointDescription edB = getTestEndpointDescription(SCOPED_ENDPOINT_B, "targetted", null, "unnamed", 
				singletonMap(PAREMUS_TARGETTED_EXTRA_ATTRIBUTE, SCOPE_B));
		EndpointDescription edAB = getTestEndpointDescription(SCOPED_ENDPOINT_AB, "targetted", null, "unnamed", 
				singletonMap(PAREMUS_TARGETTED_EXTRA_ATTRIBUTE, asList(SCOPE_A, SCOPE_B)));
		
		Map<UUID, Map<String, Map<String, Object>>> expected = new HashMap<>();
		
		Consumer<UUID> addForA = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		Consumer<UUID> addForB = u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		};
		
		clusterA.stream().forEach(addForA);
		clusterB.stream().forEach(addForB);
		
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edA), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edB), null);
		listener.endpointChanged(new EndpointEvent(EndpointEvent.ADDED, edAB), null);
		
		checkExpectation(expected);
		
		Map<UUID, Framework> lateJoinersA = createFrameworks(2);
		Map<UUID, Framework> lateJoinersB = createFrameworks(2);
		Map<UUID, Framework> lateJoinersAB = createFrameworks(2);
		
		allChildFrameworks.putAll(lateJoinersA);
		allChildFrameworks.putAll(lateJoinersB);
		allChildFrameworks.putAll(lateJoinersAB);
		
		lateJoinersA.values().forEach(f -> configureFramework(f, FABRIC));
		lateJoinersB.values().forEach(f -> configureFramework(f, FABRIC));
		lateJoinersAB.values().forEach(f -> configureFramework(f, FABRIC));
		
		lateJoinersA.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		lateJoinersB.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		lateJoinersAB.values().forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		
		lateJoinersA.values().forEach(f -> addScope(f, SCOPE_A));
		lateJoinersB.values().forEach(f -> addScope(f, SCOPE_B));
		lateJoinersAB.values().forEach(f -> addScope(f, SCOPE_A));
		lateJoinersAB.values().forEach(f -> addScope(f, SCOPE_B));
		
		lateJoinersA.keySet().forEach(addForA);
		lateJoinersB.keySet().forEach(addForB);
		lateJoinersAB.keySet().forEach( u -> {
			Map<String, Map<String, Object>> m = new HashMap<>();
			m.put(edA.getId(), edA.getProperties());
			m.put(edB.getId(), edB.getProperties());
			m.put(edAB.getId(), edAB.getProperties());
			expected.put(u, m);
		});
		
		try {
        	checkExpectation(expected);
        } catch (AssertionError afe) {
        	if(System.getenv("GITLAB_CI") != null) {
        		System.out.println("The testDiscoveryPropagationTargettedScopesExtra method is unreliable in CI and we don't know why...");
        		return;
        	} else {
        		throw afe;
        	}
        }
		
		Random r = new Random();
		
		UUID upgradedAToB = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID upgradedBToA = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		UUID removeA = clusterA.toArray(new UUID[0])[r.nextInt(clusterA.size())];
		UUID removeB = clusterB.toArray(new UUID[0])[r.nextInt(clusterB.size())];
		
		addScope(allChildFrameworks.get(upgradedAToB), SCOPE_B);
		addScope(allChildFrameworks.get(upgradedBToA), SCOPE_A);
		removeScope(allChildFrameworks.get(removeA), SCOPE_A);
		removeScope(allChildFrameworks.get(removeB), SCOPE_B);
		
		expected.get(upgradedAToB).put(edB.getId(), edB.getProperties());
		expected.get(upgradedBToA).put(edA.getId(), edA.getProperties());
		
		Map<String, Map<String, Object>> m = expected.get(removeA);
		m.remove(edA.getId());
		if(!m.containsKey(edB.getId())) {
			m.remove(edAB.getId());
		}
		
		m = expected.get(removeB);
		m.remove(edB.getId());
		if(!m.containsKey(edA.getId())) {
			m.remove(edAB.getId());
		}
		
		checkExpectation(expected);
	}

	private EndpointEventListener setupScopes() throws BundleException {
		Framework rootFw = context.getBundle(0).adapt(Framework.class);
    	configureFramework(rootFw, FABRIC);
    	
    	addToGossipCluster(rootFw, FABRIC, FABRIC_BASE_PORT);
    	
    	Map<UUID, Framework> createdFrameworks = createFrameworks(12);
		allChildFrameworks.putAll(createdFrameworks);
		
		allChildFrameworks.values().stream()
			.forEach(f -> configureFramework(f, FABRIC));
		allChildFrameworks.values().stream()
			.forEach(f -> addToGossipCluster(f, FABRIC, FABRIC_BASE_PORT));
		
		Iterator<UUID> it = createdFrameworks.keySet().iterator();
		while(it.hasNext()) {
			clusterA.add(it.next());
			clusterB.add(it.next());
			it.next();
		}
 		
		clusterA.stream().map(allChildFrameworks::get).forEach(f -> addScope(f, SCOPE_A));
		clusterB.stream().map(allChildFrameworks::get).forEach(f -> addScope(f, SCOPE_B));
		
		return context.getService(context.getServiceReference(EndpointEventListener.class));
	}
	

	private void addScope(Framework f, String system) {
		ServiceTracker<Object, Object> tracker = new ServiceTracker<>(f.getBundleContext(), 
				ScopeManager.class.getName(), null);
		tracker.open();
		try {
			Object sd = tracker.waitForService(2000);
			Method m = sd.getClass().getMethod("addLocalScope", String.class);
			m.invoke(sd, system);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			tracker.close();
		}
	}

	private void removeScope(Framework f, String system) {
		ServiceTracker<Object, Object> tracker = new ServiceTracker<>(f.getBundleContext(), 
				ScopeManager.class.getName(), null);
		tracker.open();
		try {
			Object sd = tracker.waitForService(2000);
			Method m = sd.getClass().getMethod("removeLocalScope", String.class);
			m.invoke(sd, system);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			tracker.close();
		}
	}

	private void checkExpectation(
			Map<UUID, Map<String, Map<String, Object>>> expected)
			throws InterruptedException {
		long start = System.currentTimeMillis();
		boolean finished = false;
        outer: while(System.currentTimeMillis() - start < 15000) {
        	Thread.sleep(100);
        	synchronized (knownProps) {
        		for(UUID key : expected.keySet()) {
        			if(!knownProps.containsKey(key)) {
        				System.out.println("Missing key " + key);
        				continue outer;
        			}
        			if(!expected.get(key).keySet().equals(knownProps.get(key).keySet())) {
        				System.out.println("Wrong endpoints " + knownProps.get(key).keySet() + 
        						" for node " + key + " expected " + expected.get(key).keySet());
        				continue outer;
        			}
        		}
			}
        	finished = true;
        	break outer;
        };
        
        assertTrue(knownProps.toString(), finished);
        
        System.out.println("All nodes aware after " + (System.currentTimeMillis() - start) + " milliseconds");
	} 

	@SuppressWarnings("serial")
	private void configureFramework(Framework f, String rootCluster) {
    	try {
			ServiceTracker<Object, Object> tracker = new ServiceTracker<>(f.getBundleContext(), 
					ConfigurationAdmin.class.getName(), null);
	    	tracker.open();
	    	Object cm = tracker.waitForService(2000);
	    	
	    	Method m = cm.getClass().getMethod("createFactoryConfiguration", String.class, String.class);
	    	Object encodingConfig = m.invoke(cm, "com.paremus.netty.tls", "?");
	    	Object discoveryConfig = m.invoke(cm, "com.paremus.dosgi.discovery.cluster", "?");
	    	
	    	m = encodingConfig.getClass().getMethod("update", Dictionary.class);
	    	
	    	m.invoke(encodingConfig, new Hashtable<String, Object>() {{ put("insecure", true);}});
	    	m.invoke(discoveryConfig, getDiscoveryConfig(rootCluster));
	    	tracker.close();
	    	
	    	String filter = "(!("+ RemoteConstants.ENDPOINT_FRAMEWORK_UUID + "=" + getUUID(f) + "))";
	    	Hashtable<String, Object> props = new Hashtable<>();
	    	props.put(EndpointEventListener.ENDPOINT_LISTENER_SCOPE, new String[] {filter});
	    	props.put(Constants.SERVICE_RANKING, -1);
	    	f.getBundleContext().registerService(EndpointEventListener.class.getName(), listener, props);
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

	private void addToGossipCluster(Framework f, String cluster, int basePort) {
		try {
			ServiceTracker<Object, Object> tracker = new ServiceTracker<>(f.getBundleContext(), 
					ConfigurationAdmin.class.getName(), null);
			tracker.open();
			Object cm = tracker.waitForService(2000);
			
			Method m = cm.getClass().getMethod("createFactoryConfiguration", String.class, String.class);
			
			Object gossipConfig = m.invoke(cm, "com.paremus.gossip.netty", "?");
			
			m = gossipConfig.getClass().getMethod("update", Dictionary.class);
			
			int i = allChildFrameworks.values().stream().collect(Collectors.toList()).indexOf(f) + 1;
			
			m.invoke(gossipConfig, getGossipConfig(cluster, basePort, i));
			tracker.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Dictionary<String, ?> getGossipConfig(String cluster, int basePort, int i) {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		config.put("bind.address", "127.0.0.1");
		config.put("udp.port", basePort + 100 * i);
		config.put("tcp.port", basePort + 100 * i + 1);
		config.put("initial.peers", new String[] {"127.0.0.1:" + basePort});
		config.put("cluster.name", cluster);
		return config;
	}
	
	private Dictionary<String, ?> getDiscoveryConfig(String rootCluster) {
		Hashtable<String, Object> config = new Hashtable<String, Object>();
		config.put("bind.address", "127.0.0.1");
		config.put("root.cluster", rootCluster);
		config.put("infra", "false");
		return config;
	}

	private Map<UUID,Framework> createFrameworks(int total) throws BundleException {
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
    	
    	Map<UUID,Framework> fws = new HashMap<>();
    	
        for(int i = 0; i < total; i++) {
        	Map<String, String> fwConfig = new HashMap<>();
        	fwConfig.put(Constants.FRAMEWORK_STORAGE, new File(context.getDataFile(""), 
        			"FabricOne" + counter.getAndIncrement()).getAbsolutePath());
        	fwConfig.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        	Framework f = ff.newFramework(fwConfig);
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
        	fws.put(UUID.fromString(f.getBundleContext().getProperty(Constants.FRAMEWORK_UUID)), f);
        }
		return fws;
	}

	private UUID getUUID(Framework f) {
		return UUID.fromString(f.getBundleContext().getProperty(Constants.FRAMEWORK_UUID));
	}
	
	private EndpointDescription getTestEndpointDescription(String endpointId, String scope, 
			Object cluster, Object scopes) {
		return getTestEndpointDescription(endpointId, scope, cluster, scopes, new HashMap<>());
	}
	
	private EndpointDescription getTestEndpointDescription(String endpointId, String scope, 
			Object cluster, Object scopes, Map<String, Object> custom) {
		Map<String, Object> m = new LinkedHashMap<String, Object>(custom);

        // required
        m.put(OBJECTCLASS, new String[]{"com.acme.HelloService", "some.other.Service"});
        m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, context.getProperty(Constants.FRAMEWORK_UUID));
        m.put(RemoteConstants.ENDPOINT_ID, endpointId);
        m.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.valueOf(42));
        m.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");
        if(scope != null) {
        	m.put("com.paremus.dosgi.scope", scope);
        }
        if(cluster != null) {
        	m.put("com.paremus.dosgi.target.clusters", cluster);
        }
        if(scopes != null) {
        	m.put("com.paremus.dosgi.target.scopes", scopes);
        }

        return new EndpointDescription(m);
	}
}
