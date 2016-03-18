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
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractRSAMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class ServerErrorMessageResponse extends AbstractRSAMessage<ServerMessageType> {

	private final String message;
	
	public ServerErrorMessageResponse(ServerMessageType type, UUID serviceId, int callId,
			String message) {
		super(check(type), serviceId, callId);
		this.message = message == null ? "" : message;
	}

	private static ServerMessageType check(ServerMessageType type) {
		if(!type.isError()) {
			throw new IllegalArgumentException("The type is not an error");
		}
		return type;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) throws IOException {
		writeHeader(buffer);
		
		// Create space for the length prefix
		int messageLengthStart = buffer.writerIndex();
		buffer.writerIndex(messageLengthStart + 2);
		
		// Write the string then set the length
		int length = buffer.writeCharSequence(message, StandardCharsets.UTF_8);
		buffer.setShort(messageLengthStart, length);

		// Write the overall length of the message
		writeLength(buffer);
	}
}
