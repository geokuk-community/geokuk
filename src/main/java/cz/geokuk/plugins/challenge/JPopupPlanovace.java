package cz.geokuk.plugins.challenge;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import cz.geokuk.framework.AfterInjectInit;
import cz.geokuk.framework.JSlide0;
import cz.geokuk.framework.RozmisteniPrekryvu;

/**
 * Popup plánovače challenge pod odznakem pruhu: typ a pravidla, shrnutí, chybějící sektory od nejbližšího kandidáta, další chybějící a filtr „jen kandidáti“. Leží ve vrstvě mapy a
 * myš zachytává jen ve svém obdélníku; zavře se klávesou Esc, kliknutím mimo (klik se přitom neztratí) nebo znovu kliknutím na odznak.
 */
public class JPopupPlanovace extends JSlide0 implements AfterInjectInit {

	private static final long serialVersionUID = 1L;

	/** Mezera mezi spodním okrajem pruhu a popupem. */
	static final int POD_PRUHEM = 2;
	/** O kolik popup přesahuje doleva před odznak. */
	static final int PRED_ODZNAKEM = 20;
	static final int RADKU_SEZNAMU = 7;

	final transient Obsah obsah = new Obsah();
	private transient ChallengeModel model;
	private transient PlanovacChallengeSluzba sluzba;
	private transient Runnable naNastaveni;
	private boolean otevreno;
	private transient AWTEventListener mimo;

