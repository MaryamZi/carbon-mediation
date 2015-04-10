package org.wso2.carbon.inbound.endpoint.protocol.hl7;

/**
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.synapse.inbound.InboundProcessorParams;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InboundHL7IOReactor {

    private static final Log log = LogFactory.getLog(InboundHL7IOReactor.class);

    private static ListeningIOReactor reactor;

    private static ConcurrentHashMap<Integer, ListenerEndpoint> endpointMap = new ConcurrentHashMap<Integer, ListenerEndpoint>();

    private static volatile boolean isStarted = false;

    private static ConcurrentHashMap<Integer, InboundProcessorParams>
            parameterMap = new ConcurrentHashMap<Integer, InboundProcessorParams>();

    public static void start() throws IOException {
        log.info("LOG 0: start() : isStarted = " + isStarted);

        if (reactor != null && reactor.getStatus().equals(IOReactorStatus.ACTIVE)) {
            return;
        }

        IOReactorConfig config = getDefaultReactorConfig();

        reactor = new DefaultListeningIOReactor(config);

        Thread reactorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    isStarted = true;
                    log.info("MLLP Transport IO Reactor Started");
                    reactor.execute(new MultiIOHandler(parameterMap));
                } catch (IOException e) {
                    isStarted = false;
                    log.error("Error while starting the MLLP Transport IO Reactor. " + e.getMessage());
                }
            }
        });

        reactorThread.start();
    }

    public static void stop() {
        log.info("LOG 1: stop() : isStarted = " + isStarted);

        try {
            reactor.shutdown();
            endpointMap.clear();
            isStarted = false;
        } catch (IOException e) {
            log.error("Error while shutting down MLLP Transport IO Reactor. " + e.getMessage());
        }
    }

    public static void pause() {
        try {
            reactor.pause();
        } catch (IOException e) {
            log.error("Error while pausing MLLP Transport IO Reactor. " + e.getMessage());
        }
    }

    public static boolean isStarted() {
        return isStarted;
    }

    public static boolean bind(int port, InboundProcessorParams params) {
        log.info("LOG 2: bind() : isStarted = " + isStarted);

        ListenerEndpoint ep = reactor.listen(getSocketAddress(port));

        try {
            ep.waitFor();
            endpointMap.put(port, ep);
            parameterMap.put(port, params);
            return true;
        } catch (InterruptedException e) {
            log.error("Error while starting a new MLLP Listener on port " + port + ". " + e.getMessage());
            return false;
        }
    }

    public static boolean unbind(int port) {
        log.info("LOG 3: unbind() : isStarted = " + isStarted);

        ListenerEndpoint ep = endpointMap.get(port);

        printEndpoints(reactor.getEndpoints());
        endpointMap.remove(port);
        parameterMap.remove(port);

        if (ep == null) {
            return false;
        }

        ep.close();

        return true;
    }

    private static void printEndpoints(Set<ListenerEndpoint> eps) {
        for (ListenerEndpoint e: eps) {
            log.info("LOG 4: " + e.getAddress() + " isClosed() - " + e.isClosed());
        }
    }

    private static SocketAddress getSocketAddress(int port) {
        InetSocketAddress isa = new InetSocketAddress(port);
        return isa;
    }


    private static IOReactorConfig getDefaultReactorConfig() {
        IOReactorConfig config =  new IOReactorConfig();
        config.setSelectInterval(1000);
        config.setShutdownGracePeriod(500);
        config.setInterestOpQueued(false);
        config.setIoThreadCount(Runtime.getRuntime().availableProcessors());
        config.setSoTimeout(0);
        config.setSoReuseAddress(true);
        config.setSoLinger(-1);
        config.setSoKeepalive(false);
        config.setTcpNoDelay(true);
        config.setConnectTimeout(0);
        //config.setSndBufSize(0);
        //config.setRcvBufSize(0);

        return config;
    }

}