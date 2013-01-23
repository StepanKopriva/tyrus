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
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 */
@WebSocketEndpoint(value = "/blockingbinary",configuration = DefaultServerConfiguration.class)
public class BlockingBinaryServer extends Endpoint {
    private Session session;
    static CountDownLatch messageLatch;
    private String message;

    @WebSocketOpen
    public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
        System.out.println("BLOCKINGBSERVER opened !");
        this.session = session;

        session.addMessageHandler(new MessageHandler.Async<InputStream>() {
            StringBuilder sb = new StringBuilder();
            @Override
            public void onMessage(InputStream is, boolean isLast) {
                try {
                    int i;
                    while ( (i=is.read()) != -1 ) {
                        //System.out.println("BLOCKINGBSERVER read " + (char) i + " from the input stream.");
                        sb.append((char) i);
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                System.out.println("BLOCKINGBSERVER read " + sb + " from the input stream.");
                messageLatch.countDown();
                message = sb.toString();
                reply();
            }
        });
    }

//    @Override
//    public EndpointConfiguration getEndpointConfiguration() {
//        return null;
//    }

    public void reply() {
        System.out.println("BLOCKINGBSERVER replying");
        try {
            OutputStream os = session.getRemote().getSendStream();
            os.write(message.getBytes());
            os.close();
        } catch (IOException ioe ) {
            ioe.printStackTrace();
        }
    }
}
