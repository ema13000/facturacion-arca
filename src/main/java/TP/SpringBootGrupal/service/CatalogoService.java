package TP.SpringBootGrupal.service;

import TP.SpringBootGrupal.Exceptions.RecursoNoEncontradoException;
import TP.SpringBootGrupal.dtos.OpcionDTO;
import TP.SpringBootGrupal.dtos.PuntoVentaDTO;
import TP.SpringBootGrupal.dtos.ClienteExplorerDTO;
import TP.SpringBootGrupal.dtos.PrecioArticuloExplorerDTO;
import TP.SpringBootGrupal.entities.Cliente;
import TP.SpringBootGrupal.entities.ListaPrecioArticulo;
import TP.SpringBootGrupal.repository.ClienteRepository;
import TP.SpringBootGrupal.repository.ListaPrecioArticuloRepository;
import TP.SpringBootGrupal.repository.ListaPrecioRepository;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.PuntoVentaRepository;
import TP.SpringBootGrupal.repository.TipoMonedaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogoService {

    // Máximo de filas que devuelve cada búsqueda: nunca se manda la tabla entera al front
    public static final int LIMITE_RESULTADOS = 20;

    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final ClienteRepository clienteRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;

    public CatalogoService(
            PuntoVentaRepository puntoVentaRepository,
            TipoMonedaRepository tipoMonedaRepository,
            CondicionIvaRepository condicionIvaRepository,
            ClienteRepository clienteRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            ListaPrecioRepository listaPrecioRepository
    ) {
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.clienteRepository = clienteRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
    }

    private PageRequest primeraPagina() {
        return PageRequest.of(0, LIMITE_RESULTADOS);
    }

    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    @Transactional(readOnly = true)
    public List<ClienteExplorerDTO> buscarClientes(String texto) {
        return clienteRepository.buscar(normalizar(texto), primeraPagina()).stream()
                .filter(cliente -> cliente.getFechaBaja() == null)
                .map(this::aClienteDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteExplorerDTO obtenerCliente(Long id) {
        return clienteRepository.findById(id)
                .filter(cliente -> cliente.getFechaBaja() == null)
                .map(this::aClienteDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente con ID " + id));
    }

    @Transactional(readOnly = true)
    public List<OpcionDTO> obtenerListasPrecio() {
        return listaPrecioRepository.findByFechaBajaIsNullOrderByDenominacion().stream()
                .map(lista -> new OpcionDTO(lista.getId(), lista.getDenominacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TP.SpringBootGrupal.dtos.PaginaDTO<PrecioArticuloExplorerDTO> buscarPreciosArticulos(
            Long listaPrecioId, String texto, int pagina) {
        Long lista = listaPrecioId == null || listaPrecioId <= 0 ? null : listaPrecioId;
        int numero = Math.max(pagina, 0);

        var resultado = listaPrecioArticuloRepository.buscarPagina(
                lista, normalizar(texto), PageRequest.of(numero, LIMITE_RESULTADOS));

        return new TP.SpringBootGrupal.dtos.PaginaDTO<>(
                resultado.getContent().stream().map(this::aPrecioDTO).toList(),
                numero,
                resultado.getTotalPages(),
                resultado.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PrecioArticuloExplorerDTO obtenerPrecioPorCodigo(Long listaPrecioId, String codigo) {
        String buscado = normalizar(codigo).toUpperCase();

        return listaPrecioArticuloRepository
                .findFirstByListaPrecio_IdAndArticulo_CodigoIgnoreCase(listaPrecioId, buscado)
                .filter(this::estaActivo)
                .map(this::aPrecioDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el artículo " + buscado + " en la lista de precios elegida"));
    }

    @Transactional(readOnly = true)
    public PrecioArticuloExplorerDTO obtenerPrecioArticulo(Long id) {
        return listaPrecioArticuloRepository.findById(id)
                .map(this::aPrecioDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el precio con ID " + id));
    }

    private boolean estaActivo(ListaPrecioArticulo precio) {
        return precio.getFechaBaja() == null
                && precio.getArticulo().getFechaBaja() == null
                && precio.getListaPrecio().getFechaBaja() == null;
    }

    private ClienteExplorerDTO aClienteDTO(Cliente cliente) {
        var condicion = cliente.getCondicionIva();
        return new ClienteExplorerDTO(cliente.getId(), cliente.getCuitCuil(), cliente.getDenominacion(),
                cliente.getNombre(), cliente.getApellido(),
                condicion == null ? null : condicion.getId(),
                condicion == null ? null : condicion.getDenominacion(),
                condicion == null ? null : condicion.getCodigoAfip());
    }

    private PrecioArticuloExplorerDTO aPrecioDTO(ListaPrecioArticulo precio) {
        return new PrecioArticuloExplorerDTO(
                precio.getId(), precio.getArticulo().getCodigo(),
                precio.getArticulo().getDenominacion(),
                precio.getListaPrecio().getDenominacion(), precio.getPrecioVenta(),
                precio.getListaPrecio().getId());
    }

    @Transactional(readOnly = true)
    public List<PuntoVentaDTO> obtenerPuntosVenta() {
        return puntoVentaRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(punto -> punto.getNumero()))
                .map(punto -> {
                    boolean inscripto = Boolean.TRUE.equals(punto.getResponsableInscripto());
                    return new PuntoVentaDTO(
                            punto.getId(),
                            punto.getNumero() + " - " + punto.getDescripcion()
                                    + (inscripto ? " (Resp. Inscripto)" : " (Monotributo)"),
                            inscripto
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcionDTO> obtenerTiposMoneda() {
        return tipoMonedaRepository.findAll().stream()
                .map(moneda -> new OpcionDTO(
                        moneda.getId(),
                        moneda.getDenominacion() + " (" + moneda.getSimbolo() + ")"
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcionDTO> obtenerCondicionesIva() {
        return condicionIvaRepository.findAll().stream()
                .map(condicion -> new OpcionDTO(
                        condicion.getId(), condicion.getDenominacion()
                ))
                .toList();
    }
}
