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

import static com.paremus.dosgi.net.client.ClientMessageType.STREAMING_RESPONSE_CLOSE;

import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractRSAMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class EndStreamingInvocation extends AbstractRSAMessage<ClientMessageType> {

	public EndStreamingInvocation(UUID serviceId, int callId) {
		super(STREAMING_RESPONSE_CLOSE, serviceId, callId);
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) {
		writeHeader(buffer);
		writeLength(buffer);
	}
}
