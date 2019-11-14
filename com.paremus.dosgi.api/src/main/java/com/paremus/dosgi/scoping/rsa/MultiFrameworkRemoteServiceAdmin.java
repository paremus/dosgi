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
package com.paremus.dosgi.scoping.rsa;

import java.util.Collection;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ExportReference;
import org.osgi.service.remoteserviceadmin.ExportRegistration;
import org.osgi.service.remoteserviceadmin.ImportReference;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

/**
 * A specialisation of a {@link RemoteServiceAdmin} which is able to 
 * work across multiple frameworks. 
 */
@ProviderType
public interface MultiFrameworkRemoteServiceAdmin extends RemoteServiceAdmin {

	/**
	 * Import the named endpoint into a specific framework
	 * 
	 * @see #importService(EndpointDescription)
	 * 
	 * @param framework - the target framework
	 * @param e - the endpoint to import
	 * @return An ImportRegistration representing the imported service
	 */
	ImportRegistration importService(Framework framework, EndpointDescription e);

	/**
	 * Export the supplied service from its framework framework
	 * 
	 * @see #exportService(ServiceReference, Map)
	 * 
	 * @param framework - the target framework
	 * @param ref - the service to export
	 * @param props - additional properties with which this service should be exported
	 * @return A collection of ExportRegistrations representing the endpoints for this exported service
	 */
	Collection<ExportRegistration> exportService(Framework framework, ServiceReference<?> ref, Map<String, ?> props);

	/**
	 * Get all of the services exported from a particular framework
	 * 
	 * @param framework - the target framework
	 * @return The ExportReferences associated with the named framework
	 */
	Collection<ExportReference> getExportedServices(Framework framework);

	/**
	 * Get all of the endpoints imported into a particular framework
	 * 
	 * @param framework - the target framework
	 * @return The ImportReferences associated with the named framework
	 */
	Collection<ImportReference> getImportedEndpoints(Framework framework);
	
	/**
	 * Get all of the exported services for all frameworks
	 * 
	 * @return The ExportReferences advertised by this {@link RemoteServiceAdmin}
	 * across all frameworks
	 */
	Collection<ExportReference> getAllExportedServices();
	
	/**
	 * Get all of the imported services for all frameworks
	 * 
	 * @return The ImportReferences owned by this {@link RemoteServiceAdmin}
	 * across all frameworks
	 */
	Collection<ImportReference> getAllImportedEndpoints();
}
