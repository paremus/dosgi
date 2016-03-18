/*-
 * #%L
 * com.paremus.dosgi.net.test
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
package com.paremus.dosgi.net.test;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.TestTimedOutException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.tracker.ServiceTracker;

public abstract class AbstractRemoteServiceTest {
	
	@Rule
	public TestName testName = new TestName();

	@Rule
	public RuleChain timeoutAndReporter = RuleChain.outerRule(new TestWatcher() {

			@Override
			protected void failed(Throwable e, Description description) {
				if(e instanceof TestTimedOutException) {
					System.out.println("The test timed out, printing the stacks of all threads to aid debug\n\n");
					printThreadStacks();
				}
			}
			
		}).around(Timeout.builder().withTimeout(3, TimeUnit.MINUTES).build());
	
	@Before
	public final void logTestName() {
		System.out.println("\n\n   TEST " + testName.getMethodName() + " running in " + getClass() + " \n\n");
	}

	@Before
	public final void logEndOfTest() {
		System.out.println("\n\n   TEST " + testName.getMethodName() + " in " + getClass() + " is complete \n\n");
	}
	
	private static final String NET_PID = "com.paremus.netty.tls";

	private static final String RSA_PID = "com.paremus.dosgi.net";

	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> cmTracker;
    private ServiceTracker<RemoteServiceAdmin, RemoteServiceAdmin> rsaTracker;
    
    protected RemoteServiceAdmin setUpRSA() throws Exception {
    	
    	cmTracker = new ServiceTracker<>(context, ConfigurationAdmin.class, null);
    	cmTracker.open();
        rsaTracker = new ServiceTracker<>(context, context.createFilter(
        		"(remote.configs.supported=com.paremus.dosgi.net)"), null);
        rsaTracker.open();
        
        ConfigurationAdmin configurationAdmin = cmTracker.waitForService(5000);

        if(configurationAdmin.listConfigurations(
        		"(|(service.pid=" + RSA_PID + ")(service.factoryPid=" + NET_PID + "))") == null) {
        	createConfigurations(configurationAdmin);
        } else {
        	Configuration[] cfgs = configurationAdmin.listConfigurations("(service.factoryPid=" + NET_PID + ")");
        	
        	Configuration cfg;
        	if(cfgs == null) {
        		cfg = configurationAdmin.createFactoryConfiguration(NET_PID, "?");
        	} else {
        		cfg = cfgs[0];
        	}
        	
        	Dictionary<String, Object> rsaProperties = configurationAdmin
        			.getConfiguration(RSA_PID).getProperties();
        	Dictionary<String, Object> netProperties = cfg.getProperties();
        	
        	if(!same(rsaProperties, addPid(getRSAConfig(), RSA_PID)) || 
        			!same(netProperties, addPid(getNetConfig(), NET_PID))) {
        		cfg.delete();
        		configurationAdmin.getConfiguration(RSA_PID).delete();
        		createConfigurations(configurationAdmin);
        		Thread.sleep(100);
        	}
        }
		
    	RemoteServiceAdmin manager = (RemoteServiceAdmin) rsaTracker.waitForService(5000);
    	
    	if(manager == null) {
    		
    		System.out.println("An error occurred setting up the RSA - printing thread stack traces");
    		
    		printThreadStacks();
    		
    		fail("Failing test as we were unable to set up the Remote Service Admin");
    	}
    	
    	return manager;
    }

	private void printThreadStacks() {
		Thread.getAllStackTraces().entrySet().stream()
			.forEach(e -> System.out.println(e.getKey().toString() + ":\n" + Arrays.toString(e.getValue()) +"\n\n"));
	}

	private boolean same(Dictionary<String, Object> rsaProperties, Map<String, Object> rsaConfig) {
		if(rsaProperties == null) {
			System.out.println("Config has changed! Recreating it");
			return false;
		}
		
		if(rsaProperties.size() == rsaConfig.size()) {
			
			for(Enumeration<String> e = rsaProperties.keys(); e.hasMoreElements();) {
				String key = e.nextElement();
				if(!rsaProperties.get(key).equals(rsaConfig.get(key))) {
					System.out.println("Config has changed! Recreating it");
					return false;
				}
			}
			return true;
		}
		System.out.println("Config has changed! Recreating it");
		return false;
	}

	private Map<String, Object> addPid(Map<String, Object> rsaConfig, String pid) {
		Map<String, Object> toReturn = new HashMap<>(rsaConfig);
		toReturn.put("service.pid", pid);
		return toReturn;
	}

	private void createConfigurations(ConfigurationAdmin configurationAdmin) throws IOException {
		configurationAdmin.getConfiguration(RSA_PID, null)
			.update(new Hashtable<>(getRSAConfig()));
        
		configurationAdmin.createFactoryConfiguration(NET_PID, null)
			.update(new Hashtable<>(getNetConfig()));
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected Map<String, Object> getNetConfig() {
		Map<String, Object> props = new HashMap<>();
		props.put("insecure", true);
		return props;
	}

	protected Map<String, Object> getRSAConfig() {
		Map<String, Object> config = new HashMap<String, Object>();
        config.putAll(singletonMap("allow.insecure.transports", true));
        config.put("server.bind.address", "127.0.0.1");
		return config;
	}
	
	public void tearDown() throws Exception {
		
		if(cmTracker != null) {
			cmTracker.close();
		}
    	
    	if(rsaTracker != null) {
    		try {
    			rsaTracker.close();
    		} catch (Exception e) {}
    	}
    }
}
