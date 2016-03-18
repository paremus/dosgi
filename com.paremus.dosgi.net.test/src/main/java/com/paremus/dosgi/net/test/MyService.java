/*-
 * #%L
 * com.paremus.dosgi.net.test
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
package com.paremus.dosgi.net.test;

import java.rmi.RemoteException;

import org.osgi.framework.Version;
import org.osgi.util.promise.Promise;
import org.osgi.util.pushstream.PushEventSource;
import org.osgi.util.pushstream.PushStream;

public interface MyService {

    // always succeeds
    int ping();

    // always succeeds
    Version increment(Version v);

    // returns the value
    public String echo(int arg, String s);

    // throws an undeclared RuntimeException
    void throwUndeclared();

    // throws the declared exception
    void throwDeclared() throws RemoteException;
    
    Promise<Long> delayedValue();
    
    Promise<String> waitForIt(Promise<Long> futureResult);

    PushStream<Long> streamToMe(long lengthOfStream, long defaultInterval);
    
    long lastStreamQuitEarlyAt();
    
    PushEventSource<Long> repeatableStreamToMe(long lengthOfStream, long defaultInterval);
}
