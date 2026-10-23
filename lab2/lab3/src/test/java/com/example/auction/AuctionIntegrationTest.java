package com.example.auction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:auction;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa",
 "spring.datasource.password=",
 "spring.sql.init.mode=never", "auction.local-db=false"
})
@org.springframework.test.context.TestExecutionListeners(listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuctionIntegrationTest {
 @Autowired AuctionService service;
 @Autowired JdbcTemplate jdbc;
 @Autowired MutableClock clock;
 @Autowired MockMvc mvc;
 long seller,buyer,other,item,lot;
 @TestConfiguration
 static class TestClockConfiguration {
  @Bean @Primary MutableClock testClock() { return new MutableClock(); }
 }
 static class MutableClock extends Clock {
  private volatile Instant now=Instant.parse("2030-01-01T12:00:00Z");
  void set(Instant value) { now=value; }
  @Override public ZoneId getZone() { return ZoneOffset.UTC; }
  @Override public Clock withZone(ZoneId zone) { return this; }
  @Override public Instant instant() { return now; }
 }
 @BeforeAll void schema() throws Exception {
  String sql=new ClassPathResource("db/h2-schema.sql").getContentAsString(StandardCharsets.UTF_8);
  for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
 }
 @BeforeEach void fixtures() {
  for(String table:List.of("auction_deals","bids","auction_lots","collectible_items","app_users")) jdbc.update("DELETE FROM "+table);
  clock.set(Instant.parse("2030-01-01T12:00:00Z"));
  seller=key(service.create("users",Map.of("name","Seller","email","seller@test.org")));
  buyer=key(service.create("users",Map.of("name","Buyer","email","buyer@test.org")));
  other=key(service.create("users",Map.of("name","Other","email","other@test.org")));
  item=key(service.create("collectible-items",Map.of("ownerId",seller,"description","Coin","condition","GOOD")));
  lot=key(draft(item));
 }
 long key(Map<String,Object> row) { return ((Number)row.get("id")).longValue(); }
 Map<String,Object> draft(long itemId) {
  return service.create("auction-lots",Map.of("itemId",itemId,"startPrice",100,"step",10,
   "startTime",clock.instant().minusSeconds(1).toString(),"endTime",clock.instant().plusSeconds(60).toString()));
 }
 Map<String,Object> bid(long user,int amount) { return service.bid(lot,Map.of("userId",user,"amount",amount)); }
 void expired() { clock.set(clock.instant().plusSeconds(60)); }
 @Test void fullLifecycleTransfersToHighestBidderAndPreservesHistory() {
  service.publish(lot); bid(buyer,100); var winning=bid(other,110); expired();
  var closed=service.close(lot);
  @SuppressWarnings("unchecked") var deal=(Map<String,Object>)closed.get("deal");
  assertEquals(key(winning),((Number)deal.get("winningBidId")).longValue());
  assertEquals(seller,((Number)service.get("collectible-items",item).get("ownerId")).longValue());
  service.complete(key(deal));
  assertEquals(other,((Number)service.get("collectible-items",item).get("ownerId")).longValue());
  assertEquals("SETTLED",service.get("auction-lots",lot).get("status"));
  assertEquals(2,service.all("bids").size());
  assertThrows(ResponseStatusException.class,()->service.complete(key(deal)));
 }
 @Test void ownerLowEarlyAndExpiredBidsAreRejected() {
  assertThrows(ResponseStatusException.class,()->bid(buyer,100));
  service.publish(lot);
  assertThrows(ResponseStatusException.class,()->bid(seller,100));
  assertThrows(ResponseStatusException.class,()->bid(buyer,99));
  bid(buyer,100);
  assertThrows(ResponseStatusException.class,()->bid(other,109));
  assertThrows(ResponseStatusException.class,()->service.close(lot));
  expired(); assertThrows(ResponseStatusException.class,()->bid(other,120));
  assertEquals(1,service.all("bids").size());
 }
 @Test void concurrentEqualBidsAcceptExactlyOne() throws Exception {
  service.publish(lot); var start=new CountDownLatch(1);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try {
   Callable<Boolean> task=()->{ start.await(); try { bid(buyer,100); return true; } catch(ResponseStatusException e) { return false; } };
   Future<Boolean> a=pool.submit(task),b=pool.submit(task); start.countDown();
   assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
   assertEquals(1,service.all("bids").size());
  } finally { pool.shutdownNow(); }
 }
 @Test void cancellationChecksCurrentStateAndExistingBids() {
  service.publish(lot); service.cancel(lot);
  assertEquals("CANCELLED",service.get("auction-lots",lot).get("status"));
  assertThrows(ResponseStatusException.class,()->bid(buyer,100));
  long another=key(draft(item)); service.publish(another);
  service.bid(another,Map.of("userId",buyer,"amount",100));
  assertThrows(ResponseStatusException.class,()->service.cancel(another));
 }
 @Test void unsoldLotCreatesNoDealAndAllowsRelisting() {
  service.publish(lot); expired(); service.close(lot);
  assertEquals("UNSOLD",service.get("auction-lots",lot).get("status"));
  assertTrue(service.all("auction-deals").isEmpty());
  service.publish(key(draft(item)));
 }
 @Test void activeAndPendingLotsReserveItem() {
  long second=key(draft(item)); service.publish(lot);
  assertThrows(ResponseStatusException.class,()->service.publish(second));
  bid(buyer,100); expired(); service.close(lot);
  assertThrows(ResponseStatusException.class,()->service.publish(key(draft(item))));
  assertThrows(ResponseStatusException.class,()->service.update("collectible-items",item,Map.of("ownerId",buyer),false));
 }
 @Test void foreignKeysAndUniqueEmailPreventInvalidCrud() {
  assertThrows(DataIntegrityViolationException.class,()->service.create("users",Map.of("name","Duplicate","email","SELLER@test.org")));
  assertThrows(DataIntegrityViolationException.class,()->service.delete("users",seller));
  assertThrows(ResponseStatusException.class,()->service.create("collectible-items",Map.of("ownerId",999999,"description","x","condition","GOOD")));
  assertThrows(ResponseStatusException.class,()->service.update("users",buyer,Map.of("name","Only name"),true));
 }
 @Test void invalidCompatibilityDealRollsBackClosure() {
  service.publish(lot); bid(buyer,100); expired();
  assertThrows(ResponseStatusException.class,()->service.create("auction-deals",Map.of("lotId",lot,"amount",999)));
  assertEquals("OPEN",service.get("auction-lots",lot).get("status"));
  assertTrue(service.all("auction-deals").isEmpty());
  service.create("auction-deals",Map.of("lotId",lot,"amount",100));
  assertEquals("CLOSED",service.get("auction-lots",lot).get("status"));
 }
 @Test void historyAndStatusCannotBeChangedByCrud() {
  service.publish(lot); var b=bid(buyer,100);
  assertThrows(ResponseStatusException.class,()->service.update("auction-lots",lot,Map.of("status","CLOSED"),false));
  assertThrows(ResponseStatusException.class,()->service.update("bids",key(b),Map.of("amount",1),false));
  assertThrows(ResponseStatusException.class,()->service.delete("bids",key(b)));
  assertThrows(DataIntegrityViolationException.class,()->service.delete("collectible-items",item));
 }
 @Test void completionRollsBackIfFinalDatabaseWriteFails() {
  service.publish(lot); bid(buyer,100); expired(); var closed=service.close(lot);
  @SuppressWarnings("unchecked") var deal=(Map<String,Object>)closed.get("deal");
  jdbc.execute("ALTER TABLE auction_deals ADD CONSTRAINT test_reject_completion CHECK (transfer_status='PENDING')");
  try {
   assertThrows(DataIntegrityViolationException.class,()->service.complete(key(deal)));
   assertEquals(seller,((Number)service.get("collectible-items",item).get("ownerId")).longValue());
   assertEquals("CLOSED",service.get("auction-lots",lot).get("status"));
   assertEquals("PENDING",service.get("auction-deals",key(deal)).get("transferStatus"));
  } finally { jdbc.execute("ALTER TABLE auction_deals DROP CONSTRAINT test_reject_completion"); }
 }
 @Test void httpContractAndBadInputs() throws Exception {
  mvc.perform(get("/api/users")).andExpect(status().isOk());
  mvc.perform(get("/api/users/999999")).andExpect(status().isNotFound());
  mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
  mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
  mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{broken"))
   .andExpect(status().isBadRequest());
  mvc.perform(post("/api/auction-lots/"+lot+"/publish")).andExpect(status().isOk());
  mvc.perform(post("/api/bids").contentType(MediaType.APPLICATION_JSON)
   .content("{\"lotId\":"+lot+",\"userId\":"+buyer+",\"amount\":100}"))
   .andExpect(status().isCreated()).andExpect(header().exists("Location"));
  mvc.perform(delete("/api/users/"+seller)).andExpect(status().isConflict());
 }
}
