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
package com.paremus.dosgi.net.serialize.freshvanilla;

import java.io.IOException;

import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.lang.misc.AccessUtils;
import org.freshvanilla.net.BinaryWireFormat;
import org.freshvanilla.net.VanillaPojoSerializer;
import org.freshvanilla.net.VersionAwareVanillaPojoSerializer;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;

public class VanillaRMISerializer implements Serializer {

	private final MetaClasses metaClasses;
	
	private final FastThreadLocal<BinaryWireFormat> wireFormats = 
			new FastThreadLocal<BinaryWireFormat>(){
				protected BinaryWireFormat initialValue() {
					return new BinaryWireFormat(metaClasses, AccessUtils.isSafe() ?
							new VersionAwareVanillaPojoSerializer(metaClasses) :
							new VanillaPojoSerializer(metaClasses));
				}
			};

	public VanillaRMISerializer(MetaClasses metaClasses) {
		this.metaClasses = metaClasses;
	}

	@Override
	public void serializeArgs(ByteBuf buffer, Object[] o) throws IOException {
		BinaryWireFormat bwf = wireFormats.get();
		try {
			bwf.writeNum(buffer, o.length);
			//Optimise for up to 8 args
			switch(o.length) {
				case 0 :
					break;
				case 1 :
					bwf.writeObject(buffer, o[0]);
					break;
				case 2 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					break;
				case 3 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					break;
				case 4 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					bwf.writeObject(buffer, o[3]);
					break;
				case 5 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					bwf.writeObject(buffer, o[3]);
					bwf.writeObject(buffer, o[4]);
					break;
				case 6 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					bwf.writeObject(buffer, o[3]);
					bwf.writeObject(buffer, o[4]);
					bwf.writeObject(buffer, o[5]);
					break;
				case 7 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					bwf.writeObject(buffer, o[3]);
					bwf.writeObject(buffer, o[4]);
					bwf.writeObject(buffer, o[5]);
					bwf.writeObject(buffer, o[6]);
					break;
				case 8 :
					bwf.writeObject(buffer, o[0]);
					bwf.writeObject(buffer, o[1]);
					bwf.writeObject(buffer, o[2]);
					bwf.writeObject(buffer, o[3]);
					bwf.writeObject(buffer, o[4]);
					bwf.writeObject(buffer, o[5]);
					bwf.writeObject(buffer, o[6]);
					bwf.writeObject(buffer, o[7]);
					break;
				default :
					for(int i = 0; i < o.length; i ++) {
						bwf.writeObject(buffer,o[i]);
					}
			}
		} finally {
			bwf.reset();
		}
	}
	
	private static final Object[] EMPTY_ARGS = new Object[0];
	
	@Override
	public Object[] deserializeArgs(ByteBuf buffer) throws ClassNotFoundException, IOException {
		BinaryWireFormat bwf = wireFormats.get();
		try {
			int size = (int) bwf.readNum(buffer);
			switch(size) {
				case 0:
					return EMPTY_ARGS;
				case 1:
					return new Object[] {bwf.readObject(buffer)};
				case 2:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer)};
				case 3:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer), 
							bwf.readObject(buffer)};
				case 4:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer)};
				case 5:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer), bwf.readObject(buffer)};
				case 6:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer)};
				case 7:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer)};
				case 8:
					return new Object[] {bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer), bwf.readObject(buffer),
							bwf.readObject(buffer), bwf.readObject(buffer), bwf.readObject(buffer)};
				default:
					Object[] o = new Object[size];
					for(int i = 0; i < o.length; i++) {
						o[i] = bwf.readObject(buffer);
					}
					return o;
					
			}
		} finally {
			bwf.reset();
		}
	}

	@Override
	public void serializeReturn(ByteBuf buffer, Object o) throws IOException {
		BinaryWireFormat bwf = wireFormats.get();
		try {
			bwf.writeObject(buffer, o);
		} finally {
			bwf.reset();
		}
	}

	@Override
	public Object deserializeReturn(ByteBuf buffer) throws ClassNotFoundException, IOException {
		BinaryWireFormat bwf = wireFormats.get();
		try {
			return bwf.readObject(buffer);
		} finally {
			bwf.reset();
		}
	}

}
