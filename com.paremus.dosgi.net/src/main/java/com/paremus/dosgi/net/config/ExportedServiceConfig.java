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
package com.paremus.dosgi.net.config;

import static com.paremus.dosgi.net.serialize.SerializationType.FAST_BINARY;

import com.paremus.dosgi.net.serialize.SerializationType;

public @interface ExportedServiceConfig {
	
	String[] objectClass() default {};
	
	String[] service_exported_interfaces() default {};
	
	String[] service_exported_configs() default {};
	
	String[] service_exported_intents() default {};
	
	String[] service_exported_intents_extra() default {};
	
	String[] com_paremus_dosgi_net_transports() default {};

	String[] service_intents() default {};
	
	SerializationType com_paremus_dosgi_net_serialization() default FAST_BINARY;
}
