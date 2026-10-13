ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK(role IN ('DRIVER','ADMIN','PASSENGER'));
CREATE TABLE driver_service (
 driver_id UUID PRIMARY KEY REFERENCES app_user(id), online BOOLEAN NOT NULL DEFAULT false,
 latitude DOUBLE PRECISION CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION CHECK(longitude BETWEEN -180 AND 180),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE ride (
 id UUID PRIMARY KEY, passenger_id UUID NOT NULL REFERENCES app_user(id), driver_id UUID REFERENCES app_user(id),
 pickup VARCHAR(160) NOT NULL, destination VARCHAR(160) NOT NULL,
 pickup_lat DOUBLE PRECISION NOT NULL CHECK(pickup_lat BETWEEN -90 AND 90),
 pickup_lon DOUBLE PRECISION NOT NULL CHECK(pickup_lon BETWEEN -180 AND 180),
 destination_lat DOUBLE PRECISION NOT NULL CHECK(destination_lat BETWEEN -90 AND 90),
 destination_lon DOUBLE PRECISION NOT NULL CHECK(destination_lon BETWEEN -180 AND 180),
 status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' CHECK(status IN ('REQUESTED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')),
 estimated_fare BIGINT NOT NULL CHECK(estimated_fare BETWEEN 0 AND 1000000),
 declared_fare BIGINT CHECK(declared_fare BETWEEN 0 AND 1000000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ,
 CHECK(status='REQUESTED' OR status='CANCELLED' OR driver_id IS NOT NULL),
 CHECK(status<>'COMPLETED' OR (declared_fare IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ride_passenger_active ON ride(passenger_id) WHERE status IN ('REQUESTED','ACCEPTED','IN_PROGRESS');
CREATE UNIQUE INDEX ride_driver_active ON ride(driver_id) WHERE status IN ('ACCEPTED','IN_PROGRESS');
CREATE INDEX ride_queue ON ride(status,created_at);
CREATE TABLE activity_declaration (
 driver_id UUID NOT NULL REFERENCES app_user(id), day DATE NOT NULL,
 off_platform_revenue BIGINT NOT NULL CHECK(off_platform_revenue BETWEEN 0 AND 1000000),
 off_platform_rides INTEGER NOT NULL CHECK(off_platform_rides BETWEEN 0 AND 1000),
 expenses BIGINT CHECK(expenses BETWEEN 0 AND 1000000),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(driver_id,day)
);
CREATE TABLE mobility_audit (
 id UUID PRIMARY KEY, ride_id UUID NOT NULL REFERENCES ride(id), actor_id UUID NOT NULL REFERENCES app_user(id),
 action VARCHAR(24) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
