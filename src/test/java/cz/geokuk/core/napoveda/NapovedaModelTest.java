package cz.geokuk.core.napoveda;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;

public class NapovedaModelTest {

	private final List<ZkontrolovatAktualizaceSwingWorker> spustene = new ArrayList<>();
	private NapovedaModel model;
	private boolean betaKanal;

	@Before
	public void setUp() {
		betaKanal = Diagnostika.betaKanal();
		model = new NapovedaModel() {
			@Override
			void spust(final ZkontrolovatAktualizaceSwingWorker kontrola) {
				spustene.add(kontrola);
			}
		};
		model.onEvent(new OnofflineModelChangeEvent(true));
	}

	@After
	public void tearDown() {
		Diagnostika.setBetaKanal(betaKanal);
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
}
