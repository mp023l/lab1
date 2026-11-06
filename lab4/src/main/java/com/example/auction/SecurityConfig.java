package com.example.auction;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import jakarta.servlet.http.HttpServletResponse;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    static void error(HttpServletResponse response,int status,String code) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        if(status==401) response.setHeader("WWW-Authenticate","Basic realm=\"auction\"");
        response.getWriter().write("{\"status\":"+status+",\"error\":\""+code+"\"}");
    }
    @Bean SecurityFilterChain security(HttpSecurity http,Accounts accounts,PasswordEncoder encoder) throws Exception {
        var provider=new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(accounts); provider.setPasswordEncoder(encoder);
        var manager=new org.springframework.security.authentication.ProviderManager(provider);
        http.authenticationManager(manager);
        http.securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).logout(c->c.disable())
            .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->error(res,401,"UNAUTHORIZED"))
                .accessDeniedHandler((req,res,e)-> {
                    var a=SecurityContextHolder.getContext().getAuthentication();
                    boolean anonymous=a==null || a instanceof AnonymousAuthenticationToken;
                    boolean needLogin=anonymous&&!req.getRequestURI().equals("/api/security/register");
                    error(res,needLogin?401:403,needLogin?"UNAUTHORIZED":"FORBIDDEN");
                }))
            .authorizeHttpRequests(c->c
                .requestMatchers(HttpMethod.GET,"/api/security/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/security/register").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/users").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("USER","ADMIN").anyRequest().denyAll());
        // Validate Basic on every request, even when CSRF would reject the request first.
        http.addFilterBefore(new BasicAuthenticationFilter(manager,(req,res,e)->error(res,401,"UNAUTHORIZED")),CsrfFilter.class);
        return http.build();
    }
}
