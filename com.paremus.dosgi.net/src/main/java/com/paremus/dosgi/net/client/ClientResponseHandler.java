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

import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_NO_METHOD;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_NO_SERVICE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_RESPONSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_DESERIALIZE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_SERIALIZE_FAILURE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_SERIALIZE_SUCCESS;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.SUCCESS_RESPONSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_CLOSE_EVENT;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_DATA_EVENT;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_ERROR_EVENT;
import static org.osgi.framework.ServiceException.REMOTE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.osgi.framework.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paremus.dosgi.net.message.AbstractRSAMessage.CacheKey;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.Timeout;
import io.netty.util.Timer;

public class ClientResponseHandler extends ChannelInboundHandlerAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(ClientResponseHandler.class);
	
	private final ConcurrentMap<CacheKey, AbstractClientInvocationWithResult> pendingCalls = new ConcurrentHashMap<>();

	private final ClientConnectionManager ccm;
	private final Timer timer;
	
	public ClientResponseHandler(ClientConnectionManager ccm, Timer timer) {
		this.ccm = ccm;
		this.timer = timer;
	}

	public void registerInvocation(AbstractClientInvocationWithResult invocation) {

		CacheKey key = invocation.getKey();
		pendingCalls.put(key, invocation);

		long timeout = invocation.getTimeout();
		if(timeout > 0) {
			Timeout pendingTimeout = timer.newTimeout(t -> {
				pendingCalls.remove(key);
				invocation.fail(new ServiceException(
								"There was no response from the remote service " + invocation.getServiceId(), +REMOTE,
								new TimeoutException("The invocation timed out with no response.")));
			}, timeout, TimeUnit.MILLISECONDS);

			invocation.addCompletionListener(f -> { 
					if(!pendingTimeout.isExpired()) 
						pendingTimeout.cancel(); 
				});
		}
	}

	public void unregisterInvocation(CacheKey key) {
		pendingCalls.remove(key);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buf = (ByteBuf) msg;
		
		try {
			byte command = buf.readByte();
			
			UUID serviceId = new UUID(buf.readLong(), buf.readLong());
			int callId = buf.readInt();
			
			CacheKey key = new CacheKey(serviceId, callId);
			
			AbstractClientInvocationWithResult ci = command == SERVER_DATA_EVENT ?
					pendingCalls.get(key) : pendingCalls.remove(key);
			
			if(ci == null) {
				return;
			}
			
			try {
				switch(command) {
					case SUCCESS_RESPONSE :
					case SERVER_DATA_EVENT :
						ci.data(buf);
						break;
					case FAILURE_RESPONSE :
					case SERVER_ERROR_EVENT :
						ci.fail(buf);
						break;
					case SERVER_CLOSE_EVENT :
						ci.fail((Throwable) null);
						break;
					case FAILURE_NO_SERVICE :
						ServiceException serviceException = new ServiceException("The service could not be found", REMOTE, 
								new MissingServiceException());
						ci.fail(serviceException);
						ccm.notifyFailedService(ctx.channel(), serviceId, serviceException);
						break;
					case FAILURE_NO_METHOD :
						ServiceException serviceException2 = new ServiceException("The service method could not be found", REMOTE, 
									new MissingMethodException(((ClientInvocation)ci).getMethodName()));
						ci.fail(serviceException2);
						ccm.notifyFailedService(ctx.channel(), serviceId, serviceException2);
						break;
					case FAILURE_TO_DESERIALIZE:
						ci.fail(new ServiceException("The remote invocation failed because the server could not deserialise the method arguments", REMOTE, 
								new IllegalArgumentException(buf.readCharSequence(buf.readUnsignedShort(), StandardCharsets.UTF_8).toString())));
						break;
					case FAILURE_TO_SERIALIZE_SUCCESS:
						ci.fail(new ServiceException("The remote invocation succeeded but the server could not serialise the method return value", REMOTE, 
								new IllegalArgumentException(buf.readCharSequence(buf.readUnsignedShort(), StandardCharsets.UTF_8).toString())));
						break;
					case FAILURE_TO_SERIALIZE_FAILURE:
						ci.fail(new ServiceException("The remote invocation failed and the server could not serialise the failure reason", REMOTE, 
								new IllegalArgumentException(buf.readCharSequence(buf.readUnsignedShort(), StandardCharsets.UTF_8).toString())));
						break;
					default :
						if(ci instanceof ClientInvocation) {
							LOG.error("There was a serious error trying to interpret a remote invocation response for service {} method {}. The response code {} was unrecognised.", 
								new Object[] {serviceId, ((ClientInvocation)ci).getMethodName(), command});
						} else {
							LOG.error("There was a serious error trying to interpret a remote invocation response for a streaming result {}. The response code {} was unrecognised.", 
									new Object[] {serviceId, command});
						}
						ci.fail(new UnknownResponseTypeException(command));
				}
			} catch (Exception e) {
				LOG.error("There was a serious error trying to interpret a remote invocation response for service " 
						+ serviceId, e);
				ci.fail(e);
			}
		} finally {
			buf.release();
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Exception e = new ServiceException("The remote connection was lost", ServiceException.REMOTE,
				new IOException());
		pendingCalls.values().stream()
			.forEach(f -> f.fail(e));
	}
}
