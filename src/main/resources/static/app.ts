import type {
    FacturaDetalleRequestDTO,
    FacturaFiltrosDTO,
    FacturaImpresionDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    OpcionDTO,
    PuntoVentaDTO,
    ClienteExplorerDTO,
    PrecioArticuloExplorerDTO
} from './models/factura.dto.js';

import { FacturaService } from './services/FacturaService.js';

// Usuario que registra las operaciones (ya no se pide en pantalla)
const USUARIO_OPERADOR_ID = 1;
// Factura C (monotributo): ARCA no admite IVA discriminado, por eso siempre es 0
const PORCENTAJE_IVA_FACTURA_C = 0;

const api = new FacturaService();
let renglones: FacturaDetalleRequestDTO[] = [];
let facturaEnEdicionId: number | null = null;
// Precios ya elegidos, por ID: se usan para mostrar descripción y calcular el total
const preciosElegidos = new Map<number, PrecioArticuloExplorerDTO>();
let clienteSeleccionado: ClienteExplorerDTO | null = null;
let puntosVentaCargados: PuntoVentaDTO[] = [];

/* ---------- Tipo de factura (A, B o C) ---------- */

const CONSUMIDOR_FINAL = 5;
// Condiciones del receptor que reciben Factura A de un Responsable Inscripto
const RECIBEN_FACTURA_A = [1, 6, 13, 16];
const ALICUOTA_IVA = 21;
const MENSAJE_ARCA_AB = (letra: string) =>
    `Para autorizar la Factura ${letra} en ARCA hace falta un CUIT Responsable Inscripto con su certificado. ` +
    'La factura queda guardada como PENDIENTE.';

function letraDe(factura: { tipoComprobante?: string }): string {
    return factura.tipoComprobante || 'C';
}

/** Misma regla que el backend: emisor monotributista → C; inscripto → A o B según el cliente. */
function letraFactura(): string | null {
    const punto = puntosVentaCargados.find(p => String(p.id) === valor('inputPuntoVenta'));
    if (!punto) {
        return null;
    }
    if (!punto.responsableInscripto) {
        return 'C';
    }
    const codigo = clienteSeleccionado ? clienteSeleccionado.condicionIvaCodigo : CONSUMIDOR_FINAL;
    return codigo != null && RECIBEN_FACTURA_A.includes(codigo) ? 'A' : 'B';
}

function actualizarLetra(): void {
    const letra = letraFactura();
    elemento('letraComprobante').textContent = `Factura ${letra ?? '—'}`;
    actualizarTotales();
}

/** La condición de IVA no se elige: es la del cliente, o Consumidor Final si no hay cliente. */
function aplicarCondicionDelCliente(): void {
    const select = elemento<HTMLSelectElement>('inputCondicionIva');
    const ayuda = elemento('ayudaCondicionIva');
    const opciones = Array.from(select.options).filter(o => o.value !== '');
    if (opciones.length === 0) {
        return;
    }

    if (clienteSeleccionado === null) {
        const consumidorFinal = opciones.find(o => o.textContent?.trim().toLowerCase() === 'consumidor final');
        if (consumidorFinal) {
            select.value = consumidorFinal.value;
            select.disabled = true;
            ayuda.textContent = 'Sin cliente: Consumidor Final.';
            return;
        }
    } else if (clienteSeleccionado.condicionIvaId != null) {
        select.value = String(clienteSeleccionado.condicionIvaId);
        select.disabled = true;
        ayuda.textContent = 'Se toma de la categoría del cliente.';
        return;
    }

    // Cliente sin categoría cargada: se elige a mano
    select.disabled = false;
    ayuda.textContent = 'El cliente no tiene categoría cargada: elegila.';
}
let buscarEnExplorer: () => void = () => {};
let solicitudExplorer = 0;
let facturasListado: FacturaReporteDTO[] = [];
let paginaActual = 1;
// ID de la última factura guardada: se resalta en el listado
let ultimaGuardadaId: number | null = null;

function elemento<T extends HTMLElement>(id: string): T {
    const encontrado = document.getElementById(id);

    if (!encontrado) {
        throw new Error(`Falta el elemento HTML: ${id}`);
    }

    return encontrado as T;
}

function valor(id: string): string {
    return elemento<HTMLInputElement | HTMLSelectElement>(id).value.trim();
}

function numeroCampo(id: string): number {
    const texto = valor(id);
    const numero = Number(texto);

    if (texto === '' || !Number.isFinite(numero)) {
        throw new Error(`Completá correctamente el campo ${id}.`);
    }

    return numero;
}

const formatoMoneda = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

function moneda(importe: number): string {
    return `$${formatoMoneda.format(importe)}`;
}

// Íconos (trazos estilo "Lucide") para los botones de acción
const ICONOS: Record<string, string> = {
    editar: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    eliminar: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>',
    anular: '<circle cx="12" cy="12" r="10"/><path d="m4.9 4.9 14.2 14.2"/>',
    imprimir: '<path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/>',
    quitar: '<path d="M18 6 6 18M6 6l12 12"/>',
    pdf: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M12 12v6"/><path d="m9 15 3 3 3-3"/>',
    enviar: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>'
};

/** Botón de ícono: el texto queda oculto visualmente pero sirve para lectores de pantalla. */
function botonIcono(
    texto: string,
    icono: keyof typeof ICONOS,
    clase: string,
    accion: () => void,
    descripcion = texto
): HTMLButtonElement {
    const boton = crearBoton('', `btn-icono ${clase}`, accion);
    boton.title = descripcion;
    boton.innerHTML = `<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONOS[icono]}</svg>`;
    const etiqueta = document.createElement('span');
    etiqueta.className = 'sr-only';
    etiqueta.textContent = texto;
    boton.appendChild(etiqueta);
    return boton;
}

