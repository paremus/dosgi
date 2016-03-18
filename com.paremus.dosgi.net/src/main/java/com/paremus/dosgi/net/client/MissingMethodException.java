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
package com.paremus.dosgi.net.client;

public class MissingMethodException extends Exception {

	private static final long serialVersionUID = -5390658105164314276L;

	public MissingMethodException(String name) {
		super("The remote service did not have a method " + name + " it is possible that two incompatible versions of the API are being used");
	}
}
