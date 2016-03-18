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

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Before;
import org.mockito.Mockito;

import io.netty.handler.ssl.SslHandler;

public abstract class AbstractSSLClientConnectionManagerTest extends AbstractClientConnectionManagerTest {

	protected KeyManagerFactory keyManagerFactory;
	protected TrustManagerFactory trustManagerFactory;

	@Before
	public final void setUpSSL() throws Exception {
		keyManagerFactory = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream("src/test/resources/fabric.keystore"), "paremus".toCharArray());
		keyManagerFactory.init(ks, "paremus".toCharArray());
		
		trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		
		ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream("src/test/resources/fabric.truststore"), "paremus".toCharArray());
		trustManagerFactory.init(ks);

		SSLContext instance = SSLContext.getInstance("TLSv1.2");
		instance.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
		
		instance.createSSLEngine();
		
		Mockito.when(tls.hasTrust()).thenReturn(true);
		Mockito.when(tls.getTLSClientHandler()).then(i -> {
				SSLEngine engine = instance.createSSLEngine();
				engine.setUseClientMode(true);
				return new SslHandler(engine);
			});
	}
}
