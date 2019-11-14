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

import static java.util.stream.Collectors.toList;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paremus.dosgi.net.config.ProtocolScheme;
import com.paremus.dosgi.net.config.TransportConfig;
import com.paremus.dosgi.net.tcp.VersionCheckingLengthFieldBasedFrameDecoder;
import com.paremus.netty.tls.ParemusNettyTLS;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Timer;
import io.netty.util.concurrent.ImmediateEventExecutor;

public class ServerConnectionManager {

	private static final Logger LOG = LoggerFactory.getLogger(ServerConnectionManager.class);
	
	private final EventLoopGroup serverIo;
	
	private final ByteBufAllocator allocator;

	private final ParemusNettyTLS tls;
	private final List<RemotingProviderImpl> configuredTransports;

	public ServerConnectionManager(TransportConfig config, ParemusNettyTLS tls, ByteBufAllocator allocator, 
			EventLoopGroup serverIo, Timer timer) {
		this.tls = tls;
		this.allocator = allocator;
		this.serverIo = serverIo;
				
		InetSocketAddress defaultBindAddress = new InetSocketAddress(config.server_bind_address(), 0);
		
		String[] protocols = config.server_protocols();
		configuredTransports = Arrays.stream(protocols)
			.map(ProtocolScheme::new)
			.filter(p -> {
				if(config.allow_insecure_transports() || p.getProtocol().isSecure()) {
					return true;
				}
				LOG.warn("The server transport {} is not permitted because it is insecure and insecure transports are not enabled.",
						p.getProtocol());
				return false;
			})
			.map(p -> createProviderFor(p, defaultBindAddress))
			.filter(rp -> rp != null)
			.collect(toList());
		
		if(configuredTransports.isEmpty() && protocols.length > 0) {
			LOG.error("There are no server transports available for this provider. Please check the configuration");
			throw new IllegalArgumentException("The transport configuration created no valid client transports");
		}
	}

	private RemotingProviderImpl createProviderFor(ProtocolScheme p, InetSocketAddress defaultBindAddress) {
		
		ServerBootstrap b = new ServerBootstrap();
		
		b.group(serverIo)
			.option(ChannelOption.ALLOCATOR, allocator)
			.option(ChannelOption.SO_SNDBUF, p.getSendBufferSize())
			.option(ChannelOption.SO_RCVBUF, p.getReceiveBufferSize());
			
		Consumer<Channel> c = ch -> {};
		boolean clientAuth = false;
		switch(p.getProtocol()) {
			case TCP_CLIENT_AUTH :
				clientAuth = true;
			case TCP_TLS :
				boolean useClientAuth = clientAuth;
				
				if(!tls.hasCertificate()) {
					LOG.error("The secure transport {} cannot be configured as the necessary certificate configuration is unavailable. Please check the configuration of the TLS provider.",
							p.getProtocol());
					return null;
				}
				c = c.andThen(ch -> {
					
					SslHandler serverHandler = tls.getTLSServerHandler();
					
					SSLEngine engine = serverHandler.engine();
					
					String ciphers = p.getOption("ciphers", String.class);
					if(ciphers != null) {
						engine.setEnabledCipherSuites(ciphers.split(","));
					}
					
					String protocols = p.getOption("protocols", String.class);
					if(protocols != null) {
						engine.setEnabledProtocols(protocols.split(","));
					}
					
					engine.setWantClientAuth(useClientAuth);
					engine.setNeedClientAuth(useClientAuth);

					ch.pipeline().addLast(serverHandler);
				});
			case TCP :
				b.channel(NioServerSocketChannel.class)
					.option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.TCP_NODELAY, p.getOption("nodelay", Boolean.class));
				
				c = c.andThen(ch -> {
			        	//Incoming
			        	ch.pipeline().addLast(new VersionCheckingLengthFieldBasedFrameDecoder());
					});
				break;
			default : 
				throw new IllegalArgumentException("No support for protocol " + p.getProtocol());
		}
		
		ServerRequestHandler srh = new ServerRequestHandler(p);
		ServerResponseSerializer srs = new ServerResponseSerializer();
		ChannelGroup group = new DefaultChannelGroup(serverIo.next());
		
		Consumer<Channel> fullPipeline = c.andThen(ch -> ch.pipeline()
				.addLast(srh)
				.addLast(ImmediateEventExecutor.INSTANCE, srs));
		b.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				fullPipeline.accept(ch);
				group.add(ch);
			}
		});
		
		try {
			Channel server = b.bind(p.getBindAddress() == null ? defaultBindAddress : p.getBindAddress())
					.sync().channel();
			group.add(server);
			return new RemotingProviderImpl(p, srh, server, group);
		} catch (InterruptedException ie) {
			LOG.warn("Interruped while configuring the transport {} with configuration {}", 
					p.getProtocol(), p.getConfigurationString());
			throw new RuntimeException(ie);
		}
	}

	public List<? extends RemotingProvider> getConfiguredProviders() {
		return configuredTransports;
	}

	public void close() {
		configuredTransports.stream()
			.forEach(RemotingProviderImpl::close);
	}
}
