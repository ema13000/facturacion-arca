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
    `Para enviar una Factura ${letra}, hace falta un certificado de Responsable Inscripto. La factura sigue pendiente.`;

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
    select.querySelector('[data-condicion-registrada]')?.remove();
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
        const id = String(clienteSeleccionado.condicionIvaId);
        if (!opciones.some(opcion => opcion.value === id)) {
            const registrada = new Option(clienteSeleccionado.condicionIva ?? 'Condición registrada', id);
            registrada.disabled = true;
            registrada.dataset.condicionRegistrada = 'true';
            select.add(registrada);
        }
        select.value = id;
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
let solicitudListado = 0;
const MENSAJE_FECHA_ARCA = 'La fecha de emisión es anterior a hoy. Editá la factura antes de enviarla a ARCA.';
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
    verFactura: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h6"/>',
    motivo: '<path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z"/><path d="M8 8h8m-8 4h5"/>',
    quitar: '<path d="M18 6 6 18M6 6l12 12"/>',
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

function mensajeError(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

async function ejecutar(
    accion: () => Promise<void>,
    informarError: (error: unknown) => void = error => alert(mensajeError(error))
): Promise<void> {
    try {
        await accion();
    } catch (error) {
        console.error(error);
        informarError(error);
    }
}

function mostrarAviso(tipo: 'exito' | 'aviso', titulo: string, detalle: string): void {
    const dialogo = elemento<HTMLDialogElement>('avisoResultado');
    dialogo.dataset.tipo = tipo;
    elemento('avisoTitulo').textContent = titulo;
    elemento('avisoDetalle').textContent = detalle;
    if (!dialogo.open) dialogo.showModal();
}

function detalleArca(mensaje: string, alternativa: string): string {
    if (/(?:^|\D)10015(?!\d)/.test(mensaje) || mensaje.includes('CUIT del cliente debe tener')) {
        return 'Revisá el CUIT del cliente. La factura sigue pendiente.';
    }
    const fechaMinima = /la fecha mínima[^.]*?es (\d{2}\/\d{2}\/\d{4})/i.exec(mensaje)?.[1];
    if (fechaMinima) {
        return `Elegí el ${fechaMinima} o una fecha posterior para este punto de venta.`;
    }
    if (mensaje.includes('fecha de emisión es anterior a hoy')) {
        return 'La factura tiene una fecha anterior a hoy. Revisá la fecha de emisión.';
    }
    if (mensaje.includes('consumidor final con CUIT para facturas desde')) {
        return 'Por el monto de la factura, elegí un consumidor final con CUIT.';
    }
    if (/(?:^|\D)10016(?!\d)/.test(mensaje)) {
        return 'Revisá la fecha y el punto de venta. La factura sigue pendiente.';
    }
    if (/coe\.notAuthorized|cms\.cert\./.test(mensaje)) {
        return 'La conexión con ARCA necesita revisión. La factura sigue pendiente.';
    }
    return alternativa;
}

/* ---------- Listado de facturas con paginación ---------- */

function leerFiltros(): FacturaFiltrosDTO {
    const monto = elemento<HTMLInputElement>('txtMontoMinimo');
    if (!monto.checkValidity()) {
        throw new Error('Ingresá un monto mínimo mayor o igual a cero, con hasta dos decimales.');
    }
    const arca = valor('selFiltroArca');
    return {
        numeroFactura: valor('txtFiltroNumero'),
        cliente: valor('txtFiltroCliente'),
        condicionIvaId: valor('selFiltroIva'),
        montoMinimo: monto.value === '' ? undefined : monto.valueAsNumber,
        informadaArca: arca === '' ? undefined : arca === 'informadas',
        estado: valor('selFiltroEstado'),
        fechaDesde: valor('txtFechaDesde'),
        fechaHasta: valor('txtFechaHasta')
    };
}

async function cargarListado(): Promise<void> {
    const filtros = leerFiltros();
    const solicitud = ++solicitudListado;
    for (const id of ['btnReportePdf', 'btnReporteExcel']) {
        elemento<HTMLButtonElement>(id).disabled = true;
    }
    const facturas = await api.obtenerFacturas(filtros);
    if (solicitud !== solicitudListado) return;
    facturasListado = facturas;
    paginaActual = 1;
    dibujarFacturas();
    for (const id of ['btnReportePdf', 'btnReporteExcel']) {
        elemento<HTMLButtonElement>(id).disabled = false;
    }
}

function descargarArchivo({ archivo, nombre }: { archivo: Blob; nombre: string }): void {
    const url = URL.createObjectURL(archivo);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function descargarReporte(formato: 'pdf' | 'xlsx'): Promise<void> {
    descargarArchivo(await api.descargarReporte(formato, facturasListado.map(factura => factura.id)));
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
 * - ANULADA: consultar el motivo de anulación.
 */
function accionesSegunEstado(factura: FacturaReporteDTO): HTMLButtonElement[] {
    if (factura.estado === 'PENDIENTE') {
        const vencida = fechaAnteriorAHoy(factura);
        const autorizar = botonIcono('Autorizar en ARCA', 'enviar', 'icono-verde',
            () => abrirAutorizacion(factura), vencida ? MENSAJE_FECHA_ARCA : 'Informar la factura a ARCA y obtener el CAE');
        autorizar.disabled = vencida;
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
            autorizar
        ];
    }

    if (factura.estado === 'AUTORIZADA') {
        return [
            botonIcono('Anular', 'anular', 'icono-ambar', () => abrirAnulacion(factura), 'Anular factura'),
            botonIcono('Ver factura', 'verFactura', 'icono-indigo', () => {
                // La ventana se abre en el mismo clic para que el navegador no la bloquee
                const ventana = window.open('', '_blank');
                void ejecutar(async () => {
                    if (!ventana) {
                        throw new Error('El navegador bloqueó la vista de la factura. Permití ventanas emergentes.');
                    }
                    try {
                        mostrarFactura(ventana, await api.obtenerImpresion(factura.id));
                    } catch (error) {
                        ventana.close();
                        throw error;
                    }
                });
            }, 'Ver factura autorizada y opciones de impresión')
        ];
    }

    if (factura.estado === 'ANULADA') {
        return [botonIcono('Ver motivo de anulación', 'motivo', 'icono-ambar', () => {
            mostrarAviso('aviso', 'Motivo de anulación', factura.motivoAnulacion || 'No se registró un motivo.');
        })];
    }

    return [];
}

function abrirAnulacion(factura: FacturaReporteDTO): void {
    const dialogo = elemento<HTMLDialogElement>('anularDialog');
    const formulario = elemento<HTMLFormElement>('anularForm');
    const motivo = elemento<HTMLTextAreaElement>('anularMotivo');
    const error = elemento('anularError');
    const cancelar = elemento<HTMLButtonElement>('btnCancelarAnular');
    const confirmar = elemento<HTMLButtonElement>('btnConfirmarAnular');
    elemento('anularTitulo').textContent = `Anular factura n.º ${factura.numeroFactura}`;
    formulario.reset();
    error.textContent = '';

    cancelar.onclick = () => dialogo.close();
    motivo.oninput = () => { error.textContent = ''; };
    dialogo.oncancel = evento => {
        if (confirmar.disabled) evento.preventDefault();
    };
    formulario.onsubmit = evento => {
        evento.preventDefault();
        if (confirmar.disabled) return;
        const texto = motivo.value.trim();
        if (!texto) {
            error.textContent = 'Ingresá el motivo de anulación.';
            motivo.focus();
            return;
        }

        confirmar.disabled = cancelar.disabled = true;
        void ejecutar(async () => {
            await api.actualizarEstado(factura.id, 'ANULADA', texto);
            dialogo.close();
            mostrarAviso('exito', 'Factura anulada', 'Podés consultar el motivo desde el listado.');
            void ejecutar(cargarListado);
        }, () => {
            error.textContent = 'No se pudo anular la factura. Volvé a intentar.';
        }).finally(() => {
            confirmar.disabled = cancelar.disabled = false;
        });
    };
    dialogo.showModal();
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

function mostrarFactura(ventana: Window, f: FacturaImpresionDTO): void {
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
    .observaciones { margin: 16px 0; overflow-wrap: anywhere; }
    .observaciones p { white-space: pre-wrap; }
    .cae { margin-top: 24px; border-top: 2px solid #1e293b; padding-top: 12px; text-align: right; }
    .acciones-factura { display: flex; justify-content: flex-end; margin: 0 0 16px; }
    .btn-primario {
        display: inline-flex; align-items: center; gap: 8px; border: 0; border-radius: 8px;
        padding: 10px 16px; font: inherit; font-size: .9rem; font-weight: 650;
        color: #fff; background: linear-gradient(180deg, #6366f1, #4f46e5);
        box-shadow: 0 1px 2px rgba(79, 70, 229, .3); cursor: pointer;
    }
    .btn-primario:hover { background: #4338ca; }
    .btn-primario:focus-visible { outline: 3px solid #818cf8; outline-offset: 3px; }
    .btn-primario svg { width: 18px; height: 18px; }
    @media print { .acciones-factura { display: none; } body { margin: 0; } }
</style></head>
<body>
    <div class="acciones-factura">
        <button id="btnImprimirFactura" type="button" class="btn-primario">
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
                <path d="M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2M6 14h12v8H6z"/>
            </svg>
            Imprimir factura
        </button>
    </div>
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
        <p><strong>CUIT:</strong> ${escaparHtml(f.clienteCuit ?? '-')}</p>
        <p><strong>Condición frente al IVA:</strong> ${escaparHtml(f.condicionIva)}</p>
    </div>
    <table>
        <thead><tr><th>Descripción</th><th class="num">Cantidad</th><th class="num">Precio unit.</th><th class="num">Bonif.</th><th class="num">Subtotal</th></tr></thead>
        <tbody>${filas}</tbody>
    </table>
    ${lineasIva}
    <p class="total">Total: ${moneda(f.importeTotal)}</p>
    ${f.observaciones?.trim() ? `
    <section class="observaciones">
        <strong>Observaciones</strong>
        <p>${escaparHtml(f.observaciones)}</p>
    </section>` : ''}
    <div class="cae">
        <p><strong>CAE N°:</strong> ${escaparHtml(f.cae)}</p>
        <p><strong>Fecha de vencimiento del CAE:</strong> ${fechaArgentina(f.caeFechaVencimiento)}</p>
    </div>
</body></html>`);
    ventana.document.close();
    ventana.document.getElementById('btnImprimirFactura')?.addEventListener('click', () => ventana.print());
    ventana.focus();
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

const calendarioArgentina = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Argentina/Buenos_Aires',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
});

function fechaISO(dias = 0): string {
    const hoy = calendarioArgentina.format(new Date());
    const fecha = new Date(`${hoy}T00:00:00Z`);
    fecha.setUTCDate(fecha.getUTCDate() + dias);
    return fecha.toISOString().slice(0, 10);
}

function fechaAnteriorAHoy(factura: FacturaReporteDTO): boolean {
    return factura.fechaEmision.slice(0, 10) < fechaISO();
}

/** Rango disponible para guardar el comprobante pendiente. */
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
    if (fechaAnteriorAHoy(factura)) {
        mostrarAviso('aviso', 'No se pudo enviar a ARCA', MENSAJE_FECHA_ARCA);
        return;
    }
    if (letraDe(factura) !== 'C') {
        mostrarAviso('aviso', 'No se pudo enviar a ARCA', MENSAJE_ARCA_AB(letraDe(factura)));
        return;
    }
    const cuit = factura.clienteCuit?.trim();
    facturaParaAutorizar = factura;
    elemento('autorizarTitulo').textContent = `Autorizar factura N° ${factura.numeroFactura} en ARCA`;
    elemento('autorizarCliente').textContent = factura.clienteDenominacion;
    elemento('autorizarTotal').textContent = moneda(factura.importeTotal);
    elemento('autorizarCuit').textContent = cuit ? formatearCuit(cuit) : 'Sin identificar';
    elemento<HTMLDialogElement>('autorizarDialog').showModal();
}

async function confirmarAutorizacion(): Promise<void> {
    const factura = facturaParaAutorizar;
    if (!factura) return;
    if (fechaAnteriorAHoy(factura)) {
        throw new Error(MENSAJE_FECHA_ARCA);
    }

    const boton = elemento<HTMLButtonElement>('btnConfirmarAutorizar');
    boton.disabled = true;

    try {
        const autorizacion = await api.autorizarFactura(factura.id);
        elemento<HTMLDialogElement>('autorizarDialog').close();
        const resultado = autorizacion.resultado;

        if (resultado.resultado === 'A') {
            mostrarAviso('exito', 'Factura autorizada', `Podés ver la factura n.º ${factura.numeroFactura} en el listado.`);
        } else {
            mostrarAviso('aviso', 'ARCA rechazó la factura',
                detalleArca(resultado.observaciones.join(' '), 'La factura sigue pendiente. Revisá los datos antes de volver a enviarla.'));
        }

        ultimaGuardadaId = factura.id;
        await cargarListado().catch(error => console.error(error));
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

    const idEnEdicion = facturaEnEdicionId;
    const factura = idEnEdicion === null ? await api.crearFactura(dto) : await api.actualizarFactura(idEnEdicion, dto);
    ultimaGuardadaId = factura.id;
    await mostrarListado().catch(error => console.error(error));
    mostrarAviso('exito', idEnEdicion === null ? 'Factura guardada' : 'Factura actualizada',
        `Factura n.º ${factura.numeroFactura}. Pendiente de enviar a ARCA.`);
}

function seleccionarCliente(cliente: ClienteExplorerDTO | null): void {
    clienteSeleccionado = cliente;
    elemento<HTMLInputElement>('inputCliente').value = cliente ? String(cliente.id) : '';
    elemento<HTMLInputElement>('clienteDescripcion').value =
        cliente?.denominacion ?? 'Consumidor final';
    elemento('clienteNombreVisible').textContent = cliente?.denominacion ?? 'Consumidor final';

    // Debajo de la razón social: nombre y apellido + CUIT
    const persona = cliente ? nombreCompleto(cliente) : '';
    elemento('clienteDetalle').textContent = cliente
        ? [persona, `CUIT ${formatearCuit(cliente.cuit)}`].filter(Boolean).join(' · ')
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

    solicitudExplorer++;
    boton.disabled = false;
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
                if (solicitud === solicitudExplorer && dialog.open) {
                    boton.disabled = false;
                }
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
            'Seleccionar cliente', ['CUIT', 'Razón social', 'Nombre y apellido'],
            texto => api.buscarClientes(texto),
            cliente => [formatearCuit(cliente.cuit), cliente.denominacion, nombreCompleto(cliente) || '-'],
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
    elemento<HTMLDialogElement>('explorerDialog').addEventListener('close', evento => {
        // El evento puede llegar después de que el usuario ya volvió a abrir el buscador.
        if ((evento.currentTarget as HTMLDialogElement).open) return;
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
        void ejecutar(confirmarAutorizacion, error => {
            elemento<HTMLDialogElement>('autorizarDialog').close();
            mostrarAviso('aviso', 'No se pudo enviar a ARCA',
                detalleArca(mensajeError(error), 'La factura sigue pendiente. Volvé a intentar en unos minutos.'));
        });
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
            'selFiltroIva',
            'selFiltroEstado',
            'selFiltroArca',
            'txtMontoMinimo',
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
            }, error => {
                const mensaje = mensajeError(error);
                const detalle = /^(Seleccioná|Agregá|Completá)(?=\s|$)/.test(mensaje)
                    ? mensaje : 'Revisá los datos de la factura y volvé a intentar.';
                mostrarAviso('aviso', 'No se pudo guardar', detalle);
            });
        });

    elemento('btnReportePdf').addEventListener('click', () => void ejecutar(() => descargarReporte('pdf')));
    elemento('btnReporteExcel').addEventListener('click', () => void ejecutar(() => descargarReporte('xlsx')));

    void ejecutar(async () => {
        llenarOpciones('selFiltroIva', await api.obtenerCondicionesIva(), 'Todas');
    });
    void ejecutar(cargarListado);
});
