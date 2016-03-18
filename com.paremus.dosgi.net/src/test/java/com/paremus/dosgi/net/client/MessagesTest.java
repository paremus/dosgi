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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

	private final UUID serviceId = UUID.randomUUID();
	
	private final int callId = 42;
	
	@Mock
	ChannelPromise promise;
	
	@Test
	public void testBackPressure() {
		ClientBackPressure end = new ClientBackPressure(serviceId, callId, 1234L);
		
		ByteBuf buffer = Unpooled.buffer();
		
		end.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V2.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V2.CLIENT_BACK_PRESSURE, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertEquals(1234L, buffer.readLong());
		assertFalse(buffer.isReadable());
	}
	
	@Test
	public void testEndStreamingInvocation() {
		EndStreamingInvocation end = new EndStreamingInvocation(serviceId, callId);
		
		ByteBuf buffer = Unpooled.buffer();
		
		end.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V2.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V2.CLIENT_CLOSE, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertFalse(buffer.isReadable());
	}
	
	@Test
	public void testInvocationCancellation() {
		InvocationCancellation cancellation = new InvocationCancellation(serviceId, callId, false);
		
		ByteBuf buffer = Unpooled.buffer();
		
		cancellation.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(promise);
		
		assertEquals(Protocol_V1.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V1.CANCEL, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertFalse(buffer.readBoolean());
		assertFalse(buffer.isReadable());
	}
	
}
