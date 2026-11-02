import { FacturaService } from './services/FacturaService.js';
const api = new FacturaService();
let renglones = [];
let facturaEnEdicionId = null;
let preciosArticulos = [];
let filtrarExplorer = () => { };
let solicitudExplorer = 0;
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
async function cargarOpciones() {
    try {
        const [puntosVenta, monedas, condicionesIva] = await Promise.all([
            api.obtenerPuntosVenta(),
            api.obtenerTiposMoneda(),
            api.obtenerCondicionesIva()
        ]);
        llenarOpciones('inputPuntoVenta', puntosVenta, 'Seleccioná un punto de venta');
        llenarOpciones('inputMoneda', monedas, 'Seleccioná una moneda');
        llenarOpciones('inputCondicionIva', condicionesIva, 'Seleccioná una condición de IVA');
    }
    catch (error) {
        for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva']) {
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
function configurarOperacion(numero) {
    api.configurar(numeroCampo('inputUsuario'), numeroCampo('inputPorcentajeIva'), numero);
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
    const facturas = await api.obtenerFacturas(leerFiltros());
    dibujarFacturas(facturas);
}
function dibujarFacturas(facturas) {
    const tbody = elemento('tbodyFacturas');
    tbody.replaceChildren();
    elemento('contadorFacturas').textContent =
        `${facturas.length} comprobante(s)`;
    if (facturas.length === 0) {
        const fila = tbody.insertRow();
        const celda = fila.insertCell();
        celda.colSpan = 8;
        celda.textContent = 'No hay facturas que coincidan con la búsqueda.';
        return;
    }
    for (const factura of facturas) {
        const fila = tbody.insertRow();
        const datos = [
            String(factura.numeroFactura),
            factura.fechaEmision.slice(0, 10),
            factura.clienteDenominacion,
            factura.condicionIva,
            factura.puntoVentaDescripcion,
            `$${factura.importeTotal.toFixed(2)}`,
            factura.estado
        ];
        for (const dato of datos) {
            fila.insertCell().textContent = dato;
        }
        const acciones = fila.insertCell();
        const editar = document.createElement('button');
        editar.type = 'button';
        editar.textContent = 'Editar';
        editar.className = 'btn-chico btn-secundario';
        editar.disabled = factura.estado === 'ANULADA';
        editar.addEventListener('click', () => {
            void ejecutar(() => editarFactura(factura.id));
        });
        const anular = document.createElement('button');
        anular.textContent = 'Anular';
        anular.className = 'btn-chico btn-anular';
        anular.disabled = factura.estado === 'ANULADA';
        anular.addEventListener('click', () => {
            void ejecutar(async () => {
                const motivo = prompt('Observación de la anulación:');
                if (motivo === null) {
                    return;
                }
                configurarOperacion();
                await api.actualizarEstado(factura.id, 'ANULADA', motivo);
                await cargarListado();
            });
        });
        const eliminar = document.createElement('button');
        eliminar.textContent = 'Eliminar (baja lógica)';
        eliminar.className = 'btn-chico btn-borrar';
        eliminar.disabled = factura.estado === 'ANULADA';
        eliminar.addEventListener('click', () => {
            void ejecutar(async () => {
                if (!confirm(`¿Dar de baja la factura ${factura.numeroFactura}?`)) {
                    return;
                }
                configurarOperacion();
                await api.eliminarFactura(factura.id);
                await cargarListado();
            });
        });
        acciones.append(editar, anular, eliminar);
    }
}
function agregarRenglon() {
    renglones.push({
        listaPrecioArticuloId: 0,
        cantidad: 1,
        porcentajeBonificacion: 0
    });
    dibujarRenglones();
}
function dibujarRenglones() {
    const tbody = elemento('tbodyRenglones');
    tbody.replaceChildren();
    renglones.forEach((renglon, indice) => {
        const fila = tbody.insertRow();
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
            });
            fila.insertCell().appendChild(input);
        };
        const celdaPrecio = fila.insertCell();
        const descripcion = document.createElement('span');
        descripcion.className = 'precio-seleccionado';
        const precio = preciosArticulos.find(p => p.id === renglon.listaPrecioArticuloId);
        descripcion.textContent = precio
            ? `${precio.articuloCodigo} - ${precio.articuloDenominacion} | ${precio.listaPrecioDenominacion} | ${precio.precioVenta.toFixed(2)}`
            : renglon.listaPrecioArticuloId > 0
                ? `Precio #${renglon.listaPrecioArticuloId} no disponible. Seleccioná otro.`
                : 'Sin artículo seleccionado';
        const buscar = document.createElement('button');
        buscar.type = 'button';
        buscar.className = 'btn-secundario btn-chico';
        buscar.dataset.buscarPrecio = '';
        buscar.textContent = 'Buscar artículo y precio';
        buscar.addEventListener('click', () => {
            void abrirExplorer('Seleccionar artículo y lista de precios', ['Código', 'Artículo', 'Lista de precios', 'Precio'], async () => {
                preciosArticulos = await api.obtenerPreciosArticulos();
                return preciosArticulos;
            }, p => [p.articuloCodigo, p.articuloDenominacion, p.listaPrecioDenominacion, p.precioVenta.toFixed(2)], elegido => {
                renglon.listaPrecioArticuloId = elegido.id;
                dibujarRenglones();
            });
        });
        celdaPrecio.append(descripcion, buscar);
        crearInput(renglon.cantidad, 0.01, n => renglon.cantidad = n);
        crearInput(renglon.porcentajeBonificacion ?? 0, 0, n => renglon.porcentajeBonificacion = n, 100);
        const quitar = document.createElement('button');
        quitar.type = 'button';
        quitar.textContent = 'Quitar';
        quitar.className = 'btn-chico btn-borrar';
        quitar.addEventListener('click', () => {
            renglones.splice(indice, 1);
            dibujarRenglones();
        });
        fila.insertCell().appendChild(quitar);
    });
}
function mostrarAlta() {
    facturaEnEdicionId = null;
    elemento('seccionListado').classList.add('oculto');
    elemento('seccionAlta').classList.remove('oculto');
    elemento('tabListado').classList.remove('activo');
    elemento('tabNueva').classList.add('activo');
    elemento('formAltaFactura').reset();
    seleccionarCliente(null);
    elemento('inputNumero').readOnly = false;
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
    const [, clientes, precios] = await Promise.all([
        cargarOpciones(), api.obtenerClientes(), api.obtenerPreciosArticulos()
    ]);
    preciosArticulos = precios;
    mostrarAlta();
    facturaEnEdicionId = id;
    const numero = elemento('inputNumero');
    numero.value = String(factura.numeroFactura);
    numero.readOnly = true;
    const cliente = clientes.find(c => c.id === factura.clienteId);
    seleccionarCliente(cliente ?? null);
    if (factura.clienteId !== null && !cliente) {
        elemento('inputCliente').value = String(factura.clienteId);
        elemento('clienteDescripcion').value =
            `Cliente #${factura.clienteId} no disponible`;
    }
    elemento('inputCondicionIva').value =
        String(factura.condicionIvaId);
    elemento('inputMoneda').value =
        String(factura.tipoMonedaId);
    elemento('inputPuntoVenta').value =
        String(factura.puntoVentaId);
    renglones = factura.detalles.map(detalle => ({ ...detalle }));
    dibujarRenglones();
    elemento('btnGuardarFactura').textContent =
        `Guardar cambios de factura ${factura.numeroFactura}`;
}
async function obtenerDocumentoArca(clienteId) {
    if (clienteId === null) {
        return {
            tipoDocumento: 99,
            numeroDocumento: 0
        };
    }
    const clientes = await api.obtenerClientes();
    const cliente = clientes.find(c => c.id === clienteId);
    if (!cliente) {
        throw new Error('No se encontró el cliente seleccionado.');
    }
    const tipoDocumento = numeroCampo('inputTipoDocumentoArca');
    if (tipoDocumento !== 80 && tipoDocumento !== 86) {
        throw new Error('Seleccioná CUIT o CUIL para el cliente.');
    }
    const documento = cliente.cuitCuil.trim()
        .replace(/[-\s]/g, '');
    if (!/^\d{11}$/.test(documento)) {
        throw new Error('El CUIT/CUIL del cliente debe tener 11 dígitos.');
    }
    return {
        tipoDocumento,
        numeroDocumento: Number(documento)
    };
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
            throw new Error('Seleccioná un artículo y su lista de precios en cada renglón.');
        }
    }
    const textoCliente = valor('inputCliente');
    const clienteId = textoCliente === ''
        ? null
        : numeroCampo('inputCliente');
    if (clienteId !== null
        && (!Number.isInteger(clienteId) || clienteId <= 0)) {
        throw new Error('El ID de cliente debe ser un entero positivo.');
    }
    const dto = {
        clienteId,
        condicionIvaId: numeroCampo('inputCondicionIva'),
        tipoMonedaId: numeroCampo('inputMoneda'),
        puntoVentaId: numeroCampo('inputPuntoVenta'),
        detalles: renglones
    };
    if (facturaEnEdicionId === null) {
        const documento = await obtenerDocumentoArca(clienteId);
        configurarOperacion(numeroCampo('inputNumero'));
        const creada = await api.crearFactura(dto);
        try {
            const autorizacion = await api.autorizarFactura(creada.id, documento.tipoDocumento, documento.numeroDocumento);
            const resultado = autorizacion.resultado;
            if (resultado.resultado === 'A') {
                alert([
                    `Factura ${autorizacion.numeroComprobante} autorizada en homologación.`,
                    `Total: $${creada.importeTotal.toFixed(2)}`,
                    `CAE: ${resultado.cae}`,
                    `Vencimiento: ${resultado.fechaVencimiento}`,
                    ...resultado.observaciones
                ].join('\n'));
            }
            else {
                alert([
                    `La factura se guardó con ID ${creada.id}, pero ARCA la rechazó.`,
                    'Quedó pendiente de autorización.',
                    ...resultado.observaciones
                ].join('\n'));
            }
        }
        catch (error) {
            const mensaje = error instanceof Error
                ? error.message
                : String(error);
            alert(`La factura se guardó con ID ${creada.id}, ` +
                `pero no se pudo confirmar su autorización en ARCA.\n` +
                `${mensaje}\n` +
                'No vuelvas a crearla: primero revisaremos el resultado.');
        }
    }
    else {
        configurarOperacion();
        const actualizada = await api.actualizarFactura(facturaEnEdicionId, dto);
        alert(`Factura ${actualizada.numeroFactura} actualizada. ` +
            `Total: $${actualizada.importeTotal.toFixed(2)}`);
    }
    await mostrarListado();
}
function seleccionarCliente(cliente) {
    elemento('inputCliente').value = cliente ? String(cliente.id) : '';
    elemento('clienteDescripcion').value =
        cliente?.denominacion ?? 'Consumidor final';
}
function normalizarBusqueda(texto) {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}
async function abrirExplorer(titulo, columnas, cargar, celdas, seleccionar) {
    const dialog = elemento('explorerDialog');
    const busqueda = elemento('explorerBusqueda');
    const encabezado = elemento('explorerColumnas');
    const tbody = elemento('explorerFilas');
    const estado = elemento('explorerEstado');
    const solicitud = ++solicitudExplorer;
    elemento('explorerTitulo').textContent = titulo;
    busqueda.value = '';
    busqueda.disabled = true;
    encabezado.replaceChildren();
    tbody.replaceChildren();
    estado.textContent = 'Cargando...';
    filtrarExplorer = () => { };
    for (const nombre of [...columnas, 'Acción']) {
        const th = document.createElement('th');
        th.scope = 'col';
        th.textContent = nombre;
        encabezado.appendChild(th);
    }
    dialog.showModal();
    try {
        const registros = await cargar();
        if (solicitud !== solicitudExplorer || !dialog.open)
            return;
        filtrarExplorer = () => {
            const filtro = normalizarBusqueda(busqueda.value);
            const encontrados = registros.filter(registro => normalizarBusqueda(celdas(registro).join(' ')).includes(filtro));
            tbody.replaceChildren();
            estado.textContent = `${encontrados.length} resultado(s)`;
            if (encontrados.length === 0) {
                const celda = tbody.insertRow().insertCell();
                celda.colSpan = columnas.length + 1;
                celda.textContent = 'No hay resultados para la búsqueda.';
            }
            for (const registro of encontrados) {
                const fila = tbody.insertRow();
                for (const texto of celdas(registro))
                    fila.insertCell().textContent = texto;
                const boton = document.createElement('button');
                boton.type = 'button';
                boton.className = 'btn-primario btn-chico';
                boton.textContent = 'Seleccionar';
                boton.addEventListener('click', () => {
                    seleccionar(registro);
                    dialog.close();
                });
                fila.insertCell().appendChild(boton);
            }
        };
        busqueda.disabled = false;
        filtrarExplorer();
        busqueda.focus();
    }
    catch (error) {
        if (solicitud !== solicitudExplorer || !dialog.open)
            return;
        estado.textContent = 'No se pudieron cargar los datos. Cerrá la ventana y volvé a intentar.';
        console.error(error);
    }
}
document.addEventListener('DOMContentLoaded', () => {
    elemento('btnBuscarCliente').addEventListener('click', () => {
        void abrirExplorer('Seleccionar cliente', ['CUIT/CUIL', 'Denominación'], () => api.obtenerClientes(), cliente => [cliente.cuitCuil, cliente.denominacion], seleccionarCliente);
    });
    elemento('btnQuitarCliente').addEventListener('click', () => seleccionarCliente(null));
    elemento('explorerBusqueda').addEventListener('input', () => filtrarExplorer());
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
        filtrarExplorer = () => { };
    });
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
    elemento('btnAplicarFiltros').addEventListener('click', () => {
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
