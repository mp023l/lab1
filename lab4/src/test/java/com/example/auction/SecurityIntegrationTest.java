package com.example.auction;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1","auction.admin-password=AdminTest123!"})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired Accounts accounts;
    @Autowired AuctionRepository repo; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder;
    final String password="Password123!";
    long seller,buyer,outsider;
    record Client(String name,String password,MockHttpSession session,String token){}
    Client a,b,c,admin;
    @BeforeAll void setup(){
        seller=accounts.register(new Accounts.Registration("seller","Max","max@example.com",password)).id();
        buyer=accounts.register(new Accounts.Registration("buyer","Lewis","lewis@example.com",password)).id();
        outsider=accounts.register(new Accounts.Registration("other","Charles","charles@example.com",password)).id();
    }
    @BeforeEach void reset() throws Exception {
        repo.items.clear();repo.lots.clear();repo.bids.clear();repo.deals.clear();repo.lotOwners.clear();repo.persist();
        db.update("update accounts set state='ACTIVE'");
        a=client("seller",password);b=client("buyer",password);c=client("other",password);admin=client("admin","AdminTest123!");
    }
    Client client(String name,String pass) throws Exception {
        var result=mvc.perform(get("/api/security/csrf").with(httpBasic(name,pass))).andReturn();
        assertEquals(200,result.getResponse().getStatus());
        return new Client(name,pass,(MockHttpSession)result.getRequest().getSession(false),json.readTree(result.getResponse().getContentAsString()).get("token").asText());
    }
    MvcResult call(Client x,String method,String path,Object body,int expected) throws Exception {
        var req=request(HttpMethod.valueOf(method),path).with(httpBasic(x.name,x.password)).session(x.session).header("X-CSRF-TOKEN",x.token);
        if(body!=null)req.contentType("application/json").content(json.writeValueAsString(body));
        var result=mvc.perform(req).andReturn();
        assertEquals(expected,result.getResponse().getStatus(),result.getResponse().getContentAsString());return result;
    }
    long id(MvcResult result) throws Exception{return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();}
    long item() throws Exception{return id(call(a,"POST","/api/collectible-items",Map.of("description","Шлем Ferrari","condition","EXCELLENT"),201));}
    long lot() throws Exception {return id(call(a,"POST","/api/auction-lots",Map.of("itemId",item(),"startPrice",100,"step",10,"startTime",Instant.now().minusSeconds(60).toString(),"endTime",Instant.now().plusSeconds(3600).toString(),"status","OPEN"),201));}
    @Test void anonymousAndWrongPassword() throws Exception {
        assertEquals(401,mvc.perform(get("/api/auction-lots")).andReturn().getResponse().getStatus());
        var bad=mvc.perform(get("/api/auction-lots").with(httpBasic("seller","wrong"))).andReturn().getResponse();
        assertEquals(401,bad.getStatus());assertTrue(bad.getHeader("WWW-Authenticate").contains("Basic"));
        assertEquals(401,mvc.perform(post("/api/bids").contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }
    @Test void csrfIsRequiredAndSessionBound() throws Exception {
        var req=post("/api/collectible-items").with(httpBasic("seller",password)).contentType("application/json").content("{}");
        assertEquals(403,mvc.perform(req).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/bids").with(httpBasic("seller",password)).session(a.session).header("X-CSRF-TOKEN",b.token).contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(post("/api/bids").with(httpBasic("seller",password)).header("X-CSRF-TOKEN",a.token).contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }
    @Test void realRegistrationAndBcrypt() throws Exception {
        String name="reg"+System.nanoTime();
        var result=call(c,"POST","/api/security/register",Map.of("username",name,"name","New","email",name+"@example.com","password",password,"role","ADMIN","state","ACTIVE"),201);
        var account=accounts.account(name);assertEquals("USER",account.role());assertNotEquals(password,account.passwordHash());assertTrue(encoder.matches(password,account.passwordHash()));assertFalse(result.getResponse().getContentAsString().contains(account.passwordHash()));
        var fresh=client(name,password);call(fresh,"GET","/api/security/me",null,200);call(fresh,"PATCH","/api/admin/accounts/"+buyer+"/state",Map.of("state","LOCKED"),403);
        call(c,"POST","/api/security/register",Map.of("username",name.toUpperCase(),"name","New","email","unique@example.com","password",password),409);
        call(c,"POST","/api/security/register",Map.of("username",name+"x","name","New","email",name+"@example.com","password",password),409);
    }
    @Test void weakAndInvalidRegistration() throws Exception {
        for(String weak:List.of("short","abcdefghijkl","ABCDEFGHIJK1!","Abcdefghijkl!","Abcdefghijkl1"))call(c,"POST","/api/security/register",Map.of("username","weakuser","name","New","email","weak@example.com","password",weak),400);
        call(c,"POST","/api/security/register",Map.of("username","!","name","New","email","bad","password",password),400);
    }
    @Test void allAccountStatesAndImmediateRevocation() throws Exception {
        for(String state:List.of("DISABLED","LOCKED","EXPIRED","PASSWORD_EXPIRED")) {
            call(admin,"PATCH","/api/admin/accounts/"+buyer+"/state",Map.of("state",state),200);
            call(b,"GET","/api/security/me",null,401);
            call(admin,"PATCH","/api/admin/accounts/"+buyer+"/state",Map.of("state","ACTIVE"),200);
            call(b,"GET","/api/security/me",null,200);
        }
        call(a,"PATCH","/api/admin/accounts/"+buyer+"/state",Map.of("state","LOCKED"),403);
        call(admin,"PATCH","/api/admin/accounts/"+buyer+"/state",Map.of("state","UNKNOWN"),400);
    }
    @Test void userPrivacyAndProfile() throws Exception {
        var result=call(a,"GET","/api/users",null,200);assertFalse(result.getResponse().getContentAsString().contains("lewis@example.com"));
        call(a,"GET","/api/users/"+buyer,null,403);call(a,"GET","/api/users/999999",null,403);
        call(a,"PATCH","/api/users/"+seller,Map.of("name","Max Verstappen"),200);
        call(a,"PUT","/api/users/"+seller,Map.of("name","Max","email","max@example.com"),200);
        call(a,"PATCH","/api/users/"+seller,Map.of("role","ADMIN"),403);
        call(a,"POST","/api/users",Map.of("name","Bypass","email","bypass@example.com"),403);
        call(admin,"GET","/api/users",null,200);
    }
    @Test void ownershipAndCrud() throws Exception {
        long item=item();String path="/api/collectible-items/"+item;
        call(b,"PATCH",path,Map.of("description","stolen"),403);
        call(b,"POST","/api/collectible-items",Map.of("ownerId",seller,"description","stolen","condition","NEW"),403);
        call(a,"PUT",path,Map.of("description","Кубок","condition","NEW"),200);
        call(a,"GET",path,null,200);call(a,"DELETE",path,null,403);call(admin,"DELETE",path,null,204);call(admin,"GET",path,null,404);
    }
    @Test void auctionFullLifecycleAndBusinessConflicts() throws Exception {
        long lot=lot();String path="/api/auction-lots/"+lot;
        call(a,"POST","/api/bids",Map.of("lotId",lot,"amount",100),409);
        call(b,"POST","/api/bids",Map.of("lotId",lot,"userId",outsider,"amount",100),403);
        call(b,"POST","/api/bids",Map.of("lotId",lot,"amount",99),409);
        long bid=id(call(b,"POST","/api/bids",Map.of("lotId",lot,"amount",100),201));
        call(c,"POST","/api/bids",Map.of("lotId",lot,"amount",105),409);
        call(b,"PATCH","/api/bids/"+bid,Map.of("amount",999),403);
        call(c,"PATCH",path,Map.of("status","CLOSED"),403);
        call(a,"PATCH",path,Map.of("step",1),409);
        call(a,"POST","/api/auction-deals",Map.of("lotId",lot,"winningBidId",bid,"amount",100,"transferStatus","PENDING"),409);
        call(a,"PATCH",path,Map.of("status","CLOSED"),200);
        call(b,"POST","/api/bids",Map.of("lotId",lot,"amount",110),409);
        call(c,"POST","/api/auction-deals",Map.of("lotId",lot,"winningBidId",bid,"amount",100,"transferStatus","PENDING"),403);
        long deal=id(call(a,"POST","/api/auction-deals",Map.of("lotId",lot,"winningBidId",bid,"amount",100,"transferStatus","PENDING"),201));
        call(c,"GET","/api/auction-deals/"+deal,null,403);
        assertEquals("[]",call(c,"GET","/api/auction-deals",null,200).getResponse().getContentAsString());
        call(b,"GET","/api/auction-deals/"+deal,null,200);
        call(a,"PATCH","/api/auction-deals/"+deal,Map.of("transferStatus","COMPLETED"),200);
        assertEquals(buyer,repo.items.get(repo.lots.get(lot).itemId()).ownerId());
        call(a,"GET","/api/auction-deals/"+deal,null,200);
        call(b,"PATCH","/api/auction-deals/"+deal,Map.of("transferStatus","PENDING"),403);
        call(a,"PATCH","/api/auction-deals/"+deal,Map.of("transferStatus","PENDING"),409);
    }
    @Test void cancellationAndHighestBid() throws Exception {
        long lot=lot();long bid=id(call(b,"POST","/api/bids",Map.of("lotId",lot,"amount",100),201));
        long top=id(call(c,"POST","/api/bids",Map.of("lotId",lot,"amount",110),201));
        call(a,"PATCH","/api/auction-lots/"+lot,Map.of("status","CLOSED"),200);
        call(a,"POST","/api/auction-deals",Map.of("lotId",lot,"winningBidId",bid,"amount",100,"transferStatus","PENDING"),409);
        call(a,"POST","/api/auction-deals",Map.of("lotId",lot,"winningBidId",top,"amount",999,"transferStatus","PENDING"),409);
        long cancelled=lot();call(a,"PATCH","/api/auction-lots/"+cancelled,Map.of("status","CANCELLED"),200);
        call(b,"POST","/api/bids",Map.of("lotId",cancelled,"amount",100),409);
    }
    @Test void safeErrorsAndPersistence() throws Exception {
        call(a,"POST","/api/collectible-items",Map.of(),400);
        var result=mvc.perform(post("/api/bids").with(httpBasic("seller",password)).session(a.session).header("X-CSRF-TOKEN",a.token).contentType("application/json").content("{bad")).andReturn();
        assertEquals(400,result.getResponse().getStatus());assertFalse(result.getResponse().getContentAsString().contains("Exception"));
        long item=item();var loaded=new AuctionRepository(db,json);assertEquals(repo.items.get(item),loaded.items.get(item));
        assertEquals(accounts.account("seller").passwordHash(),new Accounts(db,encoder,loaded).account("seller").passwordHash());
    }
    @Test void anonymousCanRegisterOnlyWithRealCsrf() throws Exception {
        var tokenResult=mvc.perform(get("/api/security/csrf")).andReturn();
        assertEquals(200,tokenResult.getResponse().getStatus());
        var session=(MockHttpSession)tokenResult.getRequest().getSession(false);
        String token=json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        String name="anon"+System.nanoTime();
        String body=json.writeValueAsString(Map.of("username",name,"name","Anonymous","email",name+"@example.com","password",password));
        assertEquals(403,mvc.perform(post("/api/security/register").contentType("application/json").content(body)).andReturn().getResponse().getStatus());
        assertEquals(201,mvc.perform(post("/api/security/register").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(body)).andReturn().getResponse().getStatus());
        assertEquals("USER",accounts.account(name).role());
    }
    @Test void futureAndExpiredAuctionsRejectBids() throws Exception {
        for(boolean future:List.of(true,false)) {
            long lot=id(call(a,"POST","/api/auction-lots",Map.of("itemId",item(),"startPrice",100,"step",10,"startTime",Instant.now().plusSeconds(future?3600:-7200).toString(),"endTime",Instant.now().plusSeconds(future?7200:-3600).toString(),"status","OPEN"),201));
            call(b,"POST","/api/bids",Map.of("lotId",lot,"amount",100),409);
        }
    }
    @Test void adminCanCreateAndDeleteAccount() throws Exception {
        String name="managed"+System.nanoTime();
        var data=Map.of("username",name,"name","Managed","email",name+"@example.com","password",password);
        call(a,"POST","/api/users",data,403);
        long user=id(call(admin,"POST","/api/users",data,201));
        var actor=client(name,password);call(actor,"GET","/api/security/me",null,200);
        call(admin,"DELETE","/api/users/"+user,null,204);
        call(actor,"GET","/api/security/me",null,401);
    }
    @Test void concurrentSameAmountAllowsOnlyOneBid() throws Exception {
        long lot=lot();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<java.util.concurrent.Callable<Integer>>of(()->rawBid(b,lot),()->rawBid(c,lot));
            var statuses=new ArrayList<Integer>();for(var result:pool.invokeAll(tasks))statuses.add(result.get());
            Collections.sort(statuses);assertEquals(List.of(201,409),statuses);assertEquals(1,repo.bids.size());
        }finally{pool.shutdownNow();}
    }
    int rawBid(Client actor,long lot) throws Exception {
        return mvc.perform(post("/api/bids").with(httpBasic(actor.name,actor.password)).session(actor.session).header("X-CSRF-TOKEN",actor.token).contentType("application/json").content(json.writeValueAsString(Map.of("lotId",lot,"amount",100)))).andReturn().getResponse().getStatus();
    }
}
