package com.example.auction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.*;
import java.util.*;

@Service
public class Accounts implements UserDetailsService {
    public record Registration(@NotBlank @Pattern(regexp="[a-zA-Z0-9_.-]{3,40}") String username,
            @NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
            @NotNull @Size(min=12,max=64) String password) {}
    public record Account(long id,String username,String email,String passwordHash,String role,String state) {}
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final AuctionRepository repository;
    public Accounts(JdbcTemplate db,PasswordEncoder encoder,AuctionRepository repository) {
        this.db=db; this.encoder=encoder; this.repository=repository;
    }
    static String canonical(String value) {return value.toLowerCase(Locale.ROOT);}
    public Account account(String username) {
        return db.query("select * from accounts where username=?",(rs,n)->new Account(rs.getLong("id"),rs.getString("username"),rs.getString("email"),rs.getString("password_hash"),rs.getString("role"),rs.getString("state")),canonical(username)).stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
    }
    @Override public UserDetails loadUserByUsername(String username) {
        Account a=account(username);
        return User.withUsername(a.username()).password(a.passwordHash()).roles(a.role())
            .disabled(a.state().equals("DISABLED")).accountLocked(a.state().equals("LOCKED"))
            .accountExpired(a.state().equals("EXPIRED")).credentialsExpired(a.state().equals("PASSWORD_EXPIRED")).build();
    }
    public synchronized Model.User register(Registration input) {return create(input,"USER");}
    synchronized Model.User create(Registration input,String role) {
        String password=input.password();
        if(password==null || password.length()<12 || password.length()>64 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 ||
            !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*[0-9].*") || !password.matches(".*[^a-zA-Z0-9].*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must contain 12–64 characters, uppercase, lowercase, number and symbol (max 72 UTF-8 bytes)");
        if(db.queryForObject("select count(*) from accounts where username=? or email=?",Long.class,canonical(input.username()),canonical(input.email()))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Account cannot be registered");
        long id=repository.nextId();
        db.update("insert into accounts values(?,?,?,?,?,?)",id,canonical(input.username()),canonical(input.email()),encoder.encode(password),role,"ACTIVE");
        Model.User user=new Model.User(id,input.name(),canonical(input.email()));
        repository.users.put(id,user); repository.persist();
        return user;
    }
    public void state(long id,String state) {
        if(state==null || !Set.of("ACTIVE","DISABLED","LOCKED","EXPIRED","PASSWORD_EXPIRED").contains(state)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid state");
        if(db.update("update accounts set state=? where id=?",state,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Not found");
    }
    public void remove(long id) {db.update("delete from accounts where id=?",id);}
    public void email(long id,String email) {
        if(email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>254) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid email");
        if(db.queryForObject("select count(*) from accounts where email=? and id<>?",Long.class,canonical(email),id)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email unavailable");
        db.update("update accounts set email=? where id=?",canonical(email),id);
    }
}
