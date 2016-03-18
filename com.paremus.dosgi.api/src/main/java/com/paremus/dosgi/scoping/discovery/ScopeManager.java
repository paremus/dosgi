/*-
 * #%L
 * com.paremus.dosgi.api
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
package com.paremus.dosgi.scoping.discovery;

import java.util.Set;

/**
 * The {@link ScopeManager} is used to add or remove local scopes from
 * this discovery node
 */
public interface ScopeManager {

	/**
	 * Get the currently active scopes
	 * 
	 * @return the current scopes
	 */
	public Set<String> getCurrentScopes();
	
	/**
	 * Add a scope
	 * @param name The scope to add
	 */
	public void addLocalScope(String name);
	
	/**
	 * Remove a scope
	 * @param name The scope to remove
	 */
	public void removeLocalScope(String name);
	
	/**
	 * Get the base scopes which apply to this discovery and
	 * cannot be removed
	 * 
	 * @return the current scopes
	 */
	public Set<String> getBaseScopes();
}
