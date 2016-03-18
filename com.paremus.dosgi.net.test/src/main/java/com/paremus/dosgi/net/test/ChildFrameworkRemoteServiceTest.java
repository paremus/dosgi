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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.util.function.Function;
import org.osgi.util.promise.Deferred;

import com.paremus.dosgi.scoping.rsa.MultiFrameworkRemoteServiceAdmin;

@RunWith(JUnit4.class)
public class ChildFrameworkRemoteServiceTest extends AbstractRemoteServiceTest {

	@SuppressWarnings("unused")
	private static final Class<?> FUNCTION_PACKAGE_LINK = Function.class;
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<?> reg;

	private Object service;

	private ImportRegistration importReg;

	private Framework sourceFramework;
	private Framework clientFramework;

	private Method pingMethod;
	private Method echoMethod;

	private Method delayedMethod;
    
	@Before
    public void setUp() throws Exception {
    	
		FrameworkFactory ff = ServiceLoader.load(FrameworkFactory.class, 
    			context.getBundle(0).adapt(ClassLoader.class)).iterator().next();
		
		Map<String, String> fwConfig = new HashMap<>();
    	fwConfig.put(Constants.FRAMEWORK_STORAGE, new File(context.getDataFile(""), "source").getAbsolutePath());
    	fwConfig.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
		
    	sourceFramework = ff.newFramework(fwConfig);
    	
    	fwConfig.put(Constants.FRAMEWORK_STORAGE, new File(context.getDataFile(""), "client").getAbsolutePath());
    	clientFramework = ff.newFramework(fwConfig);
    	
    	sourceFramework.start();
    	clientFramework.start();
    	
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	
    	Manifest manifest = new Manifest();
    	manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    	manifest.getMainAttributes().putValue("Bundle-ManifestVersion", "2");
    	manifest.getMainAttributes().putValue("Bundle-SymbolicName", "test-probe");
    	
    	try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {

    		copyBundleContent(jos, "org.osgi.util.pushstream");
    		copyBundleContent(jos, "org.osgi.util.promise");
    		copyBundleContent(jos, "org.osgi.util.function");

    		writeClass(jos, "org/osgi/service/async/delegate/AsyncDelegate.class");
    		
    		writeClass(jos, "com/paremus/dosgi/net/test/MyService.class");
    		writeClass(jos, "com/paremus/dosgi/net/test/MyServiceSecondRole.class");
    		
    	}
    	
    	Bundle testProbe = clientFramework.getBundleContext().installBundle(
    			"test-probe", new ByteArrayInputStream(baos.toByteArray()));
    	testProbe.start();
    	
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", "com.paremus.dosgi.net");
        props.put("service.exported.interfaces", "*");
        
        reg = sourceFramework.getBundleContext().registerService(new String[] {MyService.class.getName(), MyServiceSecondRole.class.getName()}, 
        		new MyServiceImpl(), props);
        
    	MultiFrameworkRemoteServiceAdmin manager = (MultiFrameworkRemoteServiceAdmin) setUpRSA();
    	
    	Collection<ExportRegistration> regs = manager.exportService(reg.getReference(), new HashMap<String, Object>());
    	assertFalse(regs.isEmpty());
    	EndpointDescription endpoint = regs.iterator().next().getExportReference().getExportedEndpoint();
    	
    	importReg = manager.importService(clientFramework, endpoint);
    	
    	Thread.sleep(100);
    	
    	ServiceReference<?>[] refs = testProbe.getBundleContext().getServiceReferences(
    			MyService.class.getName(), "(service.imported=true)");
    	assertEquals(1, refs.length);
    	
    	service = testProbe.getBundleContext().getService(refs[0]);
    	
    	pingMethod = service.getClass().getMethod("ping");
    	echoMethod = service.getClass().getMethod("echo", int.class, String.class);
    	delayedMethod = service.getClass().getMethod("delayedValue");
    }

