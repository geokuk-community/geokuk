package cz.geokuk.plugins.kesoid.mvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.ToastModel;
import cz.geokuk.plugins.kesoid.importek.RegistrStavuZdroju;
import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Volba „Nové databáze načíst až po vybrání“ podle typu a „jen tento typ“ bez vybrané databáze. */
public class NacitatAzPoVybraniTest {

	private final MyPreferences koren = MyPreferences.current().node("test-nacitat-az-po-vybrani");
	private final GsakParametryNacitani parametry = new GsakParametryNacitani();
	private final RegistrStavuZdroju registr = mock(RegistrStavuZdroju.class);
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return koren;
		}

		@Override
		public GsakParametryNacitani getGsakParametryNacitani() {
			return parametry;
		}

		@Override
		public RegistrStavuZdroju getRegistrStavuZdroju() {
			return registr;
		}

		@Override
		public void fire(final cz.geokuk.framework.Event0<?> event) {
			// události tu nikdo neposlouchá
		}
	};

	private final File a = new File("/tmp/geoget/Cesko.db3").getAbsoluteFile();
	private final File b = new File("/tmp/geoget/Slovensko.db3").getAbsoluteFile();

	@After
	public void uklid() throws Exception {
		koren.removeNode();
	}

	@Test
	public void volbaPodleTypu() {
		parametry.setNoveDatabazeHned(true);
		parametry.setNoveDatabazeOpensakuHned(true);
		parametry.setNoveDatabazeGeogetuHned(false);
		Assert.assertTrue(model.isNacitatAzPoVybrani(TypZdroje.GEOGET));
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.GSAK));
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.OPENSAK));
		parametry.setNoveDatabazeGeogetuHned(true);
		parametry.setNoveDatabazeHned(false);
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.GEOGET));
		Assert.assertTrue(model.isNacitatAzPoVybrani(TypZdroje.GSAK));
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.OPENSAK));
		parametry.setNoveDatabazeHned(true);
		parametry.setNoveDatabazeOpensakuHned(false);
		Assert.assertTrue(model.isNacitatAzPoVybrani(TypZdroje.OPENSAK));
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.GSAK));
		Assert.assertFalse(model.isNacitatAzPoVybrani(TypZdroje.GPX));
	}

	@Test
	public void jenTentoTypBezVybraneDatabazeNicNezapne() {
		final ToastModel toast = mock(ToastModel.class);
		model.inject(toast);
		parametry.setNoveDatabazeGeogetuHned(false);
		model.zaradGeogetDatabaze(new HashSet<>());
		model.zaradGeogetDatabaze(new HashSet<>(Arrays.asList(a, b)));
		when(registr.getSoubory(TypZdroje.GEOGET)).thenReturn(Arrays.asList(a, b));
		model.setNacitatJenTyp(TypZdroje.GEOGET);
		Assert.assertFalse(model.jeZdrojZapnut(a));
		Assert.assertFalse(model.jeZdrojZapnut(b));
		verify(toast).ukaz("Zdroj keší GeoGet – žádná databáze není vybraná, vyberte ji v Soubor > Přehled zdrojů");
	}

	@Test
	public void jenTentoTypBezVolbyZapneVse() {
		final ToastModel toast = mock(ToastModel.class);
		model.inject(toast);
		parametry.setNoveDatabazeGeogetuHned(false);
		model.zaradGeogetDatabaze(new HashSet<>());
		model.zaradGeogetDatabaze(new HashSet<>(Arrays.asList(a, b)));
		parametry.setNoveDatabazeGeogetuHned(true);
		when(registr.getSoubory(TypZdroje.GEOGET)).thenReturn(Arrays.asList(a, b));
		model.setNacitatJenTyp(TypZdroje.GEOGET);
		Assert.assertTrue(model.jeZdrojZapnut(a));
		verify(toast, never()).ukaz(org.mockito.ArgumentMatchers.anyString());
	}
}
