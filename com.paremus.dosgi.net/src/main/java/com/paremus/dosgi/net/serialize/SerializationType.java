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

import com.paremus.dosgi.net.serialize.freshvanilla.VanillaRMISerializerFactory;
import com.paremus.dosgi.net.serialize.java.JavaSerializerFactory;
import com.paremus.dosgi.net.serialize.protobuf.ProtobufSerializerFactory;

public enum SerializationType {
	
	FAST_BINARY(new VanillaRMISerializerFactory()),
	DEFAULT_JAVA_SERIALIZATION(new JavaSerializerFactory()),
	PROTOCOL_BUFFERS(new ProtobufSerializerFactory());

	private final SerializerFactory factory;

	private SerializationType(SerializerFactory factory) {
		this.factory = factory;
	}

	public SerializerFactory getFactory() {
		return factory;
	}
}
