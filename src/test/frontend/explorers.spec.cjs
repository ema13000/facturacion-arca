const { test, expect } = require('playwright/test');
test.setTimeout(10000);
test.use({ channel: 'chrome' });
const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');

let server;
let origin;
const root = path.resolve(__dirname, '../../main/resources/static');
const clientes = [
    { id: 7, cuit: '20123456789', denominacion: 'Librería Centro', nombre: 'Juan', apellido: 'Pérez',
        condicionIvaId: 3, condicionIva: 'Responsable Monotributo', condicionIvaCodigo: 6 },
    { id: 9, cuit: '20987654321', denominacion: 'Escuela Norte', nombre: 'María', apellido: 'González',
        condicionIvaId: null, condicionIva: null, condicionIvaCodigo: null }
];
const condiciones = [
    { id: 1, descripcion: 'Consumidor Final' },
    { id: 2, descripcion: 'IVA Responsable Inscripto' },
    { id: 3, descripcion: 'Responsable Monotributo' }
];
const puntosVenta = [
    { id: 1, descripcion: '1 - Central (Monotributo)', responsableInscripto: false },
    { id: 2, descripcion: '2 - Sucursal RI (Resp. Inscripto)', responsableInscripto: true }
];
const listas = [
    { id: 1, descripcion: 'Minorista' },
    { id: 2, descripcion: 'Mayorista' }
];
const TAMANIO_PAGINA = 20;
const precios = [
    { id: 25, articuloCodigo: 'A8', articuloDenominacion: 'Cuaderno', listaPrecioDenominacion: 'Minorista', precioVenta: 100, listaPrecioId: 1 },
    { id: 26, articuloCodigo: 'A8', articuloDenominacion: 'Cuaderno', listaPrecioDenominacion: 'Mayorista', precioVenta: 80, listaPrecioId: 2 },
    { id: 27, articuloCodigo: 'B1', articuloDenominacion: 'Lápiz', listaPrecioDenominacion: 'Minorista', precioVenta: 10, listaPrecioId: 1 },
    // Relleno para probar la paginación: 42 resmas más (45 precios en total)
    ...Array.from({ length: 42 }, (_, i) => ({
        id: 100 + i, articuloCodigo: `R-${String(i + 1).padStart(2, '0')}`, articuloDenominacion: `Resma ${i + 1}`,
        listaPrecioDenominacion: i % 2 === 0 ? 'Minorista' : 'Mayorista', precioVenta: 500, listaPrecioId: i % 2 === 0 ? 1 : 2
    }))
];
const hoyISO = (dias = 0) => {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + dias);
    return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
};
const reporte = { id: 1, numeroFactura: 1, fechaEmision: hoyISO(), clienteDenominacion: 'Librería Centro', condicionIva: 'IVA', puntoVentaDescripcion: 'Centro', importeTotal: 100, estado: 'PENDIENTE' };
const coincide = (texto, valores) => valores.join(' ').toLowerCase().includes((texto ?? '').toLowerCase());

