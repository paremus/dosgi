/*-
 * #%L
 * com.paremus.dosgi.topology.simple
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
package com.paremus.dosgi.topology.simple.impl;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.osgi.framework.Constants.OBJECTCLASS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteConstants;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;

import com.paremus.dosgi.scoping.discovery.Constants;
import com.paremus.dosgi.topology.simple.impl.ServiceImporter;

@RunWith(MockitoJUnitRunner.class)
public class ServiceImporterTest {

    private static final String SCOPE_A = "A";
    private static final String SCOPE_B = "B";
    private static final String SCOPE_C = "C";

    
    @Mock
    private Bundle bundleA, bundleB;
    
    @Mock
    private RemoteServiceAdmin rsaA, rsaB;

    @Mock
    private ImportRegistration regA, regB;
    
    ServiceImporter importer;

    @Before
    public void setUp() throws InvalidSyntaxException {
        
        importer = new ServiceImporter(new String[] {SCOPE_A, SCOPE_B});
        
		Mockito.when(rsaA.importService(Mockito.any())).thenReturn(regA);
		Mockito.when(rsaB.importService(Mockito.any())).thenReturn(regB);
    }
    
    private EndpointDescription getTestEndpointDescription(String endpointId, UUID frameworkId) {
    	return getTestEndpointDescription(endpointId, frameworkId, null);
    }
   
    private EndpointDescription getTestEndpointDescription(String endpointId, UUID frameworkId, 
    		String scope, String... scopes) {
 		Map<String, Object> m = new LinkedHashMap<String, Object>();

         // required
         m.put(OBJECTCLASS, new String[]{"com.acme.HelloService", "some.other.Service"});
         m.put(RemoteConstants.ENDPOINT_FRAMEWORK_UUID, frameworkId.toString());
         m.put(RemoteConstants.ENDPOINT_ID, endpointId);
         m.put(RemoteConstants.ENDPOINT_SERVICE_ID, Long.valueOf(42));
         m.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, "my.config.type");
         
         if(scope != null) {
        	 m.put(Constants.PAREMUS_SCOPES_ATTRIBUTE, scope);
         }
         
         if(scopes.length > 0) {
        	 if(scopes.length > 1) {
        		 m.put(Constants.PAREMUS_TARGETTED_EXTRA_ATTRIBUTE, scopes[scopes.length -1]);
        	 }
        	 m.put(Constants.PAREMUS_TARGETTED_ATTRIBUTE, 
        			 asList(scopes).subList(0, Math.max(1, scopes.length - 1)).stream()
        			 	.collect(toSet()));
         }

         return new EndpointDescription(m);
 	}    
    
    @Test
    public void testImportUnscopedEndpointThenRSAs() throws Exception {
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	importer.addingRSA(rsaA);
    	Mockito.verify(rsaA).importService(endpointDescription);

    	importer.addingRSA(rsaB);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }

    @Test
    public void testImportUnscopedEndpointWithRSAs() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);

    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }

    @Test
    public void testRevokeUnscopedEndpointWithRSAs() throws Exception {

    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	importer.departingEndpoint(bundleA, endpointDescription);
    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    }

    @Test
    public void testModifyUnscopedEndpointWithRSAs() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	EndpointDescription modifiedEndpoint = getTestEndpointDescription("FOO", 
    			UUID.fromString(endpointDescription.getFrameworkUUID()));
    	
    	importer.modifiedEndpoint(bundleA, modifiedEndpoint);
    	
    	Mockito.verify(regA).update(Mockito.same(modifiedEndpoint));
    	Mockito.verify(regB).update(Mockito.same(modifiedEndpoint));
    }

    @Test
    public void testImportScopedEndpointThenRSAs() throws Exception {
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A);
		importer.incomingEndpoint(bundleA, endpointDescription);

    	importer.addingRSA(rsaA);
    	Mockito.verify(rsaA).importService(endpointDescription);
    	
    	importer.addingRSA(rsaB);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }

    @Test
    public void testImportScopedEndpointWithRSAs() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);

    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A);
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }

    @Test
    public void testRevokeScopedEndpointWithRSAs() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);

    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A);
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    	
    	importer.departingEndpoint(bundleA, endpointDescription);

    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    }

    @Test
    public void testModifyExpandScope() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_C);
    	
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verifyZeroInteractions(rsaA, rsaB);
    	
    	endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A, SCOPE_C);
    	importer.modifiedEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }

    @Test
    public void testModifyReduceScope() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A, SCOPE_B);

    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    	
    	endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_C);
    	importer.modifiedEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    }
    
    @Test
    public void testReleasingListenerRevokesService() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);

    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	importer.releaseListener(bundleA);
    	
    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    }

    @Test
    public void testReleasingListenerDoesNotRevokeServiceWithMultipleSponsors() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	importer.incomingEndpoint(bundleB, endpointDescription);
    	
    	Mockito.verify(rsaA, Mockito.times(1)).importService(endpointDescription);
    	Mockito.verify(rsaB, Mockito.times(1)).importService(endpointDescription);
    	
    	importer.releaseListener(bundleA);
    	
    	Mockito.verifyZeroInteractions(regA, regB);
    }
    
    @Test
    public void testDestroy() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A, SCOPE_B);
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	importer.destroy();
    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    }
    
    @Test
    public void testModifyFrameworkScope() throws Exception {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_TARGETTED, SCOPE_A, SCOPE_B);
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);

    	importer.updateScopes(new String[] {SCOPE_C});
    	
    	Mockito.verify(regA).close();
    	Mockito.verify(regB).close();
    	
    	importer.updateScopes(new String[] {SCOPE_A});
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }
    
    @Test
    public void testRemovingRSAProviderUnregistersServices() throws InterruptedException {
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID());
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    	
    	importer.removingRSA(rsaA);
    	
    	Mockito.verify(regA).close();
    	Mockito.verifyZeroInteractions(regB);
    	
    }
    
    @Test
    public void testImportUniversalScopedEndpoint() throws Exception {
    	
    	EndpointDescription endpointDescription = getTestEndpointDescription("FOO", UUID.randomUUID(), 
    			Constants.PAREMUS_SCOPE_UNIVERSAL);
    	importer.incomingEndpoint(bundleA, endpointDescription);
    	
    	importer.addingRSA(rsaA);
    	importer.addingRSA(rsaB);
    	
    	Mockito.verify(rsaA).importService(endpointDescription);
    	Mockito.verify(rsaB).importService(endpointDescription);
    }
}
