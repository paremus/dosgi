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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.paremus.dosgi.net.serialize.Serializer;
import com.paremus.dosgi.net.wireformat.Protocol_V1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

@RunWith(MockitoJUnitRunner.class)
public class ClientResponseHandlerTest {

	@Mock
	Timer timer;
	@Mock
	Timeout timeout;
	@Mock
	Channel channel;
	@Mock
	ChannelHandlerContext ctx;
	@Mock
	Serializer serializer;
	@Mock
	ClientConnectionManager ccm;
	
	ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

	UUID serviceId = UUID.randomUUID();
	
	ClientResponseHandler impl;
	
	EventExecutor executor;
	
	Supplier<Promise<Object>> nettyPromiseSupplier;
	
	@Before
	public void setUp() {
		Mockito.when(timer.newTimeout(any(), Mockito.anyLong(), any())).thenReturn(timeout);
		
		executor = new DefaultEventExecutor();
		
		nettyPromiseSupplier = () -> executor.next().newPromise();
		
		impl = new ClientResponseHandler(ccm, timer);
	}
	
	@After
	public void tearDown() throws Exception {
		executor.shutdownGracefully(100, 500, TimeUnit.MILLISECONDS).await(1, TimeUnit.SECONDS);
	}
	
	@Test
	public void testSuccessResponseCleansUp() throws Exception {
		doTestResponseCleansUp(Protocol_V1.SUCCESS_RESPONSE, allocator.buffer(0));
	}

	@Test
	public void testFailureResponseCleansUp() throws Exception {
		ByteBuf buffer = allocator.buffer(0);
		Mockito.when(serializer.deserializeReturn(buffer)).thenReturn(new UnsupportedAudioFileException());
		doTestResponseCleansUp(Protocol_V1.SUCCESS_RESPONSE, buffer);
	}

	@Test
	public void testFailureNoService() throws Exception {
		doTestResponseCleansUp(Protocol_V1.FAILURE_NO_SERVICE, allocator.buffer(0));
	}
	
	@Test
	public void testFailureNoMethod() throws Exception {
		doTestResponseCleansUp(Protocol_V1.FAILURE_NO_METHOD, allocator.buffer(0));
	}
	
	@Test
	public void testFailureToDeserializeArgsCleansUp() throws Exception {
		doTestResponseCleansUp(Protocol_V1.FAILURE_TO_DESERIALIZE, allocator.buffer(0));
	}
	
	@Test
	public void testFailureToSerializeResponseCleansUp() throws Exception {
		doTestResponseCleansUp(Protocol_V1.FAILURE_TO_SERIALIZE_SUCCESS, allocator.buffer(0));
	}
	
	@Test
	public void testFailureToSerializeErrorCleansUp() throws Exception {
		doTestResponseCleansUp(Protocol_V1.FAILURE_TO_SERIALIZE_FAILURE, allocator.buffer(0));
	}
	
	public void doTestResponseCleansUp(byte response, ByteBuf buf) throws Exception {
		
		ClientInvocation ci = new ClientInvocation(true, serviceId, -1, 42, new Object[0], 
				new int[0], new int[0], serializer, null, nettyPromiseSupplier.get(), new AtomicLong(3000), "test");
		
		impl.registerInvocation(ci);
		
		buf.writeByte(Protocol_V1.SUCCESS_RESPONSE);
		buf.writeLong(serviceId.getMostSignificantBits());
		buf.writeLong(serviceId.getLeastSignificantBits());
		buf.writeInt(42);
		
		buf.markReaderIndex();
		int refCnt = buf.refCnt();
		buf.retain();
		
		impl.channelRead(ctx, buf);
		assertEquals(refCnt - 1, buf.refCnt());
		
		assertTrue(ci.getResult().isSuccess());
		Mockito.verify(timeout, timeout(100)).cancel();
		
		buf.resetReaderIndex();
		impl.channelRead(ctx, buf);
		assertEquals(refCnt - 2, buf.refCnt());
		Mockito.verify(timeout, after(100)).cancel();
	}

	private void assertEquals(int i, int refCnt) {
		// TODO Auto-generated method stub
		
	}

	@Test
	public void testTimeoutCleansUp() throws Exception {
		ClientInvocation ci = new ClientInvocation(true, serviceId, -1, 42, new Object[0], 
				new int[0], new int[0], serializer, null, nettyPromiseSupplier.get(), new AtomicLong(3000), "test");
		
		impl.registerInvocation(ci);

		ArgumentCaptor<TimerTask> taskCaptor = ArgumentCaptor.forClass(TimerTask.class);
		Mockito.verify(timer).newTimeout(taskCaptor.capture(), Mockito.anyLong(), any());
		
		Mockito.when(timeout.isExpired()).thenReturn(Boolean.TRUE);
		taskCaptor.getValue().run(timeout);
		assertTrue(ci.getResult().isDone());

		ByteBuf buf = allocator.heapBuffer();
		buf.writeByte(Protocol_V1.SUCCESS_RESPONSE);
		buf.writeLong(serviceId.getMostSignificantBits());
		buf.writeLong(serviceId.getLeastSignificantBits());
		buf.writeInt(42);
		
		impl.channelRead(ctx, buf);
		
		Mockito.verify(timeout, Mockito.never()).cancel();
	}

	@Test
	public void testChannelCloseCleansUp() throws Exception {
		ClientInvocation ci = new ClientInvocation(true, serviceId, -1, 42, new Object[0], 
				new int[0], new int[0], serializer, null, nettyPromiseSupplier.get(), new AtomicLong(3000), "test");
		
		impl.registerInvocation(ci);
		
		impl.channelInactive(ctx);
		
		assertTrue(ci.getResult().isDone());
		Mockito.verify(timeout, timeout(100)).cancel();
		assertNotNull(ci.getResult().cause());
	}
}
