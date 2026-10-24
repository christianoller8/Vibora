package com.vibora.almacen;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** vibora.almacen.directorio: carpeta base de los ficheros subidos (se crea al guardar el primero si no existe). */
@ConfigurationProperties(prefix = "vibora.almacen")
public record AlmacenProperties(@DefaultValue("./data/uploads") String directorio) {
}
