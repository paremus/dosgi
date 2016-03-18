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

import com.paremus.dosgi.net.message.AbstractPayloadMessage;
import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class ServerStreamDataResponse extends AbstractPayloadMessage<ServerMessageType> {

	private final Object data;
	
	public ServerStreamDataResponse(UUID serviceId, int callId, Serializer serializer, Object data) {
		super(ServerMessageType.STREAM_DATA, serviceId, callId, serializer);
		this.data = data;
	}

	public ServerStreamDataResponse fromTemplate(Object data) {
		return new ServerStreamDataResponse(getServiceId(), getCallId(), getSerializer(), data);
	}
	
	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) throws IOException {
		writeHeader(buffer);
		getSerializer().serializeReturn(buffer, data);
		writeLength(buffer);
	}
}
