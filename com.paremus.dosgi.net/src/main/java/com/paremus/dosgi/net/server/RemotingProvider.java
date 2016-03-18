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

import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import com.paremus.dosgi.net.pushstream.PushStreamFactory.DataStream;

import io.netty.channel.Channel;

public interface RemotingProvider {

	boolean isSecure();

	Collection<URI> registerService(UUID id, ServiceInvoker invoker);

	void unregisterService(UUID id);
	
	void registerStream(Channel ch, UUID id, int callId, DataStream stream);

}
