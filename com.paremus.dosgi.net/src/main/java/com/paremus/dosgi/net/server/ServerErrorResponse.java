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
import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractRSAMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class ServerErrorResponse extends AbstractRSAMessage<ServerMessageType> {

	public ServerErrorResponse(ServerMessageType type, UUID serviceId, int callId) {
		super(check(type), serviceId, callId);
	}

	private static ServerMessageType check(ServerMessageType type) {
		if(!type.isError()) {
			throw new IllegalArgumentException("The type is not an error");
		}
		return type;
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) throws IOException {
		writeHeader(buffer);
		writeLength(buffer);
	}
}
