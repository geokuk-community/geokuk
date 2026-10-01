package cz.geokuk.core.program;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class KontrolaUmisteniTest {

	private static final Map<String, String> NIC = Collections.emptyMap();

	@Test
	public void poznaSynchronizovaneSlozky() {
		Assert.assertEquals("OneDrive", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/OneDrive - Firma/Plocha/GeoKuk"), NIC));
		Assert.assertEquals("Dropbox", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/Dropbox/GeoKuk"), NIC));
		Assert.assertEquals("Google Disk", KontrolaUmisteni.synchronizovanaSluzba(new File("/G/Můj disk/GeoKuk"), NIC));
		Assert.assertEquals("OneDrive", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/Dokumenty/GeoKuk"),
				Collections.singletonMap("OneDrive", "/home/kacer/Dokumenty")));
	}

	@Test
	public void beznaSlozkaNeniSynchronizovana() {
		Assert.assertNull(KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/GeoKuk"), Collections.singletonMap("OneDrive", "/home/kacer/OneDrive")));
	}
}
