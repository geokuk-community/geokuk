/**
 *
 */
package cz.geokuk.plugins.vylety;

import java.util.concurrent.ExecutionException;

import cz.geokuk.framework.MySwingWorker0;
import cz.geokuk.plugins.kesoid.KesBag;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 */
@Slf4j
public class VyletLoadSwingWorker extends MySwingWorker0<Vylet, Void> {



	private final KesBag vsechny;
	private final VyletModel vyletModel;
	private final int generace;
	private final int pocetZmenPriStartu;

	public VyletLoadSwingWorker(final VyletModel vyletModel, final KesBag vsechny, final int generace, final int pocetZmenPriStartu) {
		this.vsechny = vsechny;
		this.vyletModel = vyletModel;
		this.generace = generace;
		this.pocetZmenPriStartu = pocetZmenPriStartu;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#doInBackground()
	 */
	@Override
	public Vylet doInBackground() throws Exception {
		return vyletModel.nactiPoZapisech(vsechny);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#done()
	 */
	@Override
	protected void donex() throws InterruptedException, ExecutionException {
		final Vylet result = get();
		if (result == null) {
			return; // asi zkanclváno
		}
		log.info("Nahran vylet, {} lovenych a {} ignorovanych.", result.get(EVylet.ANO).size(), result.get(EVylet.NE).size());
		vyletModel.prevezmiNactenyVylet(result, generace, pocetZmenPriStartu);
	}

}
