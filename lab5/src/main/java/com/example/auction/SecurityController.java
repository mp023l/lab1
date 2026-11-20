package com.example.auction;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
public class SecurityController {
    private final Accounts accounts;
    private final Access access;
    public SecurityController(Accounts accounts,Access access){this.accounts=accounts;this.access=access;}
    @PostMapping("/api/users") @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<Model.User> createUser(@Valid @RequestBody Accounts.Registration data){
        var user=accounts.register(data);
        return org.springframework.http.ResponseEntity.created(java.net.URI.create("/api/users/"+user.id())).body(user);
    }
    @GetMapping("/api/security/me") @PreAuthorize("isAuthenticated()")
    public Map<String,Object> me(){var a=access.current(); return Map.of("id",a.id(),"username",a.username(),"role",a.role(),"state",a.state());}
    public record State(String state){}
    @PatchMapping("/api/admin/accounts/{id}/state") @PreAuthorize("hasRole('ADMIN')")
    public void state(@PathVariable long id,@RequestBody State state){accounts.state(id,state.state());}
}
