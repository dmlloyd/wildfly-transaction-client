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

package org.wildfly.transaction.client;

import java.io.Serializable;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.ProviderEnvironment;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SerializedUserTransaction implements Serializable {
    private static final long serialVersionUID = - 7197250436320616251L;

    SerializedUserTransaction() {
    }

    Object readResolve() {
        AuthenticationContext context = AuthenticationContext.captureCurrent();
        final NamingProvider currentNamingProvider = NamingProvider.getCurrentNamingProvider();
        if (currentNamingProvider != null) {
            final ProviderEnvironment providerEnvironment = currentNamingProvider.getProviderEnvironment();
            context = providerEnvironment.getAuthenticationContextSupplier().get();
        }
        return context.runFunction(RemoteTransactionContext::getUserTransaction, RemoteTransactionContext.getInstancePrivate());
    }
}
