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

public class ClientConnectionManagerTest extends AbstractClientConnectionManagerTest {

	protected Map<String, Object> getConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("client.protocols", "TCP");
        config.put("allow.insecure.transports", true);
		return config;
	}

	protected String getPrefix() {
		return "ptcp://127.0.0.1:";
	}
	
	protected ServerSocket getConfiguredSocket() throws Exception {
		return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
	}
}
