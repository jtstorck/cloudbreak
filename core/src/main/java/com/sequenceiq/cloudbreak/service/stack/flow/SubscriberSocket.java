package com.sequenceiq.cloudbreak.service.stack.flow;

import com.sequenceiq.cloudbreak.api.model.InstanceGroupAdjustmentJson;
import com.sequenceiq.cloudbreak.api.model.Status;
import com.sequenceiq.cloudbreak.service.events.CloudbreakEventService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class SubscriberSocket implements WebSocket.OnTextMessage {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriberSocket.class);

    private StackService stackService;
    private CloudbreakEventService eventService;
    private Long stackId;
    private String ambariIp;
    private String url;

    public SubscriberSocket(StackService stackService, CloudbreakEventService eventService, Long stackId, String ambariIp, String url) {
        this.stackService = stackService;
        this.eventService = eventService;
        this.stackId = stackId;
        this.ambariIp = ambariIp;
        this.url = url;
    }

    @Override
    public void onMessage(String message) {
        printMessage("Received message: " + message);
        eventService.fireCloudbreakEvent(stackId, Status.NODE_DOWN.name(), message);
        String[] parts = message.trim().split(" ");
        String internalHostname = parts[parts.length - 1];
        String[] ipParts = internalHostname.split("\\.")[0].split("-");
        String[] privateIpParts = Arrays.copyOfRange(ipParts, 1, ipParts.length);
        String privateIpAddress = StringUtils.join(privateIpParts, ".");
        LOGGER.warn(String.format("Hostname of affected host: %s, Private IP: %s", internalHostname, privateIpAddress));
        stackService.recoverInstance(stackId, privateIpAddress);
    }

    @Override
    public void onOpen(Connection connection) {
        printMessage("Received connect: " + connection);
    }

    @Override
    public void onClose(int statusCode, String reason) {
        printMessage("Received close: status: " + statusCode + ", reason: " + reason);
    }

    private void printMessage(String message) {
        LOGGER.warn(String.format("From %s(%s), stack %d received message: %s", ambariIp, url, stackId, message));
    }
}
