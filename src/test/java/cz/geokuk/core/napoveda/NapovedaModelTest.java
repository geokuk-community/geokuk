package cz.geokuk.core.napoveda;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.framework.Prefe;

public class NapovedaModelTest {

	private final List<ZkontrolovatAktualizaceSwingWorker> spustene = new ArrayList<>();
	private NapovedaModel model;
	private boolean betaKanal;
	private long odklad;

	@Before
	public void setUp() {
		betaKanal = Diagnostika.betaKanal();
		odklad = vseobecne().getLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, 0L);
		model = new NapovedaModel() {
			@Override
			void spust(final ZkontrolovatAktualizaceSwingWorker kontrola) {
				spustene.add(kontrola);
			}
		};
		model.inject(new Prefe());
		model.onEvent(new OnofflineModelChangeEvent(true));
	}

	private static MyPreferences vseobecne() {
		return MyPreferences.current().node(FPref.VSEOBECNE_node);
	}

	@After
	public void tearDown() {
		Diagnostika.setBetaKanal(betaKanal);
		vseobecne().putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, odklad);
	}

	@Test
	public void rucniKontrolaBehemBeziciNeotevreDruhyDialog() {
		Diagnostika.setBetaKanal(true);
		model.zkontrolujNoveAktualizace(true);
		model.zkontrolujNoveAktualizace(true);
		Assert.assertEquals(1, spustene.size());
	}

	@Test
	public void poSkonceniJdeKontrolovatZnovu() {
		Diagnostika.setBetaKanal(true);
		model.zkontrolujNoveAktualizace(true);
		model.kontrolaSkoncila(spustene.get(0));
		model.zkontrolujNoveAktualizace(true);
		Assert.assertEquals(2, spustene.size());
	}

	@Test
	public void poZmeneBetaKanaluSeKontrolujeZnovu() {
		Diagnostika.setBetaKanal(false);
		model.zkontrolujNoveAktualizace(true);
		Diagnostika.setBetaKanal(true);
		model.zkontrolujNoveAktualizace(true);
		Assert.assertEquals(2, spustene.size());
	}

	@Test
	public void rucniKontrolaBehemAutomatickeZapneZobrazeniVysledku() {
		Diagnostika.setBetaKanal(false);
		vseobecne().putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, 0L);
		model.zkontrolujNoveAktualizace(false);
		Assert.assertEquals(1, spustene.size());
		Assert.assertFalse(spustene.get(0).isZobrazitDialogPriPosledniVerzi());
		model.zkontrolujNoveAktualizace(true);
		Assert.assertEquals(1, spustene.size());
		Assert.assertTrue(spustene.get(0).isZobrazitDialogPriPosledniVerzi());
	}

	@Test
	public void kontrolaPoPrepnutiBetaKanaluJeZastarala() {
		Diagnostika.setBetaKanal(false);
		final ZkontrolovatAktualizaceSwingWorker kontrola = new ZkontrolovatAktualizaceSwingWorker(false, model);
		Assert.assertFalse(kontrola.jeZastarala());
		Diagnostika.setBetaKanal(true);
		Assert.assertTrue(kontrola.jeZastarala());
	}
}
