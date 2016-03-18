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
import java.util.HashMap;
import java.util.Map;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SecureDefaultNetPerformanceTest extends AbstractSimpleRemoteServicePerformanceTest {

	public SecureDefaultNetPerformanceTest() {
		super("com.paremus.dosgi.net", new HashMap<String, Object>());
	}
	
	@Override
	protected Map<String, Object> getNetConfig() {
		Map<String, Object> config = new HashMap<>();
		
		config.put("signature.keystore", "test-resources/fabric.keystore");
		config.put("signature.keystore.type", "jks");
		config.put(".signature.keystore.password", "paremus");
		config.put("signature.key.alias", "servicefabricsamplecertificate");
		config.put(".signature.key.password", "paremus");
		config.put("signature.cert.alias", "servicefabricsamplecertificate");

		config.put("signature.truststore", "test-resources/fabric.truststore");
		config.put("signature.truststore.type", "jks");
		config.put(".signature.truststore.password", "paremus");
		
		config.put("encryption.key.length", "128");
		config.put("encryption.key.type", "AES");
		
		
		return config;
	}

	@Override
	protected Map<String, Object> getRSAConfig() {
		Map<String, Object> config = new HashMap<>();
		config.putAll(super.getRSAConfig());
		
		config.put("allow.insecure.transports", false);
		return config;
	}
}
