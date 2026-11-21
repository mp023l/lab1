
package com.example.auction;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class AuctionService {

 private final AuctionRepository r;

 public AuctionService(AuctionRepository r) {
  this.r = r;
 }

 private ResponseStatusException bad(String message) {
  return new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message);
 }

 private void check(boolean condition, String message) {
  if (!condition) {
   throw bad(message);
  }
 }

 private <T> T require(Map<Long, T> map, Long id) {
  T value = map.get(id);

  if (value == null) {
   throw new ResponseStatusException(
           HttpStatus.NOT_FOUND,
           "Not found: " + id);
  }

  return value;
 }

 private Map<Long, ?> map(String type) {
  return switch (type) {
   case "users" -> r.users;
   case "collectible-items" -> r.items;
   case "auction-lots" -> r.lots;
   case "bids" -> r.bids;
   case "auction-deals" -> r.deals;
   default -> throw bad("Unknown resource");
  };
 }

 // GET: all records
 public synchronized List<?> all(String type) {
  return new ArrayList<>(map(type).values());
 }

 // GET: record by ID
 public synchronized Object get(String type, Long id) {
  return require(map(type), id);
 }

 private String str(Map<String, Object> data, String key) {
  Object value = data.get(key);

  check(value != null && !value.toString().isBlank(),
          "Required: " + key);

  return value.toString();
 }

 private Long num(Map<String, Object> data, String key) {
  try {
   return Long.valueOf(str(data, key));
  } catch (NumberFormatException e) {
   throw bad("Invalid " + key);
  }
 }

 private BigDecimal money(
         Map<String, Object> data, String key) {
  try {
   return new BigDecimal(str(data, key));
  } catch (NumberFormatException e) {
   throw bad("Invalid " + key);
  }
 }

 private Instant instant(
         Map<String, Object> data, String key) {
  try {
   return Instant.parse(str(data, key));
  } catch (Exception e) {
   throw bad("Invalid ISO-8601 time: " + key);
  }
 }

 // Combine existing values with PATCH fields
 private Map<String, Object> merged(
         String type,
         Long id,
         Map<String, Object> patch) {

  Object old = get(type, id);
  Map<String, Object> data = new HashMap<>();

  if (old instanceof Model.User x) {
   data.put("name", x.name());
   data.put("email", x.email());

  } else if (old instanceof Model.Item x) {
   data.put("description", x.description());
   data.put("ownerId", x.ownerId());
   data.put("condition", x.condition());

  } else if (old instanceof Model.Lot x) {
   data.put("itemId", x.itemId());
   data.put("startPrice", x.startPrice());
   data.put("step", x.step());
   data.put("startTime", x.startTime());
   data.put("endTime", x.endTime());
   data.put("status", x.status());

  } else if (old instanceof Model.Bid x) {
   data.put("lotId", x.lotId());
   data.put("userId", x.userId());
   data.put("amount", x.amount());

  } else if (old instanceof Model.Deal x) {
   data.put("lotId", x.lotId());
   data.put("winningBidId", x.winningBidId());
   data.put("amount", x.amount());
   data.put("transferStatus", x.transferStatus());
  }

  data.putAll(patch);
  return data;
 }

 // POST and PATCH
 public synchronized Object save(
         String type,
         Long id,
         Map<String, Object> input) {

  boolean update = id != null;

  Map<String, Object> data =
          update ? merged(type, id, input) : input;

  long key = update ? id : r.nextId();

  switch (type) {

   // USERS
   case "users" -> {
    Model.User user = new Model.User(
            key,
            str(data, "name"),
            str(data, "email")
    );

    r.users.put(key, user);
    return user;
   }

   // COLLECTIBLE ITEMS
   case "collectible-items" -> {
    long ownerId = num(data, "ownerId");
    require(r.users, ownerId);

    Model.Item item = new Model.Item(
            key,
            str(data, "description"),
            ownerId,
            str(data, "condition")
    );

    r.items.put(key, item);
    return item;
   }

   // AUCTION LOTS
   case "auction-lots" -> {
    long itemId = num(data, "itemId");
    require(r.items, itemId);

    BigDecimal startPrice =
            money(data, "startPrice");

    BigDecimal step =
            money(data, "step");

    check(startPrice.signum() > 0,
            "Start price must be positive");

    check(step.signum() > 0,
            "Step must be positive");

    Instant startTime =
            instant(data, "startTime");

    Instant endTime =
            instant(data, "endTime");

    check(endTime.isAfter(startTime),
            "End time must be after start time");

    String status = str(data, "status");

    check(
            Set.of("OPEN", "CLOSED", "CANCELLED")
                    .contains(status),
            "Invalid status"
    );

    boolean duplicate = r.lots.values().stream()
            .anyMatch(x ->
                    x.itemId().equals(itemId)
                            && x.id() != key);

    check(!duplicate,
            "Item already has a lot");

    if (update) {
     Model.Lot oldLot =
             require(r.lots, key);

     boolean hasBids = r.bids.values().stream()
             .anyMatch(x -> x.lotId() == key);

     check(!hasBids
                     || oldLot.itemId().equals(itemId),
             "Cannot change item with bids");
    }

    Model.Lot lot = new Model.Lot(
            key,
            itemId,
            startPrice,
            step,
            startTime,
            endTime,
            status
    );

    r.lots.put(key, lot);
    return lot;
   }

   // BIDS
   case "bids" -> {
    long lotId = num(data, "lotId");
    long userId = num(data, "userId");

    Model.Lot lot =
            require(r.lots, lotId);

    require(r.users, userId);

    Instant now = Instant.now();

    check(
            lot.status().equals("OPEN")
                    && !now.isBefore(lot.startTime())
                    && now.isBefore(lot.endTime()),
            "Auction is not active"
    );

    Model.Item item =
            require(r.items, lot.itemId());

    check(!item.ownerId().equals(userId),
            "Owner cannot bid");

    BigDecimal amount =
            money(data, "amount");

    check(amount.signum() > 0,
            "Bid must be positive");

    Instant bidTime;

    if (update) {
     Model.Bid oldBid =
             require(r.bids, key);

     check(oldBid.lotId().equals(lotId),
             "Cannot change bid lot");

     check(oldBid.userId().equals(userId),
             "Cannot change bid user");

     check(r.deals.values().stream()
                     .noneMatch(x ->
                             x.winningBidId().equals(key)),
             "Winning bid belongs to a deal");

     bidTime = oldBid.time();

    } else {
     bidTime = now;
    }

    BigDecimal highest = r.bids.values().stream()
            .filter(x -> x.lotId().equals(lotId))
            .filter(x -> x.id() != key)
            .map(Model.Bid::amount)
            .max(BigDecimal::compareTo)
            .orElse(null);

    BigDecimal minimum = highest == null
            ? lot.startPrice()
            : highest.add(lot.step());

    check(amount.compareTo(minimum) >= 0,
            "Bid is below minimum: " + minimum);

    Model.Bid bid = new Model.Bid(
            key,
            lotId,
            userId,
            amount,
            bidTime
    );

    r.bids.put(key, bid);
    return bid;
   }

   // AUCTION DEALS
   case "auction-deals" -> {
    long lotId = num(data, "lotId");
    long bidId = num(data, "winningBidId");

    Model.Lot lot =
            require(r.lots, lotId);

    Model.Bid bid =
            require(r.bids, bidId);

    check(bid.lotId().equals(lotId),
            "Bid belongs to another lot");

    check(
            lot.status().equals("CLOSED")
                    || !Instant.now().isBefore(lot.endTime()),
            "Auction not finished"
    );

    boolean higherBid = r.bids.values().stream()
            .filter(x -> x.lotId().equals(lotId))
            .anyMatch(x ->
                    x.amount().compareTo(bid.amount()) > 0);

    check(!higherBid,
            "Not the highest bid");

    boolean duplicate = r.deals.values().stream()
            .anyMatch(x ->
                    x.lotId().equals(lotId)
                            && x.id() != key);

    check(!duplicate,
            "Deal already exists");

    BigDecimal amount =
            money(data, "amount");

    check(amount.compareTo(bid.amount()) == 0,
            "Deal amount must match bid");

    Model.Deal deal = new Model.Deal(
            key,
            lotId,
            bidId,
            amount,
            str(data, "transferStatus")
    );

    r.deals.put(key, deal);
    return deal;
   }

   default -> throw bad("Unknown resource");
  }
 }

 // DELETE
 public synchronized void delete(String type, Long id) {
  get(type, id);

  switch (type) {

   case "users" -> {
    r.items.values().stream()
            .filter(x -> x.ownerId().equals(id))
            .map(Model.Item::id)
            .toList()
            .forEach(x ->
                    delete("collectible-items", x));

    r.bids.values().removeIf(
            x -> x.userId().equals(id));

    r.users.remove(id);
    cleanupDeals();
   }

   case "collectible-items" -> {
    r.lots.values().stream()
            .filter(x -> x.itemId().equals(id))
            .map(Model.Lot::id)
            .toList()
            .forEach(x ->
                    delete("auction-lots", x));

    r.items.remove(id);
   }

   case "auction-lots" -> {
    r.bids.values().removeIf(
            x -> x.lotId().equals(id));

    r.deals.values().removeIf(
            x -> x.lotId().equals(id));

    r.lots.remove(id);
   }

   case "bids" -> {
    r.bids.remove(id);
    cleanupDeals();
   }

   case "auction-deals" -> {
    r.deals.remove(id);
   }

   default -> throw bad("Unknown resource");
  }
 }

 private void cleanupDeals() {
  r.deals.values().removeIf(
          x -> !r.bids.containsKey(x.winningBidId()));
 }
}
