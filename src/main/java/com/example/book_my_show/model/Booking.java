package com.example.book_my_show.model;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter 
public class Booking {
private final String bookingId;
    private final String userId;
    private final Show show;
    private final List<ShowSeat> seats;
    private final double totalAmount;
    private final LocalDateTime createdAt;
    private BookingStatus status;

    public Booking(String bookingId, String userId, Show show, List<ShowSeat> seats, double totalAmount) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.show = show;
        this.seats = seats;
        this.totalAmount = totalAmount;
        this.createdAt = LocalDateTime.now();
        this.status = BookingStatus.PENDING;
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void expire() {
        this.status = BookingStatus.EXPIRED;
    }
}
