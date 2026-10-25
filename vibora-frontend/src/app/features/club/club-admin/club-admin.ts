import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { AuthService } from '../../../core/auth/auth.service';
import { BloqueosClub } from '../bloqueos-club/bloqueos-club';
import { DatosClub } from '../datos-club/datos-club';
import { PersonalClub } from '../personal-club/personal-club';
import { PistasClub } from '../pistas-club/pistas-club';
import { ReservasClub } from '../reservas-club/reservas-club';
import { TarifasClub } from '../tarifas-club/tarifas-club';

/** Panel del administrador de un club: datos, pistas, tarifas, reservas del día, personal y bloqueos en pestañas. */
@Component({
  selector: 'app-club-admin',
  imports: [
    MatTabsModule,
    DatosClub,
    PistasClub,
    TarifasClub,
    ReservasClub,
    PersonalClub,
    BloqueosClub,
  ],
  templateUrl: './club-admin.html',
  styleUrl: './club-admin.scss',
})
export class ClubAdmin {
  protected readonly auth = inject(AuthService);
}
