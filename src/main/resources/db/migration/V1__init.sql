CREATE TABLE concerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    total_seats INT NOT NULL
);

CREATE TABLE seats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id BIGINT,
    seat_number INT NOT NULL,
    status VARCHAR(50),
    version BIGINT,
    CONSTRAINT fk_concert FOREIGN KEY (concert_id) REFERENCES concerts(id)
);