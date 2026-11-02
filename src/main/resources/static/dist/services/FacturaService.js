export class FacturaService {
    constructor() {
        this.baseUrl = 'http://localhost:8080/api/facturas';
        this.catalogosUrl = 'http://localhost:8080/api/catalogos';
    }
    async obtenerClientes() {
        const response = await fetch(`${this.catalogosUrl}/clientes`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async obtenerPreciosArticulos() {
        const response = await fetch(`${this.catalogosUrl}/precios-articulos`);
        await this.comprobarRespuesta(response);
        return await response.json();
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
    configurar(usuarioId, porcentajeIva, numero) {
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
    async obtenerParaEdicion(id) {
        const response = await fetch(`${this.baseUrl}/${id}/edicion`);
        await this.comprobarRespuesta(response);
        return await response.json();
    }
    async crearFactura(factura) {
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
            throw new Error(`La API respondió ${response.status}: ${await response.text()}`);
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