function actualizarKpis(facturas: FacturaReporteDTO[]): void {
    const contar = (estado: string) => facturas.filter(f => f.estado === estado).length;
    const total = facturas
        .filter(f => f.estado !== 'ANULADA')
        .reduce((suma, f) => suma + f.importeTotal, 0);

    elemento('kpiTotal').textContent = moneda(total);
    elemento('kpiPendientes').textContent = String(contar('PENDIENTE'));
    elemento('kpiAutorizadas').textContent = String(contar('AUTORIZADA'));
    elemento('kpiAnuladas').textContent = String(contar('ANULADA'));
}

function llenarOpciones(
    id: string,
    opciones: OpcionDTO[],
    mensaje: string
): void {
    const select = elemento<HTMLSelectElement>(id);
    select.replaceChildren();

    const inicial = document.createElement('option');
    inicial.value = '';
    inicial.textContent = opciones.length === 0
        ? 'No hay opciones disponibles'
        : mensaje;
    inicial.defaultSelected = true;
    inicial.selected = true;
    select.appendChild(inicial);

    for (const opcion of opciones) {
        const option = document.createElement('option');
        option.value = String(opcion.id);
        option.textContent = opcion.descripcion;
        select.appendChild(option);
    }

    select.disabled = opciones.length === 0;
}

const SELECTS_CATALOGO = ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva'];

async function cargarOpciones(): Promise<void> {
    try {
        const [puntosVenta, monedas, condicionesIva, listasPrecio] = await Promise.all([
            api.obtenerPuntosVenta(),
            api.obtenerTiposMoneda(),
            api.obtenerCondicionesIva(),
            api.obtenerListasPrecio()
        ]);

        llenarOpciones('inputPuntoVenta', puntosVenta, 'Seleccioná un punto de venta');
        llenarOpciones('inputMoneda', monedas, 'Seleccioná una moneda');
        llenarOpciones('inputCondicionIva', condicionesIva, 'Seleccioná una condición de IVA');
        llenarListasDelBuscador(listasPrecio);
        puntosVentaCargados = puntosVenta;
        aplicarCondicionDelCliente();
        actualizarLetra();
    } catch (error) {
        for (const id of SELECTS_CATALOGO) {
            const select = elemento<HTMLSelectElement>(id);
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No se pudieron cargar las opciones';
            select.replaceChildren(option);
            select.disabled = true;
        }

        throw error;
    }
}

async function ejecutar(accion: () => Promise<void>): Promise<void> {
    try {
        await accion();
    } catch (error) {
        console.error(error);
        alert(error instanceof Error ? error.message : String(error));
    }
}

/* ---------- Listado de facturas con paginación ---------- */

function leerFiltros(): FacturaFiltrosDTO {
    return {
        numeroFactura: valor('txtFiltroNumero'),
        cliente: valor('txtFiltroCliente'),
        condicionIva: valor('txtFiltroIva'),
        estado: valor('selFiltroEstado'),
        fechaDesde: valor('txtFechaDesde'),
        fechaHasta: valor('txtFechaHasta')
    };
}

async function cargarListado(): Promise<void> {
    facturasListado = await api.obtenerFacturas(leerFiltros());
    paginaActual = 1;
    dibujarFacturas();
}

function tamanioPagina(): number {
    return Number(valor('selTamanioPagina')) || 10;
}

function crearBoton(texto: string, clase: string, accion: () => void): HTMLButtonElement {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.textContent = texto;
    boton.className = clase;
    boton.addEventListener('click', accion);
    return boton;
}

function dibujarFacturas(): void {
    const facturas = facturasListado;
    const tbody = elemento<HTMLTableSectionElement>('tbodyFacturas');
    tbody.replaceChildren();

    elemento('contadorFacturas').textContent =
        `${facturas.length} comprobante(s)`;
    actualizarKpis(facturas);

    const porPagina = tamanioPagina();
    const totalPaginas = Math.max(1, Math.ceil(facturas.length / porPagina));
    paginaActual = Math.min(Math.max(paginaActual, 1), totalPaginas);

    elemento('infoPagina').textContent = `Página ${paginaActual} de ${totalPaginas}`;
    elemento<HTMLButtonElement>('btnPaginaAnterior').disabled = paginaActual <= 1;
    elemento<HTMLButtonElement>('btnPaginaSiguiente').disabled = paginaActual >= totalPaginas;

    if (facturas.length === 0) {
        const celda = tbody.insertRow().insertCell();
        celda.colSpan = 8;
        celda.className = 'texto-centro celda-vacia';
        celda.textContent = 'No hay facturas que coincidan con la búsqueda.';
        return;
    }

    const inicio = (paginaActual - 1) * porPagina;

    for (const factura of facturas.slice(inicio, inicio + porPagina)) {
        const fila = tbody.insertRow();

        const celdaNumero = fila.insertCell();
        const letra = document.createElement('span');
        letra.className = 'tag-letra';
        letra.textContent = letraDe(factura);
        celdaNumero.append(letra, ` ${factura.numeroFactura}`);

        const datos = [
            factura.fechaEmision.slice(0, 10),
            factura.clienteDenominacion,
            factura.condicionIva,
            factura.puntoVentaDescripcion
        ];

        for (const dato of datos) {
            fila.insertCell().textContent = dato;
        }

        const total = fila.insertCell();
        total.className = 'texto-derecha';
        total.textContent = moneda(factura.importeTotal);

        const estado = document.createElement('span');
        estado.className = `estado-tag tag-${factura.estado.toLowerCase()}`;
        estado.textContent = factura.estado;
        fila.insertCell().appendChild(estado);

        if (factura.id === ultimaGuardadaId) {
            fila.classList.add('fila-nueva');
            fila.title = 'Factura recién guardada';
        }

        const acciones = fila.insertCell();
        acciones.className = 'celda-acciones';
        acciones.append(...accionesSegunEstado(factura));
    }

    tbody.querySelector('.fila-nueva')?.scrollIntoView({ block: 'nearest' });
}

