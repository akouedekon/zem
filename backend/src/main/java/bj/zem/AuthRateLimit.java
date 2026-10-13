package bj.zem;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance defence. Nginx additionally limits the actual client IP at the trusted edge. */
@Component
@Order(-110)
public class AuthRateLimit extends OncePerRequestFilter {
    private final Map<String,Window> windows=new HashMap<>();
    private synchronized boolean allowed(String ip) {
        long now=Instant.now().getEpochSecond();
        windows.entrySet().removeIf(e->e.getValue().start+60<=now);
        Window w=windows.get(ip);
        if(w==null) {
            if(windows.size()>=10000) return false;
            w=new Window(now); windows.put(ip,w);
        }
        return ++w.count<=20;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if(req.getMethod().equals("POST") && (req.getRequestURI().equals("/api/auth/login") || req.getRequestURI().equals("/api/auth/register") || req.getRequestURI().equals("/api/auth/register-passenger")) && !allowed(req.getRemoteAddr())) {
            res.setStatus(429); res.setHeader("Retry-After","60"); res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Trop de tentatives, réessayez dans une minute\"}"); return;
        }
        chain.doFilter(req,res);
    }
    private static final class Window { final long start; int count; Window(long start) { this.start=start; } }
}
