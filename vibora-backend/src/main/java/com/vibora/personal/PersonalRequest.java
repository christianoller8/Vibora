package com.vibora.personal;

import com.vibora.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body para anadir personal. Solo MONITOR y MANTENIMIENTO son validos (lo comprueba el servicio). */
public record PersonalRequest(@NotBlank @Email @Size(max = 255) String email, @NotNull Rol rol) {
}
