package com.vibora.partido;

import jakarta.validation.constraints.NotNull;

public record AbiertaRequest(@NotNull Boolean abierta) {
}
