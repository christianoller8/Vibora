import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Insignia } from './insignia';

describe('Insignia', () => {
  let fixture: ComponentFixture<Insignia>;

  async function crear(texto: string, variante: string): Promise<HTMLElement> {
    await TestBed.configureTestingModule({ imports: [Insignia] }).compileComponents();
    fixture = TestBed.createComponent(Insignia);
    fixture.componentRef.setInput('texto', texto);
    fixture.componentRef.setInput('variante', variante);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el texto', async () => {
    const el = await crear('En curso', 'en_curso');

    expect(el.textContent?.trim()).toBe('En curso');
  });

  it('lleva la variante como clase para colorearla', async () => {
    const el = await crear('Urgente', 'urgente');

    expect(el.querySelector('.insignia')?.classList).toContain('variante-urgente');
  });
});
