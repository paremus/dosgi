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

import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_NO_METHOD;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_NO_SERVICE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_RESPONSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_SERVER_OVERLOADED;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_DESERIALIZE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_SERIALIZE_FAILURE;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_TO_SERIALIZE_SUCCESS;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.FAILURE_UNKNOWN;
import static com.paremus.dosgi.net.wireformat.Protocol_V1.SUCCESS_RESPONSE;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_ASYNC_METHOD_PARAM_ERROR;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_CLOSE_EVENT;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_DATA_EVENT;
import static com.paremus.dosgi.net.wireformat.Protocol_V2.SERVER_ERROR_EVENT;

import com.paremus.dosgi.net.message.MessageType;
import com.paremus.dosgi.net.wireformat.Protocol_V1;
import com.paremus.dosgi.net.wireformat.Protocol_V2;

public enum ServerMessageType implements MessageType {
	
	SUCCESS(Protocol_V1.VERSION, SUCCESS_RESPONSE, false), 
	FAILURE(Protocol_V1.VERSION, FAILURE_RESPONSE, true), 
	NO_SERVICE(Protocol_V1.VERSION, FAILURE_NO_SERVICE, true), 
	NO_METHOD(Protocol_V1.VERSION, FAILURE_NO_METHOD, true), 
	SERVER_OVERLOADED(Protocol_V1.VERSION, FAILURE_SERVER_OVERLOADED, true), 
	ARGS_SERIALIZATION_ERROR(Protocol_V1.VERSION, FAILURE_TO_DESERIALIZE, true),
	RETURN_SERIALIZATION_ERROR(Protocol_V1.VERSION, FAILURE_TO_SERIALIZE_SUCCESS, true),
	FAILURE_SERIALIZATION_ERROR(Protocol_V1.VERSION, FAILURE_TO_SERIALIZE_FAILURE, true),
	UNKNOWN_ERROR(Protocol_V1.VERSION, FAILURE_UNKNOWN, true),
	ASYNC_PARAM_ERROR(Protocol_V2.VERSION, SERVER_ASYNC_METHOD_PARAM_ERROR, true), 
	STREAM_DATA(Protocol_V2.VERSION, SERVER_DATA_EVENT, false),
	STREAM_CLOSE(Protocol_V2.VERSION, SERVER_CLOSE_EVENT, false),
	STREAM_ERROR(Protocol_V2.VERSION, SERVER_ERROR_EVENT, true);
	
	private final byte version;
	private final byte command;
	private final boolean isError;

	private ServerMessageType(byte version, byte command, boolean isError) {
		this.version = version;
		this.command = command;
		this.isError = isError;
	}

	public byte getVersion() {
		return version;
	}
	
	public byte getCommand() {
		return command;
	}

	public boolean isError() {
		return isError;
	}
}
