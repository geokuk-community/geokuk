package cz.geokuk.core.ovladani;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.Test;

public class DalkoveOvladaniTest {

	@Test
	public void portZParametru() {
		assertNull(DalkoveOvladani.portZParametru(new String[] { "--reset" }));
		assertEquals(Integer.valueOf(DalkoveOvladani.VYCHOZI_PORT), DalkoveOvladani.portZParametru(new String[] { "--ovladani" }));
		assertEquals(Integer.valueOf(5000), DalkoveOvladani.portZParametru(new String[] { "--reset", "--ovladani=5000" }));
	}

	@Test
	public void json() {
		assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", DalkoveOvladani.json("a\"b\\c\nd\u0001"));
		assertEquals("null", DalkoveOvladani.json(null));
	}

	@Test
	public void bezTokenuNeboSCizimHostemOdmitne() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0);
		final Properties p = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(DalkoveOvladani.SOUBOR), StandardCharsets.UTF_8)) {
			p.load(r);
		}
		final int port = Integer.parseInt(p.getProperty("port"));
		final String token = p.getProperty("token");
		assertEquals(401, zavolej(port, null, null));
		assertEquals(401, zavolej(port, "Bearer spatny", null));
		assertEquals("webová stránka přes DNS rebinding", 401, zavolej(port, "Bearer " + token, "zlo.example:" + port));
		assertEquals(404, zavolej(port, "Bearer " + token, null));
	}

	private static int zavolej(final int port, final String autorizace, final String host) throws IOException {
		if (host != null) {
			// HttpURLConnection hlavičku Host nastavit nedovolí, pošleme ji ručně.
			try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
				final OutputStream os = s.getOutputStream();
				os.write(("GET /neznamy HTTP/1.1\r\nHost: " + host + "\r\nAuthorization: " + autorizace + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
				os.flush();
				final String radek = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)).readLine();
				return Integer.parseInt(radek.split(" ")[1]);
			}
		}
		final HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/neznamy").openConnection();
		if (autorizace != null) {
			c.setRequestProperty("Authorization", autorizace);
		}
		return c.getResponseCode();
	}
}
