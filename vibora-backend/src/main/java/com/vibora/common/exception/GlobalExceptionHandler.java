package com.vibora.common.exception;

import com.vibora.auth.PasswordMaxBytes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.util.Map;

@RestControllerAdvice
@Slf4j
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

    // 409: bloqueo optimista (@Version): otra persona modifico la misma fila entre la lectura y la escritura
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> modificadaPorOtraPersona(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La incidencia fue modificada por otra persona, recarga"));
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

    // 400: red de seguridad para una fecha que desborda el calendario al operar con ella (los filtros ya se acotan antes
    // con LimitesFecha); DateTimeParseException es una subclase
    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<Map<String, String>> fechaInvalida(DateTimeException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Datos no válidos"));
    }

    // 413: fichero subido demasiado grande (limite de multipart del contenedor o comprobacion del servicio)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> subidaDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "La imagen supera el tamaño máximo de 5 MB"));
    }

    @ExceptionHandler(ArchivoDemasiadoGrandeException.class)
    public ResponseEntity<Map<String, String>> archivoDemasiadoGrande(ArchivoDemasiadoGrandeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage()));
    }

    // 500: el almacen de ficheros no pudo guardar (la transaccion se deshace: no queda fila ni fichero a medias)
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> errorDeAlmacen(UncheckedIOException ex) {
        log.error("Error de E/S en el almacén de ficheros", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo guardar la imagen; inténtalo de nuevo"));
    }

    // 400: falta la parte "file" o la peticion no es multipart valida
    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    public ResponseEntity<Map<String, String>> subidaInvalida(Exception ex) {
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
