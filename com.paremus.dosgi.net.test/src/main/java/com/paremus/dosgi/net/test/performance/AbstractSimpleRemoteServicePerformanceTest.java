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
package com.paremus.dosgi.net.test.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import org.osgi.util.promise.Success;

import com.paremus.dosgi.net.test.AbstractRemoteServiceTest;
import com.paremus.dosgi.net.test.MyService;
import com.paremus.dosgi.net.test.MyServiceImpl;
import com.paremus.dosgi.net.test.MyServiceSecondRole;

public abstract class AbstractSimpleRemoteServicePerformanceTest extends AbstractRemoteServiceTest {
	
	private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
	
    private ServiceRegistration<?> reg;

	protected MyService service;

	private ImportRegistration importReg;
	
	private final String configType;
	private final Map<String, Object> additionalProperties;
	
    public AbstractSimpleRemoteServicePerformanceTest(String configType,
			Map<String, Object> additionalProperties) {
		this.configType = configType;
		this.additionalProperties = additionalProperties;
	}

    @Before
	public void setUp() throws Exception {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("service.exported.configs", configType);
        props.put("service.exported.interfaces", "*");
        
        reg = context.registerService(new String[] {MyService.class.getName(), MyServiceSecondRole.class.getName()}, 
        		new MyServiceImpl(), props);
        
    	RemoteServiceAdmin manager = setUpRSA();
    	
    	Collection<ExportRegistration> regs = manager.exportService(reg.getReference(), additionalProperties);
    	assertFalse(regs.isEmpty());
    	EndpointDescription endpoint = regs.iterator().next().getExportReference().getExportedEndpoint();
    	
    	importReg = manager.importService(endpoint);
    	
    	Thread.sleep(100);
    	
    	ServiceReference<?>[] refs = context.getServiceReferences(MyService.class.getName(), 
    			"(service.imported=true)");
    	assertEquals(1, refs.length);
    	
		service = (MyService) context.getService(refs[0]);
    }

    @After
	public void tearDown() throws Exception {
		if(importReg != null) {
			importReg.close();
		}
    	
    	if(reg != null) {
    		reg.unregister();
    	}
    	
    	Thread.sleep(100);
    	
    	super.tearDown();
    }

    @Override
	protected Map<String, Object> getRSAConfig() {
		Map<String, Object> rsaConfig = super.getRSAConfig();
		rsaConfig.put("share.io.threads", "false");
		rsaConfig.put("share.worker.threads", "false");
		return rsaConfig;
	}

	@Test
    public void test50000SingleThreadedCalls() throws Exception {
    	//100 cycle warm up
		long time = perform(1000, 50000, service);

    	System.out.println(getClass().getSimpleName() + ": Took " + time);
    }

    @Test
	public void test50000SingleThreadedCallsWithArgs() throws Exception {
		//1000 cycle warm up
		long time = performWithArgs(1000, 50000, service);
		
		System.out.println(getClass().getSimpleName() + ": Took " + time);
	}

	private long perform(int warmup, int main, MyService service) {
		
		final int value = getPingValue();
		
		System.out.println("Warming up");
		for(int i = 0; i < warmup; i++) {
			assertEquals(value, service.ping());
		}
		
		System.out.println("Running");
		long start = System.nanoTime();
		for(int i = 0; i < main; i++) {
			assertEquals(value, service.ping());
		}
		long time = System.nanoTime() - start;
		return time;
	}

	protected int getPingValue() {
		return 42;
	}

	private long performWithArgs(int warmup, int main, MyService service) {
		final String input = "A reasonably long String";
		final String output = getDecoratedOutput(input);
		System.out.println("Warming up");
		for(int i = 0; i < warmup; i++) {
			assertEquals(output, service.echo(-1, input));
		}
		
		System.out.println("Running");
		long start = System.nanoTime();
		for(int i = 0; i < main; i++) {
			assertEquals(output, service.echo(-1, input));
		}
		long time = System.nanoTime() - start;
		return time;
	}

	protected String getDecoratedOutput(String input) {
		return input;
	}
	
	@Test
	public void test300000MultiThreadedCalls() throws Exception {
    	
    	Runnable target = new Runnable() {
    		@Override
    		public void run() {
    			System.out.println(getClass().getSimpleName() + ": took " + perform(1000, 100000, service));
    		}
    	};
		Thread t1 = new Thread(target);
		Thread t2 = new Thread(target);
		t1.start();
		t2.start();
    	target.run();
    	t1.join();
    	t2.join();
    }
	
	@Test
	public void testLatency() {
		
		long[] times1 = new long[50000];
		
		for(int i = 0; i < 50000; i++) {
			long start = System.nanoTime();
			service.ping();
			times1[i] = System.nanoTime() - start;
		}

		long[] times2 = new long[50000];
		
		for(int i = 0; i < 50000; i++) {
			long start = System.nanoTime();
			service.echo(-1, "A reasonably long String");
			times2[i] = System.nanoTime() - start;
		}
		
		printSummary(times1);
		printSummary(times2);
	}
	
	@Test
	public void testAsync() throws Exception {
		
		if(service instanceof AsyncDelegate) {
			final AsyncDelegate ad = (AsyncDelegate) service;
			
			final Method m = MyService.class.getMethod("ping");
			final Integer value = getPingValue();
			final Object[] args = new Object[0];
			
			System.out.println("Warming up async");
			List<Promise<?>> promises = new ArrayList<Promise<?>>(300000);
			
			for(int i = 0; i < 100; i++) {
				for(int j = 0; j < 50; j++) {
					promises.add(ad.async(m, args));
				}
				
				for(Promise<?> p : promises) {
					assertEquals(value, p.getValue());
				}
			}
			promises.clear();
			
			System.out.println("Running async");
			long start = System.nanoTime();
			
			final Semaphore sem = new Semaphore(50);
			final Success<Object, Object> success = new Success<Object, Object>() {
				@Override
				public Promise<Object> call(Promise<Object> resolved)
						throws Exception {
					assertEquals(value, resolved.getValue());
					sem.release();
					return null;
				}
			};
			
			for(int i = 0; i < 300000; i ++) {
				if(!sem.tryAcquire(1, TimeUnit.SECONDS)) {
					throw new TimeoutException("Stuck");
				}
				promises.add(ad.async(m, args).then(success));
			}
			
			for(Promise<?> p : promises) {
				p.getValue();
			}
			long time = System.nanoTime() - start;
			
			System.out.println(getClass().getSimpleName() + ": Async Took " + time);
			
		} else {
			System.out.println("Not asynchronous");
		}
		
	}

	protected void printSummary(long[] times) {
		Arrays.sort(times);
		
		long total = 0;
		for(long l : times) {
			total +=l;
		}
		
		final long avg = total/50000;
		
		for(long l : times) {
			total += ((avg -l) * (avg -l));
		}
		total /= 50000;
		
		final double deviation = Math.sqrt(total);
		
		System.out.println(getClass().getSimpleName() + ": Average latency was: " + avg + " standard deviation was " + deviation + 
				" 1%, 10%, 50%, 90%, 99%, 99.9%, 99.99% were " + times[500] + " " + times[5000] + " " + times[24999] + 
				" " + times[45000] + " " + times[49500] + " " + times[49950] + " " + times[49995]);
	}
}
