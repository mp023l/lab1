package com.example.auction;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Service public class AuctionService {
 private final AuctionRepository r;
 @org.springframework.beans.factory.annotation.Autowired private Access access;
 @org.springframework.beans.factory.annotation.Autowired private Accounts accounts;
 public AuctionService(AuctionRepository r){this.r=r;}
 private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
 private <T> T require(Map<Long,T> map,Long id){T v=map.get(id);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Not found: "+id);return v;}
 private void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
 public synchronized List<?> all(String type){if(type.equals("users")&&!access.admin())return List.of(r.users.get(access.current().id()));if(type.equals("auction-deals")&&!access.admin())return r.deals.values().stream().filter(access::participant).toList();return new ArrayList<>(map(type).values());}
 @org.springframework.security.access.prepost.PreAuthorize("@access.read(#type,#id)")
 public synchronized Object get(String type,Long id){if(!access.read(type,id))access.denied();return require(map(type),id);}
 private Map<Long,?> map(String t){return switch(t){case "users"->r.users;case "collectible-items"->r.items;case "auction-lots"->r.lots;case "bids"->r.bids;case "auction-deals"->r.deals;default->throw bad("Unknown resource");};}
 private String str(Map<String,Object> v,String key){Object x=v.get(key);if(x==null||x.toString().isBlank())throw bad("Required: "+key);return x.toString();}
 private Long num(Map<String,Object> v,String key){try{return Long.valueOf(str(v,key));}catch(NumberFormatException e){throw bad("Invalid "+key);}}
 private BigDecimal money(Map<String,Object> v,String key){try{return new BigDecimal(str(v,key));}catch(NumberFormatException e){throw bad("Invalid "+key);}}
 private Instant instant(Map<String,Object> v,String key){try{return Instant.parse(str(v,key));}catch(Exception e){throw bad("Invalid ISO-8601 time: "+key);}}
 private Map<String,Object> merged(String type,Long id,Map<String,Object> patch){Object old=get(type,id);Map<String,Object> v=new HashMap<>();if(old instanceof Model.User x){v.put("name",x.name());v.put("email",x.email());}else if(old instanceof Model.Item x){v.put("description",x.description());v.put("ownerId",x.ownerId());v.put("condition",x.condition());}else if(old instanceof Model.Lot x){v.put("itemId",x.itemId());v.put("startPrice",x.startPrice());v.put("step",x.step());v.put("startTime",x.startTime());v.put("endTime",x.endTime());v.put("status",x.status());}else if(old instanceof Model.Bid x){v.put("lotId",x.lotId());v.put("userId",x.userId());v.put("amount",x.amount());}else if(old instanceof Model.Deal x){v.put("lotId",x.lotId());v.put("winningBidId",x.winningBidId());v.put("amount",x.amount());v.put("transferStatus",x.transferStatus());}v.putAll(patch);return v;}
 @org.springframework.security.access.prepost.PreAuthorize("@access.write(#type,#id,#input)")
 public synchronized Object save(String type,Long id,Map<String,Object> input){
 if(!access.write(type,id,input))access.denied();
 input=new HashMap<>(input);
 if(type.equals("bids"))input.put("userId",access.current().id());
 if(type.equals("collectible-items")&&id==null)input.putIfAbsent("ownerId",access.current().id());
 Object result=saveInternal(type,id,input);r.persist();return result;
 }
 private Object saveInternal(String type,Long id,Map<String,Object> input){boolean update=id!=null;Map<String,Object> v=update?merged(type,id,input):input;long key=update?id:r.nextId();
 switch(type){
 case "users"->{String name=str(v,"name"),email=str(v,"email");accounts.email(key,email);Model.User x=new Model.User(key,name,Accounts.canonical(email));r.users.put(key,x);return x;}
 case "collectible-items"->{if(update)check(r.lots.values().stream().noneMatch(l->l.itemId().equals(id)&&l.status().equals("OPEN")),"Cannot edit item in open auction");long owner=num(v,"ownerId");require(r.users,owner);Model.Item x=new Model.Item(key,str(v,"description"),owner,str(v,"condition"));r.items.put(key,x);return x;}
  case "auction-lots"->{long item=num(v,"itemId");require(r.items,item);BigDecimal start=money(v,"startPrice"),step=money(v,"step");check(start.signum()>0&&step.signum()>0,"Prices must be positive");Instant a=instant(v,"startTime"),b=instant(v,"endTime");check(b.isAfter(a),"endTime must be after startTime");String status=str(v,"status");if(update){var old=r.lots.get(key);check(old.status().equals("OPEN")||old.equals(new Model.Lot(key,item,start,step,a,b,status)),"Finished lot is immutable");if(r.bids.values().stream().anyMatch(x->x.lotId()==key))check(old.itemId().equals(item)&&old.startPrice().compareTo(start)==0&&old.step().compareTo(step)==0&&old.startTime().equals(a)&&old.endTime().equals(b),"Cannot change auction terms after bids");}check(Set.of("OPEN","CLOSED","CANCELLED").contains(status),"Invalid status");check(r.lots.values().stream().noneMatch(x->x.itemId().equals(item)&&x.id()!=key),"Item already has a lot");if(update)check(r.bids.values().stream().noneMatch(x->x.lotId()==key)||r.lots.get(key).itemId().equals(item),"Cannot change item with bids");Model.Lot x=new Model.Lot(key,item,start,step,a,b,status);r.lots.put(key,x);if(!update)r.lotOwners.put(key,r.items.get(item).ownerId());return x;}
 case "bids"->{long lotId=num(v,"lotId"),userId=num(v,"userId");Model.Lot lot=require(r.lots,lotId);require(r.users,userId);check(!update,"Bids are immutable");Instant now=Instant.now();check(lot.status().equals("OPEN")&&!now.isBefore(lot.startTime())&&now.isBefore(lot.endTime()),"Auction is not active");check(!r.items.get(lot.itemId()).ownerId().equals(userId),"Owner cannot bid");BigDecimal amount=money(v,"amount");BigDecimal highest=r.bids.values().stream().filter(x->x.lotId().equals(lotId)).map(Model.Bid::amount).max(BigDecimal::compareTo).orElse(null);BigDecimal min=highest==null?lot.startPrice():highest.add(lot.step());check(amount.compareTo(min)>=0,"Bid is below minimum: "+min);Model.Bid x=new Model.Bid(key,lotId,userId,amount,now);r.bids.put(key,x);return x;}
 case "auction-deals"->{long lotId=num(v,"lotId"),bidId=num(v,"winningBidId");Model.Lot lot=require(r.lots,lotId);Model.Bid bid=require(r.bids,bidId);check(bid.lotId().equals(lotId),"Bid belongs to another lot");check(!lot.status().equals("CANCELLED")&&(lot.status().equals("CLOSED")||!Instant.now().isBefore(lot.endTime())),"Auction not finished");check(r.bids.values().stream().filter(x->x.lotId().equals(lotId)).noneMatch(x->x.amount().compareTo(bid.amount())>0),"Not the highest bid");check(r.deals.values().stream().noneMatch(x->x.lotId().equals(lotId)&&x.id()!=key),"Deal already exists");BigDecimal amount=money(v,"amount");check(amount.compareTo(bid.amount())==0,"Deal amount must match bid");String transfer=str(v,"transferStatus");check(Set.of("PENDING","COMPLETED").contains(transfer),"Invalid transfer status");if(update){var old=r.deals.get(key);check(old.lotId().equals(lotId)&&old.winningBidId().equals(bidId)&&old.amount().compareTo(amount)==0,"Deal terms are immutable");check(!old.transferStatus().equals("COMPLETED")||transfer.equals("COMPLETED"),"Transfer already completed");}
 if(transfer.equals("COMPLETED")){var item=r.items.get(lot.itemId());r.items.put(item.id(),new Model.Item(item.id(),item.description(),bid.userId(),item.condition()));}r.lots.put(lot.id(),new Model.Lot(lot.id(),lot.itemId(),lot.startPrice(),lot.step(),lot.startTime(),lot.endTime(),"CLOSED"));
 Model.Deal x=new Model.Deal(key,lotId,bidId,amount,transfer);r.deals.put(key,x);return x;}
 default->throw bad("Unknown resource");}}
 @org.springframework.security.access.prepost.PreAuthorize("@access.delete(#type,#id)")
 public synchronized void delete(String type,Long id){deleteInternal(type,id);r.persist();}
 private void deleteInternal(String type,Long id){get(type,id);switch(type){case "users"->{r.items.values().stream().filter(x->x.ownerId().equals(id)).map(Model.Item::id).toList().forEach(x->deleteInternal("collectible-items",x));r.bids.values().removeIf(x->x.userId().equals(id));r.users.remove(id);accounts.remove(id);cleanupDeals();}case "collectible-items"->{r.lots.values().stream().filter(x->x.itemId().equals(id)).map(Model.Lot::id).toList().forEach(x->deleteInternal("auction-lots",x));r.items.remove(id);}case "auction-lots"->{r.bids.values().removeIf(x->x.lotId().equals(id));r.deals.values().removeIf(x->x.lotId().equals(id));r.lots.remove(id);r.lotOwners.remove(id);}case "bids"->{r.bids.remove(id);cleanupDeals();}case "auction-deals"->r.deals.remove(id);}}
 private void cleanupDeals(){r.deals.values().removeIf(x->!r.bids.containsKey(x.winningBidId()));}
}
