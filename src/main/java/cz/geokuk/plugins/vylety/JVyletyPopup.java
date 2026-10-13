package cz.geokuk.plugins.vylety;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Obsah popupu výletů ve stavovém řádku: nahoře Nový výlet, pod ním řádek na každý výlet (aktivovat, přejmenovat, smazat). Během přepínání výletu jsou
 * všechna tlačítka zašedlá.
 */
public class JVyletyPopup extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String TEXT_SOUBORY = "Aktivní výlet je v souborech lovim.ggt a tedne.ggt.";
	static final String TEXT_PREPINANI = "Přepínám výlet, úpravy výletu počkají.";
	static final String TEXT_VYCHOZI_NESMAZAT = "Výchozí výlet nejde smazat.";
	/** Upravovaný řádek nového výletu. */
	private static final String NOVY = "";

	private VyletModel vyletModel;
	private List<VyletInfo> vylety = Collections.emptyList();
	/** Id výletu, jehož jméno se upravuje, {@link #NOVY} u nového, null když se nic neupravuje. */
	private String upravovany;
	private final JTextField pole = new JTextField(14);
	private final JLabel chybaJmena = maly("");
	private JButton potvrdit;
	/** Výlet, na který se přepíná, null když se nepřepíná. */
	private String prepinany;
	/** Operace s výlety běží (budoucnost není dokončená). */
	private boolean operace;
	/** Potvrzovací nebo chybový dialog je otevřený, popup se nemá zavřít. */
	private boolean dialog;
	private Runnable poZmeneObsahu = () -> {};

	public JVyletyPopup() {
		super(new GridBagLayout());
		setBorder(new CompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(6, 8, 6, 8)));
		chybaJmena.setForeground(new Color(180, 0, 0));
		pole.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent e) {
				zkontrolujJmeno();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				zkontrolujJmeno();
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				zkontrolujJmeno();
			}
		});
		pole.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "potvrdit");
		pole.getActionMap().put("potvrdit", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				if (potvrdit != null && potvrdit.isEnabled()) {
					potvrdit.doClick(0);
				}
			}
		});
		pole.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "zrusit");
		pole.getActionMap().put("zrusit", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				zrusUpravu();
			}
		});
	}

	public void setVyletModel(final VyletModel vyletModel) {
		this.vyletModel = vyletModel;
	}

	/** Zavolá se, když se obsah přestaví (popup se má zobrazit znovu v nové velikosti). */
	public void setPoZmeneObsahu(final Runnable poZmeneObsahu) {
		this.poZmeneObsahu = poZmeneObsahu;
	}

	/** Upravuje se jméno nebo je otevřený dialog; popup se odjetím myši nezavře a změny seznamu ho nepřestaví. */
	public boolean isRozpracovany() {
		return upravovany != null || dialog;
	}

	/** Přepíná se výlet: úpravy výletu a operace s výlety čekají. */
	boolean isPrepina() {
		return operace || vyletModel != null && vyletModel.isPrepina();
	}

	/** Načte seznam výletů a přestaví obsah. */
	public void obnov() {
		if (vyletModel == null) {
			return;
		}
		if (!isPrepina()) {
			prepinany = null;
		}
		vylety = vyletModel.getVylety();
		postav();
	}

	List<VyletInfo> getVylety() {
		return vylety;
	}

	private void postav() {
		removeAll();
		final boolean prepina = isPrepina();
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 3, 2, 3);
		c.anchor = GridBagConstraints.WEST;
		int radek = 0;

		final JButton novy = tlacitko("Nový výlet…", e -> zacniUpravu(NOVY, prvniVolneJmeno()));
		novy.setToolTipText("Založí prázdný výlet a přepne na něj.");
		novy.setEnabled(!prepina && upravovany == null);
		c.gridx = 0;
		c.gridy = radek++;
		c.gridwidth = 6;
		add(novy, c);
		c.gridwidth = 1;
		if (NOVY.equals(upravovany)) {
			c.gridy = radek++;
			c.gridx = 1;
			c.gridwidth = 3;
			add(editor(), c);
			c.gridx = 4;
			c.gridwidth = 2;
			add(tlacitkaUpravy("Založit"), c);
			c.gridwidth = 1;
		}

		c.gridy = radek++;
		c.gridx = 1;
		add(maly("Výlet"), c);
		c.anchor = GridBagConstraints.EAST;
		c.gridx = 2;
		add(maly("lovím"), c);
		c.gridx = 3;
		add(maly("nejdu"), c);
		c.anchor = GridBagConstraints.WEST;

		for (final VyletInfo v : vylety) {
			c.gridy = radek++;
			c.gridx = 0;
			final JLabel znacka = new JLabel(v.isAktivni() ? "●" : "○");
			znacka.setForeground(v.isAktivni() ? new Color(0, 120, 0) : Color.GRAY);
			add(znacka, c);
			c.gridx = 1;
			final boolean upravuje = v.getId().equals(upravovany);
			if (upravuje) {
				add(editor(), c);
			} else {
				final JLabel jmeno = new JLabel(v.getJmeno());
				if (v.isAktivni()) {
					jmeno.setFont(jmeno.getFont().deriveFont(Font.BOLD));
				}
				add(jmeno, c);
			}
			c.anchor = GridBagConstraints.EAST;
			c.gridx = 2;
			add(new JLabel(String.valueOf(v.getPocetLovim())), c);
			c.gridx = 3;
			add(new JLabel(String.valueOf(v.getPocetTedne())), c);
			c.anchor = GridBagConstraints.WEST;
			c.gridx = 4;
			if (v.getId().equals(prepinany)) {
				add(maly("Přepínám…"), c);
			} else if (v.isAktivni()) {
				add(maly("aktivní"), c);
			} else {
				final JButton aktivovat = tlacitko("Aktivovat", e -> aktivuj(v));
				aktivovat.setEnabled(!prepina && upravovany == null);
				add(aktivovat, c);
			}
			c.gridx = 5;
			if (upravuje) {
				add(tlacitkaUpravy("Uložit"), c);
			} else {
				final JPanel dalsi = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
				dalsi.setOpaque(false);
				final JButton prejmenovat = tlacitko("Přejmenovat", e -> zacniUpravu(v.getId(), v.getJmeno()));
				prejmenovat.setEnabled(!prepina && upravovany == null);
				final JButton smazat = tlacitko("Smazat", e -> smaz(v));
				smazat.setEnabled(!prepina && upravovany == null && !jeVychozi(v));
				if (jeVychozi(v)) {
					smazat.setToolTipText(TEXT_VYCHOZI_NESMAZAT);
				}
				dalsi.add(prejmenovat);
				dalsi.add(smazat);
				add(dalsi, c);
			}
		}
		c.gridx = 0;
		c.gridy = radek;
		c.gridwidth = 6;
		c.insets = new Insets(6, 3, 0, 3);
		add(maly(prepina ? TEXT_PREPINANI : TEXT_SOUBORY), c);
		revalidate();
		repaint();
		poZmeneObsahu.run();
		if (upravovany != null) {
			SwingUtilities.invokeLater(pole::requestFocusInWindow);
		}
	}

	static boolean jeVychozi(final VyletInfo v) {
		return VyletyUlozisko.VYCHOZI_ID.equals(v.getId());
	}

	private JComponent editor() {
		final JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(pole, BorderLayout.CENTER);
		p.add(chybaJmena, BorderLayout.SOUTH);
		return p;
	}

	private JComponent tlacitkaUpravy(final String textPotvrzeni) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		potvrdit = tlacitko(textPotvrzeni, e -> potvrdUpravu());
		p.add(potvrdit);
		p.add(tlacitko("Zrušit", e -> zrusUpravu()));
		zkontrolujJmeno();
		return p;
	}

	private void zacniUpravu(final String id, final String jmeno) {
		upravovany = id;
		pole.setText(jmeno);
		pole.selectAll();
		postav();
	}

	void zrusUpravu() {
		upravovany = null;
		potvrdit = null;
		postav();
	}

	/** Text chyby jména v poli, null když je jméno v pořádku. */
	String chybaJmena() {
		return vyletModel.validujJmeno(pole.getText(), NOVY.equals(upravovany) ? null : upravovany);
	}

	private void zkontrolujJmeno() {
		if (upravovany == null || vyletModel == null) {
			return;
		}
		final String chyba = chybaJmena();
		chybaJmena.setText(chyba == null ? " " : chyba);
		if (potvrdit != null) {
			potvrdit.setEnabled(chyba == null && !isPrepina());
		}
	}

	private void potvrdUpravu() {
		if (chybaJmena() != null) {
			return;
		}
		final String id = upravovany;
		final String jmeno = pole.getText().trim();
		upravovany = null;
		potvrdit = null;
		if (NOVY.equals(id)) {
			spust(vyletModel.novy(jmeno, true));
		} else {
			spust(vyletModel.prejmenovat(id, jmeno));
		}
	}

	private void aktivuj(final VyletInfo v) {
		prepinany = v.getId();
		spust(vyletModel.aktivovat(v.getId()));
	}

	private void smaz(final VyletInfo v) {
		if (v.getPocetLovim() > 0 || v.getPocetTedne() > 0 || v.isAktivni()) {
			final Object[] volby = { "Smazat", "Zrušit" };
			dialog = true;
			final int volba;
			try {
				volba = JOptionPane.showOptionDialog(this, textSmazani(v), "Smazat výlet", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, volby, volby[1]);
			} finally {
				dialog = false;
			}
			if (volba != 0) {
				return;
			}
		}
		if (v.isAktivni()) {
			prepinany = VyletyUlozisko.VYCHOZI_ID;
		}
		spust(vyletModel.smazat(v.getId()));
	}

	static String textSmazani(final VyletInfo v) {
		final String text = "Smazat výlet „" + v.getJmeno() + "“ (" + v.getPocetLovim() + " keší k lovení, " + v.getPocetTedne() + " vynechaných)?";
		return v.isAktivni() ? text + "\nAktivním výletem se stane Výchozí." : text;
	}

	private void spust(final CompletableFuture<?> budoucnost) {
		operace = true;
		postav();
		budoucnost.whenComplete((v, e) -> {
			operace = false;
			if (e != null) {
				prepinany = null;
				final Throwable pricina = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
				dialog = true;
				try {
					JOptionPane.showMessageDialog(this, pricina.getMessage() != null ? pricina.getMessage() : pricina.toString(), "Výlety", JOptionPane.ERROR_MESSAGE);
				} finally {
					dialog = false;
				}
			}
			obnov();
		});
	}

	/** První jméno „Výlet N“, které ještě není použité. */
	String prvniVolneJmeno() {
		for (int n = vylety.size() + 1; n < vylety.size() + 1000; n++) {
			final String jmeno = "Výlet " + n;
			if (vyletModel.validujJmeno(jmeno, null) == null) {
				return jmeno;
			}
		}
		return "";
	}

	private static JLabel maly(final String text) {
		final JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
		l.setForeground(new Color(80, 80, 80));
		return l;
	}

	private static JButton tlacitko(final String text, final java.awt.event.ActionListener akce) {
		final JButton b = new JButton(text);
		b.setMargin(new Insets(1, 4, 1, 4));
		b.setFont(b.getFont().deriveFont(11f));
		b.addActionListener(akce);
		return b;
	}
}
