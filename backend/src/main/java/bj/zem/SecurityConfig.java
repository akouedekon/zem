package bj.zem;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth) throws Exception {
        // API accepts bearer tokens only. The web BFF owns the HttpOnly cookie and CSRF origin check.
        return http.csrf(csrf->csrf.disable()).cors(cors->cors.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/auth/register","/api/auth/login","/api/public/**","/api/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/driver/**").hasRole("DRIVER")
                .anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401))
                .accessDeniedHandler((req,res,ex)->res.sendError(403)))
            .addFilterBefore(new BearerFilter(auth),UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    static final class BearerFilter extends OncePerRequestFilter {
        private final AuthService auth;
        BearerFilter(AuthService auth) { this.auth=auth; }
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
                throws ServletException,IOException {
            String header=req.getHeader("Authorization");
            if(header!=null && header.startsWith("Bearer ")) auth.authenticate(header.substring(7)).ifPresent(user->
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role())))));
            chain.doFilter(req,res);
        }
    }
}
