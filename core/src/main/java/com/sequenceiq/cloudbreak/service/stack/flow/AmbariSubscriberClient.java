package com.sequenceiq.cloudbreak.service.stack.flow;

import com.sequenceiq.cloudbreak.service.events.CloudbreakEventService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class AmbariSubscriberClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmbariSubscriberClient.class);
    private StackService stackService;
    private CloudbreakEventService cloudbreakEventService;
    private final Long stackId;
    private final String ambariServerIp;

    public AmbariSubscriberClient(StackService stackService, CloudbreakEventService cloudbreakEventService, Long id, String ambariServerIp) {
        this.stackService = stackService;
        this.cloudbreakEventService = cloudbreakEventService;
        this.stackId = id;
        this.ambariServerIp = ambariServerIp;
    }

    public void connect() {
        LOGGER.warn("Trying to connect to websocket at " + ambariServerIp);
        WebSocketClientFactory webSocketClientFactory = new WebSocketClientFactory();
        try {
            webSocketClientFactory.start();
        } catch (Exception e) {
            LOGGER.error("Exception while trying to start web socket client", e);
            return;
        }
        URI uri = URI.create(getUrl());
        WebSocketClient webSocketClient = webSocketClientFactory.newWebSocketClient();
        try {
            webSocketClient.open(uri, new SubscriberSocket(stackService, cloudbreakEventService, stackId, ambariServerIp, getUrl()));
        } catch (IOException e) {
            LOGGER.error("Could not open connection to ambari websocket at " + getUrl(), e);
        }
    }

    private String getUrl() {
        return "ws://" + ambariServerIp + ":8080/alerts";
    }
}