	public JPopupPlanovace() {
		setOpaque(false);
		setLayout(null);
		add(obsah);
		setVisible(false);
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "zavriPopupPlanovace");
		getActionMap().put("zavriPopupPlanovace", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isEnabled() {
				return otevreno;
			}

			@Override
			public void actionPerformed(final ActionEvent e) {
				zavri();
			}
		});
		obsah.dalsi.addActionListener(e -> dalsiChybejici());
		obsah.kandidati.addActionListener(e -> prepniKandidaty());
		obsah.nastaveni.addActionListener(e -> {
			if (naNastaveni != null) {
				zavri();
				naNastaveni.run();
			}
		});
	}

	public void inject(final ChallengeModel model) {
		this.model = model;
	}

	public void inject(final PlanovacChallengeSluzba sluzba) {
		this.sluzba = sluzba;
	}

	/** Co udělá tlačítko Nastavení…; bez něj je tlačítko nedostupné. */
	public void setNaNastaveni(final Runnable naNastaveni) {
		this.naNastaveni = naNastaveni;
		obsah.nastaveni.setEnabled(naNastaveni != null);
	}

	@Override
	public void initAfterInject() {
		obsah.nastaveni.setEnabled(naNastaveni != null);
	}

	public void onEvent(final ChallengeZmenenyEvent event) {
		if (!otevreno) {
			return;
		}
		if (model == null || !model.jePruhViditelny()) {
			zavri();
		} else {
			obnov();
		}
	}

	public boolean jeOtevreno() {
		return otevreno;
	}

	/** Otevře popup, nebo ho zavře, když je otevřený (klik na odznak). */
	public void prepni() {
		if (otevreno) {
			zavri();
		} else {
			otevri();
		}
	}

	void otevri() {
		if (model == null || !model.jePruhViditelny()) {
			return;
		}
		otevreno = true;
		obnov();
		setVisible(true);
		if (mimo == null) {
			mimo = this::mysKdekoli;
			try {
				Toolkit.getDefaultToolkit().addAWTEventListener(mimo, AWTEvent.MOUSE_EVENT_MASK);
			} catch (final SecurityException e) {
				mimo = null;
			}
		}
		revalidate();
		repaint();
	}

	void zavri() {
		otevreno = false;
		if (mimo != null) {
			Toolkit.getDefaultToolkit().removeAWTEventListener(mimo);
			mimo = null;
		}
		setVisible(false);
		repaint();
	}

	/** Stisk myši mimo popup ho zavře; událost dál normálně pokračuje. Stisk na odznaku nechá na odznaku, ten popup přepíná sám. */
	void mysKdekoli(final AWTEvent e) {
		if (e.getID() != MouseEvent.MOUSE_PRESSED || !otevreno || !(e instanceof MouseEvent)) {
			return;
		}
		final MouseEvent me = (MouseEvent) e;
		final Component zdroj = me.getComponent();
		if (zdroj == null || SwingUtilities.isDescendingFrom(zdroj, obsah)) {
			return;
		}
		final Point p = SwingUtilities.convertPoint(zdroj, me.getPoint(), this);
		if (oblastOdznaku(getWidth()).contains(p)) {
			return;
		}
		zavri();
	}

	@Override
	public boolean contains(final int x, final int y) {
		return otevreno && obsah.getBounds().contains(x, y);
	}

	@Override
	public void doLayout() {
		obsah.setBounds(poloha(getSize(), obsah.getPreferredSize()));
	}

	/** Odznak pruhu v souřadnicích mapy. */
	static Rectangle oblastOdznaku(final int sirkaMapy) {
		final Rectangle pruh = RozmisteniPrekryvu.pruhPlanovace(RozmisteniPrekryvu.volnaSirkaPruhu(sirkaMapy));
		return JPruhPlanovace.rozloz(pruh.width)[JPruhPlanovace.ODZNAK];
	}

	/**
	 * Obdélník popupu v souřadnicích mapy: pod pruhem, levým okrajem kousek před odznakem. Celý leží v mapě s odstupem {@link RozmisteniPrekryvu#OKRAJ}; když se nevejde do šířky, posune se
	 * doleva a pak zúží, když se nevejde do výšky, zkrátí se (seznam se posouvá).
	 */
	static Rectangle poloha(final Dimension mapa, final Dimension pozadovana) {
		final int okraj = RozmisteniPrekryvu.OKRAJ;
		final Rectangle pruh = RozmisteniPrekryvu.pruhPlanovace(RozmisteniPrekryvu.volnaSirkaPruhu(mapa.width));
		final Rectangle odznak = oblastOdznaku(mapa.width);
		final int y = pruh.y + pruh.height + POD_PRUHEM;
		final int w = Math.max(0, Math.min(pozadovana.width, mapa.width - 2 * okraj));
		final int x = Math.max(okraj, Math.min(odznak.x - PRED_ODZNAKEM, mapa.width - okraj - w));
		final int h = Math.max(0, Math.min(pozadovana.height, mapa.height - y - okraj));
		return new Rectangle(x, y, w, h);
	}

	private Challenge challenge() {
		final List<Challenge> aktivni = model == null ? java.util.Collections.<Challenge> emptyList() : model.aktivni();
		return aktivni.isEmpty() ? null : aktivni.get(0);
	}

	void obnov() {
		final Challenge c = challenge();
		if (c != null) {
			obsah.nastav(c, model.vysledek(c), model.stavPredpoctu(c), c.equals(model.jenKandidatiPro()));
		}
		revalidate();
		repaint();
	}

	private void dalsiChybejici() {
		final Challenge c = challenge();
		if (c != null && sluzba != null && sluzba.planovac() != null) {
			sluzba.planovac().dalsiChybejici(c);
		}
	}

	private void prepniKandidaty() {
		final Challenge c = challenge();
		if (c == null) {
			return;
		}
		try {
			model.setJenKandidatiPro(obsah.kandidati.isSelected() ? c : null);
		} catch (final ChallengeChyba e) {
			obsah.kandidati.setSelected(false);
		}
	}

	/** Vlastní obsah popupu; barvy a písmo z aktuálního vzhledu. */
	static final class Obsah extends JPanel {
		private static final long serialVersionUID = 1L;

		final JLabel nadpis = new JLabel();
		final JButton nastaveni = new JButton("Nastavení…");
		final JLabel typ = new JLabel();
		final JLabel stred = new JLabel();
		final JLabel souhrn = new JLabel();
		final JProgressBar postup = new JProgressBar(0, 360);
		final DefaultListModel<String> radky = new DefaultListModel<>();
		final JList<String> seznam = new JList<>(radky);
		final JLabel nejiste = new JLabel();
		final JButton dalsi = new JButton("▶ Další chybějící  F4");
		final JToggleButton kandidati = new JToggleButton("Zobrazit jen kandidáty");

		Obsah() {
			super(new GridBagLayout());
			setOpaque(true);
			nadpis.setFont(nadpis.getFont().deriveFont(Font.BOLD));
			postup.setStringPainted(true);
			seznam.setVisibleRowCount(RADKU_SEZNAMU);
			seznam.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			seznam.setPrototypeCellValue("359°  99 kandidátů  nejbližší 99,9 km");
			for (final AbstractButton b : new AbstractButton[] { nastaveni, dalsi, kandidati }) {
				b.setFocusable(false);
			}
			final GridBagConstraints g = new GridBagConstraints();
			g.gridx = 0;
			g.gridy = 0;
			g.anchor = GridBagConstraints.WEST;
			g.insets = new Insets(0, 0, 4, 8);
			g.weightx = 1;
			add(nadpis, g);
			g.gridx = 1;
			g.weightx = 0;
			g.insets = new Insets(0, 0, 4, 0);
			add(nastaveni, g);
			g.gridx = 0;
			g.gridwidth = 2;
			g.fill = GridBagConstraints.HORIZONTAL;
			g.insets = new Insets(0, 0, 4, 0);
			for (final JComponent c : new JComponent[] { typ, stred, souhrn, postup }) {
				g.gridy++;
				add(c, g);
			}
			g.gridy++;
			add(new JLabel("Chybějící sektory (podle vzdálenosti nejbližšího kandidáta):"), g);
			g.gridy++;
			g.fill = GridBagConstraints.BOTH;
			g.weighty = 1;
			add(new JScrollPane(seznam), g);
			g.weighty = 0;
			g.fill = GridBagConstraints.HORIZONTAL;
			g.gridy++;
			add(nejiste, g);
			final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			tlacitka.setOpaque(false);
			tlacitka.add(dalsi);
			tlacitka.add(Box.createHorizontalStrut(8));
			tlacitka.add(kandidati);
			g.gridy++;
			g.insets = new Insets(4, 0, 0, 0);
			add(tlacitka, g);
			obnovVzhled();
		}

		@Override
		public void updateUI() {
			super.updateUI();
			obnovVzhled();
		}

		/** Rámeček a barva upozornění podle pozadí vzhledu (světlý i tmavý skin). */
		private void obnovVzhled() {
			final Color pozadi = UIManager.getColor("Panel.background");
			final Color hrana = UIManager.getColor("Separator.foreground");
			setBorder(new CompoundBorder(new LineBorder(hrana == null ? Color.GRAY : hrana), new EmptyBorder(8, 10, 8, 10)));
			if (nejiste != null) {
				nejiste.setForeground(jeTmave(pozadi) ? new Color(0xFFD080) : new Color(0x9A5A00));
			}
		}

		static boolean jeTmave(final Color c) {
			return c != null && c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114 < 128_000;
		}

		void nastav(final Challenge c, final VysledekSektoru v, final StavPredpoctu stav, final boolean jenKandidati) {
			nadpis.setText(TextyPopupuPlanovace.nadpis(c));
			typ.setText("Typ: 360°    Pravidla: " + TextyPopupuPlanovace.pravidla(c.zdroj()));
			stred.setText("Střed: " + TextyPopupuPlanovace.stred(c.stred()));
			kandidati.setSelected(jenKandidati);
			radky.clear();
			if (v == null) {
				souhrn.setText(stav.faze() == StavPredpoctu.Faze.POCITA && stav.celkem() > 0 ? "Počítá se… " + stav.hotovo() + " z " + stav.celkem() + " keší" : "Počítá se…");
				postup.setValue(0);
				postup.setString("…");
				nejiste.setText(" ");
				dalsi.setEnabled(false);
				return;
			}
			souhrn.setText(TextyPopupuPlanovace.souhrn(v));
			postup.setValue(v.pocetObsazenych());
			postup.setString(TextyPopupuPlanovace.procenta(v));
			for (final int s : TextyPopupuPlanovace.serazeneChybejici(v)) {
				radky.addElement(TextyPopupuPlanovace.radekSektoru(s, v.kandidati(s)));
			}
			final String n = TextyPopupuPlanovace.nejiste(v);
			nejiste.setText(n.isEmpty() ? " " : n);
			dalsi.setEnabled(!v.chybejici().isEmpty());
		}
	}
}
