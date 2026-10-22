package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

/** Změna samotné ikony přibaleného tématu musí změnit otisk, jinak se dlaždice z cache nepřekreslí. */
public class OtiskTematuSymbolyTest {

	private static final String XML = "<rendertheme><rule><symbol src=\"file:symbols/skala.svg\"/></rule></rendertheme>";

	private static Function<String, InputStream> soubory(final String svg) {
		return soubory(XML, svg);
	}

	private static Function<String, InputStream> soubory(final String xml, final String svg) {
		final Map<String, String> m = new HashMap<>();
		m.put("geokuk/T.xml", xml);
		m.put("geokuk/symbols/skala.svg", svg);
		return cesta -> m.containsKey(cesta) ? new ByteArrayInputStream(m.get(cesta).getBytes(StandardCharsets.UTF_8)) : null;
	}

	@Test
	public void zmenaSymboluZmeniOtisk() {
		final String puvodni = TemaOfflineMapy.otiskZdroje("geokuk/T.xml", soubory("<svg>a</svg>"));
		Assert.assertEquals(puvodni, TemaOfflineMapy.otiskZdroje("geokuk/T.xml", soubory("<svg>a</svg>")));
		Assert.assertNotEquals(puvodni, TemaOfflineMapy.otiskZdroje("geokuk/T.xml", soubory("<svg>b</svg>")));
		Assert.assertNotEquals("změna XML", puvodni, TemaOfflineMapy.otiskZdroje("geokuk/T.xml", soubory(XML + " ", "<svg>a</svg>")));
	}

	@Test
	public void temaGeoKukZapocitaSymboly() {
		final java.util.List<String> otevrene = new java.util.ArrayList<>();
		TemaOfflineMapy.otiskZdroje("geokuk/GeoKuk.xml", cesta -> {
			otevrene.add(cesta);
			return TemaOfflineMapy.class.getResourceAsStream(cesta);
		});
		Assert.assertTrue(otevrene.toString(), otevrene.contains("geokuk/symbols/cliff.svg"));
	}
}
