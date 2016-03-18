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
import java.util.HashMap;
import java.util.Map;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SecureRemoteServiceTest extends RemoteServiceTest {
	
	@Override
	protected Map<String, Object> getNetConfig() {
		
		Map<String, Object> config = new HashMap<>();

		String testResources = context.getProperty("test.resources");
		
		config.put("keystore.location", testResources + "/fabric.keystore");
		config.put("keystore.type", "jks");
		config.put(".keystore.password", "paremus");

		config.put("truststore.location", testResources + "fabric.truststore");
		config.put("truststore.type", "jks");
		config.put(".truststore.password", "paremus");
		
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
