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
    { id: 7, cuitCuil: '20123456789', denominacion: 'Librería Centro' },
    { id: 9, cuitCuil: '20987654321', denominacion: 'Escuela Norte' }
];
const listas = [
    { id: 1, descripcion: 'Minorista' },
    { id: 2, descripcion: 'Mayorista' }
];
const precios = [
    { id: 25, articuloCodigo: 'A8', articuloDenominacion: 'Cuaderno', listaPrecioDenominacion: 'Minorista', precioVenta: 100, listaPrecioId: 1 },
    { id: 26, articuloCodigo: 'A8', articuloDenominacion: 'Cuaderno', listaPrecioDenominacion: 'Mayorista', precioVenta: 80, listaPrecioId: 2 },
    { id: 27, articuloCodigo: 'B1', articuloDenominacion: 'Lápiz', listaPrecioDenominacion: 'Minorista', precioVenta: 10, listaPrecioId: 1 }
];
const reporte = { id: 1, numeroFactura: 1, fechaEmision: '2026-10-23', clienteDenominacion: 'Librería Centro', condicionIva: 'IVA', puntoVentaDescripcion: 'Centro', importeTotal: 100, estado: 'PENDIENTE' };
const coincide = (texto, valores) => valores.join(' ').toLowerCase().includes((texto ?? '').toLowerCase());

