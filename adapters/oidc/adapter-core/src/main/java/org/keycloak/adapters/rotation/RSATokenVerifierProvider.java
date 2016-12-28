package org.keycloak.adapters.rotation;

/**
 * You must specify a file
 * META-INF/services/org.keycloak.adapters.rotation.RSATokenVerifierProvider in the WAR that this class is contained in (or in the JAR that is attached to the WEB-INF/lib or as jboss module
 * if you want to share the implementation among more WARs).
 */
public interface RSATokenVerifierProvider {
    
    public String verifyToken(String tokenString);
    
    public String getId();
    
}
