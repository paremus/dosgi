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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import org.osgi.util.promise.Promise;
import org.osgi.util.pushstream.PushEventSource;
import org.osgi.util.pushstream.PushStream;

public interface ServerTestService extends CharSequence {
	
	public Future<CharSequence> subSequence(Promise<Integer> p, CompletionStage<Integer> cs);
	
	public PushStream<Character> streamOfCharacters(int failAfter);

	public PushEventSource<Character> reusableStreamOfCharacters(int failAfter);

}
