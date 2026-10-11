CREATE TABLE app_user (
 id UUID PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(16) NOT NULL CHECK (role IN ('DRIVER','ADMIN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE auth_session (
 token_hash CHAR(64) PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX auth_session_expiry ON auth_session(expires_at);
CREATE TABLE dossier (
 id UUID PRIMARY KEY,
 driver_id UUID NOT NULL UNIQUE REFERENCES app_user(id),
 full_name VARCHAR(120) NOT NULL,
 phone VARCHAR(24) NOT NULL,
 plate VARCHAR(24) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')) DEFAULT 'DRAFT',
 rejection_reason VARCHAR(1000),
 submitted_at TIMESTAMPTZ,
 decided_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX dossier_queue ON dossier(status,submitted_at);
CREATE TABLE document (
 id UUID PRIMARY KEY,
 dossier_id UUID NOT NULL REFERENCES dossier(id),
 kind VARCHAR(24) NOT NULL CHECK (kind IN ('IDENTITY','DRIVING_LICENSE','VEHICLE')),
 storage_key VARCHAR(80) NOT NULL UNIQUE,
 media_type VARCHAR(40) NOT NULL,
 byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 5242880),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(dossier_id,kind)
);
CREATE TABLE digital_card (
 id UUID PRIMARY KEY,
 dossier_id UUID NOT NULL UNIQUE REFERENCES dossier(id),
 public_token VARCHAR(64) NOT NULL UNIQUE,
 issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ
);
CREATE TABLE decision_audit (
 id UUID PRIMARY KEY,
 dossier_id UUID NOT NULL REFERENCES dossier(id),
 actor_id UUID NOT NULL REFERENCES app_user(id),
 action VARCHAR(24) NOT NULL CHECK (action IN ('SUBMITTED','APPROVED','REJECTED','CARD_REVOKED')),
 reason VARCHAR(1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
