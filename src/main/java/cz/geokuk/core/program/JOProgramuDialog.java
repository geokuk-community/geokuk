package cz.geokuk.core.program;

import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.swing.*;

import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.img.ImageLoader;
import cz.geokuk.util.process.BrowserOpener;

public class JOProgramuDialog extends JMyDialog0 {

	private static final long serialVersionUID = 7180968190465321695L;

	public JOProgramuDialog() {
		setTitle("O programu");
		init();
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return null;
	}

	static final String NADPIS_PUVODNI = "Původní program";
	static final String NADPIS_UPRAVENA = "Upravená verze";

	/** Texty původního dialogu beze změny. */
	static final String AUTORSTVI_PUVODNI = "(c) 2009 Martin Veverka";
	static final String PROFIL_PUVODNI = "Profil na GC.COM a GC.CZ: \"rodinka veverek\"";
	static final String LICENCE_PUVODNI_1 = "GeoKuk je svobodný software";
	static final String LICENCE_PUVODNI_2 = "pod licencí GNU GPL v3.";

	static final String PODTITUL = "Plánování geovýletů na českých mapách";
	static final String AUTORSTVI_UPRAVENA = "GeoKuk community od 2026";
	static final String PODEKOVANI_VETA = "Autoři a poděkování jsou v README, oddíly Původ a Poděkování.";

	static final String TEXT_GITHUB = "Projekt na GitHubu";
	static final String TEXT_WIKI = "Uživatelská wiki";
	static final String TEXT_PUVODNI_PROJEKT = "Původní projekt";
	/** Tlačítko s ikonou nemá text; jméno se používá jen pro čtečky obrazovky. */
	static final String TEXT_PROFIL_AUTORA = "Profil Martina Veverky na geocaching.com";
	static final String TEXT_README = "README";

	/** Všechna tlačítka-odkazy dialogu: text a adresa. */
	static final String[][] ODKAZY = {
			{ TEXT_PROFIL_AUTORA, "http://www.geocaching.com/profile/?guid=22cad0c7-59a3-417c-99d1-7c9079e9ae27" },
			{ TEXT_PUVODNI_PROJEKT, "https://github.com/marvertin/geokuk" },
			{ TEXT_GITHUB, FConst.WEB_PAGE_URL },
			{ TEXT_WIKI, FConst.WEB_PAGE_WIKI },
			// Oddíl Původ v README; kotva je v ASCII, aby ji přijal každý prohlížeč.
			{ TEXT_README, FConst.WEB_PAGE_URL + "#p%C5%AFvod" } };

	static final String KLIC_URL = "url";

	/** Otevře webovou stránku; v testech se dá nahradit. */
	static Consumer<URL> otevirac = BrowserOpener::displayURL;

	@Override
	protected void initComponents() {
		final Box box = Box.createVerticalBox();
		add(box);
		box.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

		final JLabel nadpis = new JLabel("GeoKuk");
		nadpis.setFont(new Font("Arial", Font.BOLD, 30));
		box.add(vystredeny(nadpis));
		box.add(vystredeny(new JLabel("Verze " + FConst.VERSION)));
		box.add(Box.createVerticalStrut(6));
		for (final String radek : new String[] { LICENCE_PUVODNI_1, LICENCE_PUVODNI_2 }) {
			final JLabel l = new JLabel(radek);
			l.setFont(new Font("Serif", Font.ITALIC, 12));
			box.add(vystredeny(l));
		}
		final JButton licence = new JButton("Licence a použité knihovny");
		licence.addActionListener(e -> ukazLicence());
		box.add(vystredeny(licence));
		box.add(Box.createVerticalStrut(10));

		final JPanel puvodni = sekce(NADPIS_PUVODNI);
		puvodni.add(vystredeny(new JLabel(AUTORSTVI_PUVODNI)));
		final JLabel profil = new JLabel(PROFIL_PUVODNI);
		profil.setFont(new Font("Serif", Font.ITALIC, 12));
		puvodni.add(vystredeny(profil));
		final JButton bgccom = odkaz(TEXT_PROFIL_AUTORA);
		bgccom.setIcon(ImageLoader.seekResIcon("gccom.png"));
		bgccom.setText(null);
		bgccom.getAccessibleContext().setAccessibleName(TEXT_PROFIL_AUTORA);
		puvodni.add(vystredeny(bgccom));
		puvodni.add(Box.createVerticalStrut(6));
		puvodni.add(vystredeny(odkaz(TEXT_PUVODNI_PROJEKT)));
		box.add(puvodni);
		box.add(Box.createVerticalStrut(10));

		final JPanel upravena = sekce(NADPIS_UPRAVENA);
		upravena.add(vystredeny(new JLabel(PODTITUL)));
		upravena.add(vystredeny(new JLabel(AUTORSTVI_UPRAVENA)));
		upravena.add(Box.createVerticalStrut(6));
		final JPanel odkazy = new JPanel();
		odkazy.add(odkaz(TEXT_GITHUB));
		odkazy.add(odkaz(TEXT_WIKI));
		upravena.add(vystredeny(odkazy));
		upravena.add(Box.createVerticalStrut(6));
		final JLabel podekovani = new JLabel(PODEKOVANI_VETA);
		podekovani.setFont(new Font("Serif", Font.ITALIC, 12));
		upravena.add(vystredeny(podekovani));
		upravena.add(vystredeny(odkaz(TEXT_README)));
		box.add(upravena);
	}

	/** Oddíl dialogu s nadpisem v rámečku. */
	private static JPanel sekce(final String nadpis) {
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), nadpis),
				BorderFactory.createEmptyBorder(2, 10, 6, 10)));
		return p;
	}

	private static <T extends JComponent> T vystredeny(final T c) {
		c.setAlignmentX(CENTER_ALIGNMENT);
		return c;
	}

	static String adresa(final String text) {
		for (final String[] o : ODKAZY) {
			if (o[0].equals(text)) {
				return o[1];
			}
		}
		throw new IllegalArgumentException(text);
	}

	/** Tlačítko, které otevře webovou stránku v prohlížeči; adresa je v seznamu {@link #ODKAZY} podle textu. */
	private static JButton odkaz(final String text) {
		final String url = adresa(text);
		final JButton b = new JButton(text);
		b.putClientProperty(KLIC_URL, url);
		b.addActionListener(e -> {
			try {
				otevirac.accept(new URL(url));
			} catch (final MalformedURLException e1) {
				// Adresy jsou pevné a ověřuje je test.
			}
		});
		return b;
	}

	private void ukazLicence() {
		final JTextArea text = new JTextArea(textLicence());
		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		text.setCaretPosition(0);
		final JScrollPane posuv = new JScrollPane(text);
		posuv.setPreferredSize(new Dimension(640, 480));
		JOptionPane.showMessageDialog(this, posuv, "Licence a použité knihovny", JOptionPane.PLAIN_MESSAGE);
	}

	/** Seznam knihoven, mapových podkladů a jejich licencí a texty licencí GNU GPL v3 a LGPL v3, přibalené v programu. */
	static String textLicence() {
		return precti("/THIRD-PARTY.txt") + "\n\n" + precti("/MAPOVE-PODKLADY.txt") + "\n\n" + precti("/LICENSE") + "\n\n" + precti("/LGPL-3.0.txt");
	}

	private static String precti(final String zdroj) {
		try (InputStream in = JOProgramuDialog.class.getResourceAsStream(zdroj)) {
			if (in == null) {
				return "";
			}
			try (Scanner sc = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
				return sc.hasNext() ? sc.next() : "";
			}
		} catch (final IOException e) {
			return "";
		}
	}
}
