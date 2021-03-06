/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.authorization.admin;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.store.PolicyStore;
import org.keycloak.authorization.store.ResourceStore;
import org.keycloak.authorization.store.ScopeStore;
import org.keycloak.authorization.store.StoreFactory;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.Logic;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.services.resources.admin.RealmAuth;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.HashMap;

import static org.keycloak.models.utils.ModelToRepresentation.toRepresentation;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class ResourceServerService {

    private final AuthorizationProvider authorization;
    private final RealmAuth auth;
    private final KeycloakSession session;
    private ResourceServer resourceServer;
    private final ClientModel client;

    public ResourceServerService(AuthorizationProvider authorization, ResourceServer resourceServer, ClientModel client, RealmAuth auth) {
        this.authorization = authorization;
        this.session = authorization.getKeycloakSession();
        this.client = client;
        this.resourceServer = resourceServer;
        this.auth = auth;
    }

    public void create() {
        this.auth.requireManage();
        this.resourceServer = this.authorization.getStoreFactory().getResourceServerStore().create(this.client.getId());
        createDefaultRoles();
        createDefaultPermission(createDefaultResource(), createDefaultPolicy());
    }

    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    public Response update(ResourceServerRepresentation server) {
        this.auth.requireManage();
        this.resourceServer.setAllowRemoteResourceManagement(server.isAllowRemoteResourceManagement());
        this.resourceServer.setPolicyEnforcementMode(server.getPolicyEnforcementMode());

        return Response.noContent().build();
    }

    public void delete() {
        this.auth.requireManage();
        StoreFactory storeFactory = authorization.getStoreFactory();
        ResourceStore resourceStore = storeFactory.getResourceStore();
        String id = resourceServer.getId();

        resourceStore.findByResourceServer(id).forEach(resource -> resourceStore.delete(resource.getId()));

        ScopeStore scopeStore = storeFactory.getScopeStore();

        scopeStore.findByResourceServer(id).forEach(scope -> scopeStore.delete(scope.getId()));

        PolicyStore policyStore = storeFactory.getPolicyStore();

        policyStore.findByResourceServer(id).forEach(scope -> policyStore.delete(scope.getId()));

        storeFactory.getResourceServerStore().delete(id);
    }

    @GET
    @Produces("application/json")
    public Response findById() {
        this.auth.requireView();
        return Response.ok(toRepresentation(this.resourceServer, this.client)).build();
    }

    @Path("/settings")
    @GET
    @Produces("application/json")
    public Response exportSettings() {
        this.auth.requireManage();
        return Response.ok(ExportUtils.exportAuthorizationSettings(session, client)).build();
    }

    @Path("/import")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importSettings(@Context final UriInfo uriInfo, ResourceServerRepresentation rep) throws IOException {
        this.auth.requireManage();

        rep.setClientId(client.getId());

        RepresentationToModel.toModel(rep, authorization);

        return Response.noContent().build();
    }

    @Path("/resource")
    public ResourceSetService getResourceSetResource() {
        ResourceSetService resource = new ResourceSetService(this.resourceServer, this.authorization, this.auth);

        ResteasyProviderFactory.getInstance().injectProperties(resource);

        return resource;
    }

    @Path("/scope")
    public ScopeService getScopeResource() {
        ScopeService resource = new ScopeService(this.resourceServer, this.authorization, this.auth);

        ResteasyProviderFactory.getInstance().injectProperties(resource);

        return resource;
    }

    @Path("/policy")
    public PolicyService getPolicyResource() {
        PolicyService resource = new PolicyService(this.resourceServer, this.authorization, this.auth);

        ResteasyProviderFactory.getInstance().injectProperties(resource);

        return resource;
    }

    private void createDefaultPermission(ResourceRepresentation resource, PolicyRepresentation policy) {
        PolicyRepresentation defaultPermission = new PolicyRepresentation();

        defaultPermission.setName("Default Permission");
        defaultPermission.setType("resource");
        defaultPermission.setDescription("A permission that applies to the default resource type");
        defaultPermission.setDecisionStrategy(DecisionStrategy.UNANIMOUS);
        defaultPermission.setLogic(Logic.POSITIVE);

        HashMap<String, String> defaultPermissionConfig = new HashMap<>();

        defaultPermissionConfig.put("default", "true");
        defaultPermissionConfig.put("defaultResourceType", resource.getType());
        defaultPermissionConfig.put("applyPolicies", "[\"" + policy.getName() + "\"]");

        defaultPermission.setConfig(defaultPermissionConfig);

        getPolicyResource().create(defaultPermission);
    }

    private PolicyRepresentation createDefaultPolicy() {
        PolicyRepresentation defaultPolicy = new PolicyRepresentation();

        defaultPolicy.setName("Default Policy");
        defaultPolicy.setDescription("A policy that grants access only for users within this realm");
        defaultPolicy.setType("js");
        defaultPolicy.setDecisionStrategy(DecisionStrategy.AFFIRMATIVE);
        defaultPolicy.setLogic(Logic.POSITIVE);

        HashMap<String, String> defaultPolicyConfig = new HashMap<>();

        defaultPolicyConfig.put("code", "// by default, grants any permission associated with this policy\n$evaluation.grant();\n");

        defaultPolicy.setConfig(defaultPolicyConfig);

        getPolicyResource().create(defaultPolicy);

        return defaultPolicy;
    }

    private ResourceRepresentation createDefaultResource() {
        ResourceRepresentation defaultResource = new ResourceRepresentation();

        defaultResource.setName("Default Resource");
        defaultResource.setUri("/*");
        defaultResource.setType("urn:" + this.client.getClientId() + ":resources:default");

        getResourceSetResource().create(defaultResource);
        return defaultResource;
    }

    private void createDefaultRoles() {
        RoleModel umaProtectionRole = client.getRole(Constants.AUTHZ_UMA_PROTECTION);

        if (umaProtectionRole == null) {
            umaProtectionRole = client.addRole(Constants.AUTHZ_UMA_PROTECTION);
        }

        UserModel serviceAccount = this.session.users().getServiceAccount(client);

        if (!serviceAccount.hasRole(umaProtectionRole)) {
            serviceAccount.grantRole(umaProtectionRole);
        }
    }
}
