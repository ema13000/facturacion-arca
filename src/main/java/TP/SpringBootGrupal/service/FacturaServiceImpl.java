package TP.SpringBootGrupal.service;

import TP.SpringBootGrupal.dtos.Factura.FacturaEdicionDTO;
import TP.SpringBootGrupal.Exceptions.RecursoNoEncontradoException;
import TP.SpringBootGrupal.dtos.Factura.FacturaEdicionDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaImpresionDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Service
public class FacturaServiceImpl implements FacturaService {

    private final FacturaVentaRepository facturaVentaRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final UsuarioRepository usuarioRepository;

    public FacturaServiceImpl(
            FacturaVentaRepository facturaVentaRepository,
            ClienteRepository clienteRepository,
            CondicionIvaRepository condicionIvaRepository,
            TipoMonedaRepository tipoMonedaRepository,
            PuntoVentaRepository puntoVentaRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.facturaVentaRepository = facturaVentaRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public FacturaReporteDTO save(
            FacturaRequestDTO dto,
            Long usuarioId,
            double porcentajeIva
    ) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el usuario con ID " + usuarioId
                ));
        Date ahora = new Date();

        FacturaVenta factura = new FacturaVenta();

        factura.setFechaEmision(ahora);
        factura.setEstado("PENDIENTE");
        factura.setImporteCobrado(0);

        factura.setFechaAlta(ahora);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioCarga(usuario);
        factura.setUsuarioModificacion(usuario);

        aplicarDatos(dto, factura, porcentajeIva);

        // El número se genera solo: siguiente al último del punto de venta
        long ultimoNumero = facturaVentaRepository.obtenerUltimoNumero(dto.puntoVentaId());
        factura.setNumero(ultimoNumero + 1);

        factura = facturaVentaRepository.save(factura);

        return FacturaReporteDTO.toDTO(factura);
    }

    @Override
    @Transactional(readOnly = true)
    public FacturaEdicionDTO findParaEdicion(Long id) {
            FacturaVenta factura = buscarFacturaActiva(id);

            return FacturaEdicionDTO.toDTO(factura);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> findAll() {
        List<FacturaVenta> facturas = facturaVentaRepository.findAll();
        List<FacturaReporteDTO> resultado = new ArrayList<>();

        for (FacturaVenta factura : facturas) {
            if (estaActiva(factura)) {
                resultado.add(FacturaReporteDTO.toDTO(factura));
            }
        }

        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public FacturaReporteDTO findById(Long id) {
        FacturaVenta factura = buscarFacturaActiva(id);

        return FacturaReporteDTO.toDTO(factura);
    }

    @Override
    @Transactional
    public FacturaReporteDTO update(
            FacturaRequestDTO dto,
            Long id,
            Long usuarioId,
            double porcentajeIva
    ) {
        FacturaVenta factura = buscarFacturaActiva(id);

        if (estaAutorizada(factura)) {
            throw new IllegalStateException(
                    "La factura " + factura.getNumero() + " está autorizada en ARCA y no se puede editar"
            );
        }

        Usuario usuario = buscarUsuario(usuarioId);

        aplicarDatos(dto, factura, porcentajeIva);

        factura.setFechaModificacion(new Date());
        factura.setUsuarioModificacion(usuario);

        factura = facturaVentaRepository.save(factura);

        return FacturaReporteDTO.toDTO(factura);
    }

    @Override
    @Transactional
    public void delete(Long id, Long usuarioId) {

        FacturaVenta factura = facturaVentaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura con ID " + id));

        if (!estaActiva(factura)) {
            return;
        }

        if (estaAutorizada(factura)) {
            throw new IllegalStateException(
                    "La factura " + factura.getNumero() + " está autorizada en ARCA: no se puede eliminar, solo anular"
            );
        }

        // Baja lógica: la factura pendiente deja de aparecer en el listado
        Usuario usuario = buscarUsuario(usuarioId);
        Date ahora = new Date();

        factura.setFechaBaja(ahora);
        factura.setUsuarioBaja(usuario);

        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(usuario);

        facturaVentaRepository.save(factura);
    }

    @Override
    @Transactional(readOnly = true)
    public FacturaImpresionDTO findParaImpresion(Long id) {
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la factura con ID " + id
                ));

        if (!estaAutorizada(factura) || factura.getFechaBaja() != null) {
            throw new IllegalStateException(
                    "Solo se pueden imprimir facturas autorizadas en ARCA"
            );
        }

        return FacturaImpresionDTO.toDTO(factura);
    }

    private boolean estaAutorizada(FacturaVenta factura) {
        return "AUTORIZADA".equals(factura.getEstado());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> findByListaPrecioArticulo(Long idListaPrecioArticulo) {

        if (!listaPrecioArticuloRepository.existsById(idListaPrecioArticulo)) {throw new RecursoNoEncontradoException("No existe ListaPrecioArticulo con ID " + idListaPrecioArticulo);
        }

        List<FacturaReporteDTO> resultado = new ArrayList<>();

        for (FacturaVenta factura : facturaVentaRepository.findAll()) {
            if (!estaActiva(factura)) {
                continue;
            }

            for (FacturaVentaDetalle detalle : factura.getDetalles()) {
                Long precioId = detalle.getListaPrecioArticulo().getId();

                if (idListaPrecioArticulo.equals(precioId)) {resultado.add(FacturaReporteDTO.toDTO(factura));
                    break;
                }
            }
        }

        return resultado;
    }

    private void aplicarDatos(
            FacturaRequestDTO dto,
            FacturaVenta factura,
            double porcentajeIva
    ) {
        if (dto == null) {
            throw new IllegalArgumentException(
                    "Los datos de la factura son obligatorios"
            );
        }

        if (dto.detalles() == null || dto.detalles().isEmpty()) {
            throw new IllegalArgumentException(
                    "La factura debe tener al menos un detalle"
            );
        }

        if (!Double.isFinite(porcentajeIva) || porcentajeIva < 0) {
            throw new IllegalArgumentException(
                    "El porcentaje de IVA debe ser un número no negativo"
            );
        }

        Cliente cliente = null;

        if (dto.clienteId() != null) {

            cliente = clienteRepository.findById(dto.clienteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe el cliente"
                    ));
        }

        CondicionIva condicionIva = condicionIvaRepository
                .findById(dto.condicionIvaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la condición de IVA"
                ));

        TipoMoneda tipoMoneda = tipoMonedaRepository
                .findById(dto.tipoMonedaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el tipo de moneda"
                ));

        PuntoVenta puntoVenta = puntoVentaRepository
                .findById(dto.puntoVentaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el punto de venta"
                ));

        List<FacturaVentaDetalle> nuevosDetalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (FacturaDetalleRequestDTO detalleDto : dto.detalles()) {
            FacturaVentaDetalle detalle = crearDetalle(
                    detalleDto,
                    factura,
                    porcentajeIva
            );

            nuevosDetalles.add(detalle);

            total = total.add(
                    BigDecimal.valueOf(detalle.getImporteSubtotal())
            );
        }

        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);
        factura.setPuntoVenta(puntoVenta);

        factura.getDetalles().clear();
        factura.getDetalles().addAll(nuevosDetalles);

        factura.setImporteTotal(total.doubleValue());

        BigDecimal saldo = total.subtract(
                BigDecimal.valueOf(factura.getImporteCobrado())
        );

        factura.setImporteSaldo(redondear(saldo).doubleValue());
    }

    private FacturaVentaDetalle crearDetalle(
            FacturaDetalleRequestDTO dto,
            FacturaVenta factura,
            double porcentajeIva
    ) {
        if (dto == null) {
            throw new IllegalArgumentException(
                    "Un detalle no puede ser null"
            );
        }

        if (dto.cantidad() == null || !Double.isFinite(dto.cantidad()) || dto.cantidad() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero"
            );
        }

        double bonificacion = dto.porcentajeBonificacion() == null ? 0 : dto.porcentajeBonificacion();

        if (!Double.isFinite(bonificacion) || bonificacion < 0 || bonificacion > 100) {
            throw new IllegalArgumentException(
                    "La bonificación debe estar entre 0 y 100"
            );
        }

        ListaPrecioArticulo precio = buscarPrecio(dto.listaPrecioArticuloId());

        if (!Double.isFinite(precio.getPrecioVenta()) || precio.getPrecioVenta() < 0) {
            throw new IllegalArgumentException(
                    "El precio del artículo es inválido"
            );
        }

        BigDecimal bruto = BigDecimal.valueOf(dto.cantidad())
                .multiply(BigDecimal.valueOf(precio.getPrecioVenta()));

        BigDecimal factorBonificacion = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(bonificacion).movePointLeft(2)
        );

        BigDecimal neto = redondear(
                bruto.multiply(factorBonificacion)
        );

        BigDecimal iva = redondear(
                neto.multiply(
                        BigDecimal.valueOf(porcentajeIva).movePointLeft(2)
                )
        );

        BigDecimal subtotal = neto.add(iva);

        FacturaVentaDetalle detalle = new FacturaVentaDetalle();

        detalle.setFactura(factura);
        detalle.setListaPrecioArticulo(precio);
        detalle.setDescripcion(precio.getArticulo().getDenominacion());

        detalle.setCantidad(dto.cantidad());
        detalle.setPrecioUnitario(precio.getPrecioVenta());
        detalle.setPorcentajeBonificacion(bonificacion);

        detalle.setImporteNeto(neto.doubleValue());
        detalle.setImporteIva(iva.doubleValue());
        detalle.setImporteSubtotal(subtotal.doubleValue());

        return detalle;
    }

    private ListaPrecioArticulo buscarPrecio(Long listaPrecioArticuloId) {
        if (listaPrecioArticuloId == null || listaPrecioArticuloId <= 0) {
            throw new IllegalArgumentException("Seleccioná un artículo de una lista de precios");
        }
        ListaPrecioArticulo precio = listaPrecioArticuloRepository.findById(listaPrecioArticuloId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ListaPrecioArticulo con ID " + listaPrecioArticuloId));

        if (precio.getFechaBaja() != null
                || precio.getArticulo().getFechaBaja() != null
                || precio.getListaPrecio().getFechaBaja() != null) {
            throw new RecursoNoEncontradoException("El artículo o su lista de precios están dados de baja");
        }
        return precio;
    }

    private FacturaVenta buscarFacturaActiva(Long id) {

        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la factura con ID " + id
                ));

        if (!estaActiva(factura)) {
            throw new RecursoNoEncontradoException(
                    "La factura con ID " + id + " está dada de baja"
            );
        }

        return factura;
    }

    private Usuario buscarUsuario(Long id) {

        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el usuario con ID " + id
                ));
    }

    private boolean estaActiva(FacturaVenta factura) {
        return factura.getFechaBaja() == null
                && !"ANULADA".equals(factura.getEstado());
    }


    private BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacturaReporteDTO> findAllParaListado() {
        // La última factura generada aparece primero; las eliminadas (baja lógica) no se muestran
        return facturaVentaRepository.findAll().stream()
                .filter(f -> f.getFechaBaja() == null || "ANULADA".equals(f.getEstado()))
                .sorted(Comparator.comparing(FacturaVenta::getId).reversed())
                .map(FacturaReporteDTO::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public FacturaReporteDTO anular(
            Long id,
            Long usuarioId,
            String observaciones
    ) {
        FacturaVenta factura = facturaVentaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la factura con ID " + id
                ));

        if (!estaActiva(factura)) {
            return FacturaReporteDTO.toDTO(factura);
        }

        if (!estaAutorizada(factura)) {
            throw new IllegalStateException(
                    "Solo se pueden anular facturas autorizadas. Una pendiente se elimina"
            );
        }

        Usuario usuario = buscarUsuario(usuarioId);
        Date ahora = new Date();

        factura.setEstado("ANULADA");
        factura.setFechaAnulacion(ahora);
        factura.setObservaciones(observaciones);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(usuario);

        factura = facturaVentaRepository.save(factura);

        return FacturaReporteDTO.toDTO(factura);
    }
}