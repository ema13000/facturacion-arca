import type {
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    FacturaEdicionDTO,
    FacturaImpresionDTO,
    OpcionDTO,
    PuntoVentaDTO,
    ClienteExplorerDTO,
    PrecioArticuloExplorerDTO,
    AutorizacionFacturaC
} from '../models/factura.dto.js';

export class FacturaService {
    private readonly baseUrl = 'http://localhost:8080/api/facturas';
    private readonly catalogosUrl = 'http://localhost:8080/api/catalogos';

    private usuarioId?: number;
    private porcentajeIva?: number;

    private async obtenerJson<T>(url: string): Promise<T> {
        const response = await fetch(url);
        await this.comprobarRespuesta(response);
        return await response.json();
    }

    // Las búsquedas se hacen en el servidor: nunca se trae la tabla entera
    buscarClientes(texto: string): Promise<ClienteExplorerDTO[]> {
        const params = new URLSearchParams({ texto });
        return this.obtenerJson(`${this.catalogosUrl}/clientes?${params}`);
    }

    obtenerCliente(id: number): Promise<ClienteExplorerDTO> {
        return this.obtenerJson(`${this.catalogosUrl}/clientes/${id}`);
    }

    obtenerListasPrecio(): Promise<OpcionDTO[]> {
        return this.obtenerJson(`${this.catalogosUrl}/listas-precio`);
    }

    buscarPreciosArticulos(
        listaPrecioId: number,
        texto: string
    ): Promise<PrecioArticuloExplorerDTO[]> {
        const params = new URLSearchParams({
            listaPrecioId: String(listaPrecioId),
            texto
        });
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos?${params}`);
    }

    obtenerPrecioPorCodigo(listaPrecioId: number, codigo: string): Promise<PrecioArticuloExplorerDTO> {
        const params = new URLSearchParams({ listaPrecioId: String(listaPrecioId), codigo });
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos/por-codigo?${params}`);
    }

    obtenerPrecioArticulo(id: number): Promise<PrecioArticuloExplorerDTO> {
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos/${id}`);
    }

    async obtenerPuntosVenta(): Promise<PuntoVentaDTO[]> {
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
        porcentajeIva: number
    ): void {
        if (!Number.isInteger(usuarioId) || usuarioId <= 0) {
            throw new Error('Ingresá un ID de usuario válido.');
        }

        if (!Number.isFinite(porcentajeIva) || porcentajeIva < 0) {
            throw new Error('Ingresá un porcentaje de IVA válido.');
        }

        this.usuarioId = usuarioId;
        this.porcentajeIva = porcentajeIva;
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

    obtenerImpresion(id: number): Promise<FacturaImpresionDTO> {
        return this.obtenerJson(`${this.baseUrl}/${id}/impresion`);
    }

    /** PDF generado por el backend (solo facturas autorizadas, con CAE y vencimiento). */
    async descargarPdf(id: number): Promise<{ archivo: Blob; nombre: string }> {
        const response = await fetch(`${this.baseUrl}/${id}/pdf`);
        await this.comprobarRespuesta(response);

        const disposicion = response.headers.get('Content-Disposition') ?? '';
        const nombre = /filename="([^"]+)"/.exec(disposicion)?.[1] ?? `factura-${id}.pdf`;

        return { archivo: await response.blob(), nombre };
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

        if (this.porcentajeIva === undefined) {
            throw new Error('Falta configurar el IVA.');
        }

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
            const texto = await response.text();
            let mensaje = texto;
            try {
                // El backend responde {"mensaje": "..."}; si viene así, mostramos solo el texto
                const json = JSON.parse(texto);
                mensaje = json.mensaje ?? json.message ?? texto;
            } catch {
                // no era JSON: se muestra tal cual
            }
            throw new Error(`Error ${response.status}: ${mensaje}`);
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
