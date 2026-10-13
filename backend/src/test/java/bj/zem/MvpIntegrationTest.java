package bj.zem;

import static bj.zem.ApiModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"zem.bootstrap-email=admin@zem.test","zem.bootstrap-password=Admin-test-password-17"})
@AutoConfigureMockMvc
class MvpIntegrationTest {
    static final EmbeddedPostgres PG;
    static { try { PG=EmbeddedPostgres.builder().start(); } catch(Exception ex) { throw new ExceptionInInitializerError(ex); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username",()->"postgres"); r.add("spring.datasource.password",()->"postgres");
        r.add("zem.storage",()->System.getProperty("java.io.tmpdir")+"/zem-test-documents-"+PG.getPort());
        r.add("zem.document-budget-bytes",()->1024);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired DossierService dossiers;
    @Autowired MobilityService mobility;
    Auth driver,other,admin;
    @BeforeEach void fixtures() {
        db.execute("TRUNCATE mobility_audit,ride,driver_service,activity_declaration CASCADE");
        db.execute("TRUNCATE decision_audit,digital_card,document,dossier,auth_session CASCADE");
        db.update("DELETE FROM app_user WHERE role<>'ADMIN'");
        driver=account("driver@zem.test"); other=account("other@zem.test");
        admin=auth.login(new Login("admin@zem.test","Admin-test-password-17"));
    }
    Auth account(String email) { auth.register(new Register(email,"Driver-test-password-17")); return auth.login(new Login(email,"Driver-test-password-17")); }
    String bearer(Auth a) { return "Bearer "+a.token(); }
    Dossier draft() { return dossiers.save(driver.user(),new Profile("Conducteur Test","+2290102030405","AB 1234")); }
    Dossier submitted() throws Exception {
        Dossier d=draft();
        for(String kind:List.of("IDENTITY","DRIVING_LICENSE","VEHICLE")) dossiers.upload(driver.user(),kind,
            new MockMultipartFile("file","../private.pdf","application/pdf","%PDF-1.4\nsynthetic test fixture".getBytes(StandardCharsets.UTF_8)));
        return dossiers.submit(driver.user());
    }
    Auth passenger() { auth.registerPassenger(new Register("passenger@zem.test","Passenger-test-password-17")); return auth.login(new Login("passenger@zem.test","Passenger-test-password-17")); }
    MobilityController.Booking booking() { return new MobilityController.Booking("Étoile Rouge","Dantokpa",6.36,2.40,6.37,2.43); }
    @Test void mobilityFullJourneyAndRevenueProvenance() throws Exception {
        Auth p=passenger();Dossier d=submitted();dossiers.decide(d.id(),admin.user(),new Decision(true,null));
        mobility.availability(driver.user(),new MobilityController.Availability(true,6.36,2.40));
        var r=mobility.book(p.user(),booking());UUID id=(UUID)r.get("id");
        assertThat(mobility.requests(driver.user())).hasSize(1);
        mvc.perform(get("/api/driver/requests").header("Authorization",bearer(p))).andExpect(status().isForbidden());
        mobility.accept(driver.user(),id);
        assertThat(mobility.location(p.user(),id)).containsKeys("latitude","longitude");
        assertThatThrownBy(()->mobility.location(other.user(),id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->mobility.transition(other.user(),id,"START",null)).isInstanceOf(ResponseStatusException.class);
        mobility.transition(driver.user(),id,"START",null);mobility.transition(driver.user(),id,"COMPLETE",1500L);
        assertThatThrownBy(()->mobility.transition(driver.user(),id,"COMPLETE",1500L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->mobility.location(p.user(),id)).isInstanceOf(ResponseStatusException.class);
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Africa/Porto-Novo"));
        assertThat(mobility.earnings(driver.user().id(),day).get("estimatedBalance")).isNull();
        var e=mobility.declare(driver.user(),new MobilityController.Declaration(day,3500,5,3200L));
        assertThat(e.get("trackedRevenue")).isEqualTo(5000L);assertThat(e.get("estimatedBalance")).isEqualTo(1800L);
        var activity=mobility.activity(day,0).stream().filter(row->row.get("driver_id").equals(driver.user().id())).findFirst().orElseThrow();
        assertThat(((Number)activity.get("estimated_balance")).longValue()).isEqualTo(1800L);
        mvc.perform(get("/api/admin/activity?day="+day).header("Authorization",bearer(p))).andExpect(status().isForbidden());
        mobility.declare(driver.user(),new MobilityController.Declaration(day,3500,5,null));
        assertThat(mobility.earnings(driver.user().id(),day).get("estimatedBalance")).isNull();
        mobility.availability(driver.user(),new MobilityController.Availability(false,null,null));
        assertThat(mobility.availability(driver.user()).get("latitude")).isNull();
    }
    @Test void mobilityRequiresValidCardAndSingleActiveCourse() throws Exception {
        Auth p=passenger();
        assertThatThrownBy(()->mobility.availability(driver.user(),new MobilityController.Availability(true,6.36,2.40))).isInstanceOf(ResponseStatusException.class);
        UUID id=(UUID)mobility.book(p.user(),booking()).get("id");
        mvc.perform(post("/api/passenger/rides").header("Authorization",bearer(p)).contentType("application/json").content(json.writeValueAsString(booking()))).andExpect(status().isConflict());
        mobility.transition(p.user(),id,"CANCEL",null);
        mvc.perform(post("/api/passenger/rides").header("Authorization",bearer(p)).contentType("application/json").content("{\"pickup\":\"a\",\"destination\":\"b\",\"pickupLat\":999,\"pickupLon\":2,\"destinationLat\":6,\"destinationLon\":2}" )).andExpect(status().isBadRequest());
    }
    @Test void concurrentRideAcceptsChooseExactlyOneDriver() throws Exception {
        Auth p=passenger();Dossier d=submitted();dossiers.decide(d.id(),admin.user(),new Decision(true,null));
        Dossier od=dossiers.save(other.user(),new Profile("Autre Conducteur","+2290102030406","AC 1234"));
        db.update("UPDATE dossier SET status='APPROVED' WHERE id=?",od.id());
        db.update("INSERT INTO digital_card(id,dossier_id,public_token,expires_at) VALUES (?,?,?,now()+interval '1 year')",UUID.randomUUID(),od.id(),Tokens.random());
        for(Auth a:List.of(driver,other)) mobility.availability(a.user(),new MobilityController.Availability(true,6.36,2.40));
        UUID id=(UUID)mobility.book(p.user(),booking()).get("id");ExecutorService pool=Executors.newFixedThreadPool(2);
        try {var results=pool.invokeAll(List.of((Callable<Boolean>)()->{try{mobility.accept(driver.user(),id);return true;}catch(ResponseStatusException ex){return false;}},()->{try{mobility.accept(other.user(),id);return true;}catch(ResponseStatusException ex){return false;}}));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
            assertThat(db.queryForObject("SELECT count(*) FROM mobility_audit WHERE action='ACCEPTED'",Integer.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void registrationNeverAcceptsRoleAndPasswordsAreHashed() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"email\":\"NEW@zem.test\",\"password\":\"Long-password-17\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("DRIVER"));
        assertThat(db.queryForObject("SELECT password_hash FROM app_user WHERE email='new@zem.test'",String.class)).startsWith("$2a$");
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"missing@zem.test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void roleOwnerAndTokenControls() throws Exception {
        Dossier d=submitted(); UUID doc=dossiers.documents(d.id(),driver.user()).get(0).id();
        mvc.perform(get("/api/admin/dossiers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dossiers").header("Authorization",bearer(driver))).andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/"+doc).header("Authorization",bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/"+doc).header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(post("/api/auth/logout").header("Authorization",bearer(driver))).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization",bearer(driver))).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_session SET expires_at=now()-interval '1 second' WHERE token_hash=?",Tokens.hash(other.token()));
        mvc.perform(get("/api/auth/me").header("Authorization",bearer(other))).andExpect(status().isUnauthorized());
    }
    @Test void submissionRequiresAllDocumentsAndFrozenDossierRejectsEdits() throws Exception {
        draft();
        mvc.perform(post("/api/driver/dossier/submit").header("Authorization",bearer(driver))).andExpect(status().isConflict());
        Dossier d=submitted();
        mvc.perform(put("/api/driver/dossier").header("Authorization",bearer(driver)).contentType("application/json")
            .content(json.writeValueAsString(new Profile("Changed","+2290102030405","AB 1234")))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/dossiers/"+d.id()+"/decision").header("Authorization",bearer(driver)).contentType("application/json").content("{\"approve\":true}"))
            .andExpect(status().isForbidden());
    }
    @Test void rejectionResubmissionApprovalQrAndRevocation() throws Exception {
        Dossier d=submitted();
        mvc.perform(post("/api/admin/dossiers/"+d.id()+"/decision").header("Authorization",bearer(admin)).contentType("application/json").content("{\"approve\":false}"))
            .andExpect(status().isBadRequest());
        dossiers.decide(d.id(),admin.user(),new Decision(false,"Photo illisible"));
        assertThat(dossiers.mine(driver.user()).rejectionReason()).isEqualTo("Photo illisible");
        assertThatThrownBy(()->dossiers.card(driver.user())).isInstanceOf(ResponseStatusException.class);
        dossiers.submit(driver.user());
        mvc.perform(post("/api/admin/dossiers/"+d.id()+"/decision").header("Authorization",bearer(admin)).contentType("application/json").content("{\"approve\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        Card card=dossiers.card(driver.user());
        mvc.perform(get("/api/public/cards/"+card.publicToken())).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.fullName").doesNotExist()).andExpect(jsonPath("$.phone").doesNotExist());
        byte[] png=mvc.perform(get("/api/driver/card/qr").header("Authorization",bearer(driver))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var image=ImageIO.read(new ByteArrayInputStream(png));
        var decoded=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
        assertThat(decoded.getText()).isEqualTo(card.verificationUrl());
        assertThat(decoded.getText()).doesNotContain("Conducteur","229");
        mvc.perform(post("/api/admin/dossiers/"+d.id()+"/revoke").header("Authorization",bearer(admin)).contentType("application/json").content("{\"reason\":\"Erreur administrative\"}"))
            .andExpect(status().isNoContent());
        assertThat(dossiers.verify(card.publicToken()).status()).isEqualTo("REVOKED");
        assertThat(dossiers.audit(d.id(),admin.user())).hasSize(5);
    }
    @Test void concurrentDecisionsIssueExactlyOneCard() throws Exception {
        Dossier d=submitted(); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> decision=()-> { try { dossiers.decide(d.id(),admin.user(),new Decision(true,null)); return true; }
                catch(ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return false; } };
            var results=pool.invokeAll(List.of(decision,decision));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
            assertThat(db.queryForObject("SELECT count(*) FROM digital_card",Integer.class)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM decision_audit WHERE action='APPROVED'",Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void invalidUploadsAndExpiryAreRejected() throws Exception {
        draft();
        mvc.perform(multipart("/api/driver/documents/IDENTITY").file(new MockMultipartFile("file","fake.pdf","application/pdf","<script>bad</script>".getBytes()))
            .header("Authorization",bearer(driver))).andExpect(status().isBadRequest());
        Dossier d=submitted(); dossiers.decide(d.id(),admin.user(),new Decision(true,null)); Card c=dossiers.card(driver.user());
        db.update("UPDATE digital_card SET expires_at=now()-interval '1 day'");
        assertThat(dossiers.verify(c.publicToken()).valid()).isFalse();
        assertThat(dossiers.verify(c.publicToken()).status()).isEqualTo("EXPIRED");
        mvc.perform(get("/api/public/cards/not-a-token")).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success=true",Integer.class)).isEqualTo(3);
    }
    @Test void databaseDocumentsSurviveServiceRecreationAndReplacement() throws Exception {
        Dossier d=draft(); byte[] first="%PDF-first".getBytes(StandardCharsets.UTF_8);
        Document doc=dossiers.upload(driver.user(),"IDENTITY",new MockMultipartFile("file","a.pdf","application/pdf",first));
        DossierService restarted=new DossierService(db,System.getProperty("java.io.tmpdir")+"/unused-zem-path","http://localhost:3000","database",1024);
        assertThat(restarted.download(doc.id(),driver.user()).bytes()).isEqualTo(first);
        assertThatThrownBy(()->restarted.download(doc.id(),other.user())).isInstanceOf(ResponseStatusException.class);
        byte[] second="%PDF-second".getBytes(StandardCharsets.UTF_8);
        Document replacement=dossiers.upload(driver.user(),"IDENTITY",new MockMultipartFile("file","b.pdf","application/pdf",second));
        assertThat(restarted.download(replacement.id(),driver.user()).bytes()).isEqualTo(second);
        assertThat(db.queryForObject("SELECT count(*) FROM document_content",Integer.class)).isEqualTo(1);
        assertThat(dossiers.documents(d.id(),driver.user())).hasSize(1);
    }
    @Test void quotaFailurePreservesPreviousDocument() throws Exception {
        draft(); byte[] original="%PDF-original".getBytes(StandardCharsets.UTF_8);
        Document doc=dossiers.upload(driver.user(),"IDENTITY",new MockMultipartFile("file","a.pdf","application/pdf",original));
        byte[] tooBig=new byte[1025]; System.arraycopy(original,0,tooBig,0,original.length);
        assertThatThrownBy(()->dossiers.upload(driver.user(),"IDENTITY",new MockMultipartFile("file","b.pdf","application/pdf",tooBig)))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(507));
        assertThat(dossiers.download(doc.id(),driver.user()).bytes()).isEqualTo(original);
    }
}
