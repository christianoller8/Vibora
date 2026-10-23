import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { AuthService } from '../../../core/auth/auth.service';
import { DatosClub } from '../datos-club/datos-club';
import { PistasClub } from '../pistas-club/pistas-club';
import { ReservasClub } from '../reservas-club/reservas-club';
import { TarifasClub } from '../tarifas-club/tarifas-club';

/** Panel del administrador de un club: datos, pistas, tarifas y reservas del día en pestañas. */
@Component({
  selector: 'app-club-admin',
  imports: [MatTabsModule, DatosClub, PistasClub, TarifasClub, ReservasClub],
  templateUrl: './club-admin.html',
  styleUrl: './club-admin.scss',
})
export class ClubAdmin {
  protected readonly auth = inject(AuthService);
}
