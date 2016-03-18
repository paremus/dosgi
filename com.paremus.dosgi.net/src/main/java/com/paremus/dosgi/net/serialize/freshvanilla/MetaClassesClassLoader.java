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
package com.paremus.dosgi.net.serialize.freshvanilla;

import org.osgi.framework.Bundle;

public class MetaClassesClassLoader extends ClassLoader {

	private static final class BundleToClassLoader extends ClassLoader {
		private final Bundle classSpace;
		
		public BundleToClassLoader(Bundle classSpace) {
			this.classSpace = classSpace;
		}
		
		protected Class<?> findClass(String className) throws ClassNotFoundException {
			return classSpace.loadClass(className);
		}
	}
	

	public MetaClassesClassLoader(Bundle classSpace) {
		super(new BundleToClassLoader(classSpace));
	}
	
	protected Class<?> findClass(String className) throws ClassNotFoundException {
		return MetaClassesClassLoader.class.getClassLoader().loadClass(className);
	}
}
