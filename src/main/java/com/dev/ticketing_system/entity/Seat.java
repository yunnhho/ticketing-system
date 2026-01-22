package com.dev.ticketing_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id")
    private Concert concert;

    private int seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @Version
    private Long version;

    public Seat(Concert concert, int seatNumber) {
        this.concert = concert;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public enum SeatStatus {
        AVAILABLE, SOLD
    }

    public void markAsSold() {
        if (this.status == SeatStatus.SOLD) {
            throw new IllegalStateException("이미 팔린 좌석입니다.");
        }
        this.status = SeatStatus.SOLD;
    }
}