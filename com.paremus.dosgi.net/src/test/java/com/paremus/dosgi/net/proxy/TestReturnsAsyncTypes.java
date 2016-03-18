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
package com.paremus.dosgi.net.proxy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.osgi.util.promise.Promise;

public interface TestReturnsAsyncTypes {
	Promise<Boolean> coprime(long a, long b);
	
	Future<Boolean> isPrime(long x);
	
	CompletableFuture<Boolean> countGrainsOfSand(String location);
}
