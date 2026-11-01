package cz.geokuk.plugins.kesoid.mvc;

import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.beans.PropertyChangeListener;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;

import javax.swing.SwingUtilities;

import cz.geokuk.framework.ToggleAction0;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.KodKesoidu;
import cz.geokuk.plugins.kesoid.Kesoid;

import lombok.extern.slf4j.Slf4j;

/**
 * Volba v menu Kešoidy: když se do schránky dostane kód kešoidu nebo odkaz, program, který právě není aktivní, vycentruje mapu na tento kešoid. Hledá se ve všech
 * načtených kešoidech.
 */
@Slf4j
public class PrejdiNaKesoidZeSchrankyAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;

	private transient KesoidModel kesoidModel;
	private volatile transient KesBag kesBag;
	private transient SledovaniSchranky sledovani;

	public PrejdiNaKesoidZeSchrankyAction() {
		super("Přejít na kešoid ze schránky");
		putValue(SHORT_DESCRIPTION, "Když se do schránky vloží kód kešoidu nebo odkaz na něj a GeoKuk není aktivní, vycentruje mapu na tento kešoid.");
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void onEvent(final KeskyNactenyEvent event) {
		kesBag = event.getVsechny();
	}

	public void onEvent(final SledovaniSchrankyEvent event) {
		setSelected(event.isOnoff());
		if (!event.isOnoff() && sledovaniVytvoreno()) {
			sledovani().nastav(false);
			KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(ACTIVNI_OKNO, poslouchacOken);
		} else if (event.isOnoff()) {
			// Schránka se vytvoří až při zapnutí; při vypnuté volbě se jí program vůbec nedotýká.
			sledovani().nastav(true);
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(ACTIVNI_OKNO, poslouchacOken);
		}
	}

	private static final String ACTIVNI_OKNO = "activeWindow";

	private final transient PropertyChangeListener poslouchacOken = e -> sledovani().oknoSeZmenilo(e.getNewValue() != null);

	synchronized boolean sledovaniVytvoreno() {
		return sledovani != null;
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		kesoidModel.setSledovatSchranku(nastaveno);
	}

	private synchronized SledovaniSchranky sledovani() {
		if (sledovani == null) {
			sledovani = new SledovaniSchranky(() -> Toolkit.getDefaultToolkit().getSystemClipboard(),
					() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null, this::prejdiNa, SwingUtilities::invokeLater, pozadi(), casovac());
		}
		return sledovani;
	}

	private static Executor pozadi() {
		return Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, "Schránka kešoidů");
			t.setDaemon(true);
			return t;
		});
	}

	private static SledovaniSchranky.Casovac casovac() {
		final ScheduledExecutorService vlakno = Executors.newSingleThreadScheduledExecutor(r -> {
			final Thread t = new Thread(r, "Časovač schránky kešoidů");
			t.setDaemon(true);
			return t;
		});
		return (perioda, uloha) -> {
			final ScheduledFuture<?> f = vlakno.scheduleWithFixedDelay(uloha, perioda, perioda, TimeUnit.MILLISECONDS);
			return () -> f.cancel(false);
		};
	}

	/** Na vlákně událostí: vycentruje mapu na kešoid podle textu; vrací, zda ho našla. Žádný dialog, nenalezený kód se mlčky ignoruje. */
	boolean prejdiNa(final String text) {
		final List<String> kandidati = KodKesoidu.kandidati(text);
		if (kandidati.isEmpty()) {
			return false;
		}
		final KesBag bag = kesBag;
		final Kesoid kesoid = bag == null ? null : KodKesoidu.najdi(text, bag.getKesoidy(), Kesoid::getIdentifier);
		log.debug("Kód {} ze schránky {}", kandidati.get(0), kesoid == null ? "nenalezen" : "nalezen");
		if (kesoid == null) {
			return false;
		}
		poziceModel.setPoziceNaObjekt(kesoid.getMainWpt());
		vyrezModel.vystredovatNaPozici();
		return true;
	}
}
