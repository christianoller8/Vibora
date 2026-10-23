import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ConfirmDialogLinea {
  etiqueta: string;
  valor: string;
}

export interface ConfirmDialogData {
  titulo: string;
  mensaje?: string;
  lineas?: ConfirmDialogLinea[];
  textoConfirmar: string;
  textoCancelar?: string;
}

/** Diálogo genérico de confirmación: se cierra con `true` al confirmar y `false` al cancelar. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
})
export class ConfirmDialog {
  protected readonly datos = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ConfirmDialog, boolean>>(MatDialogRef);

  protected cerrar(confirmado: boolean): void {
    this.dialogRef.close(confirmado);
  }
}
