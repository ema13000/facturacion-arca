package TP.SpringBootGrupal;

import TP.SpringBootGrupal.arca.AfipWsaaClient;
import TP.SpringBootGrupal.arca.CredencialesArca;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import TP.SpringBootGrupal.arca.AfipWsfeClient;
import TP.SpringBootGrupal.arca.SolicitudFacturaC;
import TP.SpringBootGrupal.arca.ResultadoCae;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootApplication
public class SpringBootGrupalApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				SpringBootGrupalApplication.class,
				args
		);
	}

	@Bean
	@ConditionalOnProperty(
			name = "arca.ejecutar",
			havingValue = "true"
	)
	public CommandLineRunner ejecutarArca(
			AfipWsaaClient wsaaClient,
			AfipWsfeClient wsfeClient,
			@Value("${arca.clave-privada}") String rutaClave,
			@Value("${arca.certificado}") String rutaCertificado,
			@Value("${arca.cuit}") long cuit
	) {
		return args -> {
			System.out.println("Solicitando token y sign a WSAA...");

			CredencialesArca credenciales =
					wsaaClient.obtenerCredenciales(
							Path.of(rutaClave),
							Path.of(rutaCertificado)
					);

			System.out.println("Autenticación completada.");

			int puntoVenta = 1;
			int tipoComprobante = 11;

			long ultimoNumero = wsfeClient.obtenerUltimoComprobante(
					credenciales,
					cuit,
					puntoVenta,
					tipoComprobante
			);

			long siguienteNumero = Math.addExact(ultimoNumero, 1L);

			System.out.println("Último comprobante: " + ultimoNumero);
			System.out.println("Siguiente número: " + siguienteNumero);
			SolicitudFacturaC factura = new SolicitudFacturaC(
					puntoVenta,
					siguienteNumero,
					LocalDate.now(),
					99,
					0L,
					5,
					new BigDecimal("1500.00")
			);

			System.out.println("Solicitando CAE...");

			ResultadoCae resultado = wsfeClient.solicitarCae(
					credenciales,
					cuit,
					factura
			);

			if ("A".equals(resultado.resultado())) {
				System.out.println("Factura autorizada.");
				System.out.println("CAE: " + resultado.cae());
				System.out.println(
						"Vencimiento: " + resultado.fechaVencimiento()
				);
			} else {
				System.out.println("Factura rechazada.");
			}

			for (String observacion : resultado.observaciones()) {
				System.out.println("Observación: " + observacion);
			}
		};
	}
}