test.beforeAll(async () => {
    server = http.createServer((req, res) => {
        const file = path.resolve(root, '.' + (req.url === '/' ? '/index.html' : req.url));
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
        if (url.pathname.endsWith('/clientes')) body = clientes.filter(c => coincide(texto, [c.cuitCuil, c.denominacion]));
        else if (/\/clientes\/\d+$/.test(url.pathname)) body = clientes.find(c => url.pathname.endsWith('/' + c.id));
        else if (url.pathname.endsWith('/listas-precio')) body = listas;
        else if (url.pathname.endsWith('/precios-articulos')) {
            const lista = Number(url.searchParams.get('listaPrecioId'));
            body = precios.filter(p => p.listaPrecioId === lista && coincide(texto, [p.articuloCodigo, p.articuloDenominacion]));
        }
        else if (/\/precios-articulos\/\d+$/.test(url.pathname)) body = precios.find(p => url.pathname.endsWith('/' + p.id));
        else if (url.pathname.endsWith('/edicion')) body = {
            numeroFactura: 1, clienteId: 7, condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1,
            detalles: [{ listaPrecioArticuloId: 26, cantidad: 2, porcentajeBonificacion: 0 }]
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

test('no muestra usuario ni IVA en la parte superior', async ({ page }) => {
    await expect(page.locator('#inputUsuario')).toHaveCount(0);
    await expect(page.locator('#inputPorcentajeIva')).toHaveCount(0);
});

test('busca clientes en el servidor con el botón y permite consumidor final', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#btnBuscarCliente').click();
    await expect(page.locator('#explorerFilas button')).toHaveCount(0);
    expect(page.pedidos.some(u => u.pathname.endsWith('/clientes'))).toBe(false);
    await buscar(page, 'escuela');
    await expect(page.locator('#explorerFilas tr')).toHaveCount(1);
    await expect(page.locator('#explorerFilas')).toContainText('Escuela Norte');
    expect(page.pedidos.find(u => u.pathname.endsWith('/clientes')).searchParams.get('texto')).toBe('escuela');
    await page.locator('#explorerBusqueda').fill('20123456789');
    await page.locator('#explorerBusqueda').press('Enter');
    await expect(page.locator('#explorerFilas')).toContainText('Librería Centro');
    await page.locator('#explorerFilas button').click();
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Librería Centro');
    await expect(page.locator('#inputCliente')).toHaveValue('7');
    await page.locator('#btnQuitarCliente').click();
    await expect(page.locator('#inputCliente')).toHaveValue('');
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Consumidor final');
});

test('exige elegir la lista de precios antes de buscar artículos y filtra por ella', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await expect(page.locator('[data-buscar-precio]').first()).toBeDisabled();
    await page.locator('#inputListaPrecio').selectOption('2');
    await expect(page.locator('[data-buscar-precio]').first()).toBeEnabled();
    await page.locator('[data-buscar-precio]').first().click();
    await buscar(page, '');
    await expect(page.locator('#explorerFilas tr')).toHaveCount(1);
    await expect(page.locator('#explorerFilas')).toContainText('Cuaderno');
    const pedido = page.pedidos.find(u => u.pathname.endsWith('/precios-articulos'));
    expect(pedido.searchParams.get('listaPrecioId')).toBe('2');
});

test('elige precios, calcula el total, envía el ID y conserva renglones al quitar otro', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#inputListaPrecio').selectOption('1');
    await page.locator('[data-buscar-precio]').first().click();
    await buscar(page, 'cuaderno');
    await page.locator('#explorerFilas button').click();
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
    await page.locator('#btnSumarRenglon').click();
    await page.locator('[data-buscar-precio]').nth(1).click();
    await buscar(page, 'lápiz');
    await page.locator('#explorerFilas button').click();
    await expect(page.locator('#totalFactura')).toHaveText('$110.00');
    await page.locator('#tbodyRenglones tr').first().getByRole('button', { name: 'Quitar' }).click();
    await expect(page.locator('#tbodyRenglones tr')).toHaveCount(1);
    await expect(page.locator('#tbodyRenglones')).toContainText('Lápiz');
    await expect(page.locator('#inputNumero')).toHaveAttribute('readonly', '');
    for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva']) await page.locator('#' + id).selectOption('1');
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
    await expect(page.locator('#seccionListado')).toBeVisible();
});

test('cambiar de lista de precios limpia los artículos elegidos', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#inputListaPrecio').selectOption('1');
    await page.locator('[data-buscar-precio]').first().click();
    await buscar(page, 'cuaderno');
    await page.locator('#explorerFilas button').click();
    page.on('dialog', dialog => dialog.accept());
    await page.locator('#inputListaPrecio').selectOption('2');
    await expect(page.locator('#tbodyRenglones')).toContainText('Sin artículo seleccionado');
});

test('edición trae cliente, lista y precio por ID sin cargar todo el catálogo', async ({ page }) => {
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    await expect(page.locator('#clienteDescripcion')).toHaveValue('Librería Centro');
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
    await expect(page.locator('#inputListaPrecio')).toHaveValue('2');
    expect(page.pedidos.some(u => u.pathname.endsWith('/clientes') || u.pathname.endsWith('/precios-articulos'))).toBe(false);
    await page.locator('[data-buscar-precio]').click();
    await buscar(page, 'inexistente');
    await expect(page.locator('#explorerFilas')).toContainText('No hay resultados');
    await page.keyboard.press('Escape');
    await expect(page.locator('#explorerDialog')).not.toBeVisible();
    await expect(page.locator('#tbodyRenglones')).toContainText('Cuaderno');
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

test('no guarda renglones sin seleccionar un precio', async ({ page }) => {
    await page.locator('#tabNueva').click();
    for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva', 'inputListaPrecio']) await page.locator('#' + id).selectOption('1');
    const dialogo = page.waitForEvent('dialog');
    const guardar = page.locator('#btnGuardarFactura').click();
    const alerta = await dialogo;
    expect(alerta.message()).toContain('Seleccioná un artículo');
    await alerta.accept();
    await guardar;
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
    await expect(autorizada.getByRole('button')).toHaveText(['Anular', 'Imprimir']);
    await expect(pendiente.getByRole('button')).toHaveText(['Editar', 'Eliminar']);
    await expect(anulada.getByRole('button')).toHaveCount(0);
});

test('imprimir una autorizada muestra CAE y vencimiento', async ({ page }) => {
    await page.route('**/api/facturas', route => route.fulfill({ json: [{ ...reporte, estado: 'AUTORIZADA' }] }));
    await page.route('**/api/facturas/1/impresion', route => route.fulfill({ json: {
        id: 1, numeroFactura: 15, puntoVentaNumero: 2, puntoVentaDescripcion: 'Central', fechaEmision: '2026-11-18',
        clienteDenominacion: 'Librería Centro', clienteCuitCuil: '20123456789', condicionIva: 'Consumidor Final',
        importeTotal: 250, cae: '75123456789012', caeFechaVencimiento: '2026-11-28',
        detalles: [{ descripcion: 'Cuaderno azul', cantidad: 2, precioUnitario: 125, porcentajeBonificacion: 0, importeSubtotal: 250 }]
    } }));
    await page.locator('#btnAplicarFiltros').click();
    await page.addInitScript(() => { window.print = () => {}; });
    const popup = page.waitForEvent('popup');
    await page.getByRole('button', { name: 'Imprimir' }).click();
    const ventana = await popup;
    await expect(ventana.locator('body')).toContainText('75123456789012');
    await expect(ventana.locator('body')).toContainText('28/11/2026');
    await expect(ventana.locator('body')).toContainText('0002-00000015');
    await expect(ventana.locator('body')).toContainText('Cuaderno azul');
    await expect(ventana.locator('body')).toContainText('$250.00');
});

test('después de guardar, la factura nueva queda resaltada arriba del listado', async ({ page }) => {
    await page.route(url => url.pathname === '/api/facturas', route => route.fulfill({ json: route.request().method() === 'GET'
        ? [{ ...reporte, id: 2, numeroFactura: 2 }, reporte]
        : { ...reporte, id: 2, numeroFactura: 2 } }));
    await page.locator('#tabNueva').click();
    await page.locator('#inputListaPrecio').selectOption('1');
    await page.locator('[data-buscar-precio]').first().click();
    await buscar(page, 'cuaderno');
    await page.locator('#explorerFilas button').click();
    for (const id of ['inputPuntoVenta', 'inputMoneda', 'inputCondicionIva']) await page.locator('#' + id).selectOption('1');
    page.on('dialog', dialog => dialog.accept());
    await page.locator('#btnGuardarFactura').click();
    await expect(page.locator('#tbodyFacturas tr').first()).toHaveClass(/fila-nueva/);
    await expect(page.locator('#tbodyFacturas tr').nth(1)).not.toHaveClass(/fila-nueva/);
});

test('muestra subtotal, bonificación y total mientras se cargan artículos', async ({ page }) => {
    await page.locator('#tabNueva').click();
    await page.locator('#inputListaPrecio').selectOption('1');
    await page.locator('[data-buscar-precio]').first().click();
    await buscar(page, 'cuaderno');
    await page.locator('#explorerFilas button').click();
    const fila = page.locator('#tbodyRenglones tr').first();
    await fila.locator('input').nth(0).fill('3');
    await fila.locator('input').nth(1).fill('10');
    await expect(fila.locator('[data-subtotal]')).toHaveText('$270.00');
    await expect(page.locator('#subtotalFactura')).toHaveText('$300.00');
    await expect(page.locator('#bonificacionFactura')).toHaveText('-$30.00');
    await expect(page.locator('#totalFactura')).toHaveText('$270.00');
    await expect(page.locator('#cantidadArticulos')).toHaveText('1');
    await expect(fila.locator('[data-subtotal]')).toBeInViewport();
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
