package bj.zem;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import org.springframework.boot.SpringApplication;

/** Isolated, loopback-only synthetic backend for browser E2E; never packaged in the production JAR. */
public final class LocalE2eServer {
    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg=EmbeddedPostgres.builder().start();
        var app=new SpringApplication(ZemApplication.class);
        app.run("--spring.datasource.url="+pg.getJdbcUrl("postgres","postgres"),
            "--spring.datasource.username=postgres","--spring.datasource.password=postgres",
            "--zem.storage="+Files.createTempDirectory("zem-e2e-docs"),
            "--zem.bootstrap-email=admin@zem.test","--zem.bootstrap-password=Admin-test-password-17",
            "--server.address=127.0.0.1","--server.port=18080","--zem.public-url=http://localhost:3000");
        Runtime.getRuntime().addShutdownHook(new Thread(()-> { try { pg.close(); } catch(Exception ignored) {} }));
    }
}
