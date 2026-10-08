package cz.geokuk.core.program;


import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.*;
import java.util.function.Function;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.geokuk.core.program.JPrehledSouboru.YNejdeTo;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.util.file.Filex;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JJedenSouborPanel extends JPanel implements DocumentListener {



	private static final long serialVersionUID = -3579395922979423765L;

	private final boolean jenAdresare;

	private final String label;
	private JTextField jtext;
	private JCheckBox jActive;
	private JTextField jCurrVal;

	private Filex filex;

	private final boolean editovatelne;

	private final boolean lzeDeaktivovat;

	private final ESouborPanelName souborPanelName;

	/** Datovou složku jiného programu nezakládat, prázdná by jen skryla překlep v cestě. */
	private boolean zakladat = true;

	/**
	 * @param souborPanelName
	 *            identifikátor panelu. Je jen proto, aby se podle něj mohl panel najít.
	 */
	public JJedenSouborPanel(final ESouborPanelName souborPanelName, final String label, final boolean jenAdresare, final boolean editovatelne, final boolean lzeDeaktivovat) {
		this.souborPanelName = souborPanelName;
		this.label = label;
		this.jenAdresare = jenAdresare;
		this.editovatelne = editovatelne;
		this.lzeDeaktivovat = lzeDeaktivovat;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		initComponents();
	}

	/** Neexistující složku při uložení nehlásit založením, ale chybou. */
	public JJedenSouborPanel nezakladat() {
		zakladat = false;
		return this;
	}

	public void fokusniSe() {
		Container panel = null;
		for (Container comp = this; comp != null; comp = comp.getParent()) {
			if (comp instanceof JTabbedPane) {
				final JTabbedPane tabpane = (JTabbedPane) comp;
				tabpane.setSelectedComponent(panel);
				break;
			}
			panel = comp;
		}
		jtext.requestFocus();
	}

	/**
	 * @return the souborPanelName
	 */
	public ESouborPanelName getSouborPanelName() {
		return souborPanelName;
	}

	@Override
	public void changedUpdate(final DocumentEvent e) {
		zmemniliNamTo();
	}

	@Override
	public void insertUpdate(final DocumentEvent e) {
		zmemniliNamTo();
	}

	@Override
	public void removeUpdate(final DocumentEvent e) {
		zmemniliNamTo();
	}

	public void setFilex(final Filex filex) {
		jtext.setText(MyPreferences.cestaDoNastaveni(filex.getEffectiveFile(), FConst.KOREN));
		jActive.setSelected(filex.isActive() || !lzeDeaktivovat);
		prepocitej();
	}

	public Filex vezmiSouborAProver() throws YNejdeTo {
		final Filex f = vezmiSoubor();
		prover(f);
		return f;
	}

	/** Zadaná cesta, jak se uloží; čte jen pole dialogu, na disk nesahá (EDT). */
	Filex vezmiSoubor() {
		prepocitej();
		return filex;
	}

	/** Prověří, případně založí složku; sahá na disk, volá se mimo EDT. */
	void prover(final Filex f) throws YNejdeTo {
		File dir = f.getEffectiveFile();
		log.debug("Prověřuji soubor: " + dir);
		if (!jenAdresare) {
			dir = dir.getParentFile();
		}
		// Neaktivní složku (třeba GSAK u toho, kdo ho nemá) zakládat nemá smysl.
		if (!f.isActive()) {
			return;
		}
		final File slozka = dir;
		final StavSlozky stav = sLimitem(() -> kontrola.apply(slozka), slozka);
		if (stav == StavSlozky.CITELNA) {
			return;
		}
		if (!zakladat) {
			final String co = label.endsWith(".") ? label.substring(0, label.length() - 1) : label;
			throw new JPrehledSouboru.YNejdeTo(co + ": \"" + dir + "\" " + (stav == StavSlozky.JINA ? "není čitelná složka." : "neexistuje nebo není dostupná.")
					+ " Opravte cestu, nebo zrušte \"Aktivní\".");
		}
		if (!sLimitem(slozka::mkdirs, slozka)) {
			throw new JPrehledSouboru.YNejdeTo("Složku \"" + dir + "\" se nepodařilo vytvořit pro \"" + label + "\"");
		}
	}

	enum StavSlozky {
		CITELNA, NEEXISTUJE, JINA
	}

	/** Na neodpovídajícím síťovém disku blokuje každý dotaz na soubor desítky sekund, proto mimo EDT a s limitem. */
	static Function<File, StavSlozky> kontrola = d -> d.isDirectory() && d.canRead() ? StavSlozky.CITELNA : d.exists() ? StavSlozky.JINA : StavSlozky.NEEXISTUJE;
	static long limitMs = 3000;
	private static final ExecutorService KONTROLY = Executors.newCachedThreadPool(r -> {
		final Thread t = new Thread(r, "Kontrola složky");
		t.setDaemon(true);
		return t;
	});

	private static <T> T sLimitem(final Callable<T> dotaz, final File slozka) throws YNejdeTo {
		final Future<T> vysledek = KONTROLY.submit(dotaz);
		try {
			return vysledek.get(limitMs, TimeUnit.MILLISECONDS);
		} catch (final TimeoutException e) {
			vysledek.cancel(true);
			throw new JPrehledSouboru.YNejdeTo("Složka \"" + slozka + "\" neodpovídá (odpojený síťový disk?). Zkuste to znovu, nebo zrušte \"Aktivní\".");
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JPrehledSouboru.YNejdeTo("Kontrola složky \"" + slozka + "\" byla přerušena.");
		} catch (final ExecutionException e) {
			log.warn("Prověření složky " + slozka, e.getCause());
			final String duvod = e.getCause() == null ? null : e.getCause().getMessage();
			throw new JPrehledSouboru.YNejdeTo("Složku \"" + slozka + "\" nejde prověřit" + (duvod == null || duvod.isEmpty() ? "." : ": " + duvod));
		}
	}

	private void initComponents() {
		final TitledBorder border = BorderFactory.createTitledBorder(label);
		final Font titleFont = border.getTitleFont();
		if (titleFont != null) {
			border.setTitleFont(titleFont.deriveFont(Font.BOLD));
		}
		setBorder(border);

		jtext = new JTextField();
		// jtext.setText(defalt.getFile().getPath());
		jActive = new JCheckBox("Aktivní");
		jActive.setEnabled(lzeDeaktivovat);
		jCurrVal = new JTextField();
		jCurrVal.setForeground(Color.BLUE);
		jCurrVal.setEditable(false);
		jCurrVal.setBorder(null);
		final JButton jbut = new JButton("...");
		// jtext.setText(defalt.getFile().getPath());
		jtext.setColumns(50);
		if (editovatelne) {
			final Box box2 = Box.createHorizontalBox();
			box2.setAlignmentX(LEFT_ALIGNMENT);
			box2.add(jtext);
			box2.add(jbut);
			final Box panel3 = Box.createHorizontalBox();
			if (jActive.isEnabled()) {
				panel3.add(jActive);
			}
			if (panel3.getComponentCount() > 0) {
				add(panel3);
			}
			panel3.setAlignmentX(LEFT_ALIGNMENT);
			add(box2);
		}
		// panel.add(Box.createVerticalStrut(20));
		jCurrVal.setAlignmentX(LEFT_ALIGNMENT);
		add(jCurrVal);
		// add(panel);
		// add(Box.createVerticalStrut(20));
		jbut.addActionListener(new ActionListener() {
			private JFileChooser fc;

			@Override
			public void actionPerformed(final ActionEvent ae) {
				if (fc == null) { // dlouho to trvá, tak vytvoříme vždy nový
					fc = new JFileChooser();
				}
				fc.setCurrentDirectory(filex.getEffectiveFile());
				if (jenAdresare) {
					fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				}
				final int result = fc.showDialog(JJedenSouborPanel.this, "Vybrat");
				if (result == JFileChooser.APPROVE_OPTION) {
					jtext.setText(MyPreferences.cestaDoNastaveni(fc.getSelectedFile(), FConst.KOREN));
				}
			}
		});

		prepocitej();
		jtext.getDocument().addDocumentListener(this);

		jActive.addActionListener(e -> prepocitej());
	}

	private void prepocitej() {
		filex = new Filex(MyPreferences.cestaZNastaveni(jtext.getText(), FConst.KOREN), false, jActive.isSelected());
		final String vysledna = popisVysledneCesty(filex.getEffectiveFile(), FConst.KOREN);
		jCurrVal.setText(vysledna);
		jtext.setToolTipText(vysledna);
		jtext.setEnabled(jActive.isSelected());
	}

	/** Cesta uvnitř složky GeoKuk se ukládá relativně a při přesunu složky se posune s ní. */
	static String popisVysledneCesty(final File vysledna, final File koren) {
		final boolean relativni = MyPreferences.cestaDoNastaveni(vysledna, koren).startsWith(MyPreferences.ZNACKA_KORENE);
		return (relativni ? "Relativně ke složce GeoKuk: " : "") + vysledna.getPath();
	}

	String getZadanaCesta() {
		return jtext.getText();
	}

	String getVyslednaCesta() {
		return jCurrVal.getText();
	}

	private void zmemniliNamTo() {
		prepocitej();
	}

}
