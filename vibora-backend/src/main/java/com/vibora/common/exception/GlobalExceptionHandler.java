package com.vibora.common.exception;

import com.vibora.auth.PasswordMaxBytes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404: el recurso pedido no existe (ej. id inexistente)
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    // 409: conflicto con el estado actual (ej. email ya registrado)
    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    // 401: credenciales incorrectas (mensaje generico, sin revelar si el email existe)
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, String>> credencialesInvalidas(CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    // 400: los datos del @Valid del body no cumplen las anotaciones del DTO
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException ex) {
        // Contraseña demasiado larga: el usuario necesita saber por que (el resto de errores se mantienen genericos)
        boolean passwordLarga = ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(e -> "PasswordMaxBytes".equals(e.getCode()));
        return ResponseEntity.badRequest()
                .body(Map.of("error", passwordLarga ? PasswordMaxBytes.MENSAJE : "Datos no válidos"));
    }

    // 400: body ilegible (JSON mal formado, enum desconocido, fecha/hora con formato invalido)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Datos no válidos"));
    }

    // 400: parametro de la URL con un valor imposible (ej. estado=NOPE, fecha=no-es-fecha)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Datos no válidos"));
    }

    // 400: regla de negocio incumplida (ej. valor duplicado)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> argumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
