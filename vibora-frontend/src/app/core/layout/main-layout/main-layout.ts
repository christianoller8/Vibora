import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { Campana } from '../../notificaciones/campana/campana';
import { TiempoRealService } from '../../notificaciones/tiempo-real.service';
import { ETIQUETA_ROL } from '../../../shared/models/usuario.model';

@Component({
  selector: 'app-main-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    Campana,
  ],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly etiquetaRol = ETIQUETA_ROL;

  // En móvil el menú se superpone y arranca cerrado; en escritorio empuja el contenido y arranca abierto
  protected readonly esMovil = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.XSmall)
      .pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );
  // controla si el menú lateral está abierto o cerrado
  protected readonly menuAbierto = linkedSignal(() => !this.esMovil());

  constructor() {
    // Instancia el servicio que abre/cierra la conexión en tiempo real según la sesión. Se hace aquí (layout
    // diferido) y no al arrancar para no meter snackbar/overlay en el bundle inicial.
    inject(TiempoRealService);
  }

  protected alternarMenu(): void {
    this.menuAbierto.update((abierto) => !abierto);
  }

  /** En móvil el menú tapa el contenido, así que se cierra al navegar. */
  protected alNavegar(): void {
    if (this.esMovil()) {
      this.menuAbierto.set(false);
    }
  }

  protected salir(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
}
