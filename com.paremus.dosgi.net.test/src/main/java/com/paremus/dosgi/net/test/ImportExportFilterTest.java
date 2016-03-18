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
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

@RunWith(JUnit4.class)
public class ImportExportFilterTest extends AbstractRemoteServiceTest {
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<?> reg;

	private MyService service;

	private ImportRegistration importReg;
	
	private ServiceReference<?> ref;

	private RemoteServiceAdmin manager;
    
	@Override
	protected Map<String, Object> getRSAConfig() {
		Map<String, Object> rsaConfig = super.getRSAConfig();
		rsaConfig.put("endpoint.export.target", "(foo=bar)");
		rsaConfig.put("endpoint.import.target", "(fizz=buzz)");
		rsaConfig.put("endpoint.marker", "fizzbuzz");
		return rsaConfig;
	}
	
	@Before
    public void setUp() throws Exception {
    	
    	manager = setUpRSA();
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
    public void testExportFilter() throws Exception {
		Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", "com.paremus.dosgi.net");
        props.put("service.exported.interfaces", "*");
        
        reg = context.registerService(new String[] {MyService.class.getName(), MyServiceSecondRole.class.getName()}, 
        		new MyServiceImpl(), props);
        
        Collection<ExportRegistration> exports = manager.exportService(reg.getReference(), new HashMap<String, Object>());
        assertTrue(exports.isEmpty());

        props.put("foo", "bar");
        
        reg.setProperties(props);
        
        exports = manager.exportService(reg.getReference(), new HashMap<String, Object>());
        assertFalse(exports.isEmpty());
        
        EndpointDescription endpoint = exports.iterator().next().getExportReference().getExportedEndpoint();
        
        assertNotNull(endpoint);
        
        assertEquals("fizzbuzz", endpoint.getProperties().get("com.paremus.dosgi.net.endpoint.marker"));
    }

	@Test
	public void testImportFilter() throws Exception {
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put("service.exported.configs", "com.paremus.dosgi.net");
		props.put("service.exported.interfaces", "*");
		props.put("foo", "bar");
		
		reg = context.registerService(new String[] {MyService.class.getName(), MyServiceSecondRole.class.getName()}, 
				new MyServiceImpl(), props);
		
		Collection<ExportRegistration> exports = manager.exportService(reg.getReference(), new HashMap<String, Object>());
		assertFalse(exports.isEmpty());
		
		EndpointDescription endpoint = exports.iterator().next().getExportReference().getExportedEndpoint();
		assertNotNull(endpoint);
		
		assertNull(manager.importService(endpoint));
		
		props.put("fizz", "buzz");
		reg.setProperties(props);
		
		endpoint = exports.iterator().next().update(null);
		assertNotNull(endpoint);
		
		importReg = manager.importService(endpoint);
		
		assertNull(importReg.getException());
    	assertNotNull(importReg.getImportReference());
    	
    	Thread.sleep(100);
    	
    	ServiceReference<?>[] refs = context.getServiceReferences(MyService.class.getName(), 
    			"(service.imported=true)");
    	assertEquals(1, refs.length);
    	
    	ref = refs[0];
    	
    	service = (MyService) context.getService(ref);
    	
    	assertEquals(42, service.ping());
	}
}
