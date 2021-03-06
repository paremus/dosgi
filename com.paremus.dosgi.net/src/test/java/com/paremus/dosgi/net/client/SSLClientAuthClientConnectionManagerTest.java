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

package com.paremus.dosgi.net.client;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLServerSocket;

import org.junit.Before;
import org.mockito.Mockito;

import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public class SSLClientAuthClientConnectionManagerTest extends AbstractSSLClientConnectionManagerTest {
	
	@Before
	public final void setUpClientAuth() throws Exception {
		Mockito.when(tls.hasCertificate()).thenReturn(true);
	}

	protected Map<String, Object> getConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("client.protocols", "TCP_CLIENT_AUTH");
        config.put("allow.insecure.transports", false);
		return config;
	}

	protected String getPrefix() {
		return "ptcpca://127.0.0.1:";
	}
	
	protected ServerSocket getConfiguredSocket() throws Exception {
		SslContext sslContext;
		try {
			sslContext = SslContextBuilder.forServer(keyManagerFactory)
				.trustManager(trustManagerFactory)
				.build();
		} catch (Exception e) {
			throw new RuntimeException("Unable to create the SSL Engine", e);
		}
		ServerSocket socket = ((JdkSslContext)sslContext).context().getServerSocketFactory()
				.createServerSocket(0, 1, InetAddress.getLoopbackAddress());
				
		((SSLServerSocket)socket).setNeedClientAuth(true);
		((SSLServerSocket)socket).setWantClientAuth(true);
		
		return socket;
	}
}
