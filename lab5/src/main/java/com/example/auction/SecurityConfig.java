package com.example.auction;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    static void error(HttpServletResponse response,int status,String code) throws java.io.IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        if(status==401)response.setHeader("WWW-Authenticate","Bearer");
        response.getWriter().write("{\"status\":"+status+",\"error\":\""+code+"\"}");
    }
    @Bean AuthenticationManager authenticationManager(Accounts accounts,PasswordEncoder encoder){
        var provider=new DaoAuthenticationProvider();provider.setUserDetailsService(accounts);provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain security(HttpSecurity http,JwtTokenProvider jwt,Accounts accounts) throws Exception {
        http.csrf(c->c.disable()).httpBasic(c->c.disable()).formLogin(c->c.disable()).logout(c->c.disable())
            .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->error(res,401,"UNAUTHORIZED"))
                .accessDeniedHandler((req,res,e)->error(res,403,"FORBIDDEN")))
            .authorizeHttpRequests(c->c
                .requestMatchers(HttpMethod.POST,"/auth/login","/auth/register","/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/users").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("USER","ADMIN")
                .anyRequest().denyAll())
            .addFilterBefore(new JwtTokenFilter(jwt,accounts),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
