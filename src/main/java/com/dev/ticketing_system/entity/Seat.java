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

    @Version // 낙관적 락을 위한 버전 관리 (데이터 정합성 핵심)
    private Long version;

    @Transient
    private String temporaryStatus;

    public void setTemporaryStatus(String temporaryStatus) {
        this.temporaryStatus = temporaryStatus;
    }

    public String getDisplayStatus() {
        if (this.status == SeatStatus.SOLD) {
            return "SOLD";
        }
        if ("OCCUPIED".equals(this.temporaryStatus)) {
            return "OCCUPIED";
        }
        return "AVAILABLE";
    }

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