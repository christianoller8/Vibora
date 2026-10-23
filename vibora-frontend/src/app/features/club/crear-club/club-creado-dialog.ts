import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ClubCreadoDialogData {
  nombre: string;
}

/** Aviso tras crear el club: el token sigue diciendo JUGADOR, así que hay que volver a iniciar sesión. */
@Component({
  selector: 'app-club-creado-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Club creado</h2>
    <mat-dialog-content>
      <p>
        Tu club <strong>{{ datos.nombre }}</strong> ya existe y tú eres su administrador.
      </p>
      <p>
        Para activar tus permisos de administrador tienes que iniciar sesión de nuevo: cerraremos tu
        sesión actual y te llevaremos a la pantalla de acceso.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button type="button" data-accion="entendido" (click)="cerrar()">
        Iniciar sesión de nuevo
      </button>
    </mat-dialog-actions>
  `,
})
export class ClubCreadoDialog {
  protected readonly datos = inject<ClubCreadoDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ClubCreadoDialog>>(MatDialogRef);

  protected cerrar(): void {
    this.dialogRef.close();
  }
}
