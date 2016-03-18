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

import static com.paremus.dosgi.net.server.ServerMessageType.FAILURE;
import static com.paremus.dosgi.net.server.ServerMessageType.SUCCESS;

import java.io.IOException;
import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractPayloadMessage;
import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class MethodCompleteResponse extends AbstractPayloadMessage<ServerMessageType> {

	private final Object response;
	
	public MethodCompleteResponse(boolean successful, UUID serviceId, int callId,
			Serializer serializer, Object response) {
		super(successful ? SUCCESS : FAILURE, serviceId, callId, serializer);
		this.response = response;
	}

	public Object getResponse() {
		return response;
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) throws IOException {
		writeHeader(buffer);
		getSerializer().serializeReturn(buffer, response);
		writeLength(buffer);
	}
}
