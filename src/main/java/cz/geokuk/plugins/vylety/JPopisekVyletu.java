package cz.geokuk.plugins.vylety;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;

import cz.geokuk.util.gui.PopupNadBlokem;

/** Popisek „Výlet:“ se jménem aktivního výletu ve stavovém řádku; najetí myší nebo klik otevře popup s výlety. */
public class JPopisekVyletu extends JLabel {

	private static final long serialVersionUID = 1L;

	/** Delší jméno se zkrátí, celé je v tooltipu. */
	static final int NEJDELSI_JMENO = 20;

	private final JVyletyPopup obsah = new JVyletyPopup();
	private final PopupNadBlokem popup = new PopupNadBlokem(this, () -> {});
	private VyletModel vyletModel;

	public JPopisekVyletu() {
		super("Výlet:");
		popup.setDrzOtevreny(obsah::isRozpracovany);
		obsah.setPoZmeneObsahu(() -> {
			if (popup.viditelny() == obsah) {
				popup.ukaz(obsah, this, true);
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				popup.naCil(obsah, JPopisekVyletu.this::ukaz, System.currentTimeMillis());
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				popup.opustenCil(obsah);
			}

			@Override
			public void mouseClicked(final MouseEvent e) {
				ukaz();
			}
		});
	}

	public void inject(final VyletModel vyletModel) {
		this.vyletModel = vyletModel;
		obsah.setVyletModel(vyletModel);
	}

	/** Načtený nebo vyprázdněný výlet (ne změna jedné keše): po startu tak přijde jméno. */
	public void onEvent(final VyletChangeEvent event) {
		if (event.getEvyl() == null) {
			obnovJmeno();
		}
	}

	public void onEvent(final VyletyZmenenyEvent event) {
		obnovJmeno();
		if (popup.viditelny() == obsah && !obsah.isRozpracovany()) {
			obsah.obnov();
		}
	}

	/** Tooltip by překryl sousední bloky a jméno je v popupu celé. */
	@Override
	public String getToolTipText(final MouseEvent e) {
		return popup.otevira(obsah) ? null : super.getToolTipText(e);
	}

	/** Otevře popup s aktuálním seznamem výletů. */
	public void ukaz() {
		if (popup.viditelny() == obsah) {
			return;
		}
		obsah.obnov();
		popup.ukaz(obsah, this, false);
	}

	public void zavri() {
		popup.zavri();
	}

	JVyletyPopup getObsah() {
		return obsah;
	}

	PopupNadBlokem getPopup() {
		return popup;
	}

	void obnovJmeno() {
		if (vyletModel == null) {
			return;
		}
		for (final VyletInfo v : vyletModel.getVylety()) {
			if (v.isAktivni()) {
				setText("Výlet: " + zkrat(v.getJmeno()));
				setToolTipText("Aktivní výlet „" + v.getJmeno() + "“");
				return;
			}
		}
	}

	static String zkrat(final String jmeno) {
		return jmeno.length() <= NEJDELSI_JMENO ? jmeno : jmeno.substring(0, NEJDELSI_JMENO - 1).trim() + "…";
	}

	@Override
	public void removeNotify() {
		popup.zastav();
		super.removeNotify();
	}
}
