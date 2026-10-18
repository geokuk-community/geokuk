package cz.geokuk.plugins.mapy.kachle.data;

import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;

/** Stínovaný reliéf ČÚZK: dlaždice jako požadavek WMS na obdélník v EPSG:3857, překryv jen online, bez hromadného stahování. */
public class EKaTypeReliefTest {

	/** Sněžka z15: dlaždice x 17816, y 11008. */
	@Test
	public void adresaJeGetMapNaObdelnikDlazdice() throws Exception {
		final KaLoc loc = KaLoc.ofJZ(new Wgs(50.736, 15.740).toMou(), 15);
		Assert.assertEquals(17816, loc.getFromSzUnsignedX());
		Assert.assertEquals(11008, loc.getFromSzUnsignedY());
		final URL url = new Ka(loc, EKaType.RELIEF_CUZK).getUrl();
		Assert.assertEquals("https://ags.cuzk.gov.cz/arcgis2/services/dmr5g/ImageServer/WMSServer?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap"
				+ "&LAYERS=dmr5g:GrayscaleHillshade&STYLES=&CRS=EPSG:3857&BBOX=1751325.192,6573584.433,1752548.185,6574807.425&WIDTH=256&HEIGHT=256"
				+ "&FORMAT=image/png&TRANSPARENT=TRUE", url.toExternalForm());
	}

	/** Celý svět v z0 je čtverec ±SVET. */
	@Test
	public void obdelnikNejmensihoMeritka() throws Exception {
		final String url = new WmsUrlBuilder("https://x/wms", "v").buildUrl(new Ka(KaLoc.ofJZ(new Wgs(0, 0).toMou(), 0), EKaType.RELIEF_CUZK)).toExternalForm();
		Assert.assertTrue(url, url.contains("&BBOX=-20037508.343,-20037508.343,20037508.343,20037508.343&"));
	}

	@Test
	public void prekryvJenOnlineBezHromadnehoStahovani() {
		final EKaType r = EKaType.RELIEF_CUZK;
		Assert.assertTrue(r.isPrekryv());
		Assert.assertFalse(r.isOffline());
		Assert.assertFalse(r.isHromadneStahovaniPovoleno());
		Assert.assertEquals(12, r.getMinMoumer());
		Assert.assertEquals(20, r.getMaxMoumer());
		Assert.assertTrue(r.getKryti() > 0 && r.getKryti() < 1);
		Assert.assertEquals(1f, EKaType.CUZK_ZTM.getKryti(), 0);
		Assert.assertEquals("© ČÚZK, CC BY 4.0", r.getAtribuce());
		Assert.assertFalse("není podklad v menu", java.util.Arrays.asList(EKaType.values()).contains(r));
	}
}
