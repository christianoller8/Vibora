import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Marco limpio (sin menú lateral) para login y registro. */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  templateUrl: './auth-layout.html',
  styleUrl: './auth-layout.scss',
})
export class AuthLayout {}
