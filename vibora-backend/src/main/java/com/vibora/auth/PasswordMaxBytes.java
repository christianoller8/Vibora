package com.vibora.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** La contraseña (normalizada a NFC) no puede superar los 72 bytes UTF-8 que admite BCrypt. */
@Documented
@Constraint(validatedBy = PasswordMaxBytes.Validador.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMaxBytes {

	String MENSAJE = "La contraseña no puede superar los 72 bytes en UTF-8";

	String message() default MENSAJE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validador implements ConstraintValidator<PasswordMaxBytes, String> {

		@Override
		public boolean isValid(String valor, ConstraintValidatorContext contexto) {
			return valor == null || !PoliticaPassword.excedeLimite(valor);
		}
	}
}
