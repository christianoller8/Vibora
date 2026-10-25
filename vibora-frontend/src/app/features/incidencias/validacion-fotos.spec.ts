import { MAX_FOTOS_INCIDENCIA, TAMANO_MAX_FOTO } from '../../shared/models/incidencia.model';
import { validarFotos } from './validacion-fotos';

function archivo(nombre: string, tipo: string, bytes = 100): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

describe('validarFotos', () => {
  it.each(['image/jpeg', 'image/png', 'image/webp'])('acepta %s', (tipo) => {
    const foto = archivo('a', tipo);
    expect(validarFotos(0, [foto])).toEqual({ aceptadas: [foto], errores: [] });
  });

  it.each(['image/gif', 'image/svg+xml', 'application/pdf', 'text/plain', ''])(
    'rechaza el tipo "%s" nombrando el archivo y los formatos válidos',
    (tipo) => {
      const resultado = validarFotos(0, [archivo('doc.xyz', tipo)]);

      expect(resultado.aceptadas).toEqual([]);
      expect(resultado.errores).toHaveLength(1);
      expect(resultado.errores[0]).toContain('doc.xyz');
      expect(resultado.errores[0]).toContain('JPEG, PNG o WebP');
    },
  );

  it('acepta exactamente 5 MB y rechaza 5 MB + 1 byte', () => {
    const justo = archivo('justo.png', 'image/png', TAMANO_MAX_FOTO);
    const pasado = archivo('grande.png', 'image/png', TAMANO_MAX_FOTO + 1);

    const resultado = validarFotos(0, [justo, pasado]);

    expect(resultado.aceptadas).toEqual([justo]);
    expect(resultado.errores).toHaveLength(1);
    expect(resultado.errores[0]).toContain('grande.png');
    expect(resultado.errores[0]).toContain('5 MB');
  });

  it('rechaza un archivo vacío', () => {
    const resultado = validarFotos(0, [archivo('vacia.png', 'image/png', 0)]);

    expect(resultado.aceptadas).toEqual([]);
    expect(resultado.errores[0]).toContain('vacia.png');
    expect(resultado.errores[0]).toContain('vacío');
  });

  it('respeta el máximo de 4 fotos contando las que ya hay', () => {
    const nuevas = [1, 2, 3].map((i) => archivo(`f${i}.png`, 'image/png'));

    const resultado = validarFotos(2, nuevas);

    expect(MAX_FOTOS_INCIDENCIA).toBe(4);
    expect(resultado.aceptadas).toEqual([nuevas[0], nuevas[1]]);
    expect(resultado.errores).toHaveLength(1);
    expect(resultado.errores[0]).toContain('f3.png');
    expect(resultado.errores[0]).toContain('4');
  });

  it('con el cupo lleno no acepta ninguna', () => {
    const resultado = validarFotos(4, [archivo('f.png', 'image/png')]);

    expect(resultado.aceptadas).toEqual([]);
    expect(resultado.errores).toHaveLength(1);
  });

  it('los archivos inválidos no consumen cupo', () => {
    const malo = archivo('malo.gif', 'image/gif');
    const bueno = archivo('bueno.png', 'image/png');

    const resultado = validarFotos(3, [malo, bueno]);

    expect(resultado.aceptadas).toEqual([bueno]);
    expect(resultado.errores).toHaveLength(1);
    expect(resultado.errores[0]).toContain('malo.gif');
  });

  it('sin archivos no hay nada que decir', () => {
    expect(validarFotos(0, [])).toEqual({ aceptadas: [], errores: [] });
  });
});
