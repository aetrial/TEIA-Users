package com.teia.users.dto;

import lombok.Data;

/**
 * DTO for Twilio message webhook parameters.
 * Maps the form data that Twilio sends on inbound SMS/WhatsApp messages.
 */
@Data
public class TwilioMessageWebhookRequest {

    // Core message identifiers
    private String MessageSid;
    private String SmsSid;
    private String AccountSid;
    private String MessagingServiceSid;

    // Phone numbers
    private String From;
    private String To;

    // Message content
    private String Body;

    // Media attachments (may be null)
    private String NumMedia;
    private String MediaContentType0;
    private String MediaUrl0;

    // Geographic info (SMS only, may be null)
    private String FromCity;
    private String FromState;
    private String FromZip;
    private String FromCountry;
    private String ToCity;
    private String ToState;
    private String ToZip;
    private String ToCountry;

    // API version
    private String ApiVersion;

    // Number of segments
    private String NumSegments;

    // Status (for status callbacks)
    private String SmsStatus;
    private String MessageStatus;
}
