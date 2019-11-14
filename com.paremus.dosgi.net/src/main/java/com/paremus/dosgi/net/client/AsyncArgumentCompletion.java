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

import static com.paremus.dosgi.net.client.ClientMessageType.ASYNC_ARG_FAILURE;
import static com.paremus.dosgi.net.client.ClientMessageType.ASYNC_ARG_SUCCESS;
import static org.osgi.framework.ServiceException.REMOTE;

import java.io.IOException;

import org.osgi.framework.ServiceException;

import com.paremus.dosgi.net.message.AbstractPayloadMessage;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;

public class AsyncArgumentCompletion extends AbstractPayloadMessage<ClientMessageType> {

	private final int parameterIndex; 
	
	private final Object result;

	private final ClientInvocation invocation;
	
	public AsyncArgumentCompletion(boolean success, ClientInvocation invocation, int i, Object result) {
		super(success ? ASYNC_ARG_SUCCESS : ASYNC_ARG_FAILURE, invocation.getServiceId(), 
				invocation.getCallId(), invocation.getSerializer());

		this.invocation = invocation;

		this.parameterIndex = i;
		this.result = result;
	}

	public int getParameterIndex() {
		return parameterIndex;
	}

	public final Object getResult() {
		return result;
	}

	public ClientInvocation getParentInvocation() {
		return invocation;
	}

	@Override
	public void write(ByteBuf buffer, ChannelPromise promise) throws IOException {
		writeHeader(buffer);
		buffer.writeByte(parameterIndex);
		getSerializer().serializeReturn(buffer, result);
		writeLength(buffer);
		
		promise.addListener(f -> {
			if(!f.isSuccess()) {
				// TODO log this?
				invocation.fail(new ServiceException("Failed to handle an asynchronous method parameter for service " +
					getServiceId() + " due to a communications failure" , REMOTE, f.cause()));
			}
		});
	}
}
