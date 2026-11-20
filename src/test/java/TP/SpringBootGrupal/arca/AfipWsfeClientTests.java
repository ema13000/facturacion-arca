package TP.SpringBootGrupal.arca;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AfipWsfeClientTests {
    private final AfipWsfeClient cliente = new AfipWsfeClient();

    @Test
    void leeLaFechaDeEmisionDelComprobanteAutorizado() throws Exception {
        String respuesta = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <FECompConsultarResponse xmlns="http://ar.gov.afip.dif.FEV1/">
                      <FECompConsultarResult>
                        <ResultGet>
                          <CbteFch>20261123</CbteFch>
                          <FchProceso>20261120153045</FchProceso>
                        </ResultGet>
                      </FECompConsultarResult>
                    </FECompConsultarResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        assertEquals(LocalDate.of(2026, 11, 23), cliente.extraerFechaComprobante(respuesta));
    }

    @Test
    void informaElCodigoYElMensajeSiLaConsultaSeRechaza() {
        String respuesta = """
                <FECompConsultarResult xmlns="http://ar.gov.afip.dif.FEV1/">
                  <Errors><Err><Code>602</Code><Msg>Sin resultados</Msg></Err></Errors>
                </FECompConsultarResult>
                """;

        var error = assertThrows(IllegalStateException.class, () -> cliente.extraerFechaComprobante(respuesta));
        assertEquals("ARCA rechazó la consulta: 602. Sin resultados", error.getMessage());
    }
}
