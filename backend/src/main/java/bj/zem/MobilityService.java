package bj.zem;

import bj.zem.ApiModels.User;
import static bj.zem.MobilityController.*;
import static org.springframework.http.HttpStatus.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MobilityService {
    private final JdbcTemplate db;
    MobilityService(JdbcTemplate db) { this.db=db; }
    private void eligible(User u) {
        // Lock the dossier before checking its card; decisions/revocation use the same lock.
        var ds=db.queryForList("SELECT id FROM dossier WHERE driver_id=? AND status='APPROVED' FOR UPDATE",u.id());
        if(ds.isEmpty() || db.queryForObject("SELECT count(*) FROM digital_card WHERE dossier_id=? AND revoked_at IS NULL AND expires_at>now()",Integer.class,ds.get(0).get("id"))!=1)
            throw new ResponseStatusException(CONFLICT,"Une carte valide est requise pour passer en service");
    }
    private void audit(User u,UUID ride,String action) { db.update("INSERT INTO mobility_audit(id,ride_id,actor_id,action) VALUES (?,?,?,?)",UUID.randomUUID(),ride,u.id(),action); }
    private Map<String,Object> ride(UUID id) { return db.queryForMap("SELECT * FROM ride WHERE id=?",id); }
    @Transactional public Map<String,Object> book(User u,Booking b) {
        // A short pilot tariff, not a regulatory tariff: 500 FCFA + 200/km straight-line, rounded to 100.
        double lat1=Math.toRadians(b.pickupLat()),lat2=Math.toRadians(b.destinationLat());
        double h=Math.pow(Math.sin((lat2-lat1)/2),2)+Math.cos(lat1)*Math.cos(lat2)*Math.pow(Math.sin(Math.toRadians(b.destinationLon()-b.pickupLon())/2),2);
        double km=6371*2*Math.asin(Math.sqrt(Math.min(1,h)));
        if(km>100) throw new ResponseStatusException(BAD_REQUEST,"Le pilote est limité aux trajets de 100 km à vol d’oiseau");
        UUID id=UUID.randomUUID();long fare=Math.round((500+200*km)/100)*100;
        db.update("INSERT INTO ride(id,passenger_id,pickup,destination,pickup_lat,pickup_lon,destination_lat,destination_lon,estimated_fare) VALUES (?,?,?,?,?,?,?,?,?)",id,u.id(),b.pickup().trim(),b.destination().trim(),b.pickupLat(),b.pickupLon(),b.destinationLat(),b.destinationLon(),fare);
        audit(u,id,"REQUESTED");return ride(id);
    }
    public List<Map<String,Object>> history(User u) {
        return db.queryForList("SELECT * FROM ride WHERE "+(u.role().equals("PASSENGER")?"passenger_id":"driver_id")+"=? ORDER BY created_at DESC LIMIT 50",u.id());
    }
    public Map<String,Object> availability(User u) {
        var a=db.queryForList("SELECT online,latitude,longitude,updated_at FROM driver_service WHERE driver_id=?",u.id());
        return a.isEmpty()?Map.of("online",false):a.get(0);
    }
    @Transactional public Map<String,Object> availability(User u,Availability a) {
        if(a.online()) {
            eligible(u);
            if(a.latitude()==null || a.longitude()==null) throw new ResponseStatusException(BAD_REQUEST,"Votre position est requise en service");
        }
        db.update("INSERT INTO driver_service(driver_id,online,latitude,longitude) VALUES (?,?,?,?) ON CONFLICT(driver_id) DO UPDATE SET online=excluded.online,latitude=excluded.latitude,longitude=excluded.longitude,updated_at=now()",u.id(),a.online(),a.online()?a.latitude():null,a.online()?a.longitude():null);
        return availability(u);
    }
    @Transactional public List<Map<String,Object>> requests(User u) {
        eligible(u);
        // Do not expose the passenger account ID or far-away pick-up coordinates to the driver queue.
        return db.queryForList("SELECT r.id,r.pickup,r.destination,r.estimated_fare,r.created_at FROM ride r JOIN driver_service s ON s.driver_id=? AND s.online AND s.updated_at>now()-interval '2 minutes' WHERE r.status='REQUESTED' AND abs(r.pickup_lat-s.latitude)<0.15 AND abs(r.pickup_lon-s.longitude)<0.15 ORDER BY r.created_at LIMIT 30",u.id());
    }
    @Transactional public Map<String,Object> accept(User u,UUID id) {
        eligible(u);
        // Unique partial index also prevents a driver accepting two different rides concurrently.
        int n=db.update("UPDATE ride r SET driver_id=?,status='ACCEPTED',updated_at=now() WHERE r.id=? AND r.status='REQUESTED' AND EXISTS (SELECT 1 FROM driver_service s WHERE s.driver_id=? AND s.online AND s.updated_at>now()-interval '2 minutes' AND abs(r.pickup_lat-s.latitude)<0.15 AND abs(r.pickup_lon-s.longitude)<0.15)",u.id(),id,u.id());
        if(n!=1) throw new ResponseStatusException(CONFLICT,"Course indisponible ou position trop ancienne");
        audit(u,id,"ACCEPTED");return ride(id);
    }
    @Transactional public Map<String,Object> transition(User u,UUID id,String action,Long amount) {
        var rows=db.queryForList("SELECT * FROM ride WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Course introuvable");
        var r=rows.get(0);boolean driver=u.role().equals("DRIVER")&&u.id().equals(r.get("driver_id"));
        boolean passenger=u.role().equals("PASSENGER")&&u.id().equals(r.get("passenger_id"));
        if(!driver&&!passenger) throw new ResponseStatusException(NOT_FOUND,"Course introuvable");
        String current=(String)r.get("status");String next;
        if(action.equals("CANCEL")&&(current.equals("REQUESTED")||current.equals("ACCEPTED"))) next="CANCELLED";
        else if(driver&&action.equals("START")&&current.equals("ACCEPTED")) next="IN_PROGRESS";
        else if(driver&&action.equals("COMPLETE")&&current.equals("IN_PROGRESS")) next="COMPLETED";
        else throw new ResponseStatusException(CONFLICT,"Transition de course refusée");
        db.update("UPDATE ride SET status=?,declared_fare=?,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,updated_at=now() WHERE id=?",next,amount,next,id);
        audit(u,id,next);return ride(id);
    }
    public Map<String,Object> location(User u,UUID id) {
        // Only the passenger in this accepted/ongoing ride sees a recent driver position. Never admin-wide GPS.
        var rows=db.queryForList("SELECT s.latitude,s.longitude,s.updated_at FROM ride r JOIN driver_service s ON s.driver_id=r.driver_id WHERE r.id=? AND r.passenger_id=? AND r.status IN ('ACCEPTED','IN_PROGRESS') AND s.online AND s.updated_at>now()-interval '2 minutes'",id,u.id());
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Position indisponible");
        return rows.get(0);
    }
    @Transactional public Map<String,Object> declare(User u,Declaration d) {
        db.update("INSERT INTO activity_declaration(driver_id,day,off_platform_revenue,off_platform_rides,expenses) VALUES (?,?,?,?,?) ON CONFLICT(driver_id,day) DO UPDATE SET off_platform_revenue=excluded.off_platform_revenue,off_platform_rides=excluded.off_platform_rides,expenses=excluded.expenses,updated_at=now()",u.id(),d.day(),d.offPlatformRevenue(),d.offPlatformRides(),d.expenses());
        return earnings(u.id(),d.day());
    }
    public Map<String,Object> earnings(UUID driver,LocalDate day) {
        var r=db.queryForMap("SELECT count(*) AS rides,coalesce(sum(declared_fare),0) AS recorded_revenue FROM ride WHERE driver_id=? AND status='COMPLETED' AND (completed_at AT TIME ZONE 'Africa/Porto-Novo')::date=?",driver,day);
        var d=db.queryForList("SELECT off_platform_revenue,off_platform_rides,expenses FROM activity_declaration WHERE driver_id=? AND day=?",driver,day);
        Map<String,Object> result=new LinkedHashMap<>(r);result.put("day",day);result.put("declarationPresent",!d.isEmpty());
        result.put("offPlatformRevenue",d.isEmpty()?null:d.get(0).get("off_platform_revenue"));
        result.put("offPlatformRides",d.isEmpty()?null:d.get(0).get("off_platform_rides"));
        Object expenses=d.isEmpty()?null:d.get(0).get("expenses");result.put("expenses",expenses);
        long tracked=((Number)r.get("recorded_revenue")).longValue()+(d.isEmpty()?0:((Number)d.get(0).get("off_platform_revenue")).longValue());
        result.put("trackedRevenue",tracked);result.put("estimatedBalance",expenses==null?null:tracked-((Number)expenses).longValue());
        result.put("currency","FCFA");result.put("source","Montants déclarés ; encaissement non vérifié");return result;
    }
    public Map<String,Object> overview(LocalDate day) {
        var stats=db.queryForMap("SELECT count(*) AS completed_rides,coalesce(sum(declared_fare),0) AS recorded_revenue FROM ride WHERE status='COMPLETED' AND (completed_at AT TIME ZONE 'Africa/Porto-Novo')::date=?",day);
        var declarations=db.queryForMap("SELECT count(*) AS declaring_drivers,count(expenses) AS expenses_known,coalesce(sum(off_platform_revenue),0) AS off_platform_revenue,coalesce(sum(expenses),0) AS declared_expenses FROM activity_declaration WHERE day=?",day);
        return Map.of("day",day,"courses",stats,"declarations",declarations,"onlineDrivers",db.queryForObject("SELECT count(*) FROM driver_service s JOIN dossier d ON d.driver_id=s.driver_id JOIN digital_card c ON c.dossier_id=d.id WHERE s.online AND s.updated_at>now()-interval '2 minutes' AND c.revoked_at IS NULL AND c.expires_at>now()",Integer.class),"source","Déclarations ; pas de revenu national extrapolé");
    }
    public List<Map<String,Object>> activity(LocalDate day,int page) {
        if(page<0 || page>10000) throw new ResponseStatusException(BAD_REQUEST,"Page invalide");
        return db.queryForList("""
            SELECT d.driver_id,d.full_name,d.plate,d.status,
              coalesce(r.rides,0) AS recorded_rides,coalesce(r.revenue,0) AS recorded_revenue,
              a.off_platform_revenue,a.off_platform_rides,a.expenses,
              CASE WHEN a.expenses IS NOT NULL THEN coalesce(r.revenue,0)+a.off_platform_revenue-a.expenses END AS estimated_balance
            FROM dossier d
            LEFT JOIN (SELECT driver_id,count(*) AS rides,sum(declared_fare) AS revenue FROM ride
              WHERE status='COMPLETED' AND (completed_at AT TIME ZONE 'Africa/Porto-Novo')::date=? GROUP BY driver_id) r ON r.driver_id=d.driver_id
            LEFT JOIN activity_declaration a ON a.driver_id=d.driver_id AND a.day=?
            ORDER BY d.full_name,d.driver_id LIMIT 50 OFFSET ?
            """,day,day,page*50);
    }
}
