package cz.geokuk.plugins.challenge;

import java.awt.*;
import java.util.List;

import javax.swing.*;

import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.util.gui.JPoleSNapovedou;
import cz.geokuk.plugins.challenge.Challenge.DruhStredu;
import cz.geokuk.plugins.challenge.Challenge.ZdrojPravidel;
import cz.geokuk.plugins.challenge.Challenge.ZdrojeNalezu;

/** Dialog plánovače challenge: seznam definic se zapnutím a formulář pro úpravu vlastní definice; šablony se jen prohlíží a kopírují. */
public class JChallengeDialog extends JMyDialog0 {

	private static final long serialVersionUID = 1L;

	private transient ChallengeModel model;
	private final DefaultListModel<Challenge> seznamModel = new DefaultListModel<>();
	private final JList<Challenge> seznam = new JList<Challenge>(seznamModel) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void processMouseEvent(final java.awt.event.MouseEvent e) {
			final Challenge c = challengeNaZnacce(e.getPoint());
			if (c == null) {
				super.processMouseEvent(e);
			} else if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
				prepniChallenge(c, !model.jeAktivni(c));
			}
		}

		@Override
		protected void processMouseMotionEvent(final java.awt.event.MouseEvent e) {
			setCursor(challengeNaZnacce(e.getPoint()) != null ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR) : java.awt.Cursor.getDefaultCursor());
			super.processMouseMotionEvent(e);
		}

		@Override
		public String getToolTipText(final java.awt.event.MouseEvent e) {
			final Challenge c = challengeNaZnacce(e.getPoint());
			return c == null ? super.getToolTipText(e) : model.jeAktivni(c) ? "Vypnout" : "Zapnout";
		}
	};
	private final JCheckBox aktivni = new JCheckBox("Aktivní challenge");
	/** Šířka formuláře vpravo od seznamu. */
	private static final int SIRKA_FORMULARE = 470;

	private int sirkaSeznamu;

	/** Šířka seznamu podle písma, aby se v něm vešel název každé šablony se značkou a příponou „(šablona)“; nejvýš 700 px. */
	static int sirkaSeznamu(final java.awt.FontMetrics fm) {
		int nejvic = 0;
		for (final Challenge c : Sablony.vse()) {
			nejvic = Math.max(nejvic, fm.stringWidth(PolozkaChallengeRenderer.ZAPNUTA + "  " + c.nazev() + "  (šablona)"));
		}
		return Math.max(290, Math.min(700, nejvic + 40));
	}

	final JLabel typ = new JLabel();
	private final JButton kopie = new JButton("Vytvořit kopii…");
	/** U šablony nad formulářem: proč nejde upravit a tlačítko kopie hned u toho. */
	final JPanel radaSablony = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final JButton kopieSablony = new JButton("Vytvořit kopii…");
	private final JButton smazat = new JButton("Smazat");
	private final JTextField nazev = new JTextField(24);
	private final JComboBox<String> pravidla = new JComboBox<>(new String[] { "Project-GC", "Stator" });
	private final JRadioButton stredDomov = new JRadioButton("Domov");
	private final JRadioButton stredSouradnice = new JRadioButton("Souřadnice");
	private final JRadioButton stredListing = new JRadioButton("Keš (kód)");
	private final JPoleSNapovedou souradnice = new JPoleSNapovedou(FormularDefinice.UKAZKA_SOURADNIC, 22);
	private final JTextField kod = new JTextField(9);
	private final JComboBox<FormularDefinice.Oblast> oblast = new JComboBox<>(FormularDefinice.oblasti().toArray(new FormularDefinice.Oblast[0]));
	private final JComboBox<String> nalezy = new JComboBox<>(new String[] { "Ze zapnutých zdrojů", "Ze všech zdrojů" });
	/** Kolikrát splnit každé políčko matice D/T. */
	final JSpinner opakovani = new JSpinner(new SpinnerNumberModel(1, 1, Challenge.NEJVETSI_OPAKOVANI, 1));
	final JLabel popisOpakovani = new JLabel();
	/** Pole „Kolikrát splnit“ jen u D/T. */
	private final java.util.List<Component> poleMatice = new java.util.ArrayList<>();
	private final JButton uloz = new JButton("Uložit změny");
	private final JLabel chyba = new JLabel(" ");
	private boolean plnim;
	/** Pole formuláře, která mají smysl jen u typu 360°; u D/T se skryjí, protože D/T se počítá z pevných pravidel podle středu mapy. */
	private final java.util.List<Component> poleSektoru = new java.util.ArrayList<>();

	public JChallengeDialog() {
		setTitle("Nastavení plánovače challenge");
		init();
		nastavVelikost(new Dimension(sirkaSeznamu + SIRKA_FORMULARE, 500), new Dimension(840, 440));
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return null;
	}

	public void inject(final ChallengeModel model) {
		this.model = model;
		obnovSeznam(model.zobrazena());
	}

	public void onEvent(final ChallengeZmenenyEvent event) {
		if (event.druh() == ChallengeZmenenyEvent.Druh.DEFINICE || event.druh() == ChallengeZmenenyEvent.Druh.AKTIVNI || event.druh() == ChallengeZmenenyEvent.Druh.NACTENO) {
			obnovSeznam(seznam.getSelectedValue());
		}
	}

	@Override
	protected void initComponents() {
		// Dolní panel s tlačítkem Zavřít přidal JMyDialog0 do SOUTH; obsah je proto ve vlastním panelu a rozvržení dialogu se nemění.
		final JPanel obsah = new JPanel(new BorderLayout(8, 8));
		obsah.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		add(obsah, BorderLayout.CENTER);
		seznam.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		seznam.setCellRenderer(new PolozkaChallengeRenderer(() -> model));
		seznam.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				naplnFormular(seznam.getSelectedValue());
			}
		});
		final JScrollPane levy = new JScrollPane(seznam);
		sirkaSeznamu = sirkaSeznamu(seznam.getFontMetrics(seznam.getFont()));
		levy.setPreferredSize(new Dimension(sirkaSeznamu, 300));
		final JPanel levyPanel = new JPanel(new BorderLayout(4, 4));
		levyPanel.add(levy, BorderLayout.CENTER);
		final JPanel levaTlacitka = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		levaTlacitka.add(kopie);
		levaTlacitka.add(smazat);
		levyPanel.add(levaTlacitka, BorderLayout.SOUTH);
		obsah.add(levyPanel, BorderLayout.WEST);

		final ButtonGroup skupina = new ButtonGroup();
		skupina.add(stredDomov);
		skupina.add(stredSouradnice);
		skupina.add(stredListing);
		final JPanel f = new JPanel(new GridBagLayout());
		int r = 0;
		final JPanel radekAktivni = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		radekAktivni.add(aktivni);
		radekAktivni.add(javax.swing.Box.createHorizontalStrut(16));
		radekAktivni.add(new JLabel("Typ:"));
		radekAktivni.add(typ);
		radek(f, r++, radekAktivni, null);
		radaSablony.add(new JLabel("Šablonu nelze upravit; vytvořte kopii."));
		radaSablony.add(kopieSablony);
		radek(f, r++, radaSablony, null);
		radek(f, r++, new JLabel("Název"), nazev);
		final JLabel popisekPravidel = new JLabel("Pravidla");
		pravidla.setToolTipText("<html>Project-GC: bez Lab a Locationless, jen nálezy zalogované na geocaching.com.<br>Stator: přesnější výpočet, včetně Lab keší, bez Locationless.</html>");
		final JLabel popisekStredu = new JLabel("Střed");
		final JPanel stredSouradnicePanel = panel(stredSouradnice, souradnice);
		final JPanel stredListingPanel = panel(stredListing, kod);
		final JLabel popisekOblasti = new JLabel("Oblast");
		radek(f, r++, popisekPravidel, pravidla);
		radek(f, r++, popisekStredu, stredDomov);
		radek(f, r++, null, stredSouradnicePanel);
		radek(f, r++, null, stredListingPanel);
		radek(f, r++, popisekOblasti, oblast);
		poleSektoru.addAll(java.util.Arrays.asList(popisekPravidel, pravidla, popisekStredu, stredDomov, stredSouradnicePanel, stredListingPanel, popisekOblasti, oblast));
		radek(f, r++, new JLabel("Nálezy"), nalezy);
		final JLabel popisekOpakovani = new JLabel("Kolikrát splnit");
		final JPanel opakovaniPanel = panel(opakovani, popisOpakovani);
		radek(f, r++, popisekOpakovani, opakovaniPanel);
		poleMatice.addAll(java.util.Arrays.asList(popisekOpakovani, opakovaniPanel));
		chyba.setForeground(new Color(180, 30, 30));
		radek(f, r++, null, chyba);
		radek(f, r++, null, uloz);
		final JPanel pravyObal = new JPanel(new BorderLayout());
		pravyObal.add(f, BorderLayout.NORTH);
		final JScrollPane pravy = new JScrollPane(pravyObal, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pravy.setBorder(BorderFactory.createEmptyBorder());
		pravy.getVerticalScrollBar().setUnitIncrement(16);
		obsah.add(pravy, BorderLayout.CENTER);

		aktivni.addActionListener(e -> prepniAktivni());
		kopie.addActionListener(e -> vytvorKopii());
		kopieSablony.addActionListener(e -> vytvorKopii());
		smazat.addActionListener(e -> smaz());
		uloz.addActionListener(e -> ulozZmeny());
		opakovani.addChangeListener(e -> nasobnostSablony());
		stredDomov.addActionListener(e -> povolStred());
		stredSouradnice.addActionListener(e -> povolStred());
		stredListing.addActionListener(e -> povolStred());
		prepniStredPriFokusu(souradnice, stredSouradnice);
		prepniStredPriFokusu(kod, stredListing);
		naplnFormular(null);
	}

	private static JPanel panel(final Component... c) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		for (final Component x : c) {
			p.add(x);
		}
		return p;
	}

	private static void radek(final JPanel f, final int r, final Component popisek, final Component pole) {
		final GridBagConstraints g = new GridBagConstraints();
		g.gridy = r;
		g.insets = new Insets(3, 4, 3, 4);
		g.anchor = GridBagConstraints.WEST;
		if (popisek != null && pole == null) {
			g.gridx = 0;
			g.gridwidth = 2;
			f.add(popisek, g);
			return;
		}
		if (popisek != null) {
			g.gridx = 0;
			f.add(popisek, g);
		}
		g.gridx = 1;
		g.fill = GridBagConstraints.HORIZONTAL;
		g.weightx = 1;
		f.add(pole, g);
	}

	private void obnovSeznam(final Challenge vybrana) {
		if (model == null) {
			return;
		}
		final String klic = vybrana == null ? null : vybrana.nazev();
		seznamModel.clear();
		Challenge znovu = null;
		for (final Challenge c : model.vsechny()) {
			seznamModel.addElement(c);
			if (c.nazev().equals(klic)) {
				znovu = c;
			}
		}
		if (znovu != null) {
			seznam.setSelectedValue(znovu, true);
		} else if (!seznamModel.isEmpty()) {
			seznam.setSelectedIndex(0);
		}
		seznam.repaint();
		naplnFormular(seznam.getSelectedValue());
	}

	/** Typ, který se zatím v rozhraní nenabízí (kalendář uložený z dřívějška): jde jen vypnout a smazat. */
	private boolean jeNedostupny(final Challenge c) {
		return c != null && model != null && !model.jeVUi(c);
	}

	/** Násobnost šablony matice D/T je nastavení uživatele a platí hned; šablona sama se nemění. */
	private void nasobnostSablony() {
		final Challenge c = seznam.getSelectedValue();
		if (plnim || c == null || model == null || !jeSablona(c) || c.typ() != Challenge.Typ.DT_MATICE) {
			return;
		}
		try {
			model.nastavOpakovani(c, (Integer) opakovani.getValue());
		} catch (final ChallengeChyba ex) {
			chyba.setText(ex.getMessage());
		}
	}

	/** Kolikrát se má splnit políčko podle šablony v kódu. */
	private static int nasobnostSablony(final Challenge c) {
		for (final Challenge s : Sablony.vse()) {
			if (s.nazev().equalsIgnoreCase(c.nazev())) {
				return s.opakovani();
			}
		}
		return 1;
	}

	private boolean jeSablona(final Challenge c) {
		return c != null && model != null && model.jeSablona(c);
	}

	/** Pole středu jsou použitelná, kdykoli jde definici upravovat; radio button se přepne až kliknutím nebo přechodem fokusu do pole. */
	private void povolStred() {
		final Challenge c = seznam.getSelectedValue();
		final boolean uprava = c != null && !jeSablona(c) && !jeNedostupny(c);
		souradnice.setEnabled(uprava);
		kod.setEnabled(uprava);
	}

	/** Fokus v poli (klikem nebo Tabem) vybere příslušnou volbu středu. */
	private void prepniStredPriFokusu(final JTextField pole, final JRadioButton volba) {
		pole.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(final java.awt.event.FocusEvent e) {
				if (!e.isTemporary()) {
					vyberStred(volba);
				}
			}
		});
	}

	void vyberStred(final JRadioButton volba) {
		if (!plnim && volba.isEnabled() && !volba.isSelected()) {
			volba.setSelected(true);
			povolStred();
		}
	}

	private void naplnFormular(final Challenge c) {
		plnim = true;
		try {
			final boolean uprava = c != null && !jeSablona(c) && !jeNedostupny(c);
			for (final JComponent k : new JComponent[] { nazev, pravidla, stredDomov, stredSouradnice, stredListing, oblast, nalezy, opakovani, uloz }) {
				k.setEnabled(uprava);
			}
			opakovani.setEnabled(uprava || c != null && jeSablona(c) && c.typ() == Challenge.Typ.DT_MATICE);
			aktivni.setEnabled(c != null && (!jeNedostupny(c) || model.jeAktivni(c)));
			kopie.setEnabled(c != null && !jeNedostupny(c));
			smazat.setEnabled(c != null && !jeSablona(c));
			radaSablony.setVisible(jeSablona(c));
			chyba.setText(" ");
			if (c == null) {
				aktivni.setSelected(false);
				povolStred();
				return;
			}
			for (final Component k : poleSektoru) {
				k.setVisible(c.typ() == Challenge.Typ.SEKTORY_360 || jeNedostupny(c));
			}
			for (final Component k : poleMatice) {
				k.setVisible(c.typ() == Challenge.Typ.DT_MATICE);
			}
			opakovani.setValue(c.opakovani());
			popisOpakovani.setText(jeSablona(c) ? "vaše nastavení, šablona má " + nasobnostSablony(c) + "×" : "každé políčko 1–" + Challenge.NEJVETSI_OPAKOVANI + "×");
			aktivni.setSelected(model != null && model.jeAktivni(c));
			nazev.setText(c.nazev());
			typ.setText(FormularDefinice.nazevTypu(c.typ()));
			pravidla.setSelectedIndex(c.zdroj() == ZdrojPravidel.PGC ? 0 : 1);
			stredDomov.setSelected(c.stred().druh() == DruhStredu.DOMOV);
			stredSouradnice.setSelected(c.stred().druh() == DruhStredu.SOURADNICE);
			stredListing.setSelected(c.stred().druh() == DruhStredu.LISTING);
			souradnice.setText(c.stred().druh() == DruhStredu.SOURADNICE ? FormularDefinice.formatSouradnic(c.stred().lat(), c.stred().lon()) : "");
			kod.setText(c.stred().druh() == DruhStredu.LISTING ? c.stred().kodListingu() : "");
			oblast.setSelectedItem(FormularDefinice.oblastDefinice(c));
			nalezy.setSelectedIndex(c.nalezy() == ZdrojeNalezu.ZAPNUTE ? 0 : 1);
			povolStred();
		} finally {
			plnim = false;
		}
	}

	private void prepniAktivni() {
		final Challenge c = seznam.getSelectedValue();
		if (plnim || c == null) {
			return;
		}
		prepniChallenge(c, aktivni.isSelected());
	}

	/** Challenge pod značkou ● nebo ○ na začátku řádku seznamu, jinak null. */
	private Challenge challengeNaZnacce(final java.awt.Point bod) {
		final int i = seznam.locationToIndex(bod);
		if (model == null || i < 0 || !seznam.getCellBounds(i, i).contains(bod)) {
			return null;
		}
		final java.awt.FontMetrics fm = seznam.getFontMetrics(seznam.getFont());
		return bod.x < fm.stringWidth(PolozkaChallengeRenderer.ZAPNUTA + "  ") + 6 ? seznamModel.getElementAt(i) : null;
	}

	/** Zapne nebo vypne challenge, stejně jako zaškrtávátko Aktivní challenge; zapnutí vypne dosud aktivní. */
	private void prepniChallenge(final Challenge c, final boolean zapnout) {
		try {
			if (zapnout) {
				if (jeNedostupny(c)) {
					return;
				}
				model.aktivuj(c);
			} else {
				model.deaktivuj(c);
			}
			chyba.setText(" ");
		} catch (final ChallengeChyba e) {
			chyba.setText(e.getMessage());
		}
		final Challenge vybrana = seznam.getSelectedValue();
		aktivni.setSelected(vybrana != null && model.jeAktivni(vybrana));
		seznam.repaint();
	}

	private void vytvorKopii() {
		final Challenge c = seznam.getSelectedValue();
		if (c != null && !jeNedostupny(c)) {
			try {
				obnovSeznam(jeSablona(c) ? model.kopieSablony(c) : model.kopieVlastni(c));
			} catch (final ChallengeChyba e) {
				chyba.setText(e.getMessage());
			}
		}
	}

	private void smaz() {
		final Challenge c = seznam.getSelectedValue();
		if (c == null || jeSablona(c)) {
			return;
		}
		if (JOptionPane.showConfirmDialog(this, "Smazat challenge „" + c.nazev() + "“?", "Smazat", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
			try {
				model.smazVlastni(c);
			} catch (final ChallengeChyba e) {
				chyba.setText(e.getMessage());
			}
		}
	}

	private void ulozZmeny() {
		final Challenge c = seznam.getSelectedValue();
		if (c == null || jeSablona(c) || jeNedostupny(c)) {
			return;
		}
		try {
			final DruhStredu druh = stredSouradnice.isSelected() ? DruhStredu.SOURADNICE : stredListing.isSelected() ? DruhStredu.LISTING : DruhStredu.DOMOV;
			final Challenge nova = FormularDefinice.sestav(c, nazev.getText(), pravidla.getSelectedIndex() == 0 ? ZdrojPravidel.PGC : ZdrojPravidel.STATOR, druh, souradnice.getText(), kod.getText(),
					(FormularDefinice.Oblast) oblast.getSelectedItem(), nalezy.getSelectedIndex() == 0 ? ZdrojeNalezu.ZAPNUTE : ZdrojeNalezu.VSECHNY,
					c.typ() == Challenge.Typ.DT_MATICE ? (Integer) opakovani.getValue() : 1);
			Challenge ulozena = c;
			if (!nova.nazev().trim().equals(c.nazev())) {
				ulozena = model.prejmenuj(c, nova.nazev());
			}
			ulozena = model.ulozVlastni(nova.sNazvem(ulozena.nazev()));
			chyba.setText(" ");
			obnovSeznam(ulozena);
		} catch (final ChallengeChyba e) {
			chyba.setText(e.getMessage());
		}
	}

	/** Seznam definic v dialogu, pro testy. */
	List<Challenge> polozky() {
		final List<Challenge> l = new java.util.ArrayList<>();
		for (int i = 0; i < seznamModel.size(); i++) {
			l.add(seznamModel.get(i));
		}
		return l;
	}
}