test.beforeAll(async () => {
    server = http.createServer((req, res) => {
        const ruta = req.url.split('?')[0];
        const file = path.resolve(root, '.' + (ruta === '/' ? '/index.html' : ruta));
        if (!file.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
        fs.readFile(file, (error, data) => {
            if (error) { res.writeHead(404).end(); return; }
            res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html');
            res.end(data);
        });
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    origin = `http://127.0.0.1:${server.address().port}`;
});

test.afterAll(async () => { await new Promise(resolve => server.close(resolve)); });

test.beforeEach(async ({ page }) => {
    page.pedidos = [];
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        page.pedidos.push(url);
        const texto = url.searchParams.get('texto');
        let body = [{ id: 1, descripcion: 'Opción' }];
        if (url.pathname.endsWith('/clientes')) body = clientes.filter(c => coincide(texto, [c.cuit, c.denominacion]));
        else if (/\/clientes\/\d+$/.test(url.pathname)) body = clientes.find(c => url.pathname.endsWith('/' + c.id));
        else if (url.pathname.endsWith('/listas-precio')) body = listas;
        else if (url.pathname.endsWith('/condiciones-iva')) body = condiciones;
        else if (url.pathname.endsWith('/puntos-venta')) body = puntosVenta;
        else if (url.pathname.endsWith('/precios-articulos')) {
            const lista = Number(url.searchParams.get('listaPrecioId') || 0);
            const pagina = Number(url.searchParams.get('pagina') || 0);
            const filtrados = precios.filter(p => (lista === 0 || p.listaPrecioId === lista)
                && coincide(texto, [p.articuloCodigo, p.articuloDenominacion]));
            body = {
                contenido: filtrados.slice(pagina * TAMANIO_PAGINA, (pagina + 1) * TAMANIO_PAGINA),
                pagina, totalPaginas: Math.ceil(filtrados.length / TAMANIO_PAGINA), totalElementos: filtrados.length
            };
        }
        else if (url.pathname.endsWith('/precios-articulos/por-codigo')) {
            const lista = Number(url.searchParams.get('listaPrecioId'));
            const codigo = (url.searchParams.get('codigo') ?? '').trim().toUpperCase();
            const encontrado = precios.find(p => p.listaPrecioId === lista && p.articuloCodigo === codigo);
            await route.fulfill(encontrado ? { json: encontrado } : { status: 404, json: { mensaje: `No existe el artículo ${codigo} en la lista de precios elegida` } });
            return;
        }
        else if (url.pathname.endsWith('/autorizar-arca')) body = {
            numeroComprobante: 1, resultado: { resultado: 'A', cae: '7512', fechaVencimiento: '2026-11-30', observaciones: [] }
        };
        else if (/\/precios-articulos\/\d+$/.test(url.pathname)) body = precios.find(p => url.pathname.endsWith('/' + p.id));
        else if (url.pathname.endsWith('/edicion')) body = {
            numeroFactura: 1, clienteId: 7, condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1,
            detalles: [{ listaPrecioArticuloId: 26, cantidad: 2, porcentajeBonificacion: 0 }],
            fechaEmision: hoyISO(-2), observaciones: 'Pago contado'
        };
        else if (url.pathname === '/api/facturas') body = route.request().method() === 'GET' ? [reporte] : reporte;
        await route.fulfill({ json: body });
    });
    await page.goto(origin);
});

async function buscar(page, texto) {
    await page.locator('#explorerBusqueda').fill(texto);
    await page.locator('#btnExplorerBuscar').click();
}

// Abre "Buscar artículo", filtra (lista opcional) y agrega el primer resultado como renglón nuevo
async function agregarArticulo(page, texto, lista = '') {
    await page.locator('#btnBuscarArticulo').click();
    await page.locator('#articuloLista').selectOption(lista);
    await page.locator('#articuloBusqueda').fill(texto);
    await page.locator('#btnArticuloBuscar').click();
    await page.locator('#articuloFilas').getByRole('button', { name: 'Agregar' }).first().click();
    await expect(page.locator('#articuloDialog')).not.toBeVisible();
}

test('no muestra usuario ni IVA en la parte superior', async ({ page }) => {
    await expect(page.locator('#inputUsuario')).toHaveCount(0);
    await expect(page.locator('#inputPorcentajeIva')).toHaveCount(0);
});

test('busca clientes en el servidor con el botón y permite consumidor final', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await expect(page.locator('#btnQuitarCliente')).toBeHidden();
    await page.locator('#btnBuscarCliente').click();
    await expect(page.locator('#explorerColumnas')).toContainText('Nombre y apellido');
    await expect(page.locator('#explorerFilas button')).toHaveCount(0);
    expect(page.pedidos.some(u => u.pathname.endsWith('/clientes'))).toBe(false);
    await buscar(page, 'escuela');
    await expect(page.locator('#explorerFilas tr')).toHaveCount(1);
    await expect(page.locator('#explorerFilas')).toContainText('Escuela Norte');
    await expect(page.locator('#explorerFilas')).toContainText('María González');
    expect(page.pedidos.find(u => u.pathname.endsWith('/clientes')).searchParams.get('texto')).toBe('escuela');
    await page.locator('#explorerBusqueda').fill('20123456789');
    await page.locator('#explorerBusqueda').press('Enter');
    await expect(page.locator('#explorerFilas')).toContainText('Librería Centro');
    await page.locator('#explorerFilas button').click();
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Librería Centro');
    await expect(page.locator('#inputCliente')).toHaveValue('7');
    await expect(page.locator('#clienteDetalle')).toHaveText('Juan Pérez · CUIT 20-12345678-9');
    await expect(page.locator('#btnQuitarCliente')).toBeVisible();
    await expect(page.locator('#btnQuitarCliente')).toContainText('Quitar cliente');
    await page.locator('#btnQuitarCliente').click();
    await expect(page.locator('#inputCliente')).toHaveValue('');
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Consumidor final');
    await expect(page.locator('#clienteDetalle')).toHaveText('Sin identificar');
    await expect(page.locator('#btnQuitarCliente')).toBeHidden();
});

