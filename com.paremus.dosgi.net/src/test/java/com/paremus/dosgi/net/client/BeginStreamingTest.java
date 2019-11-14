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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.freshvanilla.lang.MetaClasses;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.paremus.dosgi.net.serialize.Serializer;
import com.paremus.dosgi.net.serialize.freshvanilla.VanillaRMISerializer;
import com.paremus.dosgi.net.wireformat.Protocol_V2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

@RunWith(MockitoJUnitRunner.class)
public class BeginStreamingTest {

	private static final Exception MARKER_EXCEPTION = new Exception("marker");
	
	private final UUID serviceId = UUID.randomUUID();
	
	private final int callId = 42;
	
	@Mock
	Channel channel;
	
	ChannelPromise promise;
	ChannelPromise promise2;
	
	Promise<Void> closePromise = ImmediateEventExecutor.INSTANCE.newPromise();
	
	List<Object> data = new CopyOnWriteArrayList<>();
	
	AtomicReference<Exception> failure = new AtomicReference<>(MARKER_EXCEPTION);
	
	Serializer serializer;
	
	@Before
	public void setUp() {
		promise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
		promise2 = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
		
		serializer = new VanillaRMISerializer(new MetaClasses(getClass().getClassLoader()));
	}
	
	@Test
	public void testOpenStream() {
		BeginStreamingInvocation bsi = new BeginStreamingInvocation(serviceId, callId, 
				serializer, ImmediateEventExecutor.INSTANCE, data::add, failure::set, closePromise);
		
		ByteBuf buffer = Unpooled.buffer();
		
		bsi.write(buffer, promise);
		
		Mockito.verifyZeroInteractions(channel);
		
		assertEquals(Protocol_V2.VERSION, buffer.readByte());
		int length = buffer.readUnsignedMedium();
		assertEquals(buffer.readableBytes(), length);
		assertEquals(Protocol_V2.CLIENT_OPEN, buffer.readByte());
		assertEquals(serviceId.getMostSignificantBits(), buffer.readLong());
		assertEquals(serviceId.getLeastSignificantBits(), buffer.readLong());
		assertEquals(callId, buffer.readInt());
		assertFalse(buffer.isReadable());
	}
}
