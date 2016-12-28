package org.keycloak.adapters.rotation;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.jboss.logging.Logger;

public final class RSATokenVerifierProviderUtils {
    public static final RSATokenVerifierProviderUtils INSTANCE = new RSATokenVerifierProviderUtils();
    private static Logger logger = Logger.getLogger(RSATokenVerifierProviderUtils.class);
    private RSATokenVerifierProvider provider;
    private boolean init;
    
    private RSATokenVerifierProviderUtils() {
    }
    
    private void loadProviders() {
        init = true;
        loadProviders(Thread.currentThread().getContextClassLoader());
        if (provider == null) {
            loadProviders(RSATokenVerifierProviderUtils.class.getClassLoader());
        }
    }
    
    private void loadProviders(ClassLoader classLoader) {
        Iterator<RSATokenVerifierProvider> iterator = ServiceLoader.load(RSATokenVerifierProvider.class, classLoader).iterator();
        while (iterator.hasNext()) {
            try {
                RSATokenVerifierProvider p = iterator.next();
                logger.debugf("Loaded RSATokenVerifierProvider %s", p.getId());
                if (provider == null) {
                    provider = p;
                } else {
                    logger.warnf("Ignore RSATokenVerifierProvider %s because there are configurations", p.getId());
                }
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to load RSATokenVerifierProvider with classloader: " + classLoader, e);
                }
            }
        }
    }
    
    public RSATokenVerifierProvider getProvider(){
        if (provider == null && !init) {
            loadProviders();
        }
        return provider;
    }
}
