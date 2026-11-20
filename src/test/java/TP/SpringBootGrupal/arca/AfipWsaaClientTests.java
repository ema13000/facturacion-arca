package TP.SpringBootGrupal.arca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AfipWsaaClientTests {

    private static final Instant HOY = Instant.parse("2026-11-20T15:00:00Z");

    @Test
    void conservaElCodigoDelFalloAunqueWsaaRespondaHttp500() {
        String respuesta = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><soap:Fault>
                    <faultcode xmlns:wsaa="urn:wsaa">wsaa:coe.notAuthorized</faultcode>
                    <faultstring>Computador no autorizado a acceder al servicio</faultstring>
                  </soap:Fault></soap:Body>
                </soap:Envelope>
                """;

        var error = assertThrows(IllegalStateException.class,
                () -> new AfipWsaaClient().procesarRespuesta(500, respuesta));

        assertTrue(error.getMessage().contains("coe.notAuthorized"));
        assertTrue(error.getMessage().contains("wsfe"));
    }

    @Test
    void mantieneUnErrorClaroCuandoWsaaDevuelveHtml() {
        var error = assertThrows(IllegalStateException.class,
                () -> new AfipWsaaClient().procesarRespuesta(503, "<html>Servicio temporalmente no disponible</html>"));

        assertEquals("WSAA respondió con HTTP 503", error.getMessage());
    }

    @Test
    void obtieneElVencimientoDelTicketEnLaZonaHorariaDeWsaa() throws Exception {
        String respuesta = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><loginCmsResponse><loginCmsReturn><![CDATA[
                    <loginTicketResponse>
                      <header><expirationTime>2026-11-20T13:00:00-03:00</expirationTime></header>
                      <credentials><token>token de prueba</token><sign>firma de prueba</sign></credentials>
                    </loginTicketResponse>
                  ]]></loginCmsReturn></loginCmsResponse></soap:Body>
                </soap:Envelope>
                """;

        var ticket = new AfipWsaaClient().procesarRespuesta(200, respuesta);

        assertEquals(HOY.plusSeconds(3600), ticket.vencimiento());
        assertEquals(new CredencialesArca("token de prueba", "firma de prueba"), ticket.credenciales());
    }

    @Test
    void recuperaElTicketVigenteInclusoDespuesDeReiniciar(@TempDir Path directorio) throws Exception {
        var credenciales = new CredencialesArca("token de prueba", "firma de prueba");
        var ticket = new CacheTicketsWsaa.Ticket(credenciales, HOY.plusSeconds(3600));
        Clock reloj = Clock.fixed(HOY, ZoneOffset.UTC);
        var cache = new CacheTicketsWsaa(directorio, reloj);

        assertEquals(credenciales, cache.obtener("certificado", () -> ticket));
        assertEquals(credenciales, new CacheTicketsWsaa(directorio, reloj).obtener("certificado", () -> {
            fail("No debe pedir otro ticket si ya hay uno vigente");
            return null;
        }));
    }

    @Test
    void pideOtroTicketCuandoVenceElAnterior(@TempDir Path directorio) throws Exception {
        var viejo = new CredencialesArca("token anterior", "firma anterior");
        var nuevo = new CredencialesArca("token nuevo", "firma nueva");

        var cache = new CacheTicketsWsaa(directorio, Clock.fixed(HOY, ZoneOffset.UTC));
        cache.obtener("certificado", () -> new CacheTicketsWsaa.Ticket(viejo, HOY.plusSeconds(300)));

        Clock despues = Clock.fixed(HOY.plusSeconds(301), ZoneOffset.UTC);
        assertEquals(nuevo, new CacheTicketsWsaa(directorio, despues).obtener("certificado",
                () -> new CacheTicketsWsaa.Ticket(nuevo, HOY.plusSeconds(3600))));
    }
}
