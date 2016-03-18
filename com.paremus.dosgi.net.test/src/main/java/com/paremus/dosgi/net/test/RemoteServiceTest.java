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
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.async.delegate.AsyncDelegate;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.pushstream.PushEventConsumer;
import org.osgi.util.pushstream.PushEventSource;
import org.osgi.util.pushstream.PushStreamProvider;
import org.osgi.util.pushstream.PushbackPolicyOption;

@RunWith(JUnit4.class)
public class RemoteServiceTest extends AbstractRemoteServiceTest {
	
	protected final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<?> reg;

	private MyService service;
	
	private ExportRegistration exportReg;

	private ImportRegistration importReg;
	
	private ServiceReference<?> ref;

	private RemoteServiceAdmin manager;
    
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
    	exportReg = regs.iterator().next();
		EndpointDescription endpoint = exportReg.getExportReference().getExportedEndpoint();
    	
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
	public void testBasicCallWithComplexType() throws Exception {
		assertEquals(new Version(2,3,4), service.increment(new Version(1,2,3)));
	}

	@Test
    public void testBlockingCall() throws Exception {
    	assertEquals("foo", service.echo(1, "foo"));
    }

	@Test
	public void testTimeoutCall() throws Exception {
		Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", "com.paremus.dosgi.net");
        props.put("service.exported.interfaces", "*");
        props.put("osgi.basic.timeout", "2000");
        
		reg.setProperties(props);
		
		EndpointDescription update = exportReg.update(null);
		assertNotNull(update);
		assertTrue(importReg.update(update));
		
		assertEquals("foo", service.echo(1, "foo"));
		
		try {
			service.echo(5, "bar");
			fail("Should time out after 2 seconds");
		} catch (ServiceException se) {
			assertEquals(ServiceException.REMOTE, se.getType());
		}
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

    @Test
    public void testSerializedPromise() throws Exception {
    	long now = System.currentTimeMillis();
    	Promise<Long> p = service.delayedValue();
    	long callTook = System.currentTimeMillis() - now;
    	assertTrue("Call took too long " + callTook, callTook < 500);
    	assertEquals(Long.valueOf(12345L), p.getValue());
    }
    
    @Test
    public void testPromiseArg() throws Exception {
    	
    	Deferred<Long> arg = new Deferred<>();
    	
    	long now = System.currentTimeMillis();
    	Promise<String> p = service.waitForIt(arg.getPromise());
    	long callTook = System.currentTimeMillis() - now;
    	assertTrue("Call took too long " + callTook, callTook < 500);
    	
    	assertFalse(p.isDone());
    	
    	Semaphore s = new Semaphore(0);
    	
    	p.onResolve(() -> s.release(1));
    	
    	assertFalse(s.tryAcquire(1, TimeUnit.SECONDS));

    	arg.resolve(12345L);
    	
    	assertTrue(s.tryAcquire(1000, TimeUnit.MILLISECONDS));
    	
    	assertEquals(String.valueOf(12345L), p.getValue());
    }

    @Test
    public void testPushStreamReturn() throws Exception {
    	
    	Promise<Long> count = service.streamToMe(20,0)
    		.count();
    	
    	assertEquals(20L, count.getValue().longValue());
    }

    @Test
    public void testPushStreamBackPressure() throws Exception {
    	// Do a slow accumulation with a small buffer which will overflow if backpressure is ignored
    	Promise<Long> count = service.streamToMe(20,10)
    			.buildBuffer()
    			.withExecutor(Executors.newSingleThreadExecutor())
    			.withBuffer(new ArrayBlockingQueue<>(4))
    			.withPushbackPolicy(PushbackPolicyOption.FIXED, 110)
    			.build()
    			.reduce(0L, (a, b) -> {
    					try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
    					return a + b;
    				});
    	
    	assertEquals(190L, count.getValue().longValue());
    }

    @Test
    public void testPushStreamEarlyTermination() throws Exception {
    	Promise<Boolean> tooBig = service.streamToMe(20,100)
    			.anyMatch(l -> l > 10);
    	
    	assertTrue(tooBig.getValue());
    	
    	Thread.sleep(20);
    	
    	long earlyExit = service.lastStreamQuitEarlyAt();
    	
    	assertTrue("Exited too early " + earlyExit, earlyExit >= 11);
    	assertTrue("Exited too late " + earlyExit, earlyExit < 15);
    }

    @Test
    public void testPushEventSourceReturn() throws Exception {
    	
    	Deferred<Long> total = new Deferred<>();
    	
    	AtomicLong counter = new AtomicLong();
    	service.repeatableStreamToMe(20,0)
    			.open(e -> {
    					if(e.isTerminal()) {
    						total.resolve(counter.get());
    					} else {
    						counter.addAndGet(e.getData());
    						return 0;
    					}
    					return -1;
    				});
    	;
    	assertEquals(190L, total.getPromise().getValue().longValue());
    }

    @Test
    public void testPushEventSourceMultiplexing() throws Exception {
    	
    	Semaphore s = new Semaphore(0);
    	
    	AtomicLong counter = new AtomicLong();
    	AtomicLong counter2 = new AtomicLong();
    	
    	BiFunction<AtomicLong, Long, PushEventConsumer<Long>> consumer = (c, max) -> 
    			e -> {
		    		if(e.isTerminal()) {
		    			s.release(1);
		    		} else {
		    			return c.addAndGet(e.getData()) > max ? -1 : 0;
		    		}
		    		return -1;
    			};

    	
    	PushEventSource<Long> source = service.repeatableStreamToMe(20,50);
    	
    	PushEventConsumer<Long> first = consumer.apply(counter, 50L);
    	PushEventConsumer<Long> second = consumer.apply(counter2, 90L);
    	
    	PushEventConsumer<Long> chaining =  e -> {
    			if(!e.isTerminal() && e.getData().equals(4L)) {
    				source.open(second);
    			}
    			return first.accept(e);
    		};
    	
    	source.open(chaining);
    	
    	assertTrue(s.tryAcquire(2, 5, TimeUnit.SECONDS));
    	// 0 -> 10
    	assertEquals(55L, counter.get());
    	// 5 -> 14
    	assertEquals(95L, counter2.get());
    	
    	Thread.sleep(20);
    	
    	assertEquals(14, service.lastStreamQuitEarlyAt());
    }
    
    @Test
    public void testPushEventSourceBackPressure() throws Exception {
    	
    	PushStreamProvider psp = new PushStreamProvider();
    	// Do a slow accumulation with a small buffer which will overflow if backpressure is ignored
    	Promise<Long> count = psp.buildStream(service.repeatableStreamToMe(20,10))
    			.withExecutor(Executors.newSingleThreadExecutor())
    			.withBuffer(new ArrayBlockingQueue<>(4))
    			.withPushbackPolicy(PushbackPolicyOption.FIXED, 110)
    			.build()
    			.reduce(0L, (a, b) -> {
    				try {
    					Thread.sleep(100);
    				} catch (InterruptedException e) {
    					e.printStackTrace();
    				}
    				return a + b;
    			});
    	
    	assertEquals(190L, count.timeout(20000).getValue().longValue());
    }
    
    @Test
    public void testPushEventSourceEarlyTermination() throws Exception {
    	Deferred<Long> total = new Deferred<>();
    	
    	AtomicLong counter = new AtomicLong();
    	service.repeatableStreamToMe(20,100)
    			.open(e -> {
    					if(e.isTerminal()) {
    						total.resolve(counter.get());
    					} else {
    						Long data = e.getData();
							counter.addAndGet(data);
							return data > 10 ? -1 : 0;
    					}
    					return -1;
    				});
    	
    	assertEquals(66L, total.getPromise().getValue().longValue());
    	
    	Thread.sleep(20);
    	
    	long earlyExit = service.lastStreamQuitEarlyAt();
    	
    	assertTrue("Exited too early " + earlyExit, earlyExit >= 11);
    	assertTrue("Exited too late " + earlyExit, earlyExit < 15);
    }

    @Test
    public void testPushEventSourceExternalEarlyTermination() throws Exception {
    	Deferred<Long> total = new Deferred<>();
    	
    	AtomicLong counter = new AtomicLong();
    	
    	AtomicReference<AutoCloseable> ac = new AtomicReference<>();
    	ac.set(service.repeatableStreamToMe(20,100)
    	.open(e -> {
    		if(e.isTerminal()) {
    			total.resolve(counter.get());
    		} else {
    			Long data = e.getData();
    			counter.addAndGet(data);
    			if(data > 10) {
    				ac.get().close();
    			}
    			return 0;
    		}
    		return -1;
    	}));
    	
    	assertEquals(66L, total.getPromise().getValue().longValue());
    	
    	Thread.sleep(20);
    	
    	long earlyExit = service.lastStreamQuitEarlyAt();
    	
    	assertTrue("Exited too early " + earlyExit, earlyExit >= 11);
    	assertTrue("Exited too late " + earlyExit, earlyExit < 15);
    }
    
    @Test
    public void testClosedClientSocketUnregisters() throws Exception {
    	assertEquals(42, service.ping());

    	Field f = importReg.getClass().getDeclaredField("_channel");
    	f.setAccessible(true);
    	Object channel = f.get(importReg);
    	
    	channel.getClass().getMethod("close").invoke(channel);
    	
    	try {
    		service.ping();
    		fail();
    	} catch (ServiceException se) {
    		assertEquals(ServiceException.REMOTE, se.getType());
    	}
    	
    	for(int i = 0; i < 10; i++) {
    		if(ref.getBundle() == null) break;
    		Thread.sleep(100);
    	}
    	
    	//The service should be unregistered but the import not closed
    	assertNull(ref.getBundle());
    	assertNotNull(importReg.getException());
    	
    }

    @Test
    public void testClosedServerSocketUnregisters() throws Exception {
    	assertEquals(42, service.ping());
    	
    	Field f = manager.getClass().getDeclaredField("remoteProviders");
    	f.setAccessible(true);
    	@SuppressWarnings("unchecked")
		Object provider = ((List<Object>)f.get(manager)).get(0);
    	
    	f = provider.getClass().getDeclaredField("channelGroup");
    	f.setAccessible(true);
    	Object channelGroup = f.get(provider);
    	
    	Class<?> matcherClass = channelGroup.getClass().getClassLoader()
    			.loadClass("io.netty.channel.group.ChannelMatcher");
    	Class<?> matchersClass = channelGroup.getClass().getClassLoader()
    			.loadClass("io.netty.channel.group.ChannelMatchers");
    	
    	Object matcher = matchersClass.getMethod("isNonServerChannel").invoke(null);
    	
    	channelGroup.getClass().getMethod("close", matcherClass).invoke(channelGroup, matcher);
    	
    	
    	try {
    		service.ping();
    		fail();
    	} catch (ServiceException se) {
    		assertEquals(ServiceException.REMOTE, se.getType());
    	}
    	
    	for(int i = 0; i < 10; i++) {
    		if(ref.getBundle() == null) break;
    		Thread.sleep(100);
    	}
    	
    	//The service should be unregistered but the import not closed
    	assertNull(ref.getBundle());
    	assertNotNull(importReg.getException());
    	
    }
}
