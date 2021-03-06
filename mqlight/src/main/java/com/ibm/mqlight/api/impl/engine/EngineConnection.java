/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ibm.mqlight.api.impl.engine;

import java.util.HashMap;
import java.util.LinkedList;

import org.apache.qpid.proton.engine.Collector;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;

import com.ibm.mqlight.api.impl.Component;
import com.ibm.mqlight.api.impl.timer.TimerPromiseImpl;
import com.ibm.mqlight.api.logging.Logger;
import com.ibm.mqlight.api.logging.LoggerFactory;
import com.ibm.mqlight.api.network.NetworkChannel;

public class EngineConnection {

    private static final Logger logger = LoggerFactory.getLogger(EngineConnection.class);
  
    protected final Connection connection;
    protected final Session session;
    protected final Component requestor;    // Used for sending "you've been disconnected notifications

    protected static class PendingQos0Response{
      
        private static final Logger logger = LoggerFactory.getLogger(PendingQos0Response.class);
      
        long amount;
        SendResponse response;
        Component component;
        Engine engine;
        protected PendingQos0Response(long amount, SendResponse response, Component component, Engine engine) {
            final String methodName = "<init>";
            logger.entry(this, methodName, amount, response, component, engine);
            
            this.amount = amount;
            this.response = response;
            this.component = component;
            this.engine = engine;
            
            logger.exit(this, methodName);
        }
    }

    // An (ordered) list of in-flight qos 0 transfers.  This is used to determine when to invoke
    // the associated callback (as supplied to the send method) based on how much data has been
    // written to the AMQP transport.
    protected final LinkedList<PendingQos0Response> inflightQos0 = new LinkedList<>();

    protected void addInflightQos0(int delta, SendResponse response, Component component, Engine engine) {
        final String methodName = "addInflightQos0";
        logger.entry(this, methodName, delta, response, component, engine);
      
        inflightQos0.addLast(new PendingQos0Response(bytesWritten + delta, response, component, engine));
        
        logger.exit(this, methodName);
    }

    protected void notifyInflightQos0(boolean purge) {
        final String methodName = "notifyInflightQos0";
        logger.entry(this, methodName, purge);
      
        while(!inflightQos0.isEmpty()) {
            PendingQos0Response pendingResponse = inflightQos0.getFirst();
            if (purge || (pendingResponse.amount <= bytesWritten)) {
                inflightQos0.removeFirst();
                pendingResponse.component.tell(pendingResponse.response, pendingResponse.engine);
            } else {
                break;
            }
        }
        
        logger.exit(this, methodName);
    }

    protected final Transport transport;
    protected final Collector collector;
    protected final NetworkChannel channel;
    protected long deliveryTag = 0;
    protected final HashMap<Delivery, SendRequest> inProgressOutboundDeliveries = new HashMap<>();
    protected final HashMap<String, SubscriptionData> subscriptionData = new HashMap<>();
    protected OpenRequest openRequest = null;
    protected CloseRequest closeRequest = null;
    protected TimerPromiseImpl timerPromise = null;
    protected boolean closed = false;
    protected boolean drained = true;
    protected long bytesWritten = 0;

    protected static class SubscriptionData {
      
        private static final Logger logger = LoggerFactory.getLogger(SubscriptionData.class);
      
        protected final Component subscriber;
        protected final int maxLinkCredit;
        protected final Receiver receiver;
        protected int unsettled;
        protected int settled;
        protected SubscriptionData(Component subscriber, int maxLinkCredit, Receiver receiver) {
            final String methodName = "<init>";
            logger.entry(this, methodName, subscriber, subscriber, receiver);
            
            this.subscriber = subscriber;
            this.maxLinkCredit = maxLinkCredit;
            this.receiver = receiver;
            this.unsettled = 0;
            this.settled = 0;
            
            logger.exit(this, methodName);
        }
    }

    protected EngineConnection(Connection connection, Session session, Component requestor, Transport transport, Collector collector, NetworkChannel channel) {
        final String methodName = "<init>";
        logger.entry(this, methodName, connection, session, requestor, transport, collector, channel);
      
        this.connection = connection;
        this.session = session;
        this.requestor = requestor;
        this.transport = transport;
        this.collector = collector;
        this.channel = channel;
        
        logger.exit(this, methodName);
    }

    /**
     * For unit testing.
     */
    public EngineConnection() {
        final String methodName = "<init>";
        logger.entry(this, methodName);
      
        requestor = null;
        session = null;
        channel = null;
        connection = null;
        collector = null;
        transport = null;
        
        logger.exit(this, methodName);
    }
}
