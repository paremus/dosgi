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
package com.paremus.dosgi.net.wireformat;

public class Protocol_V2 {

	public static final byte VERSION = 2;
	
	/** 
	 * The standard message header format is as follows:
	 * byte 0  |    byte 1-3    | byte 4  | long, long |   int   |
	 * Version | message length | Command | Service ID | Call ID |
	 * 
	 */
	
	/** 
	 * Commands - Note that these are backward compatible with V1, 
	 * so no reused command ids
	 */
	
	/** 
	 * Format: | Header | param index unsigned byte | serialized value |
	 *
	 * Usage - sent by the server to indicate an error processing a data event for the named argument
	 */
	public static final byte SERVER_ASYNC_METHOD_PARAM_ERROR = 13;
	
	/** 
	 * Format: | Header | param index unsigned byte | serialized value |
	 *
	 * Usage - sent by the client to propagate a data event for the named argument
	 */
	public static final byte ASYNC_METHOD_PARAM_DATA = 14;
	
	/** 
	 * Format: | Header | param index unsigned byte |
	 *
	 * Usage - sent by the client to propagate a close event for the named argument
	 */
	public static final byte ASYNC_METHOD_PARAM_CLOSE = 15;
	
	/** 
	 * Format: | Header | param index unsigned byte | serialized failure |
	 *
	 * Usage - sent by the client to propagate a failure event for the named argument
	 */
	public static final byte ASYNC_METHOD_PARAM_FAILURE = 16;
	
	/** 
	 * Format: | Header |
	 *
	 * Usage - sent by the client to open a streaming response
	 */
	public static final byte CLIENT_OPEN = 17;

	/** 
	 * Format: | Header |
	 *
	 * Usage - sent by the client to close a streaming response
	 */
	public static final byte CLIENT_CLOSE = 18;
	
	/** 
	 * Format: | Header |
	 *
	 * Usage - sent by the client to indicate back pressure for a streaming response
	 */
	public static final byte CLIENT_BACK_PRESSURE = 19;
	
	/** 
	 * Format: | Header | serialized data |
	 * 
	 * Usage - sent by server to pass a data event to the client
	 */
	public static final byte SERVER_DATA_EVENT = 20;
	
	/** 
	 * Format: | Header |
	 *
	 * Usage - sent by server to indicate that a streaming response should be closed
	 */
	public static final byte SERVER_CLOSE_EVENT = 21;

	/** 
	 * Format: | Header | serialized failure |
	 *
	 * Usage - sent by server to indicate that a streaming response should be failed
	 */
	public static final byte SERVER_ERROR_EVENT = 22;
}
