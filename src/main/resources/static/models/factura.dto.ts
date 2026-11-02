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
}

export interface FacturaFiltrosDTO {
    cliente?: string;
    estado?: string;
    numeroFactura?: string;
    condicionIva?: string;
    fechaDesde?: string;
    fechaHasta?: string;
}

export interface FacturaEdicionDTO extends FacturaRequestDTO {
    numeroFactura: number;
}

export interface OpcionDTO {
    id: number;
    descripcion: string;
}

export interface ClienteExplorerDTO {
    id: number;
    cuitCuil: string;
    denominacion: string;
}

export interface PrecioArticuloExplorerDTO {
    id: number;
    articuloCodigo: string;
    articuloDenominacion: string;
    listaPrecioDenominacion: string;
    precioVenta: number;
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