/**
 * Acciones permitidas según el estado:
 * - PENDIENTE: editar y eliminar (no se anula ni se imprime).
 * - AUTORIZADA: anular e imprimir (no se edita ni se elimina).
 * - ANULADA: ninguna.
 */
function accionesSegunEstado(factura: FacturaReporteDTO): HTMLButtonElement[] {
    if (factura.estado === 'PENDIENTE') {
        return [
            botonIcono('Editar', 'editar', 'icono-azul', () => {
                void ejecutar(() => editarFactura(factura.id));
            }, 'Editar factura'),
            botonIcono('Eliminar', 'eliminar', 'icono-rojo', () => {
                void ejecutar(async () => {
                    if (!confirm(`¿Eliminar la factura pendiente N° ${factura.numeroFactura}?`)) {
                        return;
                    }

                    await api.eliminarFactura(factura.id);
                    await cargarListado();
                });
            }, 'Eliminar factura pendiente'),
            botonIcono('Autorizar en ARCA', 'enviar', 'icono-verde',
                () => abrirAutorizacion(factura), 'Informar la factura a ARCA y obtener el CAE')
        ];
    }

    if (factura.estado === 'AUTORIZADA') {
        return [
            botonIcono('Anular', 'anular', 'icono-ambar', () => {
                void ejecutar(async () => {
                    const motivo = prompt(`Motivo de la anulación de la factura N° ${factura.numeroFactura}:`);

                    if (motivo === null) {
                        return;
                    }

                    await api.actualizarEstado(factura.id, 'ANULADA', motivo);
                    await cargarListado();
                });
            }, 'Anular factura'),
            botonIcono('Imprimir', 'imprimir', 'icono-indigo', () => {
                // La ventana se abre en el mismo clic para que el navegador no la bloquee
                const ventana = window.open('', '_blank');
                void ejecutar(async () => {
                    if (!ventana) {
                        throw new Error('El navegador bloqueó la ventana de impresión. Permití ventanas emergentes.');
                    }
                    try {
                        imprimirFactura(ventana, await api.obtenerImpresion(factura.id));
                    } catch (error) {
                        ventana.close();
                        throw error;
                    }
                });
            }, 'Imprimir factura con CAE'),
            botonIcono('Descargar PDF', 'pdf', 'icono-rojo', () => {
                void ejecutar(async () => {
                    const { archivo, nombre } = await api.descargarPdf(factura.id);
                    const url = URL.createObjectURL(archivo);
                    const enlace = document.createElement('a');
                    enlace.href = url;
                    enlace.download = nombre;
                    document.body.appendChild(enlace);
                    enlace.click();
                    enlace.remove();
                    setTimeout(() => URL.revokeObjectURL(url), 1000);
                });
            }, 'Descargar PDF con CAE y vencimiento')
        ];
    }

    return [];
}

/* ---------- Impresión de la factura autorizada ---------- */

function escaparHtml(texto: string): string {
    return texto.replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c] as string);
}

function fechaArgentina(iso: string): string {
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
}

function imprimirFactura(ventana: Window, f: FacturaImpresionDTO): void {
    const numero = `${String(f.puntoVentaNumero).padStart(4, '0')}-${String(f.numeroFactura).padStart(8, '0')}`;
    const letra = letraDe(f);
    const lineasIva = letra === 'A'
        ? `<p class="iva">Neto gravado: ${moneda(f.importeNeto ?? 0)}</p><p class="iva">IVA 21%: ${moneda(f.importeIva ?? 0)}</p>`
        : letra === 'B'
            ? `<p class="iva">IVA 21% incluido: ${moneda(f.importeIva ?? 0)}</p>`
            : '';
    const filas = f.detalles.map(d => `
        <tr>
            <td>${escaparHtml(d.descripcion ?? '')}</td>
            <td class="num">${d.cantidad}</td>
            <td class="num">${moneda(d.precioUnitario)}</td>
            <td class="num">${d.porcentajeBonificacion}%</td>
            <td class="num">${moneda(d.importeSubtotal)}</td>
        </tr>`).join('');

    ventana.document.write(`<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8"><title>Factura ${letra} ${numero}</title>
<style>
    body { font-family: 'Segoe UI', Arial, sans-serif; color: #1e293b; margin: 32px; }
    .cabecera { display: flex; justify-content: space-between; border: 2px solid #1e293b; padding: 16px; }
    .letra { font-size: 2.4rem; font-weight: 700; border: 2px solid #1e293b; padding: 4px 16px; height: fit-content; }
    h1 { margin: 0 0 6px; font-size: 1.3rem; }
    p { margin: 3px 0; }
    .cliente { border: 1px solid #cbd5e1; padding: 12px 16px; margin: 14px 0; }
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { border-bottom: 1px solid #cbd5e1; padding: 8px; text-align: left; }
    th { background: #f1f5f9; }
    .num { text-align: right; }
    .iva { text-align: right; }
    .total { text-align: right; font-size: 1.3rem; font-weight: 700; margin-top: 14px; }
    .cae { margin-top: 24px; border-top: 2px solid #1e293b; padding-top: 12px; text-align: right; }
    @media print { button { display: none; } body { margin: 0; } }
</style></head>
<body>
    <div class="cabecera">
        <div>
            <h1>FACTURA</h1>
            <p><strong>N°:</strong> ${numero}</p>
            <p><strong>Fecha de emisión:</strong> ${fechaArgentina(f.fechaEmision)}</p>
            <p><strong>Punto de venta:</strong> ${escaparHtml(f.puntoVentaDescripcion)}</p>
        </div>
        <div class="letra">${letra}</div>
    </div>
    <div class="cliente">
        <p><strong>Cliente:</strong> ${escaparHtml(f.clienteDenominacion)}</p>
        <p><strong>CUIT/CUIL:</strong> ${escaparHtml(f.clienteCuitCuil ?? '-')}</p>
        <p><strong>Condición frente al IVA:</strong> ${escaparHtml(f.condicionIva)}</p>
    </div>
    <table>
        <thead><tr><th>Descripción</th><th class="num">Cantidad</th><th class="num">Precio unit.</th><th class="num">Bonif.</th><th class="num">Subtotal</th></tr></thead>
        <tbody>${filas}</tbody>
    </table>
    ${lineasIva}
    <p class="total">Total: ${moneda(f.importeTotal)}</p>
    <div class="cae">
        <p><strong>CAE N°:</strong> ${escaparHtml(f.cae)}</p>
        <p><strong>Fecha de vencimiento del CAE:</strong> ${fechaArgentina(f.caeFechaVencimiento)}</p>
    </div>
    <button onclick="window.print()">Imprimir</button>
</body></html>`);
    ventana.document.close();
    ventana.focus();
    ventana.print();
}

