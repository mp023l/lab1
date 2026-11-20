package com.example.auction;
import java.math.BigDecimal;
import java.time.Instant;
public class Model {
 public record User(Long id, String name, String email) {}
 public record Item(Long id, String description, Long ownerId, String condition) {}
 public record Lot(Long id, Long itemId, BigDecimal startPrice, BigDecimal step, Instant startTime, Instant endTime, String status) {}
 public record Bid(Long id, Long lotId, Long userId, BigDecimal amount, Instant time) {}
 public record Deal(Long id, Long lotId, Long winningBidId, BigDecimal amount, String transferStatus) {}
}
