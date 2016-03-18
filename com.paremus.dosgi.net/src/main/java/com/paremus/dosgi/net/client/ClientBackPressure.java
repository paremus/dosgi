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

import static com.paremus.dosgi.net.client.ClientMessageType.STREAMING_RESPONSE_BACK_PRESSURE;

import java.util.UUID;

import com.paremus.dosgi.net.message.AbstractRSAMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class ClientBackPressure extends AbstractRSAMessage<ClientMessageType> {

	private final long backPressure; 
	
	public ClientBackPressure(UUID serviceId, int callId, long backPressure) {
		super(STREAMING_RESPONSE_BACK_PRESSURE, serviceId, callId);
		this.backPressure = backPressure;
	}

	public long getBackPressure() {
		return backPressure;
	}

	public ClientBackPressure fromTemplate(long bp) {
		return new ClientBackPressure(getServiceId(), getCallId(), bp);
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) {
		writeHeader(buffer);
		buffer.writeLong(backPressure);
		writeLength(buffer);
	}
}
