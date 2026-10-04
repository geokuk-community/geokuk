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
	public void cizihoHostaOriginASpatnyTokenOdmitne() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0, true);
		try {
			final Properties p = nactiSoubor();
			final int port = Integer.parseInt(p.getProperty("port"));
			final String token = p.getProperty("token");
			final String host = "127.0.0.1:" + port;
			assertEquals("veřejná část bez tokenu", 404, zavolej(port, "/neznamy", null, host, null));
			assertEquals("vývojová část bez tokenu", 401, zavolej(port, "/menu", null, host, null));
			assertEquals(401, zavolej(port, "/neznamy", "Bearer spatny", host, null));
			assertEquals("webová stránka přes DNS rebinding", 403, zavolej(port, "/neznamy", "Bearer " + token, "zlo.example:" + port, null));
			assertEquals("webová stránka (CSRF)", 403, zavolej(port, "/neznamy", null, host, "https://zlo.example"));
			assertEquals(404, zavolej(port, "/neznamy", "Bearer " + token, host, null));
		} finally {
			ovladani.zastav();
		}
	}

	@Test
	public void bezVyvojoveCastiNeniTokenAniSoubor() throws Exception {
		DalkoveOvladani.SOUBOR.delete();
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0);
		try {
			assertFalse(DalkoveOvladani.SOUBOR.exists());
		} finally {
			ovladani.zastav();
		}
	}

	@Test
	public void souborJeJenProVlastnika() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0, true);
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
		prvni.spust(0, true);
		druha.spust(0, true);
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
		ovladani.spust(0, true);
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

	@Test
	public void prikazPresGetSeOdmitne() throws Exception {
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		ovladani.spust(0, true);
		try {
			final Properties p = nactiSoubor();
			final int port = Integer.parseInt(p.getProperty("port"));
			assertEquals(400, zavolej(port, "/pozice?lat=50&lon=14", "Bearer " + p.getProperty("token"), "127.0.0.1:" + port, null));
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

	private static int zavolej(final int port, final String cesta, final String autorizace, final String host, final String origin) throws IOException {
		// HttpURLConnection hlavičky Host a Origin nastavit nedovolí, pošleme je ručně.
		try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
			final OutputStream os = s.getOutputStream();
			os.write(("GET " + cesta + " HTTP/1.1\r\nHost: " + host + "\r\n" + (autorizace == null ? "" : "Authorization: " + autorizace + "\r\n")
					+ (origin == null ? "" : "Origin: " + origin + "\r\n") + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			os.flush();
			final String radek = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)).readLine();
			return Integer.parseInt(radek.split(" ")[1]);
		}
	}

	@Test
	public void obsazenyPortCeskyBezTechnickychPodrobnosti() {
		final String text = DalkoveOvladani.popisChyby(48321, new java.net.BindException("Address already in use"));
		assertTrue(text, text.startsWith("Dálkové ovládání nejde spustit."));
		assertTrue(text, text.contains("Port 48321 už používá jiný program"));
		assertFalse(text, text.contains("Address already in use"));
	}

	@Test
	public void nezapisovatelnySouborCesky() {
		final String text = DalkoveOvladani.popisChyby(0, new java.nio.file.AccessDeniedException("C:\\Program Files\\GeoKuk\\data"));
		assertEquals("Dálkové ovládání nejde spustit. Nejde zapsat soubor C:\\Program Files\\GeoKuk\\data.", text);
	}

	private static java.nio.file.Path posixSlozka(final String prava) throws IOException {
		org.junit.Assume.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		final java.nio.file.Path d = java.nio.file.Files.createTempDirectory("geokuk-ovladani");
		java.nio.file.Files.setPosixFilePermissions(d, java.nio.file.attribute.PosixFilePermissions.fromString(prava));
		return d;
	}

	@Test
	public void vlastniSlozkaProjde() throws Exception {
		DalkoveOvladani.overSlozku(posixSlozka("rwx------"));
	}

	@Test
	public void uzivatelBezJmenaVlastniSlozkuPozna() throws Exception {
		final java.nio.file.Path d = posixSlozka("rwx------");
		final String jmeno = System.getProperty("user.name");
		System.setProperty("user.name", "?");
		try {
			DalkoveOvladani.overSlozku(d);
		} finally {
			System.setProperty("user.name", jmeno);
		}
	}

	@Test(expected = IOException.class)
	public void slozkaZapisovatelnaVsemiSeOdmitne() throws Exception {
		DalkoveOvladani.overSlozku(posixSlozka("rwxrwxrwx"));
	}

	@Test(expected = IOException.class)
	public void odkazNaSlozkuSeOdmitne() throws Exception {
		final java.nio.file.Path cil = posixSlozka("rwx------");
		final java.nio.file.Path odkaz = cil.resolveSibling(cil.getFileName() + "-odkaz");
		java.nio.file.Files.createSymbolicLink(odkaz, cil);
		DalkoveOvladani.overSlozku(odkaz);
	}

	@Test(expected = IOException.class)
	public void ciziSlozkaSeOdmitne() throws Exception {
		final java.nio.file.Path d = posixSlozka("rwx------");
		// Změnit vlastníka smí jen root.
		org.junit.Assume.assumeTrue("root".equals(System.getProperty("user.name")));
		java.nio.file.Files.setOwner(d, d.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("nobody"));
		DalkoveOvladani.overSlozku(d);
	}
}
