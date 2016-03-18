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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class ServerConnectionManagerTest extends AbstractServerConnectionManagerTest {

	protected Map<String, Object> getExtraConfig() {
		Map<String, Object> toReturn = new HashMap<String, Object>();
		toReturn.put("allow.insecure.transports", true);
		toReturn.put("server.protocols", "TCP");
		return toReturn;
	}
	
	protected ByteChannel getCommsChannel(URI uri) {
		
		try {
			SocketChannel sc = SocketChannel.open();
			sc.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
			sc.configureBlocking(false);
			return sc;
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}
}
