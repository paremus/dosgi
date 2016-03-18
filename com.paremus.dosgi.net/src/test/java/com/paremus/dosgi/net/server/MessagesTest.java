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

import static com.paremus.dosgi.net.server.ServerMessageType.NO_SERVICE;
import static com.paremus.dosgi.net.server.ServerMessageType.UNKNOWN_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.paremus.dosgi.net.wireformat.Protocol_V1;
import com.paremus.dosgi.net.wireformat.Protocol_V2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;

@RunWith(MockitoJUnitRunner.class)
public class MessagesTest {

	private static final String TEST_MESSAGE = "Test Message";

	private final UUID serviceId = UUID.randomUUID();
	
	private final int callId = 42;
	
	@Mock
	ChannelPromise promise;
	
	@Test
	public void testServerError() throws IOException {
		ServerErrorResponse ser = new ServerErrorResponse(NO_SERVICE, serviceId, callId);
		
		ByteBuf buffer = Unpooled.buffer();
		
		ser.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V1.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V1.FAILURE_NO_SERVICE, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertFalse(buffer.isReadable());
	}

	@Test
	public void testServerErrorWithMessage() throws IOException {
		ServerErrorMessageResponse ser = new ServerErrorMessageResponse(UNKNOWN_ERROR, serviceId, callId,
				TEST_MESSAGE);
		
		ByteBuf buffer = Unpooled.buffer();
		
		ser.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V1.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V1.FAILURE_UNKNOWN, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertEquals(TEST_MESSAGE, buffer.readCharSequence(buffer.readUnsignedShort(), StandardCharsets.UTF_8));
		assertFalse(buffer.isReadable());
	}
	
	@Test
	public void testEndStream() {
		ServerStreamCloseResponse sscr = new ServerStreamCloseResponse(serviceId, callId);
		
		ByteBuf buffer = Unpooled.buffer();
		
		sscr.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V2.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V2.SERVER_CLOSE_EVENT, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertFalse(buffer.isReadable());
	}
}
