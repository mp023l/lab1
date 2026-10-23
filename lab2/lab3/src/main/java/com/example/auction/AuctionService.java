package com.example.auction;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.example.auction.Resource.*;

@Service
@Transactional(readOnly=true)
public class AuctionService {
 private final AuctionRepository db;
 private final Clock clock;
 public AuctionService(AuctionRepository db,Clock clock) { this.db=db; this.clock=clock; }
 private static void check(boolean ok,String message) {
  if(!ok) throw new ResponseStatusException(HttpStatus.CONFLICT,message);
 }
 private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
 static String text(Map<String,Object> m,String key) {
  Object v=m.get(key);
  if(!(v instanceof String s)||s.isBlank()||s.length()>1000) throw bad("Invalid or missing field: "+key);
  return s.trim();
 }
 static long id(Map<String,Object> m,String key) {
  try { long v=new BigDecimal(m.get(key).toString()).longValueExact(); if(v<=0) throw new ArithmeticException(); return v; }
  catch(Exception e) { throw bad("Expected positive integer: "+key); }
 }
 static BigDecimal money(Map<String,Object> m,String key) {
  try { var v=new BigDecimal(m.get(key).toString()).setScale(2,java.math.RoundingMode.UNNECESSARY);
   if(v.signum()<=0||v.precision()>14) throw new ArithmeticException(); return v; }
  catch(Exception e) { throw bad("Expected positive amount with at most two decimal places: "+key); }
 }
 static Instant time(Map<String,Object> m,String key) {
  Object v=m.get(key);
  try { return v instanceof Instant t?t:Instant.parse(v.toString()); }
  catch(Exception e) { throw bad("Expected ISO-8601 timestamp: "+key); }
 }
 private static void fields(Map<String,Object> m,Set<String> allowed) {
  for(String k:m.keySet()) if(!allowed.contains(k)) throw bad("Unknown or read-only field: "+k);
 }
 private static boolean equalsId(Object v,long id) { return v instanceof Number n&&n.longValue()==id; }
 private List<Map<String,Object>> linked(Resource r,String key,long value) {
  return db.all(r).stream().filter(x->equalsId(x.get(key),value)).toList();
 }
 public List<Map<String,Object>> all(String resource) { return db.all(Resource.of(resource)); }
 public Map<String,Object> get(String resource,long id) { return db.get(Resource.of(resource),id); }
 private Map<String,Object> validate(Resource r,Map<String,Object> m) {
  Map<String,Object> v=new LinkedHashMap<>();
  switch(r) {
   case USERS -> {
    String email=text(m,"email").toLowerCase(Locale.ROOT);
    if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254) throw bad("Invalid email");
    v.put("name",text(m,"name")); v.put("email",email);
   }
   case ITEMS -> {
    long owner=id(m,"ownerId"); db.get(USERS,owner); String condition=text(m,"condition");
    if(!Set.of("POOR","FAIR","GOOD","EXCELLENT","MINT").contains(condition)) throw bad("Invalid condition");
    v.put("description",text(m,"description")); v.put("ownerId",owner); v.put("condition",condition);
   }
   case LOTS -> {
    long item=id(m,"itemId"); db.get(ITEMS,item);
    Instant start=time(m,"startTime"),end=time(m,"endTime");
    if(!end.isAfter(start)) throw bad("endTime must be after startTime");
    String status=m.containsKey("status")?text(m,"status"):"DRAFT";
    check(status.equals("DRAFT"),"Create/edit a DRAFT, then publish using the business operation");
    v.put("itemId",item); v.put("startPrice",money(m,"startPrice")); v.put("step",money(m,"step"));
    v.put("startTime",start); v.put("endTime",end); v.put("status",status);
   }
   default -> throw bad("Use a business operation");
  }
  return v;
 }
 @Transactional
 public Map<String,Object> create(String resource,Map<String,Object> input) {
  Resource r=Resource.of(resource);
  // Time is server-owned even for the compatibility POST /bids endpoint.
  fields(input,r==BIDS?Set.of("lotId","userId","amount"):r.columns.keySet()); db.lock();
  if(r==BIDS) return bid(id(input,"lotId"),input);
  if(r==DEALS) {
   var result=close(id(input,"lotId"));
   check(result.containsKey("deal"),"No winning bid: no deal can be created");
   @SuppressWarnings("unchecked") var deal=(Map<String,Object>)result.get("deal");
   if(input.containsKey("winningBidId")) check(id(input,"winningBidId")==id(deal,"winningBidId"),"Winner is determined by the server");
   if(input.containsKey("amount")) check(money(input,"amount").compareTo(money(deal,"amount"))==0,"Amount is determined by the server");
   if(input.containsKey("transferStatus")) check(text(input,"transferStatus").equals("PENDING"),"Deal must start PENDING");
   return deal;
  }
  return db.insert(r,validate(r,input));
 }
 @Transactional
 public Map<String,Object> update(String resource,long key,Map<String,Object> input,boolean replace) {
  Resource r=Resource.of(resource); fields(input,r.columns.keySet()); db.lock(); var old=db.get(r,key);
  check(r!=BIDS&&r!=DEALS,"Bids and deals are immutable history; use business operations");
  if(r==LOTS) check(old.get("status").equals("DRAFT"),"Only drafts can be edited");
  if(r==ITEMS) {
   check(!input.containsKey("ownerId")||id(input,"ownerId")==id(old,"ownerId"),"Ownership changes only through completed deals");
   check(linked(LOTS,"itemId",key).stream().noneMatch(x->Set.of("OPEN","CLOSED").contains(x.get("status"))),"Item is reserved by an auction or pending deal");
  }
  Map<String,Object> merged=new LinkedHashMap<>();
  if(!replace) { merged.putAll(old); merged.remove("id"); } merged.putAll(input);
  return db.update(r,key,validate(r,merged));
 }
 @Transactional
 public void delete(String resource,long key) {
  Resource r=Resource.of(resource); db.lock(); var row=db.get(r,key);
  check(r!=BIDS&&r!=DEALS,"Auction history cannot be deleted");
  if(r==LOTS) check(row.get("status").equals("DRAFT"),"Only unused draft lots can be deleted");
  db.delete(r,key);
 }
 @Transactional
 public Map<String,Object> publish(long lotId) {
  db.lock(); var lot=db.get(LOTS,lotId);
  check(lot.get("status").equals("DRAFT"),"Only a draft can be published");
  check(time(lot,"endTime").isAfter(clock.instant()),"Lot has already expired");
  long item=id(lot,"itemId");
  check(linked(LOTS,"itemId",item).stream().noneMatch(x->Set.of("OPEN","CLOSED").contains(x.get("status"))),"Item already reserved by another lot");
  return db.update(LOTS,lotId,Map.of("status","OPEN"));
 }
 @Transactional
 public Map<String,Object> bid(long lotId,Map<String,Object> input) {
  fields(input,Set.of("lotId","userId","amount"));
  if(input.containsKey("lotId")) check(id(input,"lotId")==lotId,"Conflicting lotId");
  db.lock(); var lot=db.get(LOTS,lotId); long user=id(input,"userId"); db.get(USERS,user);
  Instant now=clock.instant();
  check(lot.get("status").equals("OPEN")&&!now.isBefore(time(lot,"startTime"))&&now.isBefore(time(lot,"endTime")),"Auction is not active");
  check(id(db.get(ITEMS,id(lot,"itemId")),"ownerId")!=user,"Owner cannot bid");
  var highest=linked(BIDS,"lotId",lotId).stream().map(x->money(x,"amount")).max(BigDecimal::compareTo);
  BigDecimal minimum=highest.map(x->x.add(money(lot,"step"))).orElse(money(lot,"startPrice"));
  BigDecimal amount=money(input,"amount"); check(amount.compareTo(minimum)>=0,"Minimum bid: "+minimum);
  return db.insert(BIDS,Map.of("lotId",lotId,"userId",user,"amount",amount,"time",now));
 }
 @Transactional
 public Map<String,Object> close(long lotId) {
  db.lock(); var lot=db.get(LOTS,lotId);
  check(lot.get("status").equals("OPEN"),"Only an open lot can be closed");
  check(!clock.instant().isBefore(time(lot,"endTime")),"Wait until auction end time");
  var winner=linked(BIDS,"lotId",lotId).stream().max(Comparator.comparing(x->money(x,"amount")));
  if(winner.isEmpty()) return Map.of("lot",db.update(LOTS,lotId,Map.of("status","UNSOLD")));
  var b=winner.get();
  var deal=db.insert(DEALS,Map.of("lotId",lotId,"winningBidId",id(b,"id"),"amount",money(b,"amount"),"transferStatus","PENDING"));
  return Map.of("lot",db.update(LOTS,lotId,Map.of("status","CLOSED")),"deal",deal);
 }
 @Transactional
 public Map<String,Object> cancel(long lotId) {
  db.lock(); var lot=db.get(LOTS,lotId);
  check(Set.of("DRAFT","OPEN").contains(lot.get("status")),"Lot cannot be cancelled in current state");
  check(linked(BIDS,"lotId",lotId).isEmpty(),"Cannot cancel an auction with bids");
  return db.update(LOTS,lotId,Map.of("status","CANCELLED"));
 }
 @Transactional
 public Map<String,Object> complete(long dealId) {
  db.lock(); var deal=db.get(DEALS,dealId);
  check(deal.get("transferStatus").equals("PENDING"),"Deal already completed");
  var lot=db.get(LOTS,id(deal,"lotId")); check(lot.get("status").equals("CLOSED"),"Lot must be closed");
  var winner=db.get(BIDS,id(deal,"winningBidId"));
  var item=db.update(ITEMS,id(lot,"itemId"),Map.of("ownerId",id(winner,"userId")));
  db.update(LOTS,id(lot,"id"),Map.of("status","SETTLED"));
  return Map.of("deal",db.update(DEALS,dealId,Map.of("transferStatus","COMPLETED")),"item",item);
 }
}
