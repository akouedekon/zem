package bj.zem;

import static bj.zem.ApiModels.*;
import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final DossierService dossiers;
    ApiController(AuthService auth,DossierService dossiers) { this.auth=auth; this.dossiers=dossiers; }
    @GetMapping("/health") Map<String,String> health() { return Map.of("status","UP"); }
    @PostMapping("/auth/register") ResponseEntity<User> register(@Valid @RequestBody Register req) {
        return ResponseEntity.status(201).body(auth.register(req));
    }
    @PostMapping("/auth/login") Auth login(@Valid @RequestBody Login req) { return auth.login(req); }
    @PostMapping("/auth/register-passenger") ResponseEntity<User> passenger(@Valid @RequestBody Register req) {
        return ResponseEntity.status(201).body(auth.registerPassenger(req));
    }
    @GetMapping("/auth/me") User me(@AuthenticationPrincipal User user) { return user; }
    @PostMapping("/auth/logout") ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        auth.logout(header.substring(7)); return ResponseEntity.noContent().build();
    }
    @GetMapping("/driver/dossier") Dossier mine(@AuthenticationPrincipal User user) { return dossiers.mine(user); }
    @PutMapping("/driver/dossier") Dossier save(@AuthenticationPrincipal User user,@Valid @RequestBody Profile profile) { return dossiers.save(user,profile); }
    @GetMapping("/driver/documents") List<Document> documents(@AuthenticationPrincipal User user) { return dossiers.documents(dossiers.mine(user).id(),user); }
    @PostMapping(value="/driver/documents/{kind}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Document> upload(@AuthenticationPrincipal User user,@PathVariable String kind,@RequestParam MultipartFile file) throws Exception {
        return ResponseEntity.status(201).body(dossiers.upload(user,kind,file));
    }
    @GetMapping("/documents/{id}") ResponseEntity<byte[]> download(@AuthenticationPrincipal User user,@PathVariable UUID id) throws Exception {
        var doc=dossiers.download(id,user);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(doc.mediaType()))
            .header("Content-Disposition","attachment; filename=justificatif-"+id+(doc.mediaType().equals("application/pdf")?".pdf":doc.mediaType().equals("image/png")?".png":".jpg"))
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(doc.bytes());
    }
    @PostMapping("/driver/dossier/submit") Dossier submit(@AuthenticationPrincipal User user) { return dossiers.submit(user); }
    @GetMapping("/driver/card") Card card(@AuthenticationPrincipal User user) { return dossiers.card(user); }
    @GetMapping(value="/driver/card/qr",produces="image/png") byte[] qr(@AuthenticationPrincipal User user) throws Exception {
        var card=dossiers.card(user); var matrix=new MultiFormatWriter().encode(card.verificationUrl(),BarcodeFormat.QR_CODE,320,320);
        var bytes=new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(matrix,"PNG",bytes); return bytes.toByteArray();
    }
    @GetMapping("/admin/dossiers") List<Dossier> queue(@RequestParam(defaultValue="SUBMITTED") String status,@RequestParam(defaultValue="0") int page) { return dossiers.queue(status,page); }
    @GetMapping("/admin/dossiers/{id}") Dossier detail(@AuthenticationPrincipal User user,@PathVariable UUID id) { return dossiers.detail(id,user); }
    @GetMapping("/admin/dossiers/{id}/documents") List<Document> documents(@AuthenticationPrincipal User user,@PathVariable UUID id) { return dossiers.documents(id,user); }
    @GetMapping("/admin/dossiers/{id}/audit") List<Map<String,Object>> audit(@AuthenticationPrincipal User user,@PathVariable UUID id) { return dossiers.audit(id,user); }
    @PostMapping("/admin/dossiers/{id}/decision") Dossier decide(@AuthenticationPrincipal User user,@PathVariable UUID id,@Valid @RequestBody Decision decision) { return dossiers.decide(id,user,decision); }
    @PostMapping("/admin/dossiers/{id}/revoke") ResponseEntity<Void> revoke(@AuthenticationPrincipal User user,@PathVariable UUID id,@Valid @RequestBody Reason reason) {
        dossiers.revoke(id,user,reason.reason()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/public/cards/{token}") Verification verify(@PathVariable String token) { return dossiers.verify(token); }
}
