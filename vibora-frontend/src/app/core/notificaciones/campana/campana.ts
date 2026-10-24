import { Component, computed, inject } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { Notificacion } from '../../../shared/models/notificacion.model';
import { tiempoRelativo } from '../../util/tiempo-relativo';
import { destinoNotificacion } from '../notificacion-destino';
import { NotificacionesStore } from '../notificaciones.store';

const TOPE_INSIGNIA = 9;

/** Campana de la barra superior: contador de no leídas y menú con las notificaciones recientes. */
@Component({
  selector: 'app-campana',
  imports: [MatBadgeModule, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './campana.html',
  styleUrl: './campana.scss',
})
export class Campana {
  private readonly store = inject(NotificacionesStore);
  private readonly router = inject(Router);

  protected readonly items = this.store.items;
  protected readonly noLeidas = this.store.noLeidas;

  protected readonly textoInsignia = computed(() => {
    const n = this.noLeidas();
    return n > TOPE_INSIGNIA ? `${TOPE_INSIGNIA}+` : String(n);
  });

  protected readonly etiqueta = computed(() => {
    const n = this.noLeidas();
    return n === 0 ? 'Notificaciones, ninguna sin leer' : `Notificaciones, ${n} sin leer`;
  });

  protected readonly tiempo = tiempoRelativo;

  protected etiquetaItem(notificacion: Notificacion): string {
    const estado = notificacion.leida ? '' : ', sin leer';
    return `${notificacion.mensaje}, ${tiempoRelativo(notificacion.creadaEn)}${estado}`;
  }

  protected abrir(notificacion: Notificacion): void {
    if (!notificacion.leida) {
      this.store.marcarLeida(notificacion.id);
    }
    const destino = destinoNotificacion(notificacion.tipo);
    if (destino) {
      void this.router.navigateByUrl(destino);
    }
  }

  protected marcarTodas(evento: Event): void {
    evento.stopPropagation(); // el menú sigue abierto para ver cómo se atenúan
    this.store.marcarTodas();
  }
}
