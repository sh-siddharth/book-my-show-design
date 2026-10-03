package com.example.book_my_show.model;

import java.util.List;

import lombok.Getter;

@Getter 
public class Screen {

    private final String screenId;
    private final String name;
    private final List<Seat> seats;

    public Screen(String screenId, String name, List<Seat> seats) {
        this.screenId = screenId;
        this.name = name;
        this.seats = seats;
    }

}