test('buscar artículo abre un buscador que pagina de a 20 y filtra por lista, código o nombre', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#btnBuscarArticulo').click();
    const dialogo = page.locator('#articuloDialog');
    await expect(dialogo).toBeVisible();
    // Al abrir trae la primera página sin filtros
    await expect(page.locator('#articuloFilas tr')).toHaveCount(20);
    await expect(page.locator('#articuloPagina')).toHaveText('Página 1 de 3 · 45 artículos');
    await expect(page.locator('#btnArticuloAnterior')).toBeDisabled();
    await page.locator('#btnArticuloSiguiente').click();
    await expect(page.locator('#articuloPagina')).toHaveText('Página 2 de 3 · 45 artículos');
    expect(page.pedidos.filter(u => u.pathname.endsWith('/precios-articulos')).at(-1).searchParams.get('pagina')).toBe('1');
    await page.locator('#btnArticuloSiguiente').click();
    await expect(page.locator('#articuloFilas tr')).toHaveCount(5);
    await expect(page.locator('#btnArticuloSiguiente')).toBeDisabled();
    // Filtro por lista y por nombre
    await page.locator('#articuloLista').selectOption('2');
    await page.locator('#articuloBusqueda').fill('cuaderno');
    await page.locator('#btnArticuloBuscar').click();
    await expect(page.locator('#articuloFilas tr')).toHaveCount(1);
    await expect(page.locator('#articuloFilas')).toContainText('Mayorista');
    const pedido = page.pedidos.filter(u => u.pathname.endsWith('/precios-articulos')).at(-1);
    expect(pedido.searchParams.get('listaPrecioId')).toBe('2');
    expect(pedido.searchParams.get('texto')).toBe('cuaderno');
    expect(pedido.searchParams.get('pagina')).toBe('0');
    // "Ver todos" quita los filtros
    await page.locator('#btnArticuloTodos').click();
    await expect(page.locator('#articuloLista')).toHaveValue('');
    await expect(page.locator('#articuloBusqueda')).toHaveValue('');
    await expect(page.locator('#articuloPagina')).toHaveText('Página 1 de 3 · 45 artículos');
});

test('elige precios, calcula el total, envía el ID, la fecha y las observaciones', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'cuaderno', '1');
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
    await agregarArticulo(page, 'lápiz');
    await expect(page.locator('#tbodyRenglones tr')).toHaveCount(2);
    await expect(page.locator('#totalFactura')).toHaveText('$110.00');
    await page.locator('#tbodyRenglones tr').first().getByRole('button', { name: 'Quitar' }).click();
    await expect(page.locator('#tbodyRenglones tr')).toHaveCount(1);
    await expect(page.locator('#tbodyRenglones')).toContainText('Lápiz');
    await expect(page.locator('#inputNumero')).toHaveCount(0);
    await expect(page.locator('#seccionAlta')).not.toContainText('Número de factura');
    for (const id of ['inputPuntoVenta', 'inputMoneda']) await page.locator('#' + id).selectOption('1');
    await page.locator('#inputFechaEmision').fill(hoyISO(-1));
    await page.locator('#inputObservaciones').fill('  Entregar a la tarde ');
    page.on('dialog', dialog => dialog.accept());
    const request = page.waitForRequest(req => req.method() === 'POST' && req.url().includes('/api/facturas'));
    await page.locator('#btnGuardarFactura').click();
    const enviada = await request;
    const params = new URL(enviada.url()).searchParams;
    expect(params.has('numero')).toBe(false);
    expect(params.get('porcentajeIva')).toBe('0');
    expect(params.get('usuarioId')).toBe('1');
    const dto = enviada.postDataJSON();
    expect(dto.detalles[0].listaPrecioArticuloId).toBe(27);
    expect(dto.condicionIvaId).toBe(1);
    expect(dto.fechaEmision).toBe(hoyISO(-1));
    expect(dto.observaciones).toBe('Entregar a la tarde');
    await expect(page.locator('#seccionListado')).toBeVisible();
});

test('elegir el mismo artículo otra vez suma un renglón nuevo y se pueden mezclar listas', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'cuaderno', '1');
    await agregarArticulo(page, 'cuaderno', '1');
    await agregarArticulo(page, 'cuaderno', '2');
    const filas = page.locator('#tbodyRenglones tr');
    await expect(filas).toHaveCount(3);
    await expect(filas.nth(0)).toContainText('Minorista');
    await expect(filas.nth(2)).toContainText('Mayorista');
    await expect(page.locator('#totalFactura')).toHaveText('$280.00');
});

test('edición trae cliente, precio, fecha y observaciones por ID sin cargar todo el catálogo', async ({ page }) => {
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Librería Centro');
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
    await expect(page.locator('#tbodyRenglones')).toContainText('Mayorista');
    await expect(page.locator('#inputFechaEmision')).toHaveValue(hoyISO(-2));
    await expect(page.locator('#inputObservaciones')).toHaveValue('Pago contado');
    expect(page.pedidos.some(u => u.pathname.endsWith('/clientes') || u.pathname.endsWith('/precios-articulos'))).toBe(false);
    await page.locator('#btnBuscarArticulo').click();
    await page.locator('#articuloBusqueda').fill('inexistente');
    await page.locator('#btnArticuloBuscar').click();
    await expect(page.locator('#articuloFilas')).toContainText('No hay artículos');
    await page.keyboard.press('Escape');
    await expect(page.locator('#articuloDialog')).not.toBeVisible();
    await expect(page.locator('#tbodyRenglones tr')).toHaveCount(1);
});

