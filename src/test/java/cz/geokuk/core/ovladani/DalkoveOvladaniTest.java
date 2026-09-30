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
		assertNull(DalkoveOvladani.portZParametru(new String[] { "--ovladani=abc" }));
		assertNull(DalkoveOvladani.portZParametru(new String[] { "--ovladani=" }));
		assertNull(DalkoveOvladani.portZParametru(new String[] { "--ovladani=70000" }));
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
		try {
			final Properties p = nactiSoubor();
			final int port = Integer.parseInt(p.getProperty("port"));
			final String token = p.getProperty("token");
			assertEquals(401, zavolej(port, null, null));
			assertEquals(401, zavolej(port, "Bearer spatny", null));
			assertEquals("webová stránka přes DNS rebinding", 401, zavolej(port, "Bearer " + token, "zlo.example:" + port));
			assertEquals(404, zavolej(port, "Bearer " + token, null));
		} finally {
			ovladani.zastav();
		}
	}

	@Test
	public void souborJeJenProVlastnika() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0);
		try {
			if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(DalkoveOvladani.SOUBOR.toPath())));
			}
		} finally {
			ovladani.zastav();
		}
		assertFalse(DalkoveOvladani.SOUBOR.exists());
	}

	@Test
	public void souborDruheInstanceSeNesmaze() throws Exception {
		final DalkoveOvladani prvni = new DalkoveOvladani();
		final DalkoveOvladani druha = new DalkoveOvladani();
		prvni.spust(0);
		druha.spust(0);
		try {
			final String tokenDruhe = nactiSoubor().getProperty("token");
			prvni.zastav();
			assertEquals(tokenDruhe, nactiSoubor().getProperty("token"));
		} finally {
			prvni.zastav();
			druha.zastav();
		}
	}

	@Test
	public void neplatnaPoziceSeOdmitne() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0);
		try {
			final Properties p = nactiSoubor();
			final HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + p.getProperty("port") + "/pozice?lat=NaN&lon=0").openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Authorization", "Bearer " + p.getProperty("token"));
			assertEquals(400, c.getResponseCode());
		} finally {
			ovladani.zastav();
		}
	}

	private static Properties nactiSoubor() throws IOException {
		final Properties p = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(DalkoveOvladani.SOUBOR), StandardCharsets.UTF_8)) {
			p.load(r);
		}
		return p;
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
