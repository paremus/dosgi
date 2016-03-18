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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLServerConnectionManagerTest extends AbstractSSLServerConnectionManagerTest {

	protected Map<String, Object> getExtraConfig() {
		Map<String, Object> toReturn = new HashMap<String, Object>();
		toReturn.put("allow.insecure.transports", true);
		toReturn.put("server.protocols", "TCP_TLS");
		return toReturn;
	}
	
	protected SSLEngine getConfiguredSSLEngine() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext context = SSLContext.getInstance("TLSv1.2");
		context.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
		
		SSLEngine sslEngine = context.createSSLEngine();
		sslEngine.setUseClientMode(true);
		return sslEngine;
	}
	
}
