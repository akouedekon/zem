package bj.zem;

import static bj.zem.ApiModels.*;
import static org.springframework.http.HttpStatus.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DossierService {
    private final JdbcTemplate db;
    private final Path storage;
    private final String publicUrl;
    private final boolean databaseStorage;
    private final long storageLimit;
    public DossierService(JdbcTemplate db,@Value("${zem.storage}") String storage,
                          @Value("${zem.public-url}") String publicUrl,
                          @Value("${zem.document-storage:database}") String storageMode,
                          @Value("${zem.document-budget-bytes:157286400}") long storageLimit) throws IOException {
        this.db=db; this.storage=Path.of(storage).toAbsolutePath().normalize();
        if(!Set.of("database","filesystem").contains(storageMode) || storageLimit<1)
            throw new IllegalArgumentException("Configuration de stockage invalide");
        this.databaseStorage=storageMode.equals("database"); this.storageLimit=storageLimit;
        this.publicUrl=publicUrl.replaceAll("/+$","");
        if(!databaseStorage) Files.createDirectories(this.storage);
    }
    private static OffsetDateTime time(ResultSet rs,String field) throws SQLException { return rs.getObject(field,OffsetDateTime.class); }
    private static Dossier map(ResultSet rs,int row) throws SQLException {
        return new Dossier(rs.getObject("id",UUID.class),rs.getObject("driver_id",UUID.class),
            rs.getString("full_name"),rs.getString("phone"),rs.getString("plate"),rs.getString("status"),
            rs.getString("rejection_reason"),time(rs,"submitted_at"),time(rs,"decided_at"));
    }
    public Dossier mine(User user) {
        return db.query("SELECT * FROM dossier WHERE driver_id=?",DossierService::map,user.id()).stream()
            .findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Aucun dossier"));
    }
    private Dossier locked(UUID id) {
        return db.query("SELECT * FROM dossier WHERE id=? FOR UPDATE",DossierService::map,id).stream()
            .findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Dossier introuvable"));
    }
    private static void owner(Dossier d,User user) {
        if(!d.driverId().equals(user.id())) throw new ResponseStatusException(NOT_FOUND,"Dossier introuvable");
    }
    private static void editable(Dossier d) {
        if(!Set.of("DRAFT","REJECTED").contains(d.status())) throw new ResponseStatusException(CONFLICT,"Le dossier ne peut plus être modifié");
    }
    @Transactional public Dossier save(User user,Profile p) {
        db.update("INSERT INTO dossier(id,driver_id,full_name,phone,plate) VALUES (?,?,?,?,?) ON CONFLICT(driver_id) DO NOTHING",
            UUID.randomUUID(),user.id(),p.fullName().trim(),p.phone().trim(),p.plate().trim());
        Dossier d=locked(mine(user).id()); editable(d);
        db.update("UPDATE dossier SET full_name=?,phone=?,plate=?,status='DRAFT',rejection_reason=NULL,decided_at=NULL,updated_at=now() WHERE id=?",
            p.fullName().trim(),p.phone().trim(),p.plate().trim(),d.id());
        return mine(user);
    }
    public List<Document> documents(UUID dossierId,User user) {
        Dossier d=detail(dossierId,user);
        return db.query("SELECT id,kind,media_type,byte_size FROM document WHERE dossier_id=? ORDER BY kind",
            (rs,n)->new Document(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getString("media_type"),rs.getLong("byte_size")),d.id());
    }
    public Dossier detail(UUID id,User user) {
        Dossier d=db.query("SELECT * FROM dossier WHERE id=?",DossierService::map,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Dossier introuvable"));
        if(!user.role().equals("ADMIN")) owner(d,user);
        return d;
    }
    @Transactional public Document upload(User user,String kind,MultipartFile file) throws IOException {
        if(!Set.of("IDENTITY","DRIVING_LICENSE","VEHICLE").contains(kind)) throw new ResponseStatusException(BAD_REQUEST,"Type de justificatif inconnu");
        Dossier d=locked(mine(user).id()); owner(d,user); editable(d);
        if(file.isEmpty() || file.getSize()>3145728) throw new ResponseStatusException(BAD_REQUEST,"Taille de document invalide");
        byte[] bytes=file.getBytes(); String type=mediaType(bytes);
        // UUID storage keys ignore filenames entirely; originals are never exposed as paths.
        UUID id=UUID.randomUUID(); String key=id.toString(); Path path=storage.resolve(key);
        List<String> old=db.queryForList("SELECT storage_key FROM document WHERE dossier_id=? AND kind=?",String.class,d.id(),kind);
        if(databaseStorage) {
            db.queryForObject("SELECT id FROM document_storage_lock WHERE id=1 FOR UPDATE",Integer.class);
            Long used=db.queryForObject("SELECT coalesce(sum(octet_length(content)),0) FROM document_content",Long.class);
            Long replaced=db.queryForObject("SELECT coalesce(sum(octet_length(c.content)),0) FROM document_content c JOIN document d ON d.id=c.document_id WHERE d.dossier_id=? AND d.kind=?",Long.class,d.id(),kind);
            if(used-replaced+bytes.length>storageLimit) throw new ResponseStatusException(INSUFFICIENT_STORAGE,"Capacité documentaire du pilote atteinte");
        } else {
            Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                List<Path> removals=status==STATUS_COMMITTED?old.stream().map(storage::resolve).toList():databaseStorage?List.of():List.of(path);
                for(Path p:removals) try { Files.deleteIfExists(p); } catch(IOException ex) {
                    org.slf4j.LoggerFactory.getLogger(DossierService.class).warn("Nettoyage de stockage à reprendre");
                }
            }
        });
        db.update("INSERT INTO document(id,dossier_id,kind,storage_key,media_type,byte_size) VALUES (?,?,?,?,?,?) ON CONFLICT(dossier_id,kind) DO UPDATE SET id=excluded.id,storage_key=excluded.storage_key,media_type=excluded.media_type,byte_size=excluded.byte_size,created_at=now()",
            id,d.id(),kind,key,type,bytes.length);
        if(databaseStorage) db.update("INSERT INTO document_content(document_id,content) VALUES (?,?) ON CONFLICT(document_id) DO UPDATE SET content=excluded.content",id,bytes);
        else db.update("DELETE FROM document_content WHERE document_id=?",id);
        db.update("UPDATE dossier SET status='DRAFT',rejection_reason=NULL,decided_at=NULL,updated_at=now() WHERE id=?",d.id());
        return new Document(id,kind,type,bytes.length);
    }
    static String mediaType(byte[] b) {
        if(b.length>=5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-') return "application/pdf";
        if(b.length>=3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) return "image/jpeg";
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        if(b.length>=8 && Arrays.equals(Arrays.copyOf(b,8),png)) return "image/png";
        throw new ResponseStatusException(BAD_REQUEST,"Formats acceptés : PDF, JPEG, PNG");
    }
    public Download download(UUID id,User user) throws IOException {
        var records=db.query("SELECT dossier_id,storage_key,media_type FROM document WHERE id=?",
            (rs,n)->new Stored(rs.getObject("dossier_id",UUID.class),rs.getString("storage_key"),rs.getString("media_type")),id);
        if(records.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Document introuvable");
        Stored s=records.get(0); detail(s.dossierId(),user);
        List<byte[]> contents=db.query("SELECT content FROM document_content WHERE document_id=?",(rs,n)->rs.getBytes("content"),id);
        if(!contents.isEmpty()) return new Download(contents.get(0),s.type());
        Path path=storage.resolve(s.key()).normalize();
        if(!path.startsWith(storage) || Files.isSymbolicLink(path)) throw new ResponseStatusException(NOT_FOUND,"Document introuvable");
        return new Download(Files.readAllBytes(path),s.type());
    }
    @Transactional public Dossier submit(User user) {
        Dossier d=locked(mine(user).id()); owner(d,user); editable(d);
        Integer count=db.queryForObject("SELECT count(*) FROM document WHERE dossier_id=?",Integer.class,d.id());
        if(count==null || count!=3) throw new ResponseStatusException(CONFLICT,"Les trois justificatifs sont requis");
        db.update("UPDATE dossier SET status='SUBMITTED',submitted_at=now(),decided_at=NULL,rejection_reason=NULL,updated_at=now() WHERE id=?",d.id());
        audit(d.id(),user.id(),"SUBMITTED",null);
        return mine(user);
    }
    public List<Dossier> queue(String status,int page) {
        if(!Set.of("SUBMITTED","APPROVED","REJECTED","DRAFT").contains(status) || page<0 || page>10000)
            throw new ResponseStatusException(BAD_REQUEST,"Filtre invalide");
        return db.query("SELECT * FROM dossier WHERE status=? ORDER BY submitted_at NULLS LAST,id LIMIT 50 OFFSET ?",
            DossierService::map,status,page*50);
    }
    @Transactional public Dossier decide(UUID id,User admin,Decision decision) {
        requireAdmin(admin); Dossier d=locked(id);
        if(!d.status().equals("SUBMITTED")) throw new ResponseStatusException(CONFLICT,"Dossier déjà décidé ou non soumis");
        String reason=decision.reason()==null?null:decision.reason().trim();
        if(!decision.approve() && (reason==null || reason.isBlank())) throw new ResponseStatusException(BAD_REQUEST,"Un motif de rejet est requis");
        String status=decision.approve()?"APPROVED":"REJECTED";
        db.update("UPDATE dossier SET status=?,rejection_reason=?,decided_at=now(),updated_at=now() WHERE id=?",
            status,decision.approve()?null:reason,id);
        if(decision.approve()) db.update("INSERT INTO digital_card(id,dossier_id,public_token,expires_at) VALUES (?,?,?,?)",
            UUID.randomUUID(),id,Tokens.random(),OffsetDateTime.now().plusYears(1));
        audit(id,admin.id(),status,reason);
        return detail(id,admin);
    }
    @Transactional public void revoke(UUID dossierId,User admin,String reason) {
        requireAdmin(admin); locked(dossierId);
        int updated=db.update("UPDATE digital_card SET revoked_at=now() WHERE dossier_id=? AND revoked_at IS NULL",dossierId);
        if(updated!=1) throw new ResponseStatusException(CONFLICT,"Carte absente ou déjà révoquée");
        audit(dossierId,admin.id(),"CARD_REVOKED",reason.trim());
    }
    private static void requireAdmin(User user) {
        if(!user.role().equals("ADMIN")) throw new ResponseStatusException(FORBIDDEN,"Accès réservé à l’administration");
    }
    private void audit(UUID dossier,UUID actor,String action,String reason) {
        db.update("INSERT INTO decision_audit(id,dossier_id,actor_id,action,reason) VALUES (?,?,?,?,?)",UUID.randomUUID(),dossier,actor,action,reason);
    }
    public List<Map<String,Object>> audit(UUID id,User user) {
        requireAdmin(user); detail(id,user);
        return db.queryForList("SELECT actor_id,action,reason,created_at FROM decision_audit WHERE dossier_id=? ORDER BY created_at,id",id);
    }
    public Card card(User user) {
        UUID dossier=mine(user).id();
        return db.query("SELECT * FROM digital_card WHERE dossier_id=?",(rs,n)->new Card(
            rs.getObject("id",UUID.class),dossier,rs.getString("public_token"),time(rs,"issued_at"),time(rs,"expires_at"),time(rs,"revoked_at"),
            publicUrl+"/verify/"+rs.getString("public_token")),dossier).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Carte non délivrée"));
    }
    public Verification verify(String token) {
        if(!token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(NOT_FOUND,"Carte inconnue");
        return db.query("SELECT c.*,d.status FROM digital_card c JOIN dossier d ON d.id=c.dossier_id WHERE c.public_token=?",(rs,n)->{
            OffsetDateTime expiry=time(rs,"expires_at");
            String status=time(rs,"revoked_at")!=null?"REVOKED":!expiry.isAfter(OffsetDateTime.now())?"EXPIRED":
                !rs.getString("status").equals("APPROVED")?"INVALID":"VALID";
            return new Verification(status.equals("VALID"),status,time(rs,"issued_at"),expiry);
        },token).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Carte inconnue"));
    }
    public record Download(byte[] bytes,String mediaType) {}
    private record Stored(UUID dossierId,String key,String type) {}
}
