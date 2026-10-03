package com.example.book_my_show.model;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter 
public class Show {

    private final String showId;
    private final String movieId;
    private final Screen screen;
    private final LocalDateTime startTime;
    private final List<ShowSeat> showSeats;

    public Show(String showId, String movieId, Screen screen, LocalDateTime startTime, List<ShowSeat> showSeats) {
        this.showId = showId;
        this.movieId = movieId;
        this.screen = screen;
        this.startTime = startTime;
        this.showSeats = showSeats;
    }
}
