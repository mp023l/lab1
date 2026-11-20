package com.example.auction;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    private final Accounts accounts;
    public JwtTokenFilter(JwtTokenProvider jwt,Accounts accounts){this.jwt=jwt;this.accounts=accounts;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        return "POST".equals(request.getMethod()) &&
            (request.getServletPath().equals("/auth/login") || request.getServletPath().equals("/auth/register") || request.getServletPath().equals("/auth/refresh"));
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader(HttpHeaders.AUTHORIZATION);
        if(header!=null){
            try{
                if(!header.regionMatches(true,0,"Bearer ",0,7) || header.substring(7).isBlank())throw new IllegalArgumentException("Bearer required");
                var claims=jwt.parse(header.substring(7).trim(),"access");
                var user=accounts.loadUserByUsername(claims.getSubject());
                if(!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired() || !user.isCredentialsNonExpired())throw new IllegalArgumentException("Account disabled");
                var auth=new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);
            }catch(RuntimeException ex){SecurityContextHolder.clearContext();SecurityConfig.error(res,401,"UNAUTHORIZED");return;}
        }
        chain.doFilter(req,res);
    }
}