test('un error del explorer se informa y permite reintentar', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.route('**/api/catalogos/clientes?*', route => route.fulfill({ status: 500, body: 'Error de prueba' }));
    await page.locator('#btnBuscarCliente').click();
    await buscar(page, '');
    await expect(page.locator('#explorerEstado')).toContainText('No se pudieron cargar');
    await page.unroute('**/api/catalogos/clientes?*');
    await buscar(page, '');
    await expect(page.locator('#explorerFilas button')).toHaveCount(2);
});

test('no guarda una factura sin artículos', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await expect(page.locator('#tbodyRenglones')).toContainText('Todavía no agregaste artículos');
    for (const id of ['inputPuntoVenta', 'inputMoneda']) await page.locator('#' + id).selectOption('1');
    await page.locator('#btnGuardarFactura').click();
    const aviso = page.getByRole('dialog', { name: 'No se pudo guardar' });
    await expect(aviso).toContainText('Agregá al menos un artículo');
    await aviso.getByRole('button', { name: 'Entendido' }).click();
    await expect(page.locator('#seccionAlta')).toBeVisible();
});

test('el listado pagina de a 10, 15 o 20 comprobantes', async ({ page }) => {
    const muchas = Array.from({ length: 23 }, (_, i) => ({ ...reporte, id: i + 1, numeroFactura: i + 1 }));
    await page.route('**/api/facturas', route => route.fulfill({ json: muchas }));
    await page.locator('#btnAplicarFiltros').click();
    const filas = page.locator('#tbodyFacturas tr');
    await expect(filas).toHaveCount(10);
    await expect(page.locator('#infoPagina')).toHaveText('Página 1 de 3');
    await expect(page.locator('#btnPaginaAnterior')).toBeDisabled();
    await page.locator('#btnPaginaSiguiente').click();
    await page.locator('#btnPaginaSiguiente').click();
    await expect(filas).toHaveCount(3);
    await expect(page.locator('#infoPagina')).toHaveText('Página 3 de 3');
    await expect(page.locator('#btnPaginaSiguiente')).toBeDisabled();
    await page.locator('#selTamanioPagina').selectOption('15');
    await expect(filas).toHaveCount(15);
    await expect(page.locator('#infoPagina')).toHaveText('Página 1 de 2');
    await page.locator('#selTamanioPagina').selectOption('20');
    await page.locator('#btnPaginaSiguiente').click();
    await expect(filas).toHaveCount(3);
    await expect(page.locator('#contadorFacturas')).toHaveText('23 comprobante(s)');
});

test('las acciones dependen del estado de la factura', async ({ page }) => {
    const lista = [
        { ...reporte, id: 3, numeroFactura: 3, estado: 'AUTORIZADA' },
        { ...reporte, id: 2, numeroFactura: 2, estado: 'PENDIENTE' },
        { ...reporte, id: 1, numeroFactura: 1, estado: 'ANULADA' }
    ];
    await page.route('**/api/facturas', route => route.fulfill({ json: lista }));
    await page.locator('#btnAplicarFiltros').click();
    const [autorizada, pendiente, anulada] = [0, 1, 2].map(i => page.locator('#tbodyFacturas tr').nth(i));
    await expect(autorizada.getByRole('button')).toHaveText(['Anular', 'Ver factura']);
    await expect(pendiente.getByRole('button')).toHaveText(['Editar', 'Eliminar', 'Autorizar en ARCA']);
    await expect(anulada.getByRole('button')).toHaveCount(0);
});

test('las acciones son botones con ícono y descripción al pasar el mouse', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 2, estado: 'AUTORIZADA' }, { ...reporte, id: 1, estado: 'PENDIENTE' }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    for (const nombre of ['Editar', 'Eliminar', 'Anular', 'Ver factura']) {
        const boton = page.getByRole('button', { name: nombre, exact: true });
        await expect(boton).toHaveAttribute('title', new RegExp(nombre));
        await expect(boton.locator('svg')).toHaveCount(1);
    }
});

