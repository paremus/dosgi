/*-
 * #%L
 * com.paremus.dosgi.discovery.cluster
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
package com.paremus.dosgi.discovery.cluster.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(factoryPid="com.paremus.dosgi.discovery.gossip")
public @interface Config {
	@AttributeDefinition(max="65535", min="0")
	int port() default 0;

	String bind_address() default "0.0.0.0"; 

	long rebroadcast_interval() default 15000;
	
	String root_cluster();
	
	String[] target_clusters() default {};
	
	String[] additional_filters() default {};
	
	String local_id_filter_extension() default "";

	String tls_target() default "";
	
	String[] base_scopes() default {};

}