/* ---------- Renglones de la factura ---------- */

function subtotalRenglon(renglon: FacturaDetalleRequestDTO): number {
    const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);

    if (!precio || !Number.isFinite(renglon.cantidad)) {
        return 0;
    }

    const bonificacion = renglon.porcentajeBonificacion ?? 0;
    return renglon.cantidad * precio.precioVenta * (1 - bonificacion / 100);
}

function actualizarTotales(): void {
    const filas = elemento<HTMLTableSectionElement>('tbodyRenglones').rows;

    renglones.forEach((renglon, indice) => {
        const celda = filas[indice]?.querySelector('[data-subtotal]');
        if (celda) {
            celda.textContent = moneda(subtotalRenglon(renglon));
        }
    });

    const bruto = renglones.reduce((suma, renglon) => suma + importeBruto(renglon), 0);
    const total = renglones.reduce((suma, renglon) => suma + subtotalRenglon(renglon), 0);
    const conArticulo = renglones.filter(r => preciosElegidos.has(r.listaPrecioArticuloId)).length;

    elemento('cantidadArticulos').textContent = String(conArticulo);
    elemento('subtotalFactura').textContent = moneda(bruto);
    elemento('bonificacionFactura').textContent = `-${moneda(bruto - total)}`;
    elemento('totalFactura').textContent = moneda(total);

    // Factura A y B: el precio ya incluye IVA 21%; en A se muestra discriminado
    const letra = letraFactura();
    const llevaIva = letra === 'A' || letra === 'B';
    elemento('filaIvaFactura').classList.toggle('oculto', !llevaIva);
    if (llevaIva) {
        const neto = Math.round(total / (1 + ALICUOTA_IVA / 100) * 100) / 100;
        elemento('etiquetaIvaFactura').textContent =
            `IVA ${ALICUOTA_IVA}% (${letra === 'A' ? 'discriminado' : 'incluido'})`;
        elemento('ivaFactura').textContent = moneda(total - neto);
    }
}

function importeBruto(renglon: FacturaDetalleRequestDTO): number {
    const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);
    return precio && Number.isFinite(renglon.cantidad) ? renglon.cantidad * precio.precioVenta : 0;
}

/** Cada artículo elegido en el buscador suma un renglón nuevo (aunque ya esté en la factura). */
function agregarRenglonCon(precio: PrecioArticuloExplorerDTO): void {
    preciosElegidos.set(precio.id, precio);
    renglones.push({
        listaPrecioArticuloId: precio.id,
        cantidad: 1,
        porcentajeBonificacion: 0
    });
    dibujarRenglones();
}

/* ---------- Buscador de artículos (filtro por lista, código o nombre; páginas de 20) ---------- */

let paginaArticulos = 0;
let solicitudArticulos = 0;

function llenarListasDelBuscador(listas: OpcionDTO[]): void {
    const select = elemento<HTMLSelectElement>('articuloLista');
    const todas = document.createElement('option');
    todas.value = '';
    todas.textContent = 'Todas las listas';
    select.replaceChildren(todas);
    for (const lista of listas) {
        const option = document.createElement('option');
        option.value = String(lista.id);
        option.textContent = lista.descripcion;
        select.appendChild(option);
    }
}

function abrirBuscadorArticulos(): void {
    elemento<HTMLDialogElement>('articuloDialog').showModal();
    elemento('articuloBusqueda').focus();
    // Al abrir se trae la primera página sin filtros
    void buscarArticulos(0);
}

