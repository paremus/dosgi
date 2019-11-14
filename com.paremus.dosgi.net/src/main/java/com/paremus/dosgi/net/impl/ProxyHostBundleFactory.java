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
package com.paremus.dosgi.net.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;

/**
 * This class dynamically creates and installs a "virtual" {@link Bundle} to host service
 * proxies for imported services. This is necessary to properly enable "magic" package
 * wiring and service visibility rules in the OSGi framework.<br>
 * For background information please see <a
 * href=http://www.mail-archive.com/osgi-dev@mail.osgi.org/msg01876.html>here</a>.
 */
public class ProxyHostBundleFactory {

    public static final String PEER_PROXY_BUNDLE_SYMBOLIC_NAME = "com.paremus.dosgi.net.host";
    public static final String CHILD_PROXY_BUNDLE_SYMBOLIC_NAME = "com.paremus.dosgi.net.child.host";
    public static final String PROXY_BUNDLE_MANIFESTVERSION = "2";
    public static final String PROXY_BUNDLE_NAME = "DOSGi Virtual Proxy Host";

    /**
     * Finds or installs a virtual {@link Bundle} with no exported or imported packages.
     * The {@link BundleContext} of this Bundle must be used for registration of the
     * {@link ServiceFactory} which acts as access point for imported remote services. <br>
     * The new Bundle has the same version as the dosgi net bundle and has a
     * Bundle-SymbolicName of {@value #PEER_PROXY_BUNDLE_SYMBOLIC_NAME} or
     * {@value #CHILD_PROXY_BUNDLE_SYMBOLIC_NAME}
     * 
     * @param target the framework into which the proxy bundle should be installed
     * @return the new virtual bundle
     */
    public Bundle getProxyBundle(Framework target) {
       
    	Bundle dosgiBundle = FrameworkUtil.getBundle(ProxyHostBundleFactory.class);
		boolean peer = dosgiBundle.getBundleContext().getBundle(0).equals(target);
    	
        // use name & version of the installer bundle to create the proxy host
        String proxySymbolicName = peer ? PEER_PROXY_BUNDLE_SYMBOLIC_NAME : CHILD_PROXY_BUNDLE_SYMBOLIC_NAME;
        Version proxyVersion = dosgiBundle.getVersion();

        BundleContext targetContext = target.getBundleContext();
        
        Optional<Bundle> existing = Arrays.stream(targetContext.getBundles())
        	.filter(b -> proxySymbolicName.equals(b.getSymbolicName()))
        	.filter(b -> proxyVersion.equals(b.getVersion()))
        	.findAny();
        
        return existing.orElseGet(() -> createBundle(proxySymbolicName, proxyVersion, targetContext));
    }

	private static Bundle createBundle(String proxySymbolicName, Version proxyVersion, BundleContext targetContext) {
		try {
			Manifest m = new Manifest();
			Attributes a = m.getMainAttributes();
			a.put(Name.MANIFEST_VERSION, "1.0");
			a.put(new Name(Constants.BUNDLE_MANIFESTVERSION), PROXY_BUNDLE_MANIFESTVERSION);
			a.put(new Name(Constants.BUNDLE_NAME), PROXY_BUNDLE_NAME);
			a.put(new Name(Constants.BUNDLE_SYMBOLICNAME), proxySymbolicName);
			a.put(new Name(Constants.BUNDLE_VERSION), proxyVersion.toString());
			
			ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);
			try (JarOutputStream out = new JarOutputStream(stream, m)){ }
			// nothing to write, so just close again
			stream.close();
			
			// "materialize" the bundle
			String location = UUID.randomUUID().toString();
			InputStream input = new ByteArrayInputStream(stream.toByteArray());
			Bundle proxy = targetContext.installBundle(location, input);
			// INSTALLED state is not enough
			proxy.start();
			return proxy;
		} catch (Exception ex) {
			throw new IllegalArgumentException(ex);
		}
	}

}
