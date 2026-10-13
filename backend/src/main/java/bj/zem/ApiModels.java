package bj.zem;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=12,max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,
                        @NotBlank @Size(max=72) String password) {}
    public record Profile(@NotBlank @Size(max=120) String fullName,
                          @Pattern(regexp="^\\+?[0-9 ]{8,24}$") @NotBlank String phone,
                          @NotBlank @Size(max=24) String plate) {}
    public record Decision(@NotNull Boolean approve, @Size(max=1000) String reason) {}
    public record Reason(@NotBlank @Size(max=1000) String reason) {}
    public record User(UUID id, String email, String role) {}
    public record Auth(String token, OffsetDateTime expiresAt, User user) {}
    public record Dossier(UUID id, UUID driverId, String fullName, String phone, String plate,
                          String status, String rejectionReason, OffsetDateTime submittedAt,
                          OffsetDateTime decidedAt) {}
    public record Document(UUID id, String kind, String mediaType, long byteSize) {}
    public record Card(UUID id, UUID dossierId, String publicToken, OffsetDateTime issuedAt,
                       OffsetDateTime expiresAt, OffsetDateTime revokedAt, String verificationUrl) {}
    public record Verification(boolean valid, String status, OffsetDateTime issuedAt,
                               OffsetDateTime expiresAt) {}
}
