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
package com.paremus.dosgi.net.serialize;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

public interface Serializer {

	void serializeArgs(ByteBuf buffer, Object[] args) throws IOException;

	Object[] deserializeArgs(ByteBuf buffer) throws ClassNotFoundException, IOException;
	
	void serializeReturn(ByteBuf buffer, Object o) throws IOException;
	
	Object deserializeReturn(ByteBuf buffer) throws ClassNotFoundException, IOException;
	
}
