package TP.SpringBootGrupal.config;

import TP.SpringBootGrupal.entities.Articulo;
import TP.SpringBootGrupal.entities.ListaPrecio;
import TP.SpringBootGrupal.entities.ListaPrecioArticulo;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.ArticuloRepository;
import TP.SpringBootGrupal.repository.ListaPrecioArticuloRepository;
import TP.SpringBootGrupal.repository.ListaPrecioRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Completa el catálogo hasta tener 100 artículos de prueba (ART-001 a ART-100),
 * cada uno con precio en todas las listas de precios activas.
 * Si ya hay 100 artículos o más, no hace nada.
 */
@Component
public class ArticulosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ArticulosIniciales.class);

    public static final int CANTIDAD_ARTICULOS = 100;

    // 20 productos x 5 variantes = 100 artículos
    private static final String[] PRODUCTOS = {
            "Cuaderno", "Lapicera", "Lápiz", "Goma de borrar", "Regla",
            "Carpeta", "Resaltador", "Marcador", "Tijera", "Pegamento",
            "Cartuchera", "Mochila", "Calculadora", "Abrochadora", "Cinta adhesiva",
            "Hojas A4", "Block de dibujo", "Compás", "Corrector", "Agenda"
    };
    private static final String[] VARIANTES = {"azul", "rojo", "negro", "verde", "x unidad"};

    private final ArticuloRepository articuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final UsuarioRepository usuarioRepository;

    public ArticulosIniciales(
            ArticuloRepository articuloRepository,
            ListaPrecioRepository listaPrecioRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.articuloRepository = articuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (articuloRepository.count() >= CANTIDAD_ARTICULOS) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.warn("No hay usuarios cargados: no se pueden crear los artículos de prueba");
            return;
        }

        Usuario usuario = usuarios.get(0);
        Date ahora = new Date();
        List<ListaPrecio> listas = obtenerListas(usuario, ahora);
        int creados = 0;

        for (int i = 1; i <= CANTIDAD_ARTICULOS; i++) {
            String codigo = String.format("ART-%03d", i);
            if (articuloRepository.existsByCodigo(codigo)) {
                continue;
            }

            Articulo articulo = new Articulo();
            articulo.setCodigo(codigo);
            articulo.setDenominacion(PRODUCTOS[(i - 1) / VARIANTES.length] + " " + VARIANTES[(i - 1) % VARIANTES.length]);
            auditar(articulo, usuario, ahora);

            // Precio base entre $500 y $10.400; cada lista siguiente es un 10% más barata
            double precioBase = 500 + (i * 99) % 9900;
            for (int l = 0; l < listas.size(); l++) {
                ListaPrecioArticulo precio = new ListaPrecioArticulo();
                precio.setArticulo(articulo);
                precio.setListaPrecio(listas.get(l));
                precio.setPrecioVenta(Math.round(precioBase * (1 - 0.1 * l)));
                auditar(precio, usuario, ahora);
                listaPrecioArticuloRepository.save(precio);
            }
            creados++;
        }

        log.info("Se cargaron {} artículos de prueba", creados);
    }

    private List<ListaPrecio> obtenerListas(Usuario usuario, Date ahora) {
        List<ListaPrecio> listas = listaPrecioRepository.findByFechaBajaIsNullOrderByDenominacion();
        if (!listas.isEmpty()) {
            return listas;
        }

        ListaPrecio general = new ListaPrecio();
        general.setCodigo("GEN");
        general.setDenominacion("Lista general");
        auditar(general, usuario, ahora);
        return List.of(listaPrecioRepository.save(general));
    }

    private void auditar(TP.SpringBootGrupal.entities.AuditoriaApp entidad, Usuario usuario, Date ahora) {
        entidad.setFechaAlta(ahora);
        entidad.setFechaModificacion(ahora);
        entidad.setUsuarioCarga(usuario);
        entidad.setUsuarioModificacion(usuario);
    }
}
