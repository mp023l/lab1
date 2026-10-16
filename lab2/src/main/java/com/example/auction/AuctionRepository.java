package com.example.auction;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
@Repository public class AuctionRepository {
 final Map<Long,Model.User> users=new ConcurrentHashMap<>();
 final Map<Long,Model.Item> items=new ConcurrentHashMap<>();
 final Map<Long,Model.Lot> lots=new ConcurrentHashMap<>();
 final Map<Long,Model.Bid> bids=new ConcurrentHashMap<>();
 final Map<Long,Model.Deal> deals=new ConcurrentHashMap<>();
 private final AtomicLong sequence=new AtomicLong();
 long nextId(){return sequence.incrementAndGet();}
}
