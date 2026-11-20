package TP.SpringBootGrupal.arca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

final class ConexionArca {

    private static final Logger LOG = LoggerFactory.getLogger(ConexionArca.class);
    static final HttpClient HTTP = crearCliente();

    private ConexionArca() {
    }

    private static HttpClient crearCliente() {
        HttpClient.Builder cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
        if (System.getProperty("os.name", "").startsWith("Windows")
                && System.getProperty("javax.net.ssl.trustStore") == null
                && System.getProperty("javax.net.ssl.trustStoreType") == null) {
            try {
                KeyStore raices = KeyStore.getInstance("Windows-ROOT");
                raices.load(null, null);
                var confianza = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                confianza.init(raices);
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null, confianza.getTrustManagers(), null);
                cliente.sslContext(tls);
            } catch (GeneralSecurityException | IOException error) {
                LOG.warn("No se pudo usar el almacén de confianza de Windows para ARCA. Se usará el de Java.");
            }
        }
        return cliente.build();
    }
}
