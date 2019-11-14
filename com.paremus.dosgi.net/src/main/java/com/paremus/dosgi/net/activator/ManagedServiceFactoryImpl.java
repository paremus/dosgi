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
package com.paremus.dosgi.net.activator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.util.converter.Converters;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paremus.dosgi.net.config.TransportConfig;
import com.paremus.dosgi.net.impl.RemoteServiceAdminFactoryImpl;
import com.paremus.dosgi.scoping.rsa.MultiFrameworkRemoteServiceAdmin;
import com.paremus.netty.tls.ParemusNettyTLS;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import io.netty.util.concurrent.EventExecutorGroup;

public class ManagedServiceFactoryImpl implements ManagedServiceFactory {

	private static final Logger logger = LoggerFactory.getLogger(ManagedServiceFactoryImpl.class);
	
	private final BundleContext context;
	
	private final Timer timer;
	
	private final EventLoopGroup serverIo;
	
	private final EventLoopGroup clientIo;
	
	private final EventExecutorGroup serverWorkers;
	
	private final EventExecutorGroup clientWorkers;
	
	private final ByteBufAllocator allocator;
	
	private final ConcurrentHashMap<String, ServiceTracker<ParemusNettyTLS, ParemusNettyTLS>> trackers = new ConcurrentHashMap<>();
	
	private final ConcurrentHashMap<String, RemoteServiceAdminFactoryImpl> rsas = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<RemoteServiceAdminFactoryImpl, ServiceRegistration<?>> registrations = new ConcurrentHashMap<>();
	
	private final ConcurrentHashMap<ServiceReference<ParemusNettyTLS>, List<RemoteServiceAdminFactoryImpl>> usedBy = new ConcurrentHashMap<>();

	private volatile boolean open = true;
	
	public ManagedServiceFactoryImpl(BundleContext context, Timer timer, EventLoopGroup serverIo, EventLoopGroup clientIo,
			EventExecutorGroup serverWorkers, EventExecutorGroup clientWorkers, ByteBufAllocator allocator) {
		this.context = context;
		this.timer = timer;
		this.serverIo = serverIo;
		this.clientIo = clientIo;
		this.serverWorkers = serverWorkers;
		this.clientWorkers = clientWorkers;
		this.allocator = allocator;
	}

	@Override
	public String getName() {
		return "Paremus RSA additional Transports provider";
	}

	@Override
	public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
		
		deleted(pid);
		
		if(!open) {
			return;
		}
		
		TransportConfig config;
		try {
			config = Converters.standardConverter().convert(toMap(properties)).to(TransportConfig.class);
		} catch (Exception e) {
			logger.error("Unable to process the configuration for pid {}", pid, e);
			throw new ConfigurationException(null, e.getMessage());
		}
		
		if(config.server_protocols().length == 0 && config.client_protocols().length == 0) {
			logger.info("The pid {} defines no RSA transports, so no RSA will be created", pid);
			return;
		}
		
		String filter = config.encoding_scheme_target();
		
		Predicate<ServiceReference<ParemusNettyTLS>> selector;
		if(filter.isEmpty()) {
			selector = r -> true;
		} else {
			Filter f;
			try {
				f = context.createFilter(filter);
			} catch (InvalidSyntaxException e) {
				logger.error("Unable to process the encoding scheme target filter for pid {}", pid, e);
				deleted(pid);
				throw new ConfigurationException("encoding.scheme.target", e.getMessage());
			}
			selector = r -> f.match(r);
		}
		
		ServiceTracker<ParemusNettyTLS, ParemusNettyTLS> tracker = new ServiceTracker<ParemusNettyTLS, ParemusNettyTLS>(context, ParemusNettyTLS.class, null) {
				@Override
				public ParemusNettyTLS addingService(ServiceReference<ParemusNettyTLS> reference) {
					ParemusNettyTLS esf = super.addingService(reference);
					
					if(esf != null && selector.test(reference) && !rsas.containsKey(pid)) {
						setup(reference, esf, config);
					}
					
					return esf;
				}

				private boolean setup(ServiceReference<ParemusNettyTLS> reference, ParemusNettyTLS esf, TransportConfig cfg) {
					RemoteServiceAdminFactoryImpl newRSA;
					try {
						newRSA = new RemoteServiceAdminFactoryImpl(config, esf, allocator, serverIo, 
								clientIo, serverWorkers, clientWorkers, timer);
					} catch (IllegalArgumentException iae) {
						logger.error("The RSA could not be created with encoding scheme {}", reference, iae);
						return false;
					}
					
					rsas.put(pid, newRSA);
					usedBy.compute(reference, (k,v) -> v == null ? singletonList(newRSA) :
								concat(v.stream(), of(newRSA)).collect(toList()));
					
					Hashtable<String, Object> props = new Hashtable<>();
					
					Enumeration<String> keys = properties.keys();
					
					while(keys.hasMoreElements()) {
						String key = keys.nextElement();
						if(!key.startsWith(".")) {
							props.put(key, properties.get(key));
						}
					}
					
					props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, newRSA.getSupportedIntents());
			        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, Collections.singletonList("com.paremus.dosgi.net"));
			        
					ServiceRegistration<?> newRSAReg = context.registerService(new String[] {
							RemoteServiceAdmin.class.getName(), MultiFrameworkRemoteServiceAdmin.class.getName()}, 
							newRSA, props);
					
					registrations.put(newRSA, newRSAReg);
					return true;
				}
				
				@Override
				public void modifiedService(ServiceReference<ParemusNettyTLS> reference,
						ParemusNettyTLS service) {
					if(!selector.test(reference)) {
						removeAndAdd(reference);
					}
				}
				
				private void removeAndAdd(ServiceReference<ParemusNettyTLS> reference) {
					RemoteServiceAdminFactoryImpl rsa = rsas.get(pid);
					if(rsa != null) {
						if(usedBy.getOrDefault(reference, emptyList()).contains(rsa)) {
						
							usedBy.compute(reference, (k,v) -> {
									List<RemoteServiceAdminFactoryImpl> l = v == null ? emptyList() :
										new ArrayList<>(v);
									l.remove(rsa);
									return l.isEmpty() ? null : l;
								});
							rsas.remove(pid, rsa);
							
							ServiceRegistration<?> reg = registrations.remove(rsa);
							
							if(reg != null) {
								try {
									reg.unregister();
								} catch (IllegalStateException ise) {
									// No matter
								}
							}
							
							rsa.close();
						} else {
							return;
						}
					}
					getTracked().entrySet().stream()
						.filter(e -> setup(e.getKey(), e.getValue(), config))
						.findFirst();
				}

				@Override
				public void removedService(ServiceReference<ParemusNettyTLS> reference,
						ParemusNettyTLS service) {
					removeAndAdd(reference);
					super.removedService(reference, service);
				}
			};
		trackers.put(pid, tracker);
		
		tracker.open();
		
		if(!open) {
			deleted(pid);
		}
	}

	static Map<String,Object> toMap(Dictionary<String, ?> properties) {
		Map<String,Object> map = new HashMap<>();
		Enumeration<String> keys = properties.keys();
		while(keys.hasMoreElements()) {
			String key = keys.nextElement();
			map.put(key, properties.get(key));
		}
		return map;
	}
	
	public void destroy() {
		open = false;
		trackers.keySet().stream().forEach(this::deleted);
	}

	@Override
	public void deleted(String pid) {
		ofNullable(trackers.remove(pid)).ifPresent(ServiceTracker::close);
	}
}
