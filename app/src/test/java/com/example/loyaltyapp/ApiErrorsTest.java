package com.example.loyaltyapp;

import static org.junit.Assert.*;

import org.junit.Test;

public class ApiErrorsTest {

    @Test
    public void knownCode_mapsToFriendlyMessage() {
        assertEquals("You don't have enough points.",
                ApiErrors.messageFor("INSUFFICIENT_POINTS", "raw server text"));
    }

    @Test
    public void unknownCode_fallsBackToServerMessage() {
        assertEquals("odd backend error",
                ApiErrors.messageFor("SOME_NEW_CODE", "odd backend error"));
    }

    @Test
    public void nullCode_fallsBackToGeneric() {
        assertEquals("Something went wrong. Please try again.",
                ApiErrors.messageFor(null, null));
    }
}
