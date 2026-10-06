package cz.geokuk.plugins.kesoid.importek;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/** Došlá paměť při načítání nesmí ukončit program ani zahodit zobrazená data. */
public class MultiNacitacSwingWorkerPametTest {

	@Test
	public void maloPametiPriNacitaniNechaDataAOhlasi() throws Exception {
		final AtomicReference<KesBag> zobrazene = new AtomicReference<>();
		final KesoidModel model = new KesoidModel() {
			@Override
			public void setVsechnyKesoidy(final KesBag bag) {
				zobrazene.set(bag);
			}
		};
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		model.inject(progress);
		model.inject(new KesoidPluginManager());
		final MultiNacitac nacitac = new MultiNacitac(model) {
			@Override
			public synchronized KesBag nacti(final Future<?> future, final Genom genom) throws IOException {
				throw new OutOfMemoryError("Java heap space");
			}
		};
		final AtomicReference<String> hlaska = new AtomicReference<>();
		final MultiNacitacSwingWorker worker = new MultiNacitacSwingWorker(nacitac, new Genom(), model) {
			@Override
			protected void ohlasMaloPameti(final String text) {
				hlaska.set(text);
			}
		};
		worker.execute();
		try {
			worker.get(30, TimeUnit.SECONDS);
		} catch (final java.util.concurrent.ExecutionException e) {
			// očekává se, výsledek se zpracuje v done() na EDT
		}
		// done() se na EDT zařadí až po dokončení úlohy, může se opozdit.
		for (int i = 0; i < 100 && hlaska.get() == null; i++) {
			SwingUtilities.invokeAndWait(() -> {});
			Thread.sleep(50);
		}
		Assert.assertNull("zobrazená data se nemění", zobrazene.get());
		Assert.assertNotNull("uživatel dostane hlášku", hlaska.get());
		Assert.assertTrue(hlaska.get(), hlaska.get().contains("paměť") && hlaska.get().contains(cz.geokuk.core.program.PametProgramuAction.jakZvysitPamet()));
	}
}