test('las tarjetas de resumen muestran totales por estado', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 4, estado: 'AUTORIZADA', importeTotal: 1000 },
        { ...reporte, id: 3, estado: 'PENDIENTE', importeTotal: 200 },
        { ...reporte, id: 2, estado: 'PENDIENTE', importeTotal: 50 },
        { ...reporte, id: 1, estado: 'ANULADA', importeTotal: 999 }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    await expect(page.locator('#kpiTotal')).toHaveText('$1,250.00');
    await expect(page.locator('#kpiPendientes')).toHaveText('2');
    await expect(page.locator('#kpiAutorizadas')).toHaveText('1');
    await expect(page.locator('#kpiAnuladas')).toHaveText('1');
});

test('imprimir una autorizada muestra CAE y vencimiento', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [{ ...reporte, estado: 'AUTORIZADA' }] }));
    await page.route('**/api/facturas/1/impresion', route => route.fulfill({ json: {
        id: 1, numeroFactura: 15, puntoVentaNumero: 2, puntoVentaDescripcion: 'Central', fechaEmision: '2026-11-18',
        clienteDenominacion: 'Librería Centro', clienteCuit: '20123456789', condicionIva: 'Consumidor Final',
        importeTotal: 250, cae: '75123456789012', caeFechaVencimiento: '2026-11-28',
        detalles: [{ descripcion: 'Cuaderno azul', cantidad: 2, precioUnitario: 125, porcentajeBonificacion: 0, importeSubtotal: 250 }]
    } }));
    await page.locator('#btnAplicarFiltros').click();
    await page.addInitScript(() => { window.print = () => {}; });
    const popup = page.waitForEvent('popup');
    await page.getByRole('button', { name: 'Ver factura' }).click();
    const ventana = await popup;
    await expect(ventana.locator('body')).toContainText('75123456789012');
    await expect(ventana.locator('body')).toContainText('28/11/2026');
    await expect(ventana.locator('body')).toContainText('0002-00000015');
    await expect(ventana.locator('body')).toContainText('Cuaderno azul');
    await expect(ventana.locator('body')).toContainText('$250.00');
    const imprimir = ventana.getByRole('button', { name: 'Imprimir factura' });
    await expect(imprimir).toHaveClass('btn-primario');
    await ventana.evaluate(() => { window.print = () => { window.sePidioImpresion = true; }; });
    await imprimir.click();
    expect(await ventana.evaluate(() => window.sePidioImpresion)).toBe(true);
    await ventana.emulateMedia({ media: 'print' });
    await expect(imprimir).toBeHidden();
});

test('mantiene los reportes a la derecha y quita los textos secundarios pedidos', async ({ page }) => {
    await expect(page.getByText('Facturas A, B y C · ARCA')).toHaveCount(0);
    const barra = page.locator('.acciones-comprobantes');
    const ultimoReporte = page.getByRole('button', { name: 'Reporte Excel' });
    await expect(ultimoReporte).toBeVisible();
    const [grupo, boton] = await Promise.all([barra.boundingBox(), ultimoReporte.boundingBox()]);
    expect(Math.abs(grupo.x + grupo.width - boton.x - boton.width)).toBeLessThan(1);

    await page.locator('#tabNueva').click();
    await expect(page.locator('#inputFechaEmision').locator('..').locator('small')).toHaveCount(0);
});

test('si el backend rechaza la vista de la factura se muestra el motivo', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [{ ...reporte, estado: 'AUTORIZADA' }] }));
    await page.route('**/api/facturas/1/impresion', route => route.fulfill({
        status: 409, json: { mensaje: 'Solo se pueden imprimir facturas autorizadas en ARCA' }
    }));
    await page.locator('#btnAplicarFiltros').click();
    const dialogo = page.waitForEvent('dialog');
    await page.getByRole('button', { name: 'Ver factura' }).click();
    const alerta = await dialogo;
    expect(alerta.message()).toContain('Solo se pueden imprimir facturas autorizadas');
    await alerta.accept();
});

test('después de guardar, la factura nueva queda resaltada arriba del listado', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: route.request().method() === 'GET'
        ? [{ ...reporte, id: 2, numeroFactura: 2 }, reporte]
        : { ...reporte, id: 2, numeroFactura: 2 } }));
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'cuaderno', '1');
    for (const id of ['inputPuntoVenta', 'inputMoneda']) await page.locator('#' + id).selectOption('1');
    page.on('dialog', dialog => dialog.accept());
    await page.locator('#btnGuardarFactura').click();
    await expect(page.locator('#tbodyFacturas tr').first()).toHaveClass(/fila-nueva/);
    await expect(page.locator('#tbodyFacturas tr').nth(1)).not.toHaveClass(/fila-nueva/);
});

