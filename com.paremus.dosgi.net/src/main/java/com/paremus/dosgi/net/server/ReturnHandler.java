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

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

interface ReturnHandler {
	Future<?> success(Channel channel, int callId, Object returnValue);

	Future<?> failure(Channel channel, int callId, Throwable failure);
}
