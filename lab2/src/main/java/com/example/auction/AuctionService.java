package com.example.auction;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service public class AuctionService {
 private final AuctionRepository r;
 public AuctionService(AuctionRepository r){this.r=r;}
 private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
 private <T> T require(Map<Long,T> map,Long id){T v=map.get(id);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Not found: "+id);return v;}
 private void check(boolean ok,String message){if(!ok)throw bad(message);}
 public synchronized List<?> all(String type){return new ArrayList<>(map(type).values());}
 public synchronized Object get(String type,Long id){return require(map(type),id);}
 private Map<Long,?> map(String t){return switch(t){case "users"->r.users;case "collectible-items"->r.items;case "auction-lots"->r.lots;case "bids"->r.bids;case "auction-deals"->r.deals;default->throw bad("Unknown resource");};}
 private String str(Map<String,Object> v,String key){Object x=v.get(key);check(x!=null&&!x.toString().isBlank(),"Required: "+key);return x.toString();}
 private Long num(Map<String,Object> v,String key){try{return Long.valueOf(str(v,key));}catch(NumberFormatException e){throw bad("Invalid "+key);}}
 private BigDecimal money(Map<String,Object> v,String key){try{return new BigDecimal(str(v,key));}catch(NumberFormatException e){throw bad("Invalid "+key);}}
 private Instant instant(Map<String,Object> v,String key){try{return Instant.parse(str(v,key));}catch(Exception e){throw bad("Invalid ISO-8601 time: "+key);}}
 private Map<String,Object> merged(String type,Long id,Map<String,Object> patch){Object old=get(type,id);Map<String,Object> v=new HashMap<>();if(old instanceof Model.User x){v.put("name",x.name());v.put("email",x.email());}else if(old instanceof Model.Item x){v.put("description",x.description());v.put("ownerId",x.ownerId());v.put("condition",x.condition());}else if(old instanceof Model.Lot x){v.put("itemId",x.itemId());v.put("startPrice",x.startPrice());v.put("step",x.step());v.put("startTime",x.startTime());v.put("endTime",x.endTime());v.put("status",x.status());}else if(old instanceof Model.Bid x){v.put("lotId",x.lotId());v.put("userId",x.userId());v.put("amount",x.amount());}else if(old instanceof Model.Deal x){v.put("lotId",x.lotId());v.put("winningBidId",x.winningBidId());v.put("amount",x.amount());v.put("transferStatus",x.transferStatus());}v.putAll(patch);return v;}
 public synchronized Object save(String type,Long id,Map<String,Object> input){boolean update=id!=null;Map<String,Object> v=update?merged(type,id,input):input;long key=update?id:r.nextId();
 switch(type){
 case "users"->{Model.User x=new Model.User(key,str(v,"name"),str(v,"email"));r.users.put(key,x);return x;}
 case "collectible-items"->{long owner=num(v,"ownerId");require(r.users,owner);Model.Item x=new Model.Item(key,str(v,"description"),owner,str(v,"condition"));r.items.put(key,x);return x;}
  case "auction-lots"->{long item=num(v,"itemId");require(r.items,item);BigDecimal start=money(v,"startPrice"),step=money(v,"step");check(start.signum()>0&&step.signum()>0,"Prices must be positive");Instant a=instant(v,"startTime"),b=instant(v,"endTime");check(b.isAfter(a),"endTime must be after startTime");String status=str(v,"status");check(Set.of("OPEN","CLOSED","CANCELLED").contains(status),"Invalid status");check(r.lots.values().stream().noneMatch(x->x.itemId().equals(item)&&x.id()!=key),"Item already has a lot");if(update)check(r.bids.values().stream().noneMatch(x->x.lotId()==key)||r.lots.get(key).itemId().equals(item),"Cannot change item with bids");Model.Lot x=new Model.Lot(key,item,start,step,a,b,status);r.lots.put(key,x);return x;}
 case "bids"->{long lotId=num(v,"lotId"),userId=num(v,"userId");Model.Lot lot=require(r.lots,lotId);require(r.users,userId);check(!update,"Bids are immutable");Instant now=Instant.now();check(lot.status().equals("OPEN")&&!now.isBefore(lot.startTime())&&now.isBefore(lot.endTime()),"Auction is not active");check(!r.items.get(lot.itemId()).ownerId().equals(userId),"Owner cannot bid");BigDecimal amount=money(v,"amount");BigDecimal highest=r.bids.values().stream().filter(x->x.lotId().equals(lotId)).map(Model.Bid::amount).max(BigDecimal::compareTo).orElse(null);BigDecimal min=highest==null?lot.startPrice():highest.add(lot.step());check(amount.compareTo(min)>=0,"Bid is below minimum: "+min);Model.Bid x=new Model.Bid(key,lotId,userId,amount,now);r.bids.put(key,x);return x;}
 case "auction-deals"->{long lotId=num(v,"lotId"),bidId=num(v,"winningBidId");Model.Lot lot=require(r.lots,lotId);Model.Bid bid=require(r.bids,bidId);check(bid.lotId().equals(lotId),"Bid belongs to another lot");check(lot.status().equals("CLOSED")||!Instant.now().isBefore(lot.endTime()),"Auction not finished");check(r.bids.values().stream().filter(x->x.lotId().equals(lotId)).noneMatch(x->x.amount().compareTo(bid.amount())>0),"Not the highest bid");check(r.deals.values().stream().noneMatch(x->x.lotId().equals(lotId)&&x.id()!=key),"Deal already exists");BigDecimal amount=money(v,"amount");check(amount.compareTo(bid.amount())==0,"Deal amount must match bid");Model.Deal x=new Model.Deal(key,lotId,bidId,amount,str(v,"transferStatus"));r.deals.put(key,x);return x;}
 default->throw bad("Unknown resource");}}
 public synchronized void delete(String type,Long id){get(type,id);switch(type){case "users"->{r.items.values().stream().filter(x->x.ownerId().equals(id)).map(Model.Item::id).toList().forEach(x->delete("collectible-items",x));r.bids.values().removeIf(x->x.userId().equals(id));r.users.remove(id);cleanupDeals();}case "collectible-items"->{r.lots.values().stream().filter(x->x.itemId().equals(id)).map(Model.Lot::id).toList().forEach(x->delete("auction-lots",x));r.items.remove(id);}case "auction-lots"->{r.bids.values().removeIf(x->x.lotId().equals(id));r.deals.values().removeIf(x->x.lotId().equals(id));r.lots.remove(id);}case "bids"->{r.bids.remove(id);cleanupDeals();}case "auction-deals"->r.deals.remove(id);}}
 private void cleanupDeals(){r.deals.values().removeIf(x->!r.bids.containsKey(x.winningBidId()));}
}
