import { Component, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { ClubService } from '../../../core/api/club.service';
import { AuthService } from '../../../core/auth/auth.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { ClubRequest } from '../../../shared/models/club.model';
import { ClubFormulario } from '../club-formulario/club-formulario';
import { ClubCreadoDialog, ClubCreadoDialogData } from './club-creado-dialog';

/** Onboarding: un jugador sin club crea el suyo y pasa a administrarlo (tras volver a iniciar sesión). */
@Component({
  selector: 'app-crear-club',
  imports: [ClubFormulario],
  templateUrl: './crear-club.html',
  styleUrl: './crear-club.scss',
})
export class CrearClub {
  private readonly clubService = inject(ClubService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected crear(datos: ClubRequest): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.clubService.crear(datos).subscribe({
      next: (club) => {
        // El backend ya nos ha hecho ADMIN_CLUB, pero el token que tenemos sigue diciendo JUGADOR y no se
        // puede reutilizar: hay que volver a iniciar sesión. Se avisa y, al cerrar el aviso, se sale.
        this.dialog
          .open<ClubCreadoDialog, ClubCreadoDialogData>(ClubCreadoDialog, {
            disableClose: true,
            data: { nombre: club.nombre },
          })
          .afterClosed()
          .subscribe(() => {
            this.auth.logout();
            void this.router.navigateByUrl('/login');
          });
      },
      error: (err: unknown) => {
        this.error.set(mensajeDeError(err, 'No se pudo crear el club. Inténtalo de nuevo.'));
        this.enviando.set(false);
      },
    });
  }
}
