package com.example.auction;

import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class TokenPairService {
    public record Pair(String accessToken,String refreshToken) {}
    public record RefreshRequest(String refreshToken,String deviceId) {}
    private final JwtTokenProvider jwt;
    private final UserSessionRepository sessions;
    private final Accounts accounts;
    private final TransactionTemplate tx;
    public TokenPairService(JwtTokenProvider jwt,UserSessionRepository sessions,Accounts accounts,
                            org.springframework.transaction.PlatformTransactionManager manager){
        this.jwt=jwt;this.sessions=sessions;this.accounts=accounts;this.tx=new TransactionTemplate(manager);
    }
    private static String hash(String token){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(bytes);}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid refresh token");}
    private Pair create(Accounts.Account account,String device){
        String sid=UUID.randomUUID().toString(),aid=UUID.randomUUID().toString(),rid=UUID.randomUUID().toString();
        String access=jwt.issue(account.username(),"access",aid,sid);
        String refresh=jwt.issue(account.username(),"refresh",rid,sid);
        sessions.insert(sid,account.id(),device,aid,rid,hash(refresh),Instant.now().plusMillis(jwt.refreshTtl()));
        return new Pair(access,refresh);
    }
    public Pair issue(Accounts.Account account,String device){return tx.execute(status->create(account,device));}
    public Pair rotate(String refresh,String device){
        if(refresh==null || refresh.isBlank())throw denied();
        final Claims claims;
        try{claims=jwt.parse(refresh,"refresh");}catch(RuntimeException e){throw denied();}
        // Return a result instead of throwing inside the transaction: REVOKED must commit on replay.
        Result result=tx.execute(status->{
            var session=sessions.locked(claims.get("sid",String.class)).orElse(null);
            if(session==null)return new Result(null,false);
            boolean matches=MessageDigest.isEqual(session.refreshHash().getBytes(StandardCharsets.US_ASCII),hash(refresh).getBytes(StandardCharsets.US_ASCII))
                && session.refreshJti().equals(claims.getId());
            if(!matches || !session.expiresAt().isAfter(Instant.now()))return new Result(null,false);
            if(session.status()==SessionStatus.USED){sessions.status(session.id(),SessionStatus.REVOKED);return new Result(null,false);}
            if(session.status()!=SessionStatus.ACTIVE)return new Result(null,false);
            Accounts.Account account;
            try{account=accounts.account(claims.getSubject());}catch(RuntimeException e){return new Result(null,false);}
            if(account.id()!=session.accountId() || !"ACTIVE".equals(account.state()))return new Result(null,false);
            sessions.status(session.id(),SessionStatus.USED);
            return new Result(create(account,device),true);
        });
        if(result==null || !result.ok())throw denied();
        return result.pair();
    }
    private record Result(Pair pair,boolean ok) {}
}
