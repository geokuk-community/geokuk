package cz.geokuk.core.render;

import static org.junit.Assert.*;

import org.junit.Test;

public class ZkraceniCestyTest {

	private static final int MAX = JRenderDialog.MAX_ZNAKU_CESTY;

	@Test
	public void kratkaCestaZustane() {
		assertEquals("C:\\GeoKuk\\data\\render\\kmz", JRenderDialog.zkratCestu("C:\\GeoKuk\\data\\render\\kmz", MAX));
	}

	@Test
	public void dlouhaCestaWindowsZkracenaUprostred() {
		final String cesta = "C:\\Users\\Uzivatel\\Downloads\\GeoKuk-windows-6.2.0-beta.11\\GeoKuk\\data\\render\\kmz";
		final String z = JRenderDialog.zkratCestu(cesta, MAX);
		assertEquals("C:\\…\\GeoKuk\\data\\render\\kmz", z);
		assertTrue(z.length() <= MAX);
	}

	@Test
	public void dlouhaCestaLinux() {
		final String z = JRenderDialog.zkratCestu("/home/uzivatel/stazene/GeoKuk-linux-6.2.0-beta.11/GeoKuk/data/render/kmz", MAX);
		assertEquals("/…/GeoKuk/data/render/kmz", z);
	}

	@Test
	public void sitovaCesta() {
		final String z = JRenderDialog.zkratCestu("\\\\server\\sdileni\\uzivatele\\nekdo\\dokumenty\\GeoKuk\\data\\render\\kmz", MAX);
		assertTrue(z, z.startsWith("\\\\server\\…"));
		assertTrue(z, z.endsWith("\\render\\kmz"));
		assertTrue(z.length() <= MAX);
	}

	@Test
	public void dlouhaPosledniSlozka() {
		final String posledni = "slozka-s-velmi-dlouhym-nazvem-ktery-se-nevejde-ani-sam";
		final String z = JRenderDialog.zkratCestu("C:\\" + posledni, MAX);
		assertEquals(MAX, z.length());
		assertTrue(z.startsWith("…"));
		assertTrue(posledni.endsWith(z.substring(1)));
	}
}
