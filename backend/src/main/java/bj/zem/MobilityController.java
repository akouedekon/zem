package bj.zem;

import bj.zem.ApiModels.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MobilityController {
    private final MobilityService mobility;
    MobilityController(MobilityService mobility) { this.mobility=mobility; }
    public record Booking(@NotBlank @Size(max=160) String pickup,@NotBlank @Size(max=160) String destination,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLon,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double destinationLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double destinationLon) {}
    public record Availability(@NotNull Boolean online,@DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record Fare(@NotNull @Min(0) @Max(1000000) Long amount) {}
    public record Declaration(@NotNull @PastOrPresent LocalDate day,@Min(0) @Max(1000000) long offPlatformRevenue,
        @Min(0) @Max(1000) int offPlatformRides,@Min(0) @Max(1000000) Long expenses) {}
    @PostMapping("/passenger/rides") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Map<String,Object> book(@AuthenticationPrincipal User u,@Valid @RequestBody Booking b) { return mobility.book(u,b); }
    @GetMapping("/passenger/rides") List<Map<String,Object>> passengerRides(@AuthenticationPrincipal User u) { return mobility.history(u); }
    @GetMapping("/driver/rides") List<Map<String,Object>> driverRides(@AuthenticationPrincipal User u) { return mobility.history(u); }
    @GetMapping("/driver/requests") List<Map<String,Object>> requests(@AuthenticationPrincipal User u) { return mobility.requests(u); }
    @GetMapping("/driver/service") Map<String,Object> service(@AuthenticationPrincipal User u) { return mobility.availability(u); }
    @PutMapping("/driver/service") Map<String,Object> service(@AuthenticationPrincipal User u,@Valid @RequestBody Availability a) { return mobility.availability(u,a); }
    @PostMapping("/driver/rides/{id}/accept") Map<String,Object> accept(@AuthenticationPrincipal User u,@PathVariable UUID id) { return mobility.accept(u,id); }
    @PostMapping("/driver/rides/{id}/start") Map<String,Object> start(@AuthenticationPrincipal User u,@PathVariable UUID id) { return mobility.transition(u,id,"START",null); }
    @PostMapping("/driver/rides/{id}/complete") Map<String,Object> complete(@AuthenticationPrincipal User u,@PathVariable UUID id,@Valid @RequestBody Fare f) { return mobility.transition(u,id,"COMPLETE",f.amount()); }
    @PostMapping("/rides/{id}/cancel") Map<String,Object> cancel(@AuthenticationPrincipal User u,@PathVariable UUID id) { return mobility.transition(u,id,"CANCEL",null); }
    @GetMapping("/rides/{id}/location") Map<String,Object> location(@AuthenticationPrincipal User u,@PathVariable UUID id) { return mobility.location(u,id); }
    @PutMapping("/driver/declarations") Map<String,Object> declare(@AuthenticationPrincipal User u,@Valid @RequestBody Declaration d) { return mobility.declare(u,d); }
    @GetMapping("/driver/earnings") Map<String,Object> earnings(@AuthenticationPrincipal User u,@RequestParam LocalDate day) { return mobility.earnings(u.id(),day); }
    @GetMapping("/admin/mobility") Map<String,Object> overview(@RequestParam LocalDate day) { return mobility.overview(day); }
    @GetMapping("/admin/activity") List<Map<String,Object>> activity(@RequestParam LocalDate day,@RequestParam(defaultValue="0") int page) { return mobility.activity(day,page); }
}