test('muestra subtotal, bonificación y total mientras se cargan artículos', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'cuaderno', '1');
    const fila = page.locator('#tbodyRenglones tr').first();
    await fila.locator('input[type=number]').nth(0).fill('3');
    await fila.locator('input[type=number]').nth(1).fill('10');
    await expect(fila.locator('[data-subtotal]')).toHaveText('$270.00');
    await expect(page.locator('#subtotalFactura')).toHaveText('$300.00');
    await expect(page.locator('#bonificacionFactura')).toHaveText('-$30.00');
    await expect(page.locator('#totalFactura')).toHaveText('$270.00');
    await expect(page.locator('#cantidadArticulos')).toHaveText('1');
    await expect(fila.locator('[data-subtotal]')).toBeInViewport();
});

test('tocando la tarjeta del cliente se abre el buscador, sin botón aparte', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await expect(page.getByRole('button', { name: 'Buscar cliente', exact: true })).toHaveCount(0);
    const tarjeta = page.locator('.tarjeta-cliente');
    await expect(tarjeta).toContainText('Consumidor final');
    await tarjeta.click();
    await expect(page.locator('#explorerDialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await tarjeta.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#explorerDialog')).toBeVisible();
    await buscar(page, 'escuela');
    await page.locator('#explorerFilas button').click();
    await expect(tarjeta).toContainText('Escuela Norte');
    await expect(tarjeta).toContainText('María González');
});

test('el buscador encuentra un artículo por su código', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'b1');
    await expect(page.locator('#tbodyRenglones')).toContainText('Lápiz');
    await expect(page.locator('#tbodyRenglones tr').first().locator('td').first()).toHaveText('B1');
    await expect(page.locator('#totalFactura')).toHaveText('$10.00');
    const pedido = page.pedidos.filter(u => u.pathname.endsWith('/precios-articulos')).at(-1);
    expect(pedido.searchParams.get('texto')).toBe('b1');
    expect(pedido.searchParams.has('listaPrecioId')).toBe(false);
});

test('los artículos se agregan solo con Buscar artículo: sin renglón vacío, lupa ni código editable', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await expect(page.locator('#btnSumarRenglon')).toHaveCount(0);
    await expect(page.locator('#inputListaPrecio')).toHaveCount(0);
    await expect(page.locator('#btnBuscarArticulo')).toContainText('Buscar artículo');
    await agregarArticulo(page, 'cuaderno', '1');
    await expect(page.locator('#tbodyRenglones [data-codigo]')).toHaveCount(0);
    await expect(page.locator('#tbodyRenglones [data-buscar-precio]')).toHaveCount(0);
});

test('guardar deja la factura pendiente y no la manda a ARCA', async ({ page }) => {
    await expect(page.locator('#inputTipoDocumentoArca')).toHaveCount(0);
    await page.locator('#tabNueva').click();
    await agregarArticulo(page, 'A8', '1');
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
    for (const id of ['inputPuntoVenta', 'inputMoneda']) await page.locator('#' + id).selectOption('1');
    await page.locator('#btnGuardarFactura').click();
    await expect(page.locator('#seccionListado')).toBeVisible();
    const aviso = page.getByRole('dialog', { name: 'Factura guardada' });
    await expect(aviso).toContainText('Pendiente de enviar a ARCA');
    await aviso.getByRole('button', { name: 'Entendido' }).click();
    await expect(aviso).toBeHidden();
    expect(page.pedidos.some(u => u.pathname.endsWith('/autorizar-arca'))).toBe(false);
});

test('filtra monto, CUIT, IVA y envío a ARCA y exporta ese listado', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 11, clienteCuit: '20123456789', condicionIvaId: 3, importeTotal: 500, informadaArca: true },
        { ...reporte, id: 12, clienteCuit: '20123456789', condicionIvaId: 3, importeTotal: 499, informadaArca: true },
        { ...reporte, id: 13, clienteCuit: '20123456789', condicionIvaId: 3, importeTotal: 700, informadaArca: false },
        { ...reporte, id: 14, clienteCuit: '20123456789', condicionIvaId: 2, importeTotal: 800, informadaArca: true },
        { ...reporte, id: 15, clienteCuit: '20987654321', condicionIvaId: 3, importeTotal: 900, informadaArca: true }
    ] }));
    await page.locator('#txtFiltroCliente').fill('20-12345678-9');
    await page.locator('#txtMontoMinimo').fill('500');
    await page.locator('#selFiltroIva').selectOption({ label: 'Responsable Monotributo' });
    await page.locator('#selFiltroArca').selectOption('informadas');
    await page.locator('#btnAplicarFiltros').click();
    await expect(page.locator('#contadorFacturas')).toHaveText('1 comprobante(s)');

    for (const [formato, boton] of [['pdf', 'Reporte PDF'], ['xlsx', 'Reporte Excel']]) {
        const url = '**/api/facturas/reportes/' + formato;
        await page.route(url, route => route.fulfill({ body: 'reporte' }));
        const pedido = page.waitForRequest(url);
        const descarga = page.waitForEvent('download');
        await page.getByRole('button', { name: boton }).click();
        expect((await pedido).postDataJSON()).toEqual([11]);
        expect((await descarga).suggestedFilename()).toBe('reporte-facturas.' + formato);
    }

    await page.locator('#selFiltroArca').selectOption('no-informadas');
    await page.locator('#btnAplicarFiltros').click();
    await expect(page.locator('#tbodyFacturas')).toContainText('700');
});

