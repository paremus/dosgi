/*-
 * #%L
 * com.paremus.dosgi.net
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
package com.paremus.dosgi.net.proxy;

import static com.paremus.dosgi.net.client.ClientMessageType.WITH_RETURN;
import static java.util.Arrays.asList;
import static java.util.Arrays.deepEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.util.promise.Promise;

import com.paremus.dosgi.net.client.ClientInvocation;
import com.paremus.dosgi.net.client.ClientMessageType;
import com.paremus.dosgi.net.impl.ImportRegistrationImpl;
import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;

@RunWith(MockitoJUnitRunner.class)
public class PromiseReturningServiceInvocationHandlerTest {

    @Mock
    private ImportRegistrationImpl _importRegistration;
    @Mock
    Channel _ch;
    @Mock
    Serializer _serializer;
    @Mock
    Bundle _callingContext;
    
    
	private EndpointDescription _endpointDescription;
	
	private Class<?> _proxyClass;
	private List<Class<?>> _proxyClassInterfaces;
	private Class<?> _proxyClassWithDifferentPromise;
	private List<Class<?>> _proxyClassWithDifferentPromiseInterfaces;
	private Class<?> _differentPromise;
    
	private EventExecutor executor;

    private Timer timer;
    
    @Before
	public void setUp() throws Exception {
        executor = new DefaultEventExecutor();
        timer = new HashedWheelTimer();
        
        Mockito.when(_ch.newPromise()).then(x -> new DefaultChannelPromise(_ch, executor));
        
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(RemoteConstants.ENDPOINT_ID, new UUID(123, 456).toString());
        map.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");
        map.put(Constants.OBJECTCLASS, new String[] {TestReturnsAsyncTypes.class.getName()});
        map.put("com.paremus.dosgi.net.methods", new String[] {"1=coprime[long,long]",
        		"2=isPrime[long]", "3=countGrainsOfSand[java.lang.String]"});
        _endpointDescription = new EndpointDescription(map);

        _proxyClass = Proxy.getProxyClass(new ClassLoader(){}, TestReturnsAsyncTypes.class);
        _proxyClassInterfaces = asList(TestReturnsAsyncTypes.class);
        
        ClassLoader differentClassLoader = getSeparateClassLoader();
        
        _proxyClassWithDifferentPromise = Proxy.getProxyClass(differentClassLoader, 
        		differentClassLoader.loadClass(TestReturnsAsyncTypes.class.getName()));
        _proxyClassWithDifferentPromiseInterfaces = asList(
        		differentClassLoader.loadClass(TestReturnsAsyncTypes.class.getName()));
        _differentPromise = differentClassLoader.loadClass(Promise.class.getName());
        
        Map<Integer, String> methods = new HashMap<>();
        methods.put(1, "coprime[long,long]");
        methods.put(2, "isPrime[long]");
        methods.put(3, "countGrainsOfSand[java.lang.String]");
        when(_importRegistration.getMethodMappings()).thenReturn(methods);
        when(_importRegistration.getId()).thenReturn(new UUID(123, 456));
    }

    @After
	public void tearDown() throws Exception {
		timer.stop();
		executor.shutdownGracefully();
		executor.awaitTermination(1, TimeUnit.SECONDS);
	}
    
	private ClassLoader getSeparateClassLoader() {
		return new ClassLoader() {
			private final Map<String, Class<?>> cache = new HashMap<String, Class<?>>();
			
    		@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
    			if(name.startsWith("java")) {
    				return super.loadClass(name);
    			}
    			Class<?> c = cache.get(name);
    			if(c != null) return c;
    			
    			String resourceName = name.replace('.', '/') + ".class";
    			
				InputStream resourceAsStream = PromiseReturningServiceInvocationHandlerTest.this.getClass()
						.getClassLoader().getResourceAsStream(resourceName);
				if(resourceAsStream == null) throw new ClassNotFoundException(name);
				try(InputStream is = resourceAsStream) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] b = new byte[4096];
					
					int i = 0;
					while((i = is.read(b)) > -1) {
						baos.write(b, 0, i);
					}
					c = defineClass(name, baos.toByteArray(), 0, baos.size());
				} catch (IOException e) {
					throw new ClassNotFoundException(name, e);
				}
				cache.put(name, c);
				return c;
			}
		};
	}

	private Object createProxy(Class<?> proxyClass, ServiceInvocationHandler handler) {
		try {
			return proxyClass.getConstructor(InvocationHandler.class).newInstance(handler);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
	}
	
    ArgumentMatcher<Object[]> isArrayOf(Object... o) {
		return new ArgumentMatcher<Object[]>() {
	
			@Override
			public boolean matches(Object[] item) {
				return (o.length == 0 && item == null) || deepEquals(o, item);
			}
		};
	}

    @Test
	public void testSuccessfulInvocationPromise() throws Exception {
    	
        ServiceInvocationHandler sih = new ServiceInvocationHandler(_importRegistration, _endpointDescription,
        		_callingContext, _proxyClass, _proxyClassInterfaces, Promise.class, false, null, null, _ch, 
        		_serializer, () -> 1, new AtomicLong(3000), executor, timer);

        TestReturnsAsyncTypes proxy = (TestReturnsAsyncTypes) createProxy(_proxyClass, sih);
        
        when(_ch.writeAndFlush(argThat(isInvocationWith(WITH_RETURN, 
        		TestReturnsAsyncTypes.class.getMethod("coprime", long.class, long.class).toString(), 
        		new Object[] {7L, 42L})), any()))
			.then(i -> {
				i.<ClientInvocation>getArgument(0).getResult()
        			.setSuccess(false);
				return null;
			});
        
        assertFalse(proxy.coprime(7, 42).getValue());
    }

    @Test
	public void testSuccessfulInvocationFuture() throws Exception {
		
		ServiceInvocationHandler sih = new ServiceInvocationHandler(_importRegistration, _endpointDescription,
				_callingContext, _proxyClass, _proxyClassInterfaces, Promise.class, false, null, null, _ch, 
        		_serializer, () -> 1, new AtomicLong(3000), executor, timer);
		
		TestReturnsAsyncTypes proxy = (TestReturnsAsyncTypes) createProxy(_proxyClass, sih);
		
		when(_ch.writeAndFlush(argThat(isInvocationWith(WITH_RETURN, 
        		TestReturnsAsyncTypes.class.getMethod("isPrime", long.class).toString(), 
        		new Object[] {17L})), any()))
			.then(i -> {
				i.<ClientInvocation>getArgument(0).getResult()
        			.setSuccess(false);
				return null;
			});
		
		assertFalse(proxy.isPrime(17).get());
	}

    @Test
	public void testSuccessfulInvocationCompletableFuture() throws Exception {
		
		ServiceInvocationHandler sih = new ServiceInvocationHandler(_importRegistration, _endpointDescription,
        		_callingContext, _proxyClass, _proxyClassInterfaces, Promise.class, false, null, null, _ch, 
        		_serializer, () -> 1, new AtomicLong(3000), executor, timer);

        TestReturnsAsyncTypes proxy = (TestReturnsAsyncTypes) createProxy(_proxyClass, sih);
        
        when(_ch.writeAndFlush(argThat(isInvocationWith(WITH_RETURN, 
        		TestReturnsAsyncTypes.class.getMethod("countGrainsOfSand", String.class).toString(), 
        		new Object[] {"Foo"})), any()))
			.then(i -> {
				i.<ClientInvocation>getArgument(0).getResult()
        			.setSuccess(false);
				return null;
			});
        
        assertFalse(proxy.countGrainsOfSand("Foo").get());
	}
	
    @Test
	public void testSuccessfulInvocationDifferentPromise() throws Exception {
    	
        ServiceInvocationHandler sih = new ServiceInvocationHandler(_importRegistration, _endpointDescription,
        		_callingContext, _proxyClass, _proxyClassWithDifferentPromiseInterfaces, _differentPromise, false, null, null, _ch, 
        		_serializer, () -> 1, new AtomicLong(3000), executor, timer);

        Object proxy = createProxy(_proxyClassWithDifferentPromise, sih);
        
        when(_ch.writeAndFlush(argThat(isInvocationWith(WITH_RETURN, 
        		TestReturnsAsyncTypes.class.getMethod("coprime", long.class, long.class).toString(), 
        		new Object[] {14L, 15L})), any()))
			.then(i -> {
				i.<ClientInvocation>getArgument(0).getResult()
        			.setSuccess(true);
				return null;
			});
        
        Method m = _proxyClassWithDifferentPromise.getMethod("coprime", long.class, long.class);
        
        Object returnedPromise = m.invoke(proxy, new Object[] {14L, 15L});
		
        assertTrue((Boolean) _differentPromise.getMethod("getValue").invoke(returnedPromise));
    }
	
	private ArgumentMatcher<ClientInvocation> isInvocationWith(ClientMessageType callType, 
			String method, Object[] args) {
		return new ArgumentMatcher<ClientInvocation>() {
	
				@Override
				public boolean matches(ClientInvocation clientInvocation) {
					return clientInvocation.getType() == callType &&
							clientInvocation.getMethodName().equals(method) &&
							deepEquals(args, clientInvocation.getArgs());
				}
			};
	}
}
