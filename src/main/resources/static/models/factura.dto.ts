export interface FacturaDetalleRequestDTO {
    listaPrecioArticuloId: number;
    cantidad: number;
    porcentajeBonificacion: number | null;
}

export interface FacturaRequestDTO {
    clienteId: number | null;
    condicionIvaId: number;
    tipoMonedaId: number;
    puntoVentaId: number;
    detalles: FacturaDetalleRequestDTO[];
    fechaEmision: string | null;
    observaciones: string | null;
}

export interface FacturaReporteDTO {
    id: number;
    numeroFactura: number;
    fechaEmision: string;
    clienteDenominacion: string;
    condicionIva: string;
    puntoVentaDescripcion: string;
    importeTotal: number;
    cantidadItems: number;
    estado: string;
    observaciones: string | null;
    clienteCuit: string | null;
    clienteNombreCompleto: string;
    condicionIvaId: number;
    informadaArca: boolean;
    motivoAnulacion: string | null;
    tipoComprobante?: string;
}

export interface FacturaFiltrosDTO {
    cliente?: string;
    estado?: string;
    numeroFactura?: string;
    condicionIvaId?: string;
    montoMinimo?: number;
    informadaArca?: boolean;
    fechaDesde?: string;
    fechaHasta?: string;
}

export interface FacturaEdicionDTO extends FacturaRequestDTO {
    numeroFactura: number;
}

export interface PaginaDTO<T> {
    contenido: T[];
    pagina: number;
    totalPaginas: number;
    totalElementos: number;
}

export interface OpcionDTO {
    id: number;
    descripcion: string;
}

export interface ClienteExplorerDTO {
    id: number;
    cuit: string;
    denominacion: string;
    nombre: string | null;
    apellido: string | null;
    condicionIvaId?: number | null;
    condicionIva?: string | null;
    condicionIvaCodigo?: number | null;
}

export interface PuntoVentaDTO extends OpcionDTO {
    responsableInscripto: boolean;
}

export interface PrecioArticuloExplorerDTO {
    id: number;
    articuloCodigo: string;
    articuloDenominacion: string;
    listaPrecioDenominacion: string;
    precioVenta: number;
    listaPrecioId: number;
}

export interface FacturaImpresionDTO {
    id: number;
    numeroFactura: number;
    puntoVentaNumero: number;
    puntoVentaDescripcion: string;
    fechaEmision: string;
    clienteDenominacion: string;
    clienteCuit: string | null;
    condicionIva: string;
    importeTotal: number;
    cae: string;
    caeFechaVencimiento: string;
    observaciones: string | null;
    detalles: {
        descripcion: string;
        cantidad: number;
        precioUnitario: number;
        porcentajeBonificacion: number;
        importeSubtotal: number;
    }[];
    tipoComprobante?: string;
    importeNeto?: number;
    importeIva?: number;
}

export interface AutorizacionFacturaC {
    numeroComprobante: number;
    resultado: {
        resultado: string;
        cae: string | null;
        fechaVencimiento: string | null;
        observaciones: string[];
    };
}