async function buscarArticulos(pagina: number): Promise<void> {
    const dialog = elemento<HTMLDialogElement>('articuloDialog');
    const tbody = elemento<HTMLTableSectionElement>('articuloFilas');
    const estado = elemento('articuloEstado');
    const anterior = elemento<HTMLButtonElement>('btnArticuloAnterior');
    const siguiente = elemento<HTMLButtonElement>('btnArticuloSiguiente');
    const textoLista = valor('articuloLista');
    const listaPrecioId = textoLista === '' ? null : Number(textoLista);

    const solicitud = ++solicitudArticulos;
    estado.textContent = 'Buscando...';
    anterior.disabled = true;
    siguiente.disabled = true;

    try {
        const resultado = await api.buscarPreciosArticulos(listaPrecioId, valor('articuloBusqueda'), pagina);
        if (solicitud !== solicitudArticulos || !dialog.open) return;

        paginaArticulos = resultado.pagina;
        tbody.replaceChildren();
        estado.textContent = '';

        if (resultado.contenido.length === 0) {
            const celda = tbody.insertRow().insertCell();
            celda.colSpan = 5;
            celda.className = 'explorer-vacio';
            celda.textContent = 'No hay artículos para esa búsqueda.';
        }

        for (const precio of resultado.contenido) {
            const fila = tbody.insertRow();
            fila.insertCell().textContent = precio.articuloCodigo;
            fila.insertCell().textContent = precio.articuloDenominacion;
            fila.insertCell().textContent = precio.listaPrecioDenominacion;
            const celdaPrecio = fila.insertCell();
            celdaPrecio.className = 'texto-derecha';
            celdaPrecio.textContent = moneda(precio.precioVenta);
            fila.insertCell().appendChild(crearBoton('Agregar', 'btn-primario btn-chico', () => {
                agregarRenglonCon(precio);
                dialog.close();
            }));
        }

        const totalPaginas = Math.max(resultado.totalPaginas, 1);
        elemento('articuloPagina').textContent =
            `Página ${resultado.pagina + 1} de ${totalPaginas} · ${resultado.totalElementos} artículos`;
        anterior.disabled = resultado.pagina <= 0;
        siguiente.disabled = resultado.pagina + 1 >= totalPaginas;
    } catch (error) {
        if (solicitud !== solicitudArticulos || !dialog.open) return;
        estado.textContent = 'No se pudieron cargar los artículos. Volvé a intentar.';
        console.error(error);
    }
}

function dibujarRenglones(): void {
    const tbody = elemento<HTMLTableSectionElement>('tbodyRenglones');
    tbody.replaceChildren();

    if (renglones.length === 0) {
        const celda = tbody.insertRow().insertCell();
        celda.colSpan = 8;
        celda.className = 'explorer-vacio';
        celda.textContent = 'Todavía no agregaste artículos. Tocá "Buscar artículo".';
    }

    renglones.forEach((renglon, indice) => {
        const fila = tbody.insertRow();
        const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);

        fila.insertCell().textContent = precio?.articuloCodigo ?? '-';

        const celdaArticulo = fila.insertCell();
        const descripcion = document.createElement('span');
        descripcion.className = 'precio-seleccionado';
        descripcion.textContent = precio
            ? precio.articuloDenominacion
            : `Precio #${renglon.listaPrecioArticuloId} no disponible. Quitalo y buscá otro.`;
        celdaArticulo.appendChild(descripcion);

        fila.insertCell().textContent = precio?.listaPrecioDenominacion ?? '-';

        const celdaPrecio = fila.insertCell();
        celdaPrecio.className = 'texto-derecha';
        celdaPrecio.textContent = precio ? moneda(precio.precioVenta) : '-';

        const crearInput = (
            valorInicial: number,
            minimo: number,
            actualizar: (numero: number) => void,
            maximo?: number
        ): void => {
            const input = document.createElement('input');

            input.type = 'number';
            input.value = String(valorInicial);
            input.min = String(minimo);
            input.step = 'any';
            input.required = true;

            if (maximo !== undefined) {
                input.max = String(maximo);
            }

            input.addEventListener('input', () => {
                actualizar(Number(input.value));
                actualizarTotales();
            });

            fila.insertCell().appendChild(input);
        };

        crearInput(renglon.cantidad, 0.01, n => renglon.cantidad = n);

        crearInput(
            renglon.porcentajeBonificacion ?? 0,
            0,
            n => renglon.porcentajeBonificacion = n,
            100
        );

        const celdaSubtotal = fila.insertCell();
        celdaSubtotal.className = 'texto-derecha';
        celdaSubtotal.dataset.subtotal = '';

        const quitar = botonIcono('Quitar', 'eliminar', 'icono-rojo', () => {
            renglones.splice(indice, 1);
            dibujarRenglones();
        }, 'Quitar renglón');

        fila.insertCell().appendChild(quitar);
    });

    actualizarTotales();
}

/* ---------- Alta / edición ---------- */

function mostrarAlta(): void {
    facturaEnEdicionId = null;

    elemento('seccionListado').classList.add('oculto');
    elemento('seccionAlta').classList.remove('oculto');
    elemento('tabListado').classList.remove('activo');
    elemento('tabNueva').classList.add('activo');

    elemento<HTMLFormElement>('formAltaFactura').reset();
    prepararFechaEmision();
    seleccionarCliente(null);

    elemento('btnGuardarFactura').textContent =
        'Confirmar y Guardar Factura';

    renglones = [];
    dibujarRenglones();
}

const DIAS_TOLERANCIA_FECHA = 5;

function fechaISO(dias = 0): string {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + dias);
    const dosDigitos = (n: number) => String(n).padStart(2, '0');
    return `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}`;
}

/** La fecha arranca en hoy y ARCA solo acepta hasta 5 días antes o después. */
function prepararFechaEmision(): void {
    const fecha = elemento<HTMLInputElement>('inputFechaEmision');
    fecha.value = fechaISO();
    fecha.min = fechaISO(-DIAS_TOLERANCIA_FECHA);
    fecha.max = fechaISO(DIAS_TOLERANCIA_FECHA);
}

