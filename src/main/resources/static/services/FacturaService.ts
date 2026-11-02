import type {
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    FacturaEdicionDTO,
    OpcionDTO,
    ClienteExplorerDTO,
    PrecioArticuloExplorerDTO,
    AutorizacionFacturaC
} from '../models/factura.dto.js';

export class FacturaService {
    private readonly baseUrl = 'http://localhost:8080/api/facturas';
    private readonly catalogosUrl = 'http://localhost:8080/api/catalogos';

    private usuarioId?: number;
    private porcentajeIva?: number;
    private numero?: number;

    async obtenerClientes(): Promise<ClienteExplorerDTO[]> {
        const response = await fetch(`${this.catalogosUrl}/clientes`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async obtenerPreciosArticulos(): Promise<PrecioArticuloExplorerDTO[]> {
        const response = await fetch(`${this.catalogosUrl}/precios-articulos`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async obtenerPuntosVenta(): Promise<OpcionDTO[]> {
        const response = await fetch(`${this.catalogosUrl}/puntos-venta`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async obtenerTiposMoneda(): Promise<OpcionDTO[]> {
        const response = await fetch(`${this.catalogosUrl}/tipos-moneda`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async obtenerCondicionesIva(): Promise<OpcionDTO[]> {
        const response = await fetch(`${this.catalogosUrl}/condiciones-iva`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    configurar(
        usuarioId: number,
        porcentajeIva: number,
        numero?: number
    ): void {
        if (!Number.isInteger(usuarioId) || usuarioId <= 0) {
            throw new Error('Ingresá un ID de usuario válido.');
        }

        if (!Number.isFinite(porcentajeIva) || porcentajeIva < 0) {
            throw new Error('Ingresá un porcentaje de IVA válido.');
        }

        if (numero !== undefined
            && (!Number.isInteger(numero) || numero <= 0)) {
            throw new Error('Ingresá un número de factura válido.');
        }

        this.usuarioId = usuarioId;
        this.porcentajeIva = porcentajeIva;
        this.numero = numero;
    }

    async obtenerFacturas(
        filtros: FacturaFiltrosDTO = {}
    ): Promise<FacturaReporteDTO[]> {
        const response = await fetch(this.baseUrl);
        await this.comprobarRespuesta(response);

        const facturas: FacturaReporteDTO[] = await response.json();

        return facturas.filter(f => {
            const contiene = (valor: string, filtro?: string): boolean =>
                !filtro || valor.toLowerCase().includes(
                    filtro.trim().toLowerCase()
                );

            const fecha = f.fechaEmision.slice(0, 10);

            return contiene(String(f.numeroFactura), filtros.numeroFactura)
                && contiene(f.clienteDenominacion, filtros.cliente)
                && contiene(f.condicionIva, filtros.condicionIva)
                && (!filtros.estado || f.estado === filtros.estado)
                && (!filtros.fechaDesde || fecha >= filtros.fechaDesde)
                && (!filtros.fechaHasta || fecha <= filtros.fechaHasta);
        });
    }

    async obtenerParaEdicion(id: number): Promise<FacturaEdicionDTO> {
        const response = await fetch(`${this.baseUrl}/${id}/edicion`);

        await this.comprobarRespuesta(response);

        return await response.json();
    }

    async crearFactura(
        factura: FacturaRequestDTO
    ): Promise<FacturaReporteDTO> {
        const params = this.parametrosUsuario();

        if (this.numero === undefined || this.porcentajeIva === undefined) {
            throw new Error('Falta configurar número o IVA.');
        }

        params.set('numero', String(this.numero));
        params.set('porcentajeIva', String(this.porcentajeIva));

        const response = await fetch(`${this.baseUrl}?${params}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(factura)
        });

        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async actualizarFactura(
        id: number,
        factura: FacturaRequestDTO
    ): Promise<FacturaReporteDTO> {
        const params = this.parametrosUsuario();

        if (this.porcentajeIva === undefined) {
            throw new Error('Falta configurar el IVA.');
        }

        params.set('porcentajeIva', String(this.porcentajeIva));

        const response = await fetch(`${this.baseUrl}/${id}?${params}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(factura)
        });

        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async actualizarEstado(
        id: number,
        estado: string,
        observaciones?: string
    ): Promise<FacturaReporteDTO> {
        const params = this.parametrosUsuario();

        const response = await fetch(
            `${this.baseUrl}/${id}/estado?${params}`,
            {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    estado,
                    observaciones: observaciones ?? null
                })
            }
        );

        await this.comprobarRespuesta(response);
        return await response.json();
    }

    async eliminarFactura(id: number): Promise<void> {
        const params = this.parametrosUsuario();

        const response = await fetch(`${this.baseUrl}/${id}?${params}`, {
            method: 'DELETE'
        });

        await this.comprobarRespuesta(response);
    }

    private parametrosUsuario(): URLSearchParams {
        if (this.usuarioId === undefined) {
            throw new Error('Falta configurar el usuario.');
        }

        return new URLSearchParams({
            usuarioId: String(this.usuarioId)
        });
    }

    private async comprobarRespuesta(response: Response): Promise<void> {
        if (!response.ok) {
            throw new Error(
                `La API respondió ${response.status}: ${await response.text()}`
            );
        }
    }

    async autorizarFactura(
        id: number,
        tipoDocumento: number,
        numeroDocumento: number
    ): Promise<AutorizacionFacturaC> {

        const response = await fetch(
            `${this.baseUrl}/${id}/autorizar-arca`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    tipoDocumento,
                    numeroDocumento
                })
            }
        );

        await this.comprobarRespuesta(response);

        return await response.json();
    }
}
