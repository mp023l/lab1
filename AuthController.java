package com.example.auction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/auth")
public class AuthController {
    public record Login(@NotBlank String username,@NotBlank String password,String deviceId) {}
    private final AuthenticationManager manager;
    private final Accounts accounts;
    private final TokenPairService pairs;
    public AuthController(AuthenticationManager manager,Accounts accounts,TokenPairService pairs){this.manager=manager;this.accounts=accounts;this.pairs=pairs;}
    @PostMapping("/login") public TokenPairService.Pair login(@Valid @RequestBody Login req){
        try{manager.authenticate(new UsernamePasswordAuthenticationToken(req.username(),req.password()));}
        catch(AuthenticationException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");}
        return pairs.issue(accounts.account(req.username()),req.deviceId());
    }
    @PostMapping("/register") public ResponseEntity<TokenPairService.Pair> register(@Valid @RequestBody Accounts.Registration req){
        accounts.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(pairs.issue(accounts.account(req.username()),null));
    }
    @PostMapping("/refresh") public TokenPairService.Pair refresh(@RequestBody TokenPairService.RefreshRequest req){
        return pairs.rotate(req.refreshToken(),req.deviceId());
    }
}
