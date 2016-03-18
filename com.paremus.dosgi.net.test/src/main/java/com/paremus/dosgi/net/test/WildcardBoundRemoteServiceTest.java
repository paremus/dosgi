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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.async.delegate.AsyncDelegate;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.promise.Promise;

@RunWith(JUnit4.class)
public class WildcardBoundRemoteServiceTest extends AbstractRemoteServiceTest {
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<?> reg;

	private MyService service;

	private ImportRegistration importReg;
	
	private ServiceReference<?> ref;

	private RemoteServiceAdmin manager;
    
	@Override
	protected Map<String, Object> getRSAConfig() {
		Map<String, Object> rsaConfig = super.getRSAConfig();
		rsaConfig.put("server.bind.address", "0.0.0.0");
		return rsaConfig;
	}

	
	@Before
    public void setUp() throws Exception {
    	
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", "com.paremus.dosgi.net");
        props.put("service.exported.interfaces", "*");
        
        reg = context.registerService(new String[] {MyService.class.getName(), MyServiceSecondRole.class.getName()}, 
        		new MyServiceImpl(), props);
        
    	manager = setUpRSA();
    	
    	Collection<ExportRegistration> regs = manager.exportService(reg.getReference(), new HashMap<String, Object>());
    	assertFalse(regs.isEmpty());
    	EndpointDescription endpoint = regs.iterator().next().getExportReference().getExportedEndpoint();
    	
    	importReg = manager.importService(endpoint);
    	
    	assertNull(importReg.getException());
    	assertNotNull(importReg.getImportReference());
    	
    	Thread.sleep(100);
    	
    	ServiceReference<?>[] refs = context.getServiceReferences(MyService.class.getName(), 
    			"(service.imported=true)");
    	assertEquals(1, refs.length);
    	
    	ref = refs[0];
    	
    	service = (MyService) context.getService(ref);
    }

	@After
	public void tearDown() throws Exception {
		if(importReg != null) {
			importReg.close();
		}

		if(reg != null) {
			reg.unregister();
		}
    	super.tearDown();
    }
    
	@Test
    public void testBasicCall() throws Exception {
    	assertEquals(42, service.ping());
    	assertEquals(42, service.ping());
    	assertEquals(42, service.ping());
    	assertEquals(42, service.ping());
    }

	@Test
    public void testBlockingCallAsync() throws Exception {
    	Method m = MyService.class.getMethod("echo", int.class, String.class);
    	long now = System.currentTimeMillis();
    	Promise<?> f = ((AsyncDelegate)service).async(m, new Object[] {2, "bar"});
    	long callTook = System.currentTimeMillis() - now;
		assertTrue("Call took too long " + callTook, callTook < 500);
    	assertEquals("bar", f.getValue());
    }
}
