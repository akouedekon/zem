package bj.zem;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Flyway owns DDL; Hibernate validates this mapping at startup. */
@Entity
@Table(name="app_user")
public class UserEntity {
    @Id private UUID id;
    @Column(nullable=false,length=254,unique=true) private String email;
    @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
    @Column(nullable=false,length=16) private String role;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    protected UserEntity() {}
    ApiModels.User publicUser() { return new ApiModels.User(id,email,role); }
    String passwordHash() { return passwordHash; }
}
