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
package com.paremus.dosgi.net.server;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.paremus.dosgi.net.config.ProtocolScheme;
import com.paremus.dosgi.net.pushstream.PushStreamFactory.DataStream;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;

class RemotingProviderImpl implements RemotingProvider {

	private final boolean secure;
	
	private final String scheme;
	
	private final ServerRequestHandler handler;
	
	private final Channel channel;

	private final ChannelGroup channelGroup;
	
	private final List<URI> endpoints;
	
	public RemotingProviderImpl(ProtocolScheme p, ServerRequestHandler handler, Channel channel,
			ChannelGroup group) {
		this.secure = p.getProtocol().isSecure();
		this.scheme = p.getProtocol().getUriScheme();
		this.handler = handler;
		this.channel = channel;
		this.channelGroup = group;
		
		Map<String, Integer> addressesToAdvertise = p.getAddressesToAdvertise();
		if(addressesToAdvertise.isEmpty()) {
			endpoints = calculateURIs(channel);
		} else {
			int locaPort = ((InetSocketAddress) channel.localAddress()).getPort();
			endpoints = Collections.unmodifiableList(addressesToAdvertise.entrySet().stream()
					.map(e -> toURI(e.getKey(), e.getValue() == 0 ? locaPort : e.getValue()))
					.collect(Collectors.toList()));
		}
	}

	private List<URI> calculateURIs(Channel channel) {
		InetSocketAddress address = (InetSocketAddress) channel.localAddress();
		
		if(address.getAddress().isAnyLocalAddress()) {
			SortedSet<InetAddress> addresses = new TreeSet<>((a,b) -> {
					int aScore = 0;
					int bScore = 0;
					
					if(a.isLinkLocalAddress()) {
						aScore = 5;
					} else if (a.isSiteLocalAddress()) {
						aScore = 3;
					}
					if(b.isLinkLocalAddress()) {
						bScore = 5;
					} else if (b.isSiteLocalAddress()) {
						bScore = 3;
					}
					
					if(bScore == aScore) {
						byte[] aBytes = a.getAddress();
						byte[] bBytes = b.getAddress();
						if(aBytes.length != bBytes.length) {
							// Prefer longer addresses
							return bBytes.length - aBytes.length;
						}
						for (int i = 0; i < aBytes.length; i++) {
							// Prefer a lower address
							if(aBytes[i] != bBytes[i]) {
								return aBytes[i] - bBytes[i];
							}
						}
						return 0;
					} else {
						// Prefer higher scoring addresses
						return bScore - aScore;
					}
				});
			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				while(interfaces.hasMoreElements()) {
					NetworkInterface iface = interfaces.nextElement();
					if(iface.isLoopback() || !iface.isUp()) {
						// TODO log this?
						continue;
					}
					Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
					while(ifaceAddresses.hasMoreElements()) {
						InetAddress a = ifaceAddresses.nextElement();
						if(!a.isLoopbackAddress()) {
							addresses.add(a);
						}
					}
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Unable to determine the real addresses to advertise for this RemotingProvider", e);
			}
			return unmodifiableList(addresses.stream()
					.map(a -> toURI(a.getHostAddress(), address.getPort()))
					.collect(Collectors.toList()));
		} else {
			return singletonList(toURI(address.getHostString(), address.getPort()));
		}
	}
	
	private URI toURI(String host, int port) {
		try {
			return new URI(scheme,  null,  host, port, null, null, null);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public boolean isSecure() {
		return secure;
	}

	@Override
	public Collection<URI> registerService(UUID id, ServiceInvoker invoker) {
		handler.registerService(id, invoker);
		return endpoints;
	}

	@Override
	public void unregisterService(UUID id) {
		handler.unregisterService(id, channel);
	}

	@Override
	public void registerStream(Channel ch, UUID id, int callId, DataStream stream) {
		handler.registerStream(ch, id, callId, stream);
	}

	public void close() {
		channelGroup.close();
	}
}
