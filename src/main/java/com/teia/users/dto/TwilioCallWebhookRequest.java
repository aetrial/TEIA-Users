package com.teia.users.dto;

import lombok.Data;

/**
 * DTO for Twilio voice call webhook parameters.
 * Maps the form data that Twilio sends on inbound/outbound call events.
 */
@Data
public class TwilioCallWebhookRequest {

    // Core call identifiers
    private String CallSid;
    private String AccountSid;

    // Phone numbers
    private String From;
    private String To;
    private String Caller;
    private String Called;

    // Call direction and status
    private String Direction;
    private String CallStatus;

    // Geographic info (may be null)
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

    // Forwarded from (if call was forwarded)
    private String ForwardedFrom;

    // Parent call SID (for child legs)
    private String ParentCallSid;
}
