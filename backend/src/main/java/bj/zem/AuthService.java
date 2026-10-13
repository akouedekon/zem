package bj.zem;

import static bj.zem.ApiModels.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService implements ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    private final UserRepository users;
    @Value("${zem.bootstrap-email}") private String adminEmail;
    @Value("${zem.bootstrap-password}") private String adminPassword;
    AuthService(JdbcTemplate db, PasswordEncoder encoder, UserRepository users) {
        this.db=db; this.encoder=encoder; this.users=users; this.dummyHash=encoder.encode(Tokens.random());
    }
    static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    static void checkPassword(String password) {
        if(password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(BAD_REQUEST,"Mot de passe : 12 caractères minimum, 72 octets maximum");
    }
    @Transactional
    public User register(Register request) {
        return registerAs(request,"DRIVER");
    }
    @Transactional
    public User registerPassenger(Register request) {
        return registerAs(request,"PASSENGER");
    }
    private User registerAs(Register request,String role) {
        checkPassword(request.password());
        UUID id=UUID.randomUUID(); String email=normalize(request.email());
        int inserted=db.update("INSERT INTO app_user(id,email,password_hash,role) VALUES (?,?,?,?) ON CONFLICT(email) DO NOTHING",
                id,email,encoder.encode(request.password()),role);
        if(inserted==0) throw new ResponseStatusException(CONFLICT,"Inscription impossible avec cette adresse");
        return new User(id,email,role);
    }
    @Transactional
    public Auth login(Login request) {
        var account=users.findByEmail(normalize(request.email()));
        String hash=account.map(UserEntity::passwordHash).orElse(dummyHash);
        boolean matches=request.password().getBytes(StandardCharsets.UTF_8).length<=72
                && encoder.matches(request.password(),hash);
        if(!matches || account.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED,"Identifiants incorrects");
        User user=account.get().publicUser();
        String token=Tokens.random(); OffsetDateTime expiry=OffsetDateTime.now().plusHours(8);
        db.update("INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES (?,?,?)",
                Tokens.hash(token),user.id(),expiry);
        db.update("DELETE FROM auth_session WHERE expires_at <= now()");
        return new Auth(token,expiry,user);
    }
    public Optional<User> authenticate(String token) {
        if(!token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return db.query("SELECT u.id,u.email,u.role FROM auth_session s JOIN app_user u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>now()",
                (rs,n)->new User(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("role")),Tokens.hash(token)).stream().findFirst();
    }
    public void logout(String token) { db.update("DELETE FROM auth_session WHERE token_hash=?",Tokens.hash(token)); }
    @Override public void run(ApplicationArguments args) {
        if(adminEmail.isBlank() && adminPassword.isBlank()) return;
        if(adminEmail.isBlank() || adminPassword.isBlank()) throw new IllegalStateException("Les deux variables administrateur sont requises");
        checkPassword(adminPassword);
        if(!adminEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalStateException("Adresse administrateur invalide");
        db.update("INSERT INTO app_user(id,email,password_hash,role) VALUES (?,?,?,'ADMIN') ON CONFLICT(email) DO NOTHING",
                UUID.randomUUID(),normalize(adminEmail),encoder.encode(adminPassword));
        var roles=db.queryForList("SELECT role FROM app_user WHERE email=?",String.class,normalize(adminEmail));
        if(!roles.contains("ADMIN")) throw new IllegalStateException("Cette adresse appartient déjà à un conducteur");
    }
}
