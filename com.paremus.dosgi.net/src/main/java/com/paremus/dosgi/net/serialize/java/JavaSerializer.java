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
package com.paremus.dosgi.net.serialize.java;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import org.osgi.framework.Bundle;

import com.paremus.dosgi.net.serialize.Serializer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

public class JavaSerializer implements Serializer {

	private final Bundle classSpace;
	
	public JavaSerializer(Bundle classSpace) {
		this.classSpace = classSpace;
	}

	@Override
	public void serializeArgs(ByteBuf buffer, Object[] o) throws IOException {
		serializeReturn(buffer, o);
	}
	
	@Override
	public void serializeReturn(ByteBuf buffer, Object o) throws IOException {
		serialzeWithJava(new ByteBufOutputStream(buffer), o);
	}

	public static void serialzeWithJava(ByteBufOutputStream bbos, Object o) throws IOException {
		try (ObjectOutputStream oos = new ObjectOutputStream(bbos)) {
			oos.writeObject(o);
		}
	}

	@Override
	public Object[] deserializeArgs(ByteBuf buffer) throws ClassNotFoundException, IOException {
		return (Object[]) deserializeReturn(buffer);
	}
	
	@Override
	public Object deserializeReturn(ByteBuf buffer) throws ClassNotFoundException, IOException {
		try (ObjectInputStream ois = new ObjectInputStream(new ByteBufInputStream(buffer)) {
			@Override
			protected Class<?> resolveClass(ObjectStreamClass arg0)
					throws IOException, ClassNotFoundException {
				return classSpace.loadClass(arg0.getName());
			}
		}) {
			return ois.readObject();
		}
	}

}
