/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

/**
 * Provides an instance of component. Searches for registered {@link ComponentProvider}s which are used to provide instances.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ComponentProviderService {

    private final Map<Session, Map<Class<?>, Object>> sessionToObject;
    private final List<ComponentProvider> providers;

    /**
     * Create new instance of {@link ComponentProviderService}.
     * </p>
     * Searches for registered {@link ComponentProvider}s and registers them with this service.
     * </p>
     * {@link DefaultComponentProvider} is always added to found providers.
     *
     * @return initialized {@link ComponentProviderService}.
     */
    public static ComponentProviderService create() {
        final List<ComponentProvider> foundProviders = new ArrayList<ComponentProvider>();
        ServiceFinder<ComponentProvider> finder = ServiceFinder.find(ComponentProvider.class);

        for (ComponentProvider componentProvider : finder) {
            foundProviders.add(componentProvider);
        }

        foundProviders.add(new DefaultComponentProvider());
        return new ComponentProviderService(Collections.unmodifiableList(foundProviders));
    }

    private ComponentProviderService(List<ComponentProvider> providers) {
        this.providers = providers;
        this.sessionToObject = new ConcurrentHashMap<Session, Map<Class<?>, Object>>();
    }

    /**
     * Copy constructor.
     *
     * @param componentProviderService original instance.
     */
    public ComponentProviderService(ComponentProviderService componentProviderService) {
        this.providers = componentProviderService.providers;
        this.sessionToObject = componentProviderService.sessionToObject;
    }

    /**
     * Provide an instance of class which is coupled to {@link Session}.
     * </p>
     * The first time the method is called the provider creates an instance and caches it.
     * Next time the method is called the cached instance is returned.
     *
     * @param c         {@link Class} whose instance will be provided.
     * @param collector error collector.
     * @param <T>       type of the provided instance.
     * @return instance
     */
    public <T> T getInstance(Class<T> c, Session session, ErrorCollector collector) {
        T loaded = null;

        final Map<Class<?>, Object> classObjectMap = sessionToObject.get(session);

        if (classObjectMap != null && classObjectMap.containsKey(c)) {
            Object fromMap = classObjectMap.get(c);
            loaded = c.isAssignableFrom(fromMap.getClass()) ? (T) fromMap : null;
        } else {
            try {
                // returns not-null value
                loaded = getEndpointInstance(c);
                if (classObjectMap == null) {
                    sessionToObject.put(session, new HashMap<Class<?>, Object>());
                }
                sessionToObject.get(session).put(c, loaded);
            } catch (Exception e) {
                collector.addException(new DeploymentException(String.format("Component provider threw exception when providing instance of class %s",
                        c.getName()), e));
            }
        }

        return loaded;
    }

    /**
     * Provide an instance of {@link javax.websocket.Encoder} or {@link javax.websocket.Decoder} descendant which is coupled to {@link Session}.
     * </p>
     * The first time the method is called the provider creates an instance, calls {@link javax.websocket.Encoder#init(javax.websocket.EndpointConfig)}
     * or {@link javax.websocket.Decoder#init(javax.websocket.EndpointConfig)} and caches it.
     * Next time the method is called the cached instance is returned.
     *
     * @param c              {@link Class} whose instance will be provided.
     * @param collector      error collector.
     * @param endpointConfig configuration corresponding to current context. Used for
     *                       {@link javax.websocket.Encoder#init(javax.websocket.EndpointConfig)} and
     *                       {@link javax.websocket.Decoder#init(javax.websocket.EndpointConfig)}
     * @param <T>            type of the provided instance.
     * @return instance
     */
    public <T> T getCoderInstance(Class<T> c, Session session, EndpointConfig endpointConfig, ErrorCollector collector) {
        T loaded = null;

        final Map<Class<?>, Object> classObjectMap = sessionToObject.get(session);

        if (classObjectMap != null && classObjectMap.containsKey(c)) {
            Object fromMap = classObjectMap.get(c);
            loaded = c.isAssignableFrom(fromMap.getClass()) ? (T) fromMap : null;
        } else {
            for (ComponentProvider componentProvider : providers) {
                if (componentProvider.isApplicable(c)) {
                    try {
                        loaded = componentProvider.create(c);
                        if (loaded != null) {
                            if (loaded instanceof Encoder) {
                                ((Encoder) loaded).init(endpointConfig);
                            } else if (loaded instanceof Decoder) {
                                ((Decoder) loaded).init(endpointConfig);
                            }
                            if (classObjectMap == null) {
                                sessionToObject.put(session, new HashMap<Class<?>, Object>());
                            }
                            sessionToObject.get(session).put(c, loaded);
                            break;
                        }
                    } catch (Exception e) {
                        collector.addException(new DeploymentException(String.format("Component provider %s threw exception when providing instance of class %s",
                                componentProvider.getClass().getName(), c.getName()), e));
                    }
                }
            }
        }

        return loaded;
    }

    /**
     * Remove {@link Session} from cache.
     *
     * @param session to be removed.
     */
    public void removeSession(Session session) {
        final Map<Class<?>, Object> classObjectMap = sessionToObject.get(session);
        if (classObjectMap != null) {
            for (Object o : classObjectMap.values()) {
                if (o instanceof Encoder) {
                    ((Encoder) o).destroy();
                } else if (o instanceof Decoder) {
                    ((Decoder) o).destroy();
                }

                for (ComponentProvider componentProvider : providers) {
                    if(componentProvider.destroy(o)){
                        break;
                    }
                }
            }
        }

        sessionToObject.remove(session);
    }

    /**
     * This method is called by the container each time a new client
     * connects to the logical endpoint this configurator configures.
     * Developers may override this method to control instantiation of
     * endpoint instances in order to customize the initialization
     * of the endpoint instance, or manage them in some other way.
     * If the developer overrides this method, services like
     * dependency injection that are otherwise supported, for example, when
     * the implementation is part of the Java EE platform
     * may not be available.
     * The platform default implementation of this method returns a new
     * endpoint instance per call, thereby ensuring that there is one
     * endpoint instance per client, the default deployment cardinality.
     *
     * @param endpointClass the class of the endpoint.
     * @param <T>           the type of the endpoint.
     * @return an instance of the endpoint that will handle all
     *         interactions from a new client.
     * @throws InstantiationException if there was an error producing the
     *                                endpoint instance.
     * @see javax.websocket.server.ServerEndpointConfig.Configurator#getEndpointInstance(Class)
     */
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        for (ComponentProvider componentProvider : providers) {
            if (componentProvider.isApplicable(endpointClass)) {
                final T t = componentProvider.create(endpointClass);
                if (t != null) {
                    return t;
                }
            }
        }

        throw new InstantiationException(String.format("Component provider for class %s not found.", endpointClass.getName()));
    }
}