package com.example.reservation.notification.service.channel;

public interface NotificationChannel {

    /**
     * The unique channel identifier (e.g. "EMAIL", "SMS").
     */
    String getChannelType();

    /**
     * Dispatches a notification to the recipient using the specified channel.
     *
     * @param request The notification payload containing recipient, template, and model
     */
    void send(NotificationRequest request);
}
