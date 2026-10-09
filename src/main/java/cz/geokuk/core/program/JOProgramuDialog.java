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

		final JLabel c2 = new JLabel("Verze " + FConst.VERSION);
		c2.setAlignmentX(CENTER_ALIGNMENT);
		box.add(c2);

		box.add(Box.createVerticalStrut(10));
		final JLabel c3 = new JLabel("(c) 2009 Martin Veverka");
		c3.setAlignmentX(CENTER_ALIGNMENT);
		box.add(c3);

		final JLabel c4 = new JLabel("Profil na GC.COM a GC.CZ: \"rodinka veverek\"");

		c4.setAlignmentX(CENTER_ALIGNMENT);
		c4.setFont(new Font("Serif", Font.ITALIC, 12));
		box.add(c4);

		final JButton bgccom = new JButton(ImageLoader.seekResIcon("gccom.png"));
		bgccom.addActionListener(e -> {
			final String urls = "http://www.geocaching.com/profile/?guid=22cad0c7-59a3-417c-99d1-7c9079e9ae27";
			try {
				BrowserOpener.displayURL(new URL(urls));
			} catch (final MalformedURLException e1) { // to půjde
			}
		});
		bgccom.setAlignmentX(CENTER_ALIGNMENT);
		box.add(bgccom);

		box.add(Box.createVerticalStrut(10));
		final JLabel zdarma1 = new JLabel("GeoKuk je svobodný software");
		final JLabel zdarma2 = new JLabel("pod licencí GNU GPL v3.");
		zdarma1.setFont(new Font("Serif", Font.ITALIC, 12));
		zdarma1.setAlignmentX(CENTER_ALIGNMENT);
		box.add(zdarma1);
		zdarma2.setFont(new Font("Serif", Font.ITALIC, 12));
		zdarma2.setAlignmentX(CENTER_ALIGNMENT);
		box.add(zdarma2);
		final JButton licence = new JButton("Licence a použité knihovny");
		licence.addActionListener(e -> ukazLicence());
		licence.setAlignmentX(CENTER_ALIGNMENT);
		box.add(licence);
		box.add(Box.createVerticalStrut(20));
		box.add(Box.createGlue());
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
