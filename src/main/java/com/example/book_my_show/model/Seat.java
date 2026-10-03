package com.example.book_my_show.model;

import lombok.Getter;

@Getter 
public class Seat {

    private final String seatId;
    private final String row;
    private final int number;
    private final SeatCategory category;

    public Seat(String seatId, String row, int number, SeatCategory category) {
        this.seatId = seatId;
        this.row = row;
        this.number = number;
        this.category = category;
    }
}
