package bj.zem;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Requête refusée":ex.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<?> validation(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message","Champs invalides")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception ex) { return ResponseEntity.status(409).body(Map.of("message","Conflit, rechargez le dossier")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> upload(Exception ex) { return ResponseEntity.status(413).body(Map.of("message","Document trop volumineux (5 Mo maximum)")); }
}