	private void copyBundleContent(JarOutputStream jos, String bsn) {
		Bundle pushStreamBundle = Arrays.stream(context.getBundles())
			.filter(b -> bsn.equals(b.getSymbolicName()))
			.findFirst().get();
		
		Collections.list(pushStreamBundle.findEntries("/", "*.class", true))
			.stream()
			.map(URL::getPath)
			.map(s -> s.substring(1))
			.forEach(p -> writeClass(jos, p));
	}

	protected void writeClass(JarOutputStream jos, String resource) {
		try {
			jos.putNextEntry(new ZipEntry(resource));
			InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
			byte[] b = new byte[2048];
			int read;
			while((read = stream.read(b, 0, b.length)) != -1) {
				jos.write(b, 0, read);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@After
	public void tearDown() throws Exception {
		if(importReg != null) {
			importReg.close();
		}

		if(reg != null) {
			reg.unregister();
		}
		
		if(sourceFramework != null) {
			sourceFramework.stop();
		}
		
		if(clientFramework != null) {
			clientFramework.stop();
		}
		
    	super.tearDown();
    }
    
	@Test
    public void testBasicCall() throws Exception {
    	assertEquals(42, pingMethod.invoke(service));
    	assertEquals(42, pingMethod.invoke(service));
    	assertEquals(42, pingMethod.invoke(service));
    	assertEquals(42, pingMethod.invoke(service));
    }

	@Test
    public void testBlockingCall() throws Exception {
    	assertEquals("foo", echoMethod.invoke(service, new Object[] {1, "foo"}));
    }

	@Test
    public void testBlockingCallAsync() throws Exception {
    	long now = System.currentTimeMillis();
    	Method asyncMethod = service.getClass().getMethod("async", Method.class, Object[].class);
    	asyncMethod.setAccessible(true);
    	Object p = asyncMethod.invoke(service, echoMethod, new Object[] {2, "bar"});
    	long callTook = System.currentTimeMillis() - now;
		assertTrue("Call took too long " + callTook, callTook < 500);
		Method method = p.getClass().getMethod("getValue");
    	method.setAccessible(true);
    	assertEquals("bar", method.invoke(p));
    }

	@Test
    public void testSerializedPromise() throws Exception {
    	long now = System.currentTimeMillis();
    	Object p = delayedMethod.invoke(service);
    	long callTook = System.currentTimeMillis() - now;
    	assertTrue("Call took too long " + callTook, callTook < 500);
    	Method method = p.getClass().getMethod("getValue");
    	method.setAccessible(true);
		assertEquals(Long.valueOf(12345L), method.invoke(p));
    }
	
    @Test
    public void testPromiseArg() throws Exception {
    	
    	Method waitForIt = Arrays.stream(service.getClass().getDeclaredMethods())
    		.filter(m -> "waitForIt".equals(m.getName()))
    		.findFirst().get();
    	
    	
    	Object arg = service.getClass().getClassLoader().loadClass(Deferred.class.getName())
    			.getConstructor().newInstance();
    	
    	long now = System.currentTimeMillis();
    	waitForIt.setAccessible(true);
    	Object p = waitForIt.invoke(service, arg.getClass().getMethod("getPromise").invoke(arg));
    	long callTook = System.currentTimeMillis() - now;
    	assertTrue("Call took too long " + callTook, callTook < 500);
    	
    	Method done = p.getClass().getMethod("isDone");
    	done.setAccessible(true);
		assertFalse((Boolean) done.invoke(p));
    	
    	Semaphore s = new Semaphore(0);
    	
    	Method onResolve = p.getClass().getMethod("onResolve", Runnable.class);
    	onResolve.setAccessible(true);
		onResolve.invoke(p, (Runnable) () -> s.release(1));
    	
    	assertFalse(s.tryAcquire(1, TimeUnit.SECONDS));

    	Method resolve = arg.getClass().getMethod("resolve", Object.class);
    	resolve.setAccessible(true);
		resolve.invoke(arg, 12345L);
    	
    	assertTrue(s.tryAcquire(1000, TimeUnit.MILLISECONDS));
    	
    	Method getValue = p.getClass().getMethod("getValue");
    	getValue.setAccessible(true);
		assertEquals(String.valueOf(12345L), getValue.invoke(p));
    }
}
