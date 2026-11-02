package com.edstem.bookingSlot.exception;

import lombok.Getter;

@Getter
public class BookingOverlapException extends RuntimeException {

    private final Long clashingBookingId;

    public BookingOverlapException(String message, Long clashingBookingId) {
        super(message);
        this.clashingBookingId = clashingBookingId;
    }
}
