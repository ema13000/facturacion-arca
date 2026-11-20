import { FacturaService } from './services/FacturaService.js';
// Usuario que registra las operaciones (ya no se pide en pantalla)
const USUARIO_OPERADOR_ID = 1;
// Factura C (monotributo): ARCA no admite IVA discriminado, por eso siempre es 0
const PORCENTAJE_IVA_FACTURA_C = 0;
const api = new FacturaService();
let renglones = [];
let facturaEnEdicionId = null;
// Precios ya elegidos, por ID: se usan para mostrar descripción y calcular el total
const preciosElegidos = new Map();
let clienteSeleccionado = null;
let buscarEnExplorer = () => { };
let solicitudExplorer = 0;
let facturasListado = [];
let paginaActual = 1;
// ID de la última factura guardada: se resalta en el listado
let ultimaGuardadaId = null;
function elemento(id) {
    const encontrado = document.getElementById(id);
    if (!encontrado) {
        throw new Error(`Falta el elemento HTML: ${id}`);
    }
    return encontrado;
}
function valor(id) {
    return elemento(id).value.trim();
}
function numeroCampo(id) {
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
function moneda(importe) {
    return `$${formatoMoneda.format(importe)}`;
}
// Íconos (trazos estilo "Lucide") para los botones de acción
const ICONOS = {
    editar: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    eliminar: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>',
    anular: '<circle cx="12" cy="12" r="10"/><path d="m4.9 4.9 14.2 14.2"/>',
    imprimir: '<path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/>',
    quitar: '<path d="M18 6 6 18M6 6l12 12"/>',
    pdf: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M12 12v6"/><path d="m9 15 3 3 3-3"/>',
    enviar: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>'
};
/** Botón de ícono: el texto queda oculto visualmente pero sirve para lectores de pantalla. */
function botonIcono(texto, icono, clase, accion, descripcion = texto) {
    const boton = crearBoton('', `btn-icono ${clase}`, accion);
    boton.title = descripcion;
    boton.innerHTML = `<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONOS[icono]}</svg>`;
    const etiqueta = document.createElement('span');
    etiqueta.className = 'sr-only';
    etiqueta.textContent = texto;
    boton.appendChild(etiqueta);
    return boton;
}
function actualizarKpis(facturas) {
    const contar = (estado) => facturas.filter(f => f.estado === estado).length;
    const total = facturas
        .filter(f => f.estado !== 'ANULADA')
        .reduce((suma, f) => suma + f.importeTotal, 0);
    elemento('kpiTotal').textContent = moneda(total);
    elemento('kpiPendientes').textContent = String(contar('PENDIENTE'));
    elemento('kpiAutorizadas').textContent = String(contar('AUTORIZADA'));
    elemento('kpiAnuladas').textContent = String(contar('ANULADA'));
}
function llenarOpciones(id, opciones, mensaje) {
    const select = elemento(id);
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
const SELECTS_CATALOGO = ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva', 'inputListaPrecio'];
async function cargarOpciones() {
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
        llenarOpciones('inputListaPrecio', listasPrecio, 'Seleccioná una lista de precios');
    }
    catch (error) {
        for (const id of SELECTS_CATALOGO) {
            const select = elemento(id);
            const option = document.createElement('option');
            option.value = '';
            option.textContent = 'No se pudieron cargar las opciones';
            select.replaceChildren(option);
            select.disabled = true;
        }
        throw error;
    }
}
async function ejecutar(accion) {
    try {
        await accion();
    }
    catch (error) {
        console.error(error);
        alert(error instanceof Error ? error.message : String(error));
    }
}
/* ---------- Listado de facturas con paginación ---------- */
function leerFiltros() {
    return {
        numeroFactura: valor('txtFiltroNumero'),
        cliente: valor('txtFiltroCliente'),
        condicionIva: valor('txtFiltroIva'),
        estado: valor('selFiltroEstado'),
        fechaDesde: valor('txtFechaDesde'),
        fechaHasta: valor('txtFechaHasta')
    };
}
async function cargarListado() {
    facturasListado = await api.obtenerFacturas(leerFiltros());
    paginaActual = 1;
    dibujarFacturas();
}
function tamanioPagina() {
    return Number(valor('selTamanioPagina')) || 10;
}
function crearBoton(texto, clase, accion) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.textContent = texto;
    boton.className = clase;
    boton.addEventListener('click', accion);
    return boton;
}
function dibujarFacturas() {
    const facturas = facturasListado;
    const tbody = elemento('tbodyFacturas');
    tbody.replaceChildren();
    elemento('contadorFacturas').textContent =
        `${facturas.length} comprobante(s)`;
    actualizarKpis(facturas);
    const porPagina = tamanioPagina();
    const totalPaginas = Math.max(1, Math.ceil(facturas.length / porPagina));
    paginaActual = Math.min(Math.max(paginaActual, 1), totalPaginas);
    elemento('infoPagina').textContent = `Página ${paginaActual} de ${totalPaginas}`;
    elemento('btnPaginaAnterior').disabled = paginaActual <= 1;
    elemento('btnPaginaSiguiente').disabled = paginaActual >= totalPaginas;
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
        const datos = [
            String(factura.numeroFactura),
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
function accionesSegunEstado(factura) {
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
            botonIcono('Autorizar en ARCA', 'enviar', 'icono-verde', () => abrirAutorizacion(factura), 'Informar la factura a ARCA y obtener el CAE')
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
                    }
                    catch (error) {
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
function escaparHtml(texto) {
    return texto.replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c]);
}
function fechaArgentina(iso) {
    const [anio, mes, dia] = iso.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
}
function imprimirFactura(ventana, f) {
    const numero = `${String(f.puntoVentaNumero).padStart(4, '0')}-${String(f.numeroFactura).padStart(8, '0')}`;
    const filas = f.detalles.map(d => `
        <tr>
            <td>${escaparHtml(d.descripcion ?? '')}</td>
            <td class="num">${d.cantidad}</td>
            <td class="num">${moneda(d.precioUnitario)}</td>
            <td class="num">${d.porcentajeBonificacion}%</td>
            <td class="num">${moneda(d.importeSubtotal)}</td>
        </tr>`).join('');
    ventana.document.write(`<!DOCTYPE html>
<html lang="es"><head><meta charset="UTF-8"><title>Factura C ${numero}</title>
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
        <div class="letra">C</div>
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
function listaPrecioElegida() {
    const texto = valor('inputListaPrecio');
    return texto === '' ? null : Number(texto);
}
function subtotalRenglon(renglon) {
    const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);
    if (!precio || !Number.isFinite(renglon.cantidad)) {
        return 0;
    }
    const bonificacion = renglon.porcentajeBonificacion ?? 0;
    return renglon.cantidad * precio.precioVenta * (1 - bonificacion / 100);
}
function actualizarTotales() {
    const filas = elemento('tbodyRenglones').rows;
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
}
function importeBruto(renglon) {
    const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);
    return precio && Number.isFinite(renglon.cantidad) ? renglon.cantidad * precio.precioVenta : 0;
}
function agregarRenglon() {
    renglones.push({
        listaPrecioArticuloId: 0,
        cantidad: 1,
        porcentajeBonificacion: 0
    });
    dibujarRenglones();
}
function buscarArticulo(renglon) {
    const listaPrecioId = listaPrecioElegida();
    if (listaPrecioId === null) {
        alert('Seleccioná primero una lista de precios.');
        return;
    }
    const lista = elemento('inputListaPrecio');
    const nombreLista = lista.selectedOptions[0]?.textContent ?? '';
    void abrirExplorer(`Artículos de la lista "${nombreLista}"`, ['Código', 'Artículo', 'Precio'], texto => api.buscarPreciosArticulos(listaPrecioId, texto), p => [p.articuloCodigo, p.articuloDenominacion, moneda(p.precioVenta)], elegido => {
        preciosElegidos.set(elegido.id, elegido);
        renglon.listaPrecioArticuloId = elegido.id;
        dibujarRenglones();
    });
}
let buscandoCodigo = false;
async function cargarArticuloPorCodigo(renglon, codigo) {
    const listaPrecioId = listaPrecioElegida();
    if (listaPrecioId === null || buscandoCodigo)
        return;
    buscandoCodigo = true;
    try {
        const precio = await api.obtenerPrecioPorCodigo(listaPrecioId, codigo);
        preciosElegidos.set(precio.id, precio);
        renglon.listaPrecioArticuloId = precio.id;
    }
    catch (error) {
        renglon.listaPrecioArticuloId = 0;
        throw error;
    }
    finally {
        buscandoCodigo = false;
        dibujarRenglones();
    }
}
function dibujarRenglones() {
    const tbody = elemento('tbodyRenglones');
    tbody.replaceChildren();
    const sinLista = listaPrecioElegida() === null;
    renglones.forEach((renglon, indice) => {
        const fila = tbody.insertRow();
        const precio = preciosElegidos.get(renglon.listaPrecioArticuloId);
        // Código del artículo: se escribe y con Enter (o al salir del campo) se carga
        const inputCodigo = document.createElement('input');
        inputCodigo.type = 'text';
        inputCodigo.className = 'input-codigo';
        inputCodigo.placeholder = 'Ej: ART-015';
        inputCodigo.value = precio?.articuloCodigo ?? '';
        inputCodigo.dataset.codigo = '';
        inputCodigo.disabled = sinLista;
        inputCodigo.title = sinLista ? 'Elegí primero una lista de precios' : 'Escribí el código y presioná Enter';
        inputCodigo.setAttribute('aria-label', `Código del artículo del renglón ${indice + 1}`);
        const cargarPorCodigo = () => {
            const codigo = inputCodigo.value.trim();
            if (codigo === '' || codigo.toUpperCase() === precio?.articuloCodigo)
                return;
            void ejecutar(() => cargarArticuloPorCodigo(renglon, codigo));
        };
        inputCodigo.addEventListener('keydown', evento => {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                cargarPorCodigo();
            }
        });
        inputCodigo.addEventListener('change', cargarPorCodigo);
        fila.insertCell().appendChild(inputCodigo);
        const celdaArticulo = fila.insertCell();
        const descripcion = document.createElement('span');
        descripcion.className = 'precio-seleccionado';
        descripcion.textContent = precio
            ? precio.articuloDenominacion
            : renglon.listaPrecioArticuloId > 0
                ? `Precio #${renglon.listaPrecioArticuloId} no disponible. Seleccioná otro.`
                : 'Sin artículo seleccionado';
        const buscar = crearBoton(precio ? 'Cambiar artículo' : 'Buscar artículo', 'btn-secundario btn-chico', () => buscarArticulo(renglon));
        buscar.dataset.buscarPrecio = '';
        buscar.disabled = sinLista;
        buscar.title = sinLista ? 'Elegí primero una lista de precios' : '';
        celdaArticulo.append(descripcion, buscar);
        const celdaPrecio = fila.insertCell();
        celdaPrecio.className = 'texto-derecha';
        celdaPrecio.textContent = precio ? moneda(precio.precioVenta) : '-';
        const crearInput = (valorInicial, minimo, actualizar, maximo) => {
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
        crearInput(renglon.porcentajeBonificacion ?? 0, 0, n => renglon.porcentajeBonificacion = n, 100);
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
function cambiarListaPrecio(evento) {
    const select = evento.target;
    const hayElegidos = renglones.some(r => r.listaPrecioArticuloId > 0);
    if (hayElegidos && !confirm('Al cambiar la lista de precios se quitan los artículos elegidos. ¿Continuar?')) {
        select.value = select.dataset.anterior ?? '';
        return;
    }
    for (const renglon of renglones) {
        renglon.listaPrecioArticuloId = 0;
    }
    select.dataset.anterior = select.value;
    dibujarRenglones();
}
/* ---------- Alta / edición ---------- */
function mostrarAlta() {
    facturaEnEdicionId = null;
    elemento('seccionListado').classList.add('oculto');
    elemento('seccionAlta').classList.remove('oculto');
    elemento('tabListado').classList.remove('activo');
    elemento('tabNueva').classList.add('activo');
    elemento('formAltaFactura').reset();
    elemento('inputListaPrecio').dataset.anterior = '';
    seleccionarCliente(null);
    elemento('btnGuardarFactura').textContent =
        'Confirmar y Guardar Factura';
    renglones = [];
    agregarRenglon();
}
async function mostrarListado() {
    facturaEnEdicionId = null;
    elemento('seccionAlta').classList.add('oculto');
    elemento('seccionListado').classList.remove('oculto');
    elemento('tabNueva').classList.remove('activo');
    elemento('tabListado').classList.add('activo');
    await cargarListado();
}
async function editarFactura(id) {
    const factura = await api.obtenerParaEdicion(id);
    // Solo se piden al servidor el cliente y los precios de ESTA factura
    const [, cliente, precios] = await Promise.all([
        cargarOpciones(),
        factura.clienteId === null
            ? Promise.resolve(null)
            : api.obtenerCliente(factura.clienteId).catch(() => null),
        Promise.all(factura.detalles.map(d => api.obtenerPrecioArticulo(d.listaPrecioArticuloId).catch(() => null)))
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
        elemento('inputCliente').value = String(factura.clienteId);
        elemento('clienteDescripcion').value =
            `Cliente #${factura.clienteId} no disponible`;
        elemento('clienteNombreVisible').textContent =
            `Cliente #${factura.clienteId} no disponible`;
    }
    elemento('inputCondicionIva').value = String(factura.condicionIvaId);
    elemento('inputMoneda').value = String(factura.tipoMonedaId);
    elemento('inputPuntoVenta').value = String(factura.puntoVentaId);
    const primerPrecio = precios.find(p => p !== null);
    if (primerPrecio) {
        const lista = elemento('inputListaPrecio');
        lista.value = String(primerPrecio.listaPrecioId);
        lista.dataset.anterior = lista.value;
    }
    renglones = factura.detalles.map(detalle => ({ ...detalle }));
    dibujarRenglones();
    elemento('btnGuardarFactura').textContent =
        `Guardar cambios de factura ${factura.numeroFactura}`;
}
/* ---------- Autorización en ARCA (paso aparte, desde el listado) ---------- */
let facturaParaAutorizar = null;
function formatearCuit(cuit) {
    const limpio = cuit.replace(/\D/g, '');
    return limpio.length === 11
        ? `${limpio.slice(0, 2)}-${limpio.slice(2, 10)}-${limpio.slice(10)}`
        : cuit;
}
function abrirAutorizacion(factura) {
    facturaParaAutorizar = factura;
    const tieneCliente = Boolean(factura.clienteCuitCuil);
    elemento('autorizarTitulo').textContent = `Autorizar factura N° ${factura.numeroFactura} en ARCA`;
    elemento('autorizarCliente').textContent = factura.clienteDenominacion;
    elemento('autorizarTotal').textContent = moneda(factura.importeTotal);
    elemento('autorizarDocumento').textContent = tieneCliente
        ? formatearCuit(factura.clienteCuitCuil)
        : 'Consumidor final sin identificar';
    // El tipo de documento solo tiene sentido si hay un cliente con CUIT/CUIL
    elemento('campoTipoDocumento').classList.toggle('oculto', !tieneCliente);
    elemento('autorizarTipoDocumento').value = '80';
    elemento('autorizarDialog').showModal();
}
function documentoParaArca(factura) {
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
async function confirmarAutorizacion() {
    const factura = facturaParaAutorizar;
    if (!factura)
        return;
    const documento = documentoParaArca(factura);
    const boton = elemento('btnConfirmarAutorizar');
    boton.disabled = true;
    try {
        const autorizacion = await api.autorizarFactura(factura.id, documento.tipoDocumento, documento.numeroDocumento);
        elemento('autorizarDialog').close();
        const resultado = autorizacion.resultado;
        if (resultado.resultado === 'A') {
            alert([
                `Factura ${autorizacion.numeroComprobante} autorizada en ARCA (homologación).`,
                `CAE: ${resultado.cae}`,
                `Vencimiento: ${resultado.fechaVencimiento}`,
                ...resultado.observaciones
            ].join('\n'));
        }
        else {
            alert([
                `ARCA rechazó la factura N° ${factura.numeroFactura}. Sigue pendiente.`,
                ...resultado.observaciones
            ].join('\n'));
        }
        ultimaGuardadaId = factura.id;
        await cargarListado();
    }
    finally {
        boton.disabled = false;
    }
}
async function guardarFactura() {
    for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva']) {
        const select = elemento(id);
        if (select.disabled || select.value === '') {
            throw new Error('Seleccioná un punto de venta, una moneda y una condición de IVA disponibles.');
        }
    }
    if (renglones.length === 0) {
        throw new Error('Agregá al menos un renglón.');
    }
    for (const renglon of renglones) {
        if (!Number.isInteger(renglon.listaPrecioArticuloId)
            || renglon.listaPrecioArticuloId <= 0) {
            throw new Error('Seleccioná un artículo en cada renglón.');
        }
    }
    const textoCliente = valor('inputCliente');
    const clienteId = textoCliente === '' ? null : numeroCampo('inputCliente');
    const dto = {
        clienteId,
        condicionIvaId: numeroCampo('inputCondicionIva'),
        tipoMonedaId: numeroCampo('inputMoneda'),
        puntoVentaId: numeroCampo('inputPuntoVenta'),
        detalles: renglones
    };
    if (facturaEnEdicionId === null) {
        // Guardar NO informa a ARCA: la factura queda PENDIENTE y se autoriza en otro paso
        const creada = await api.crearFactura(dto);
        ultimaGuardadaId = creada.id;
        alert(`Factura N° ${creada.numeroFactura} guardada como PENDIENTE. ` +
            `Total: ${moneda(creada.importeTotal)}.\n` +
            'Para informarla a ARCA usá el botón "Autorizar en ARCA" del listado.');
    }
    else {
        const actualizada = await api.actualizarFactura(facturaEnEdicionId, dto);
        ultimaGuardadaId = actualizada.id;
        alert(`Factura ${actualizada.numeroFactura} actualizada. ` +
            `Total: ${moneda(actualizada.importeTotal)}`);
    }
    await mostrarListado();
}
function seleccionarCliente(cliente) {
    clienteSeleccionado = cliente;
    elemento('inputCliente').value = cliente ? String(cliente.id) : '';
    elemento('clienteDescripcion').value =
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
}
function nombreCompleto(cliente) {
    return [cliente.nombre, cliente.apellido].filter(Boolean).join(' ');
}
function iniciales(texto) {
    return texto.split(/\s+/).filter(Boolean).slice(0, 2)
        .map(palabra => palabra[0].toUpperCase()).join('');
}
/* ---------- Ventana de búsqueda: consulta al servidor al presionar Buscar ---------- */
async function abrirExplorer(titulo, columnas, buscar, celdas, seleccionar) {
    const dialog = elemento('explorerDialog');
    const busqueda = elemento('explorerBusqueda');
    const encabezado = elemento('explorerColumnas');
    const tbody = elemento('explorerFilas');
    const estado = elemento('explorerEstado');
    const boton = elemento('btnExplorerBuscar');
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
                if (solicitud !== solicitudExplorer || !dialog.open)
                    return;
                estado.textContent = `${encontrados.length} resultado(s)`;
                if (encontrados.length === 0) {
                    const celda = tbody.insertRow().insertCell();
                    celda.colSpan = columnas.length + 1;
                    celda.className = 'explorer-vacio';
                    celda.textContent = 'No hay resultados para la búsqueda.';
                }
                for (const registro of encontrados) {
                    const fila = tbody.insertRow();
                    for (const texto of celdas(registro))
                        fila.insertCell().textContent = texto;
                    fila.insertCell().appendChild(crearBoton('Seleccionar', 'btn-primario btn-chico', () => {
                        seleccionar(registro);
                        dialog.close();
                    }));
                }
            }
            catch (error) {
                if (solicitud !== solicitudExplorer || !dialog.open)
                    return;
                estado.textContent = 'No se pudieron cargar los datos. Volvé a intentar.';
                console.error(error);
            }
            finally {
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
        void abrirExplorer('Seleccionar cliente', ['CUIT/CUIL', 'Razón social', 'Nombre y apellido'], texto => api.buscarClientes(texto), cliente => [formatearCuit(cliente.cuitCuil), cliente.denominacion, nombreCompleto(cliente) || '-'], seleccionarCliente);
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
        elemento('explorerDialog').close();
    });
    elemento('explorerDialog').addEventListener('keydown', evento => {
        if (evento.key === 'Escape') {
            evento.preventDefault();
            elemento('explorerDialog').close();
        }
    });
    elemento('explorerDialog').addEventListener('close', () => {
        solicitudExplorer++;
        buscarEnExplorer = () => { };
    });
    elemento('inputListaPrecio').addEventListener('change', cambiarListaPrecio);
    elemento('btnConfirmarAutorizar').addEventListener('click', () => {
        void ejecutar(confirmarAutorizacion);
    });
    for (const id of ['btnCancelarAutorizar', 'btnCerrarAutorizar']) {
        elemento(id).addEventListener('click', () => {
            elemento('autorizarDialog').close();
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
    elemento('btnSumarRenglon').addEventListener('click', agregarRenglon);
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
            elemento(id).value = '';
        }
        void ejecutar(cargarListado);
    });
    elemento('formAltaFactura')
        .addEventListener('submit', evento => {
        evento.preventDefault();
        const boton = elemento('btnGuardarFactura');
        if (boton.disabled) {
            return;
        }
        boton.disabled = true;
        void ejecutar(async () => {
            try {
                await guardarFactura();
            }
            finally {
                boton.disabled = false;
            }
        });
    });
    void ejecutar(cargarListado);
});
