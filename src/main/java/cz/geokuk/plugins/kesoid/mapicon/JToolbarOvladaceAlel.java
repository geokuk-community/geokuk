/**
 *
 */
package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.*;

import cz.geokuk.framework.Factory;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.mvc.KeskyNactenyEvent;
import cz.geokuk.plugins.kesoid.mvc.SwitchKesoidUrciteAlelyAction;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.gui.JIconCheckBox;

/**
 * @author Martin Veverka
 *
 */
public class JToolbarOvladaceAlel extends JPanel {

	private static final long serialVersionUID = 2858792073950044988L;

	private Genom genom;
	protected Factory factory;

	private final JToolbarOvladaceAlel tb;

	//////////////////////////////////////////
	// TODO Celkově nějak refactorovat
	private final Map<String, JIconCheckBox> mapka = new HashMap<>();

	/** Výška ikon standardní sady, vyšší ikony se na toolbaru zmenší. */
	static final int VYSKA_IKONY = 24;
	private static final Icon PRAZDNA_IKONA = new ImageIcon(new BufferedImage(VYSKA_IKONY, VYSKA_IKONY, BufferedImage.TYPE_INT_ARGB));

	/**
	 *
	 */
	public JToolbarOvladaceAlel(final JToolBar tb) {
		// super(BoxLayout.LINE_AXIS);
		this.tb = this;
	}

	/**
	 * @return
	 */
	public Set<String> getAlely() {
		return mapka.keySet();
	}

	public void inject(final Factory factory) {
		this.factory = factory;
	}

	@SuppressWarnings("deprecation")
	public void onEvent(final KeskyNactenyEvent event) {
		// TODO Nějak ty separátory a layoutování lépe řešit.
		removeAll();
		final Map<SwitchKesoidUrciteAlelyAction, JIconCheckBox> bezIkony = new LinkedHashMap<>();
		final KesBag vsechny = event.getVsechny();
		genom = vsechny.getGenom();
		ovladac(bezIkony, vsechny, genom.ALELA_hnf);
		ovladac(bezIkony, vsechny, genom.ALELA_fnd);
		ovladac(bezIkony, vsechny, genom.ALELA_own);
		ovladac(bezIkony, vsechny, genom.ALELA_dsbl);
		ovladac(bezIkony, vsechny, genom.ALELA_arch);
		ovladac(bezIkony, vsechny, genom.ALELA_cpt);
		ovladac(bezIkony, vsechny, genom.ALELA_dpl);
		// add(new JToolBar.Separator());
		add(new JSeparator(SwingConstants.VERTICAL));
		ovladac(bezIkony, vsechny, genom.ALELA_gc);
		ovladac(bezIkony, vsechny, genom.ALELA_mz);
		ovladac(bezIkony, vsechny, genom.ALELA_wm);
		ovladac(bezIkony, vsechny, genom.ALELA_gb);
		ovladac(bezIkony, vsechny, genom.ALELA_wp);
		add(new JSeparator(SwingConstants.VERTICAL));
		ovladac(bezIkony, vsechny, genom.ALELA_nevyluste);
		add(new JSeparator(SwingConstants.VERTICAL));
		if (genom.GRUPA_gc != null) {
			for (final Alela alela : genom.GRUPA_gc.getAlely()) {
				ovladac(bezIkony, vsechny, alela);
			}
		}
		// add(new JToolBar.Separator());
		add(new JSeparator(SwingConstants.VERTICAL));
		if (genom.GRUPA_gcawp != null) {
			for (final Alela alela : genom.GRUPA_gcawp.getAlely()) {
				ovladac(bezIkony, vsechny, alela);
			}
		}
		setVisible(true);
		nactiIkony(bezIkony);
	}

	/** Ikony alel se skládají z obrázků sady, při prvním startu to trvá i sekundy; ovladače mezitím drží místo prázdnou ikonou. */
	static void nactiIkony(final Map<SwitchKesoidUrciteAlelyAction, JIconCheckBox> akce) {
		if (akce.isEmpty()) {
			return;
		}
		new SwingWorker<Void, Object[]>() {
			@Override
			protected Void doInBackground() {
				for (final SwitchKesoidUrciteAlelyAction a : akce.keySet()) {
					publish(new Object[] { a, a.pripravIkonu() });
				}
				return null;
			}

			@Override
			protected void process(final List<Object[]> hotove) {
				for (final Object[] h : hotove) {
					if (h[1] != null) {
						final SwitchKesoidUrciteAlelyAction a = (SwitchKesoidUrciteAlelyAction) h[0];
						a.setIkona((Icon) h[1]);
						// Zaškrtávátko ikonu z akce samo nepřebírá.
						akce.get(a).setIcon((Icon) h[1]);
					}
				}
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					FExceptionDumper.dump(e.getCause(), EExceptionSeverity.WORKARROUND, "Ikony alel na toolbaru");
				}
			}
		}.execute();
	}

	/** Pevná výška, ať se toolbar a s ním mapa nemění podle toho, co je v datech a kdy se načtou. */
	@Override
	public Dimension getPreferredSize() {
		final Dimension d = super.getPreferredSize();
		d.height = vyskaOvladace();
		return d;
	}

	static JIconCheckBox novyOvladac() {
		final JIconCheckBox cb = new JIconCheckBox();
		// Název alely je v HTML; jako text tlačítka by se zbytečně sázel, stačí v tooltipu.
		cb.setHideActionText(true);
		cb.setFocusable(false);
		cb.setMaxVyskaIkony(VYSKA_IKONY);
		return cb;
	}

	static int vyskaOvladace() {
		final JIconCheckBox cb = new JIconCheckBox();
		cb.setIcon(new ImageIcon(new BufferedImage(1, VYSKA_IKONY, BufferedImage.TYPE_INT_ARGB)));
		return cb.getPreferredSize().height;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.Container#removeAll()
	 */
	@Override
	public void removeAll() {
		super.removeAll();
		mapka.clear();
	}

	private void ovladac(final Map<SwitchKesoidUrciteAlelyAction, JIconCheckBox> bezIkony, final KesBag vsechny, final Alela alela) {
		JIconCheckBox cb = mapka.get(alela.qualName());
		if (cb == null) {
			final SwitchKesoidUrciteAlelyAction action = factory.init(new SwitchKesoidUrciteAlelyAction(alela));
			cb = novyOvladac();
			action.join(cb);
			cb.setIcon(PRAZDNA_IKONA);
			bezIkony.put(action, cb);
			tb.add(cb);
			mapka.put(alela.qualName(), cb);
		}
		final boolean jetam = vsechny.getPouziteAlely().contains(alela);
		cb.setVisible(jetam);

	}

}
