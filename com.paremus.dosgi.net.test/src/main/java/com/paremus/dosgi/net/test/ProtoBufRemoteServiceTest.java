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
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;

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

import com.example.tutorial.AddressBookProtos.Person;
import com.example.tutorial.AddressBookProtos.Person.PhoneNumber;
import com.example.tutorial.AddressBookProtos.Person.PhoneType;

@RunWith(JUnit4.class)
public class ProtoBufRemoteServiceTest extends AbstractRemoteServiceTest {
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<ProtobufService> reg;

	private ProtobufService service;

	private ImportRegistration importReg;
    
	@Before
    public void setUp() throws Exception {
    	
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", "com.paremus.dosgi.net");
        props.put("service.exported.interfaces", "*");
        props.put("com.paremus.dosgi.net.serialization", "PROTOCOL_BUFFERS");
        
        reg = context.registerService(ProtobufService.class, 
        		new ProtobufServiceImpl(), props);
        
    	RemoteServiceAdmin manager = setUpRSA();
    	
    	Collection<ExportRegistration> regs = manager.exportService(reg.getReference(), new HashMap<String, Object>());
    	assertFalse(regs.isEmpty());
    	EndpointDescription endpoint = regs.iterator().next().getExportReference().getExportedEndpoint();
    	
    	importReg = manager.importService(endpoint);
    	
    	Thread.sleep(100);
    	
    	ServiceReference<?>[] refs = context.getServiceReferences(ProtobufService.class.getName(), 
    			"(service.imported=true)");
    	assertEquals(1, refs.length);
    	
    	service = (ProtobufService) context.getService(refs[0]);
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
    	
    	Person p = Person.newBuilder()
    		.addPhone(PhoneNumber.newBuilder().setNumber("12345").setType(PhoneType.HOME))
    		.addPhone(PhoneNumber.newBuilder().setNumber("23456").setType(PhoneType.MOBILE))
    		.addPhone(PhoneNumber.newBuilder().setNumber("34567").setType(PhoneType.WORK))
    		.setId(1)
    		.setName("Bob")
    		.build();
    		
    	
    	assertEquals("23456", service.findMobile(p).getNumber());
    }

	@Test
    public void testBlockingCallAsync() throws Exception {
    	Method m = ProtobufService.class.getMethod("findMobile", Person.class);
    	
    	Person p = Person.newBuilder()
        		.addPhone(PhoneNumber.newBuilder().setNumber("12345").setType(PhoneType.HOME))
        		.addPhone(PhoneNumber.newBuilder().setNumber("23456").setType(PhoneType.MOBILE))
        		.addPhone(PhoneNumber.newBuilder().setNumber("34567").setType(PhoneType.WORK))
        		.setId(1)
        		.setName("Bob")
        		.build();
    	
    	long now = System.currentTimeMillis();
    	@SuppressWarnings("unchecked")
		Promise<PhoneNumber> f = (Promise<PhoneNumber>) ((AsyncDelegate)service).async(m, new Object[] {p});
    	long callTook = System.currentTimeMillis() - now;
		assertTrue("Call took too long " + callTook, callTook < 500);
		assertFalse(f.isDone());
    	assertEquals("23456", f.getValue().getNumber());
    }
}
