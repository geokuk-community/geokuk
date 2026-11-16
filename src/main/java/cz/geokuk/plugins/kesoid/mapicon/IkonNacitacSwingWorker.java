package cz.geokuk.plugins.kesoid.mapicon;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import cz.geokuk.framework.MySwingWorker0;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/**
 * @author Martin Veverka
 *
 */
public class IkonNacitacSwingWorker extends MySwingWorker0<IkonBag, Void> {

	private final IkonNacitacLoader ikonNacitac;
	private final boolean iPrenacti;
	/** Sada a zvýraznění v okamžiku spuštění, čtené na EDT. */
	private final ASada sada;
	private final Zvyrazneni zvyrazneni;
	/** Dostane na EDT výsledek; null, když se nic nového nenačetlo nebo načtení selhalo. Zavolá se vždy. */
	private final Consumer<IkonBag> poDokonceni;

	public IkonNacitacSwingWorker(final IkonNacitacLoader multiNacitac, final boolean prenacti, final KesoidModel kesoidModel, final Consumer<IkonBag> poDokonceni) {
		ikonNacitac = multiNacitac;
		iPrenacti = prenacti;
		this.poDokonceni = poDokonceni;
		sada = kesoidModel.getJmenoAktualniSadyIkon();
		zvyrazneni = kesoidModel.getZvyrazneni();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#doInBackground()
	 */
	@Override
	protected IkonBag doInBackground() throws Exception {
		assert sada != null;
		return ikonNacitac.nacti(iPrenacti, sada, zvyrazneni);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#done()
	 */
	@Override
	protected void donex() throws InterruptedException, ExecutionException {
		IkonBag result = null;
		try {
			if (!isCancelled()) {
				result = get(); // null = nedošlo ke změně
			}
		} finally {
			poDokonceni.accept(result);
		}
	}

}
