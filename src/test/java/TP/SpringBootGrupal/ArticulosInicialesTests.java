package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.ArticulosIniciales;
import TP.SpringBootGrupal.entities.ListaPrecio;
import TP.SpringBootGrupal.entities.ListaPrecioArticulo;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.ArticuloRepository;
import TP.SpringBootGrupal.repository.ListaPrecioArticuloRepository;
import TP.SpringBootGrupal.repository.ListaPrecioRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArticulosInicialesTests {
    private final ArticuloRepository articulos = mock(ArticuloRepository.class);
    private final ListaPrecioRepository listas = mock(ListaPrecioRepository.class);
    private final ListaPrecioArticuloRepository precios = mock(ListaPrecioArticuloRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private ArticulosIniciales cargador() {
        return new ArticulosIniciales(articulos, listas, precios, usuarios);
    }

    private ListaPrecio lista(long id, String nombre) {
        ListaPrecio lista = new ListaPrecio();
        lista.setId(id);
        lista.setDenominacion(nombre);
        return lista;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void cargaCienArticulosConPrecioEnCadaLista(int cantidadListas) {
        List<ListaPrecio> existentes = cantidadListas == 1
                ? List.of(lista(1, "General"))
                : List.of(lista(1, "General"), lista(2, "Mayorista"));
        when(articulos.count()).thenReturn(0L);
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(existentes);

        cargador().run();

        ArgumentCaptor<ListaPrecioArticulo> captor = ArgumentCaptor.forClass(ListaPrecioArticulo.class);
        verify(precios, times(ArticulosIniciales.CANTIDAD_ARTICULOS * cantidadListas)).save(captor.capture());
        var guardados = captor.getAllValues();
        var codigos = new HashSet<String>();
        for (ListaPrecioArticulo precio : guardados) {
            codigos.add(precio.getArticulo().getCodigo());
            assertTrue(precio.getPrecioVenta() > 0);
            assertNotNull(precio.getUsuarioCarga());
            assertNotNull(precio.getArticulo().getFechaAlta());
        }
        assertEquals(ArticulosIniciales.CANTIDAD_ARTICULOS, codigos.size());
    }

    @Test
    void creaUnaListaGeneralSiNoHayNinguna() {
        when(articulos.count()).thenReturn(0L);
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(List.of());
        when(listas.save(any(ListaPrecio.class))).thenAnswer(i -> i.getArgument(0));

        cargador().run();

        verify(listas).save(any(ListaPrecio.class));
        verify(precios, times(ArticulosIniciales.CANTIDAD_ARTICULOS)).save(any(ListaPrecioArticulo.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 120})
    void noCargaNadaSiYaHayArticulos(long existentes) {
        when(articulos.count()).thenReturn(existentes);
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));

        cargador().run();

        verify(precios, never()).save(any());
    }

    @Test
    void completaHastaCienSinRepetirCodigosExistentes() {
        when(articulos.count()).thenReturn(1L);
        when(articulos.existsByCodigo("ART-001")).thenReturn(true);
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(List.of(lista(1, "General")));

        cargador().run();

        verify(precios, times(ArticulosIniciales.CANTIDAD_ARTICULOS - 1)).save(any(ListaPrecioArticulo.class));
    }

    @Test
    void noCargaNadaSinUsuarios() {
        when(articulos.count()).thenReturn(0L);
        when(usuarios.findAll()).thenReturn(List.of());

        cargador().run();

        verify(precios, never()).save(any());
    }
}
