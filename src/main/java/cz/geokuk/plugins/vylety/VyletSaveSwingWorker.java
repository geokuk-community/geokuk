/**
 *
 */
package cz.geokuk.plugins.vylety;

import java.util.List;
import java.util.concurrent.ExecutionException;

import cz.geokuk.framework.MySwingWorker0;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 */
@Slf4j
public class VyletSaveSwingWorker extends MySwingWorker0<Vylet, Void> {



	private final VyletovyZperzistentnovac vyletovyZperzistentnovac;
	private final List<String> ano;
	private final List<String> ne;

	public VyletSaveSwingWorker(final VyletovyZperzistentnovac vyletovyZperzistentnovac, final Vylet vylet) {
		this.vyletovyZperzistentnovac = vyletovyZperzistentnovac;
		// snímek, zapisuje se na pozadí, zatímco uživatel může výlet dál měnit
		ano = vylet.kody(EVylet.ANO);
		ne = vylet.kody(EVylet.NE);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#doInBackground()
	 */
	@Override
	protected Vylet doInBackground() throws Exception {
		vyletovyZperzistentnovac.immediatlyZapisVylet(ano, ne);
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see javax.swing.SwingWorker#done()
	 */
	@Override
	protected void donex() throws InterruptedException, ExecutionException {
		if (isCancelled()) {
			return;
		}
		final Vylet result = get();
		if (result == null) {
			return; // asi zkanclváno
		}
		log.info("Nahran vylet, %d lovenych a %d ignorovanych: \n", result.get(EVylet.ANO).size(), result.get(EVylet.NE).size());
		// Board.eveman.fire(new VyletChangeEvent(result, null, null, null));
	}

}