test('bloquea el envío a ARCA de una factura pendiente anterior a hoy', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 22, clienteCuit: '20123456789', fechaEmision: hoyISO(-1) }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    const boton = page.getByRole('button', { name: 'Autorizar en ARCA' });
    await expect(boton).toBeDisabled();
    await expect(boton).toHaveAttribute('title', /anterior a hoy/i);
});

test('autorizar en ARCA muestra el CUIT del cliente y envía solo el ID de factura', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 5, numeroFactura: 5, clienteCuit: '20123456789' }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    await page.getByRole('button', { name: 'Autorizar en ARCA' }).click();
    const dialogo = page.locator('#autorizarDialog');
    await expect(dialogo).toBeVisible();
    await expect(dialogo).toContainText('Librería Centro');
    await expect(dialogo).toContainText('20-12345678-9');
    await expect(dialogo.locator('select')).toHaveCount(0);
    const pedido = page.waitForRequest(r => r.url().includes('/autorizar-arca'));
    await page.locator('#btnConfirmarAutorizar').click();
    const aviso = page.getByRole('dialog', { name: 'Factura autorizada' });
    await expect(aviso).toBeVisible();
    await expect(aviso).not.toContainText('CAE');
    expect((await pedido).postData()).toBeNull();
});

test('autoriza una factura C de consumidor final sin identificar', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 6, clienteDenominacion: 'Consumidor Final', clienteCuit: null }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    await page.getByRole('button', { name: 'Autorizar en ARCA' }).click();
    await expect(page.locator('#autorizarCuit')).toHaveText('Sin identificar');
    const pedido = page.waitForRequest('**/api/facturas/6/autorizar-arca');
    await page.locator('#btnConfirmarAutorizar').click();
    await expect(page.getByRole('dialog', { name: 'Factura autorizada' })).toBeVisible();
    expect((await pedido).method()).toBe('POST');
});

test('muestra el rechazo por CUIT en lenguaje simple', async ({ page }) => {
    await page.route('**/api/facturas/1/autorizar-arca', route => route.fulfill({ json: {
        resultado: { resultado: 'R', observaciones: ['10015Comprobante C, DocNro 20334447775 no registrado'] }
    } }));
    await page.getByRole('button', { name: 'Autorizar en ARCA' }).click();
    await page.locator('#btnConfirmarAutorizar').click();
    const aviso = page.getByRole('dialog', { name: 'ARCA rechazó la factura' });
    await expect(aviso).toContainText('Revisá el CUIT del cliente. La factura sigue pendiente.');
    await expect(aviso).not.toContainText('10015');
    await expect(aviso).not.toContainText('20334447775');
});

test('una búsqueda sin coincidencias se informa', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#btnBuscarCliente').click();
    await buscar(page, 'zzz');
    await expect(page.locator('#explorerFilas')).toContainText('No hay resultados');
    await expect(page.locator('#explorerFilas button')).toHaveCount(0);
    await page.locator('#btnCerrarExplorer').click();
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Consumidor final');
});

async function elegirCliente(page, texto) {
    await page.locator('#btnBuscarCliente').click();
    await buscar(page, texto);
    await page.locator('#explorerFilas button').first().click();
}

test('sin cliente la condición de IVA queda en Consumidor Final y no se puede cambiar', async ({ page }) => {
    await page.locator('#tabNueva').click();
    const condicion = page.locator('#inputCondicionIva');
    await expect(condicion).toHaveValue('1');
    await expect(condicion).toBeDisabled();
    await expect(condicion).toHaveCSS('appearance', 'none');
});

test('al elegir un cliente se pone su categoría fija; al quitarlo vuelve a Consumidor Final', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await elegirCliente(page, 'librería');
    const condicion = page.locator('#inputCondicionIva');
    await expect(condicion).toHaveValue('3');
    await expect(condicion).toBeDisabled();
    await page.locator('#btnQuitarCliente').click();
    await expect(condicion).toHaveValue('1');
    await expect(condicion).toBeDisabled();
});

test('un cliente sin categoría cargada deja elegir la condición a mano', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await elegirCliente(page, 'escuela');
    await expect(page.locator('#inputCondicionIva')).toBeEnabled();
});

