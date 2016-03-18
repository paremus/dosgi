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
package com.paremus.dosgi.net.tcp;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

public class VersionCheckingLengthFieldBasedFrameDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
		while (buf.readableBytes() > 4) {
			final int offset = buf.readerIndex();
			final short version = buf.getUnsignedByte(offset);
	        if(version > 2) {
	        	throw new CorruptedFrameException("Unacceptable message version (" + version + ")"); 
	        }
	        final int length = buf.getUnsignedMedium(offset + 1);
	        
	        if(!buf.isReadable(length + 4)) {
	        	break;
	        }
	        
        	out.add(buf.retainedSlice(offset + 4, length));
        	buf.skipBytes(length + 4); 
        }
	}
}
