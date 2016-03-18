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

import java.io.Closeable;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.framework.Version;
import org.osgi.service.async.delegate.AsyncDelegate;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushEventConsumer;
import org.osgi.util.pushstream.PushEventSource;
import org.osgi.util.pushstream.PushStream;
import org.osgi.util.pushstream.PushStreamProvider;

public class MyServiceImpl implements MyService, MyServiceSecondRole, AsyncDelegate {

	private AtomicInteger count = new AtomicInteger();
	
	private PushStreamProvider provider = new PushStreamProvider();
	
	private AtomicLong earlyExit = new AtomicLong(-1);
	
	
    public int ping() {
        return 42;
    }
    
    public Version increment(Version v) {
    	return new Version(v.getMajor() + 1, v.getMinor() + 1, v.getMicro() + 1);
    }

    public String echo(int arg, String s) {
    	try {
    		if(arg > 0)
    			Thread.sleep(arg * 1000);
		} catch (InterruptedException e) {
			throw new RuntimeException();
		}
        return s;
    }

    public void throwUndeclared() {
        throw new IllegalStateException("I'm soo confused");
    }

    public void throwDeclared() throws RemoteException {
        throw new RemoteException("remote ouch!");
    }

    public String hello() {
        return "Hello World :-)";
    }

	@Override
	public Promise<Long> delayedValue() {
		return doDelayedValue(12345L);
	}

	@Override
	public Promise<String> waitForIt(Promise<Long> futureResult) {
		return futureResult.map(Number::toString);
	}

	private Promise<Long> doDelayedValue(final long value) {
		final Deferred<Long> d = new Deferred<Long>();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				d.resolve(value);
			}
		}).start();
		return d.getPromise();
	}

	@Override
	public Promise<?> async(Method m, Object[] args) throws Exception {
		if("ping".equals(m.getName())) {
			return Promises.resolved(43);
		} else if("echo".equals(m.getName())) {
			return Promises.resolved("Async " + args[1]);
		} else if ("throwUndeclared".equals(m.getName())) {
			return Promises.failed(new IllegalStateException("Async Failure"));
		} else if("throwDeclared".equals(m.getName())) {
			return Promises.failed(new RemoteException("Async remote ouch!"));
		} else if("hello".equals(m.getName())) {
			return Promises.resolved("Hello World :-)");
		} else if("delayedValue".equals(m.getName())) {
			return doDelayedValue(12346L);
		}
		throw new IllegalArgumentException("Unknown method");
	}

	@Override
	public boolean execute(Method m, Object[] args) throws Exception {
		count.incrementAndGet();
		return true;
	}
	
	public int getCount() {
		return count.get();
	}

	@Override
	public PushStream<Long> streamToMe(long lengthOfStream, long defaultInterval) {
		return provider.<Long, BlockingQueue<PushEvent<? extends Long>>>buildStream(
				consumer -> onConnect(consumer, lengthOfStream, defaultInterval))
			.unbuffered()
			.build();
	}

	private Closeable onConnect(PushEventConsumer<? super Long> consumer, long lengthOfStream, long defaultInterval) {
		
		AtomicBoolean closed = new AtomicBoolean(false);
		
		Thread t = new Thread(() -> {
			try {
				for(long i = 0; i < lengthOfStream; i++) {
					
					if(closed.get()) {
						earlyExit.set(i);
						break;
					}
					
					try {
						long bp = consumer.accept(PushEvent.data(i));
						if(bp < 0) {
							earlyExit.set(i);
							break;
						} else {
							long toWaitMillis = Math.max(bp, defaultInterval);
							if(toWaitMillis > 0) {
								System.out.println("Waiting " + toWaitMillis);
								Thread.sleep(toWaitMillis);
							}
						}
					} catch (InterruptedException ie) {
						earlyExit.set(i);
						if(closed.compareAndSet(false, true)) {
							consumer.accept(PushEvent.close());
						}
						return;
					} catch (Exception e) {
						earlyExit.set(i);
						consumer.accept(PushEvent.error(e));
						return;
					}
				}
				consumer.accept(PushEvent.close());
			} catch (Exception e) {
				// Swallow and exit
			}
		});
		
		t.start();
		return () -> {
				closed.set(true);
				t.interrupt();
			};
	}

	@Override
	public long lastStreamQuitEarlyAt() {
		return earlyExit.getAndSet(-1);
	}

	List<PushEventConsumer<? super Long>> list = new CopyOnWriteArrayList<>();
	boolean running;
	
	@Override
	public PushEventSource<Long> repeatableStreamToMe(long lengthOfStream, long defaultInterval) {
		return pec -> onConnect(pec, lengthOfStream, defaultInterval);
	}
	
}
