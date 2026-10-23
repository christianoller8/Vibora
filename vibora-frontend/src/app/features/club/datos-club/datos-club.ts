import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ClubService } from '../../../core/api/club.service';
import { mensajeDeError } from '../../../core/http/error.util';
import { Club, ClubRequest } from '../../../shared/models/club.model';
import { ClubFormulario } from '../club-formulario/club-formulario';

@Component({
  selector: 'app-datos-club',
  imports: [MatButtonModule, MatProgressSpinnerModule, ClubFormulario],
  templateUrl: './datos-club.html',
  styleUrl: './datos-club.scss',
})
export class DatosClub implements OnInit {
  private readonly clubService = inject(ClubService);
  private readonly snack = inject(MatSnackBar);

  readonly clubId = input.required<number>();

  protected readonly club = signal<Club | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorGuardado = signal<string | null>(null);

  /** `clubId` es un input requerido: no se puede leer en el constructor, solo a partir de ngOnInit. */
  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.clubService.obtener(this.clubId()).subscribe({
      next: (club) => {
        this.club.set(club);
        this.cargando.set(false);
      },
      error: (err: unknown) => {
        this.errorCarga.set(mensajeDeError(err, 'No se pudieron cargar los datos del club.'));
        this.cargando.set(false);
      },
    });
  }

  protected guardar(datos: ClubRequest): void {
    if (this.guardando()) {
      return;
    }
    this.guardando.set(true);
    this.errorGuardado.set(null);
    this.clubService.actualizar(this.clubId(), datos).subscribe({
      next: (club) => {
        this.club.set(club);
        this.guardando.set(false);
        this.snack.open('Datos del club guardados.', 'Cerrar', { duration: 5000 });
      },
      error: (err: unknown) => {
        this.errorGuardado.set(
          mensajeDeError(err, 'No se pudieron guardar los cambios. Inténtalo de nuevo.'),
        );
        this.guardando.set(false);
      },
    });
  }
}