test('la letra de la factura depende del punto de venta y del cliente', async ({ page }) => {
    await page.locator('#tabNueva').click();
    const letra = page.locator('#letraComprobante');
    await page.locator('#inputPuntoVenta').selectOption('1');
    await expect(letra).toHaveText('Factura C');
    await page.locator('#inputPuntoVenta').selectOption('2');
    await expect(letra).toHaveText('Factura B');
    await elegirCliente(page, 'librería');
    await expect(letra).toHaveText('Factura A');
});

test('en Factura A se ve el IVA 21% discriminado y en C no', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#inputPuntoVenta').selectOption('2');
    await elegirCliente(page, 'librería');
    await agregarArticulo(page, 'cuaderno', '1');
    await page.locator('#tbodyRenglones input[type=number]').first().fill('121');
    await expect(page.locator('#filaIvaFactura')).toBeVisible();
    await expect(page.locator('#etiquetaIvaFactura')).toHaveText('IVA 21% (discriminado)');
    await expect(page.locator('#ivaFactura')).toHaveText('$2,100.00');
    await expect(page.locator('#totalFactura')).toHaveText('$12,100.00');
    await page.locator('#inputPuntoVenta').selectOption('1');
    await expect(page.locator('#filaIvaFactura')).toBeHidden();
});

test('el listado muestra la letra de cada factura', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [
        { ...reporte, id: 2, numeroFactura: 2, tipoComprobante: 'A' }, { ...reporte, tipoComprobante: 'C' }
    ] }));
    await page.locator('#btnAplicarFiltros').click();
    await expect(page.locator('#tbodyFacturas tr').nth(0).locator('td').first()).toHaveText('A 2');
    await expect(page.locator('#tbodyFacturas tr').nth(1).locator('td').first()).toHaveText('C 1');
});

test('autorizar una Factura A avisa que falta el certificado de Responsable Inscripto', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [{ ...reporte, tipoComprobante: 'A' }] }));
    await page.locator('#btnAplicarFiltros').click();
    await page.getByRole('button', { name: 'Autorizar en ARCA' }).click();
    await expect(page.getByRole('dialog', { name: 'No se pudo enviar a ARCA' })).toContainText('Responsable Inscripto');
    await expect(page.locator('#autorizarDialog')).not.toBeVisible();
    expect(page.pedidos.some(u => u.pathname.endsWith('/autorizar-arca'))).toBe(false);
});

test('imprimir una Factura A muestra la letra y el IVA', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [{ ...reporte, estado: 'AUTORIZADA', tipoComprobante: 'A' }] }));
    await page.route('**/api/facturas/1/impresion', route => route.fulfill({ json: {
        id: 1, numeroFactura: 3, puntoVentaNumero: 2, puntoVentaDescripcion: 'Sucursal RI', fechaEmision: '2026-11-18',
        clienteDenominacion: 'Sofía Indovino', clienteCuit: '27381112220', condicionIva: 'IVA Responsable Inscripto',
        importeTotal: 1210, cae: '75123456789012', caeFechaVencimiento: '2026-11-28', tipoComprobante: 'A',
        importeNeto: 1000, importeIva: 210,
        detalles: [{ descripcion: 'Resma', cantidad: 1, precioUnitario: 1210, porcentajeBonificacion: 0, importeSubtotal: 1210 }]
    } }));
    await page.locator('#btnAplicarFiltros').click();
    await page.addInitScript(() => { window.print = () => {}; });
    const popup = page.waitForEvent('popup');
    await page.getByRole('button', { name: 'Ver factura' }).click();
    const ventana = await popup;
    await expect(ventana.locator('.letra')).toHaveText('A');
    await expect(ventana.locator('body')).toContainText('Neto gravado: $1,000.00');
    await expect(ventana.locator('body')).toContainText('IVA 21%: $210.00');
});

test('el cliente está dentro de Datos del comprobante, entre moneda y condición de IVA', async ({ page }) => {
    await page.locator('#tabNueva').click();
    const datos = page.locator('#seccionAlta .panel').first();
    await expect(datos.locator('.campo > label')).toHaveText([
        'Fecha de emisión', 'Punto de venta', 'Tipo de moneda', 'Cliente', 'Condición de IVA del cliente', 'Observaciones'
    ]);
    await expect(page.locator('#seccionAlta .panel-titulo > span:first-child')).toHaveText([
        /^1\. Datos del comprobante/, '2. Artículos'
    ]);
});

test('la fecha de emisión arranca en hoy y solo permite 5 días antes o después', async ({ page }) => {
    await page.locator('#tabNueva').click();
    const fecha = page.locator('#inputFechaEmision');
    await expect(fecha).toHaveValue(hoyISO());
    await expect(fecha).toHaveAttribute('min', hoyISO(-5));
    await expect(fecha).toHaveAttribute('max', hoyISO(5));
    await expect(page.locator('#inputObservaciones')).toHaveAttribute('maxlength', '500');
});

