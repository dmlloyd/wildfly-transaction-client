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
import java.net.URI;
import java.util.List;

import org.wildfly.naming.client.NamingProvider;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SerializedUserTransaction implements Serializable {
    private static final long serialVersionUID = - 7197250436320616251L;

    private final URI location;

    SerializedUserTransaction(final URI location) {
        this.location = location;
    }

    Object readResolve() {
        final NamingProvider currentNamingProvider = NamingProvider.getCurrentNamingProvider();
        if (currentNamingProvider != null) {
            final List<NamingProvider.Location> locations = currentNamingProvider.getLocations();
            for (NamingProvider.Location location : locations) {
                final URI providerUri = location.getUri();
                if (providerUri.equals(this.location)) {
                    return RemoteTransactionContext.getInstancePrivate().getUserTransaction(providerUri, location.getSSLContext(), location.getAuthenticationConfiguration());
                }
            }
        }
        return RemoteTransactionContext.getInstancePrivate().getUserTransaction(location);
    }
}