async function mostrarListado(): Promise<void> {
    facturaEnEdicionId = null;
    elemento('seccionAlta').classList.add('oculto');
    elemento('seccionListado').classList.remove('oculto');
    elemento('tabNueva').classList.remove('activo');
    elemento('tabListado').classList.add('activo');

    await cargarListado();
}

async function editarFactura(id: number): Promise<void> {
    const factura = await api.obtenerParaEdicion(id);

    // Solo se piden al servidor el cliente y los precios de ESTA factura
    const [, cliente, precios] = await Promise.all([
        cargarOpciones(),
        factura.clienteId === null
            ? Promise.resolve(null)
            : api.obtenerCliente(factura.clienteId).catch(() => null),
        Promise.all(factura.detalles.map(d =>
            api.obtenerPrecioArticulo(d.listaPrecioArticuloId).catch(() => null)))
    ]);

    for (const precio of precios) {
        if (precio) {
            preciosElegidos.set(precio.id, precio);
        }
    }

    mostrarAlta();

    facturaEnEdicionId = id;

    seleccionarCliente(cliente);
    if (factura.clienteId !== null && !cliente) {
        elemento<HTMLInputElement>('inputCliente').value = String(factura.clienteId);
        elemento<HTMLInputElement>('clienteDescripcion').value =
            `Cliente #${factura.clienteId} no disponible`;
        elemento('clienteNombreVisible').textContent =
            `Cliente #${factura.clienteId} no disponible`;
    }

    elemento<HTMLSelectElement>('inputCondicionIva').value = String(factura.condicionIvaId);
    elemento<HTMLSelectElement>('inputMoneda').value = String(factura.tipoMonedaId);
    elemento<HTMLSelectElement>('inputPuntoVenta').value = String(factura.puntoVentaId);
    aplicarCondicionDelCliente();
    actualizarLetra();

    if (factura.fechaEmision) {
        elemento<HTMLInputElement>('inputFechaEmision').value = factura.fechaEmision.slice(0, 10);
    }
    elemento<HTMLTextAreaElement>('inputObservaciones').value = factura.observaciones ?? '';

    renglones = factura.detalles.map(detalle => ({ ...detalle }));

    dibujarRenglones();

    elemento('btnGuardarFactura').textContent =
        `Guardar cambios de factura ${factura.numeroFactura}`;
}

/* ---------- Autorización en ARCA (paso aparte, desde el listado) ---------- */

let facturaParaAutorizar: FacturaReporteDTO | null = null;

function formatearCuit(cuit: string): string {
    const limpio = cuit.replace(/\D/g, '');
    return limpio.length === 11
        ? `${limpio.slice(0, 2)}-${limpio.slice(2, 10)}-${limpio.slice(10)}`
        : cuit;
}

function abrirAutorizacion(factura: FacturaReporteDTO): void {
    if (letraDe(factura) !== 'C') {
        alert(MENSAJE_ARCA_AB(letraDe(factura)));
        return;
    }
    facturaParaAutorizar = factura;
    const tieneCliente = Boolean(factura.clienteCuitCuil);

    elemento('autorizarTitulo').textContent = `Autorizar factura N° ${factura.numeroFactura} en ARCA`;
    elemento('autorizarCliente').textContent = factura.clienteDenominacion;
    elemento('autorizarTotal').textContent = moneda(factura.importeTotal);
    elemento('autorizarDocumento').textContent = tieneCliente
        ? formatearCuit(factura.clienteCuitCuil as string)
        : 'Consumidor final sin identificar';

    // El tipo de documento solo tiene sentido si hay un cliente con CUIT/CUIL
    elemento('campoTipoDocumento').classList.toggle('oculto', !tieneCliente);
    elemento<HTMLSelectElement>('autorizarTipoDocumento').value = '80';

    elemento<HTMLDialogElement>('autorizarDialog').showModal();
}

function documentoParaArca(factura: FacturaReporteDTO): { tipoDocumento: number; numeroDocumento: number } {
    if (!factura.clienteCuitCuil) {
        return { tipoDocumento: 99, numeroDocumento: 0 };
    }

    const tipoDocumento = numeroCampo('autorizarTipoDocumento');
    const documento = factura.clienteCuitCuil.replace(/\D/g, '');

    if (!/^\d{11}$/.test(documento)) {
        throw new Error('El CUIT/CUIL del cliente debe tener 11 dígitos.');
    }

    return { tipoDocumento, numeroDocumento: Number(documento) };
}

async function confirmarAutorizacion(): Promise<void> {
    const factura = facturaParaAutorizar;
    if (!factura) return;

    const documento = documentoParaArca(factura);
    const boton = elemento<HTMLButtonElement>('btnConfirmarAutorizar');
    boton.disabled = true;

    try {
        const autorizacion = await api.autorizarFactura(
            factura.id, documento.tipoDocumento, documento.numeroDocumento
        );
        elemento<HTMLDialogElement>('autorizarDialog').close();
        const resultado = autorizacion.resultado;

        if (resultado.resultado === 'A') {
            alert([
                `Factura ${autorizacion.numeroComprobante} autorizada en ARCA (homologación).`,
                `CAE: ${resultado.cae}`,
                `Vencimiento: ${resultado.fechaVencimiento}`,
                ...resultado.observaciones
            ].join('\n'));
        } else {
            alert([
                `ARCA rechazó la factura N° ${factura.numeroFactura}. Sigue pendiente.`,
                ...resultado.observaciones
            ].join('\n'));
        }

        ultimaGuardadaId = factura.id;
        await cargarListado();
    } finally {
        boton.disabled = false;
    }
}

