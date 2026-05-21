package com.hotel.concierge.repository;

import com.hotel.concierge.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    List<Hotel> findByActiveTrue();
    Hotel findByName(String name);
}
