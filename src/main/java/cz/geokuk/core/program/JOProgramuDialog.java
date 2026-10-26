package cz.geokuk.core.program;

import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

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

	static final String PODTITUL = "Plánování geovýletů na českých mapách";
	static final String AUTORSTVI_PUVODNI = "© Martin Veverka a spoluautoři (původní dílo, 2009)";
	static final String AUTORSTVI_UPRAVENA = "Upravená verze od 2026: GeoKuk community";
	static final String LICENCE_VETA = "GeoKuk je svobodný software pod licencí GNU GPL v3.";
	static final String PODEKOVANI_VETA = "Autoři a poděkování jsou v README, oddíly Původ a Poděkování.";

	static final String TEXT_GITHUB = "Projekt na GitHubu";
	static final String TEXT_WIKI = "Uživatelská wiki";
	static final String TEXT_PUVODNI_PROJEKT = "Původní projekt";
	static final String TEXT_PROFIL_AUTORA = "Původní autor Martin Veverka na geocaching.com";
	static final String TEXT_README = "README";

	static final String URL_PUVODNI_PROJEKT = "https://github.com/marvertin/geokuk";
	static final String URL_PROFIL_AUTORA = "http://www.geocaching.com/profile/?guid=22cad0c7-59a3-417c-99d1-7c9079e9ae27";
	/** Oddíl Původ v README (kotva je v ASCII, aby ji přijal každý prohlížeč). */
	static final String URL_README = FConst.WEB_PAGE_URL + "#p%C5%AFvod";

	@Override
	protected void initComponents() {
		final Box box = Box.createVerticalBox();
		add(box);

		box.add(Box.createGlue());
		box.add(Box.createVerticalStrut(20));

		final JLabel c1 = new JLabel("GeoKuk");
		c1.setAlignmentX(CENTER_ALIGNMENT);
		c1.setFont(new Font("Arial", Font.BOLD, 30));
		box.add(c1);

		box.add(vystredeny(new JLabel("Verze " + FConst.VERSION)));
		box.add(vystredeny(new JLabel(PODTITUL)));

		box.add(Box.createVerticalStrut(10));
		box.add(vystredeny(new JLabel(AUTORSTVI_PUVODNI)));
		box.add(vystredeny(new JLabel(AUTORSTVI_UPRAVENA)));

		box.add(Box.createVerticalStrut(10));
		final JPanel odkazy = new JPanel();
		odkazy.add(odkaz(TEXT_GITHUB, FConst.WEB_PAGE_URL));
		odkazy.add(odkaz(TEXT_WIKI, FConst.WEB_PAGE_WIKI));
		odkazy.add(odkaz(TEXT_PUVODNI_PROJEKT, URL_PUVODNI_PROJEKT));
		box.add(vystredeny(odkazy));

		final JButton bgccom = odkaz(TEXT_PROFIL_AUTORA, URL_PROFIL_AUTORA);
		bgccom.setIcon(ImageLoader.seekResIcon("gccom.png"));
		box.add(vystredeny(bgccom));

		box.add(Box.createVerticalStrut(10));
		final JLabel licenceVeta = new JLabel(LICENCE_VETA);
		licenceVeta.setFont(new Font("Serif", Font.ITALIC, 12));
		box.add(vystredeny(licenceVeta));
		final JButton licence = new JButton("Licence a použité knihovny");
		licence.addActionListener(e -> ukazLicence());
		box.add(vystredeny(licence));

		box.add(Box.createVerticalStrut(10));
		final JLabel podekovani = new JLabel(PODEKOVANI_VETA);
		podekovani.setFont(new Font("Serif", Font.ITALIC, 12));
		box.add(vystredeny(podekovani));
		box.add(vystredeny(odkaz(TEXT_README, URL_README)));
		box.add(Box.createVerticalStrut(20));
		box.add(Box.createGlue());
	}

	private static <T extends JComponent> T vystredeny(final T c) {
		c.setAlignmentX(CENTER_ALIGNMENT);
		return c;
	}

	/** Tlačítko, které otevře webovou stránku v prohlížeči. */
	private static JButton odkaz(final String text, final String url) {
		final JButton b = new JButton(text);
		b.addActionListener(e -> {
			try {
				BrowserOpener.displayURL(new URL(url));
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
