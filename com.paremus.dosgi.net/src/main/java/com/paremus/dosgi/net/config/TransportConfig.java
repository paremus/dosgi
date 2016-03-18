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

import org.osgi.service.metatype.annotations.ObjectClassDefinition;


@ObjectClassDefinition(factoryPid="com.paremus.dosgi.net.transport")
public @interface TransportConfig {
	
	boolean allow_insecure_transports() default false;
	
	String[] client_protocols() default {"TCP;nodelay=true", "TCP_CLIENT_AUTH;nodelay=true;connect.timeout=3000"};
	
	String[] server_protocols() default {"TCP;nodelay=true", "TCP_CLIENT_AUTH;nodelay=true"};
	
	String server_bind_address() default "0.0.0.0";

	int client_default_timeout() default 30000;

	String encoding_scheme_target() default "";
	
	String endpoint_export_target() default "";
	
	String endpoint_import_target() default "";

	String endpoint_marker() default "";
	
	String[] additional_intents() default {};
}
