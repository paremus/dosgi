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

import static com.paremus.dosgi.net.client.ClientMessageType.CacheAction.ADD;
import static com.paremus.dosgi.net.client.ClientMessageType.CacheAction.REMOVE;
import static com.paremus.dosgi.net.client.ClientMessageType.CacheAction.SKIP;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.CALL_WITHOUT_RETURN;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.CALL_WITH_RETURN;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.CANCEL;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.ASYNC_METHOD_PARAM_FAILURE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.CLIENT_BACK_PRESSURE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.CLIENT_CLOSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.CLIENT_OPEN;

import com.paremus.dosgi.net.message.MessageType;

import static com.paremus.dosgi.net.wireformat.Protocol_V2.ASYNC_METHOD_PARAM_CLOSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.ASYNC_METHOD_PARAM_DATA;

import com.paremus.dosgi.net.wireformat.Protocol_V1;
import com.paremus.dosgi.net.wireformat.Protocol_V2;

public enum ClientMessageType implements MessageType {
	
		WITH_RETURN(Protocol_V1.VERSION, CALL_WITH_RETURN, ADD), 
		FIRE_AND_FORGET(Protocol_V1.VERSION, CALL_WITHOUT_RETURN, SKIP), 
		CANCELLATION(Protocol_V1.VERSION, CANCEL, REMOVE), 
		ASYNC_ARG_SUCCESS(Protocol_V2.VERSION, ASYNC_METHOD_PARAM_DATA, SKIP),
		ASYNC_ARG_FAILURE(Protocol_V2.VERSION, ASYNC_METHOD_PARAM_FAILURE, SKIP),
		ASYNC_ARG_CLOSE(Protocol_V2.VERSION, ASYNC_METHOD_PARAM_CLOSE, SKIP),
		STREAMING_RESPONSE_OPEN(Protocol_V2.VERSION, CLIENT_OPEN, ADD),
		STREAMING_RESPONSE_CLOSE(Protocol_V2.VERSION, CLIENT_CLOSE, REMOVE),
		STREAMING_RESPONSE_BACK_PRESSURE(Protocol_V2.VERSION, CLIENT_BACK_PRESSURE, SKIP);
	
	public enum CacheAction {ADD, REMOVE, SKIP};
	
		private final byte version;
		private final byte command;
		private final CacheAction action;

		private ClientMessageType(byte version, byte command, CacheAction action) {
			this.version = version;
			this.command = command;
			this.action = action;
		}

		public byte getVersion() {
			return version;
		}
		
		public byte getCommand() {
			return command;
		}

		public CacheAction getAction() {
			return action;
		}
	}
