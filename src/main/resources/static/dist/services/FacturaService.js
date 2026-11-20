export class FacturaService {
    constructor() {
        this.baseUrl = 'http://localhost:8080/api/facturas';
        this.catalogosUrl = 'http://localhost:8080/api/catalogos';
    }
    async obtenerJson(url) {
        const response = await fetch(url);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    // Las búsquedas se hacen en el servidor: nunca se trae la tabla entera
    buscarClientes(texto) {
        const params = new URLSearchParams({ texto });
        return this.obtenerJson(`${this.catalogosUrl}/clientes?${params}`);
    }
    obtenerCliente(id) {
        return this.obtenerJson(`${this.catalogosUrl}/clientes/${id}`);
    }
    obtenerListasPrecio() {
        return this.obtenerJson(`${this.catalogosUrl}/listas-precio`);
    }
    buscarPreciosArticulos(listaPrecioId, texto) {
        const params = new URLSearchParams({
            listaPrecioId: String(listaPrecioId),
            texto
        });
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos?${params}`);
    }
    obtenerPrecioPorCodigo(listaPrecioId, codigo) {
        const params = new URLSearchParams({ listaPrecioId: String(listaPrecioId), codigo });
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos/por-codigo?${params}`);
    }
    obtenerPrecioArticulo(id) {
        return this.obtenerJson(`${this.catalogosUrl}/precios-articulos/${id}`);
    }
    async obtenerPuntosVenta() {
        const response = await fetch(`${this.catalogosUrl}/puntos-venta`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async obtenerTiposMoneda() {
        const response = await fetch(`${this.catalogosUrl}/tipos-moneda`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async obtenerCondicionesIva() {
        const response = await fetch(`${this.catalogosUrl}/condiciones-iva`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    configurar(usuarioId, porcentajeIva) {
        if (!Number.isInteger(usuarioId) || usuarioId <= 0) {
            throw new Error('Ingresá un ID de usuario válido.');
        }
        if (!Number.isFinite(porcentajeIva) || porcentajeIva < 0) {
            throw new Error('Ingresá un porcentaje de IVA válido.');
        }
        this.usuarioId = usuarioId;
        this.porcentajeIva = porcentajeIva;
    }
    async obtenerFacturas(filtros = {}) {
        const response = await fetch(this.baseUrl);
        await this.comprobarRespuesta(response);
        const facturas = await response.json();
        return facturas.filter(f => {
            const contiene = (valor, filtro) => !filtro || valor.toLowerCase().includes(filtro.trim().toLowerCase());
            const fecha = f.fechaEmision.slice(0, 10);
            return contiene(String(f.numeroFactura), filtros.numeroFactura)
                && contiene(f.clienteDenominacion, filtros.cliente)
                && contiene(f.condicionIva, filtros.condicionIva)
                && (!filtros.estado || f.estado === filtros.estado)
                && (!filtros.fechaDesde || fecha >= filtros.fechaDesde)
                && (!filtros.fechaHasta || fecha <= filtros.fechaHasta);
        });
    }
    obtenerImpresion(id) {
        return this.obtenerJson(`${this.baseUrl}/${id}/impresion`);
    }
    /** PDF generado por el backend (solo facturas autorizadas, con CAE y vencimiento). */
    async descargarPdf(id) {
        const response = await fetch(`${this.baseUrl}/${id}/pdf`);
        await this.comprobarRespuesta(response);
        const disposicion = response.headers.get('Content-Disposition') ?? '';
        const nombre = /filename="([^"]+)"/.exec(disposicion)?.[1] ?? `factura-${id}.pdf`;
        return { archivo: await response.blob(), nombre };
    }
    async obtenerParaEdicion(id) {
        const response = await fetch(`${this.baseUrl}/${id}/edicion`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async crearFactura(factura) {
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
    async actualizarFactura(id, factura) {
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
    async actualizarEstado(id, estado, observaciones) {
        const params = this.parametrosUsuario();
        const response = await fetch(`${this.baseUrl}/${id}/estado?${params}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                estado,
                observaciones: observaciones ?? null
            })
        });
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async eliminarFactura(id) {
        const params = this.parametrosUsuario();
        const response = await fetch(`${this.baseUrl}/${id}?${params}`, {
            method: 'DELETE'
        });
        await this.comprobarRespuesta(response);
    }
    parametrosUsuario() {
        if (this.usuarioId === undefined) {
            throw new Error('Falta configurar el usuario.');
        }
        return new URLSearchParams({
            usuarioId: String(this.usuarioId)
        });
    }
    async comprobarRespuesta(response) {
        if (!response.ok) {
            const texto = await response.text();
            let mensaje = texto;
            try {
                // El backend responde {"mensaje": "..."}; si viene así, mostramos solo el texto
                const json = JSON.parse(texto);
                mensaje = json.mensaje ?? json.message ?? texto;
            }
            catch {
                // no era JSON: se muestra tal cual
            }
            throw new Error(`Error ${response.status}: ${mensaje}`);
        }
    }
    async autorizarFactura(id, tipoDocumento, numeroDocumento) {
        const response = await fetch(`${this.baseUrl}/${id}/autorizar-arca`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                tipoDocumento,
                numeroDocumento
            })
        });
        await this.comprobarRespuesta(response);
        return await response.json();
    }
}
