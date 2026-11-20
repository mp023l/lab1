package com.example.auction;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
@Component public class AuctionRepository {
 final Map<Long,Model.User> users=new ConcurrentHashMap<>();
 final Map<Long,Model.Item> items=new ConcurrentHashMap<>();
 final Map<Long,Model.Lot> lots=new ConcurrentHashMap<>();
 final Map<Long,Model.Bid> bids=new ConcurrentHashMap<>();
 final Map<Long,Model.Deal> deals=new ConcurrentHashMap<>();
 final Map<Long,Long> lotOwners=new ConcurrentHashMap<>();
 private final AtomicLong sequence=new AtomicLong();
 private final org.springframework.jdbc.core.JdbcTemplate db;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 public record Snapshot(java.util.List<Model.User> users,java.util.List<Model.Item> items,java.util.List<Model.Lot> lots,java.util.List<Model.Bid> bids,java.util.List<Model.Deal> deals,Map<Long,Long> lotOwners,long sequence) {}
 public AuctionRepository(org.springframework.jdbc.core.JdbcTemplate db,com.fasterxml.jackson.databind.ObjectMapper json){
 this.db=db;this.json=json;
 var rows=db.queryForList("select payload from auction_state where id=1",String.class);
 if(!rows.isEmpty())try{var s=json.readValue(rows.getFirst(),Snapshot.class);s.users().forEach(x->users.put(x.id(),x));s.items().forEach(x->items.put(x.id(),x));s.lots().forEach(x->lots.put(x.id(),x));s.bids().forEach(x->bids.put(x.id(),x));s.deals().forEach(x->deals.put(x.id(),x));lotOwners.putAll(s.lotOwners());sequence.set(s.sequence());}catch(Exception e){throw new IllegalStateException("Cannot load auction",e);}
 }
 synchronized void persist(){try{db.update("merge into auction_state (id,payload) key(id) values (1,?)",json.writeValueAsString(new Snapshot(new ArrayList<>(users.values()),new ArrayList<>(items.values()),new ArrayList<>(lots.values()),new ArrayList<>(bids.values()),new ArrayList<>(deals.values()),new HashMap<>(lotOwners),sequence.get())));}catch(Exception e){throw new IllegalStateException("Cannot save auction",e);}}

 long nextId(){return sequence.incrementAndGet();}
}