async function guardarFactura(): Promise<void> {
    for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva']) {
        const select = elemento<HTMLSelectElement>(id);
        // La condición de IVA queda bloqueada a propósito (la define el cliente)
        const bloqueadaAPropósito = id === 'inputCondicionIva' && select.options.length > 1;
        if ((select.disabled && !bloqueadaAPropósito) || select.value === '') {
            throw new Error(
                'Seleccioná un punto de venta, una moneda y una condición de IVA disponibles.'
            );
        }
    }

    if (renglones.length === 0) {
        throw new Error('Agregá al menos un artículo con "Buscar artículo".');
    }

    const fechaEmision = valor('inputFechaEmision');
    if (fechaEmision === '') {
        throw new Error('Completá la fecha de emisión.');
    }

    for (const renglon of renglones) {
        if (!Number.isInteger(renglon.listaPrecioArticuloId)
            || renglon.listaPrecioArticuloId <= 0) {
            throw new Error('Seleccioná un artículo en cada renglón.');
        }
    }

    const textoCliente = valor('inputCliente');
    const clienteId = textoCliente === '' ? null : numeroCampo('inputCliente');

    const dto: FacturaRequestDTO = {
        clienteId,
        condicionIvaId: numeroCampo('inputCondicionIva'),
        tipoMonedaId: numeroCampo('inputMoneda'),
        puntoVentaId: numeroCampo('inputPuntoVenta'),
        detalles: renglones,
        fechaEmision,
        observaciones: valor('inputObservaciones') || null
    };

    if (facturaEnEdicionId === null) {
        // Guardar NO informa a ARCA: la factura queda PENDIENTE y se autoriza en otro paso
        const creada = await api.crearFactura(dto);
        ultimaGuardadaId = creada.id;

        alert(
            `Factura N° ${creada.numeroFactura} guardada como PENDIENTE. ` +
            `Total: ${moneda(creada.importeTotal)}.\n` +
            'Para informarla a ARCA usá el botón "Autorizar en ARCA" del listado.'
        );
    } else {
        const actualizada = await api.actualizarFactura(facturaEnEdicionId, dto);
        ultimaGuardadaId = actualizada.id;

        alert(
            `Factura ${actualizada.numeroFactura} actualizada. ` +
            `Total: ${moneda(actualizada.importeTotal)}`
        );
    }

    await mostrarListado();
}

function seleccionarCliente(cliente: ClienteExplorerDTO | null): void {
    clienteSeleccionado = cliente;
    elemento<HTMLInputElement>('inputCliente').value = cliente ? String(cliente.id) : '';
    elemento<HTMLInputElement>('clienteDescripcion').value =
        cliente?.denominacion ?? 'Consumidor final';
    elemento('clienteNombreVisible').textContent = cliente?.denominacion ?? 'Consumidor final';

    // Debajo de la razón social: nombre y apellido + CUIT/CUIL
    const persona = cliente ? nombreCompleto(cliente) : '';
    elemento('clienteDetalle').textContent = cliente
        ? [persona, `CUIT/CUIL ${formatearCuit(cliente.cuitCuil)}`].filter(Boolean).join(' · ')
        : 'Sin identificar';

    elemento('avatarCliente').textContent = cliente
        ? iniciales(persona || cliente.denominacion)
        : 'CF';

    // "Quitar cliente" solo tiene sentido si hay un cliente elegido
    elemento('btnQuitarCliente').classList.toggle('oculto', cliente === null);

    aplicarCondicionDelCliente();
    actualizarLetra();
}

function nombreCompleto(cliente: ClienteExplorerDTO): string {
    return [cliente.nombre, cliente.apellido].filter(Boolean).join(' ');
}

function iniciales(texto: string): string {
    return texto.split(/\s+/).filter(Boolean).slice(0, 2)
        .map(palabra => palabra[0].toUpperCase()).join('');
}

/* ---------- Ventana de búsqueda: consulta al servidor al presionar Buscar ---------- */

async function abrirExplorer<T>(
    titulo: string,
    columnas: string[],
    buscar: (texto: string) => Promise<T[]>,
    celdas: (registro: T) => string[],
    seleccionar: (registro: T) => void
): Promise<void> {
    const dialog = elemento<HTMLDialogElement>('explorerDialog');
    const busqueda = elemento<HTMLInputElement>('explorerBusqueda');
    const encabezado = elemento('explorerColumnas');
    const tbody = elemento<HTMLTableSectionElement>('explorerFilas');
    const estado = elemento('explorerEstado');
    const boton = elemento<HTMLButtonElement>('btnExplorerBuscar');

    elemento('explorerTitulo').textContent = titulo;
    busqueda.value = '';
    encabezado.replaceChildren();
    tbody.replaceChildren();
    estado.textContent = 'Escribí un texto (o dejalo vacío) y presioná Buscar. Se muestran hasta 20 resultados.';

    for (const nombre of [...columnas, '']) {
        const th = document.createElement('th');
        th.scope = 'col';
        th.textContent = nombre;
        encabezado.appendChild(th);
    }

    buscarEnExplorer = () => {
        void (async () => {
            const solicitud = ++solicitudExplorer;
            boton.disabled = true;
            estado.textContent = 'Buscando...';
            tbody.replaceChildren();

            try {
                const encontrados = await buscar(busqueda.value.trim());
                if (solicitud !== solicitudExplorer || !dialog.open) return;

                estado.textContent = `${encontrados.length} resultado(s)`;

                if (encontrados.length === 0) {
                    const celda = tbody.insertRow().insertCell();
                    celda.colSpan = columnas.length + 1;
                    celda.className = 'explorer-vacio';
                    celda.textContent = 'No hay resultados para la búsqueda.';
                }

                for (const registro of encontrados) {
                    const fila = tbody.insertRow();
                    for (const texto of celdas(registro)) fila.insertCell().textContent = texto;
                    fila.insertCell().appendChild(crearBoton('Seleccionar', 'btn-primario btn-chico', () => {
                        seleccionar(registro);
                        dialog.close();
                    }));
                }
            } catch (error) {
                if (solicitud !== solicitudExplorer || !dialog.open) return;
                estado.textContent = 'No se pudieron cargar los datos. Volvé a intentar.';
                console.error(error);
            } finally {
                boton.disabled = false;
            }
        })();
    };

    dialog.showModal();
    busqueda.focus();
}

document.addEventListener('DOMContentLoaded', () => {
    api.configurar(USUARIO_OPERADOR_ID, PORCENTAJE_IVA_FACTURA_C);

    elemento('btnBuscarCliente').addEventListener('click', () => {
        void abrirExplorer(
            'Seleccionar cliente', ['CUIT/CUIL', 'Razón social', 'Nombre y apellido'],
            texto => api.buscarClientes(texto),
            cliente => [formatearCuit(cliente.cuitCuil), cliente.denominacion, nombreCompleto(cliente) || '-'],
            seleccionarCliente
        );
    });
    elemento('btnQuitarCliente').addEventListener('click', () => seleccionarCliente(null));

    elemento('btnExplorerBuscar').addEventListener('click', () => buscarEnExplorer());
    elemento('explorerBusqueda').addEventListener('keydown', evento => {
        if (evento.key === 'Enter') {
            evento.preventDefault();
            buscarEnExplorer();
        }
    });
    elemento('btnCerrarExplorer').addEventListener('click', () => {
        elemento<HTMLDialogElement>('explorerDialog').close();
    });
    elemento('explorerDialog').addEventListener('keydown', evento => {
        if (evento.key === 'Escape') {
            evento.preventDefault();
            elemento<HTMLDialogElement>('explorerDialog').close();
        }
    });
    elemento('explorerDialog').addEventListener('close', () => {
        solicitudExplorer++;
        buscarEnExplorer = () => {};
    });

    elemento('btnBuscarArticulo').addEventListener('click', abrirBuscadorArticulos);
    elemento('btnArticuloBuscar').addEventListener('click', () => void buscarArticulos(0));
    elemento('articuloBusqueda').addEventListener('keydown', evento => {
        if (evento.key === 'Enter') {
            evento.preventDefault();
            void buscarArticulos(0);
        }
    });
    elemento('articuloLista').addEventListener('change', () => void buscarArticulos(0));
    elemento('btnArticuloTodos').addEventListener('click', () => {
        elemento<HTMLSelectElement>('articuloLista').value = '';
        elemento<HTMLInputElement>('articuloBusqueda').value = '';
        void buscarArticulos(0);
    });
    elemento('btnArticuloAnterior').addEventListener('click', () => void buscarArticulos(paginaArticulos - 1));
    elemento('btnArticuloSiguiente').addEventListener('click', () => void buscarArticulos(paginaArticulos + 1));
    elemento('btnCerrarArticulo').addEventListener('click', () => {
        elemento<HTMLDialogElement>('articuloDialog').close();
    });
    elemento('articuloDialog').addEventListener('close', () => {
        solicitudArticulos++;
    });
    elemento('inputPuntoVenta').addEventListener('change', actualizarLetra);

    elemento('btnConfirmarAutorizar').addEventListener('click', () => {
        void ejecutar(confirmarAutorizacion);
    });
    for (const id of ['btnCancelarAutorizar', 'btnCerrarAutorizar']) {
        elemento(id).addEventListener('click', () => {
            elemento<HTMLDialogElement>('autorizarDialog').close();
        });
    }

    elemento('tabNueva').addEventListener('click', () => {
        void ejecutar(async () => {
            await cargarOpciones();
            mostrarAlta();
        });
    });

    for (const id of ['tabListado', 'btnVolver', 'btnCancelarAlta']) {
        elemento(id).addEventListener('click', () => {
            void ejecutar(mostrarListado);
        });
    }


    elemento('selTamanioPagina').addEventListener('change', () => {
        paginaActual = 1;
        dibujarFacturas();
    });
    elemento('btnPaginaAnterior').addEventListener('click', () => {
        paginaActual--;
        dibujarFacturas();
    });
    elemento('btnPaginaSiguiente').addEventListener('click', () => {
        paginaActual++;
        dibujarFacturas();
    });

    elemento('btnAplicarFiltros').addEventListener('click', () => {
        ultimaGuardadaId = null;
        void ejecutar(cargarListado);
    });

    elemento('btnLimpiarFiltros').addEventListener('click', () => {
        for (const id of [
            'txtFiltroNumero',
            'txtFiltroCliente',
            'txtFiltroIva',
            'selFiltroEstado',
            'txtFechaDesde',
            'txtFechaHasta'
        ]) {
            elemento<HTMLInputElement>(id).value = '';
        }

        void ejecutar(cargarListado);
    });

    elemento<HTMLFormElement>('formAltaFactura')
        .addEventListener('submit', evento => {
            evento.preventDefault();

            const boton = elemento<HTMLButtonElement>('btnGuardarFactura');

            if (boton.disabled) {
                return;
            }

            boton.disabled = true;

            void ejecutar(async () => {
                try {
                    await guardarFactura();
                } finally {
                    boton.disabled = false;
                }
            });
        });

    void ejecutar(cargarListado);
});
