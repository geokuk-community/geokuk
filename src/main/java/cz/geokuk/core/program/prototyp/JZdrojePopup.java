package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import cz.geokuk.core.program.prototyp.ZdrojeModel.*;

/**
 * Tabulka zdrojů, která se vysune nad stavovým řádkem: Načíst | Zdroj | Velikost | WP | Stav, skupiny podle typu zdroje a jejich položky. Úplná má i globální přepínač,
 * popup jednoho typu je táž tabulka jen pro tento typ.
 */
public class JZdrojePopup extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final java.util.Locale CS = new java.util.Locale("cs");

	private static final String[] SLOUPCE = { "Načíst", "Zdroj", "Velikost", "WP", "Stav" };

	/** Řádek tabulky je buď záhlaví typu zdroje, nebo jeden soubor či databáze. */
	private static final class Radek {
		final Typ typ;
		final Polozka polozka;

		Radek(final Typ typ, final Polozka polozka) {
			this.typ = typ;
			this.polozka = polozka;
		}
	}

	static final int PLNA_SIRKA = 776;

	private final ZdrojeModel model;
	/** Typ, jehož položky se ukazují; null je souhrn po typech. */
	private final Typ zobrazenyTyp;
	private final List<Radek> radky = new ArrayList<>();
	private final JTable tabulka;
	private final JScrollPane scroll;
	private final javax.swing.table.TableColumn sloupecVelikost;
	private boolean uzky;

	private final AbstractTableModel tm = new AbstractTableModel() {
		private static final long serialVersionUID = 1L;

		@Override
		public int getRowCount() {
			return radky.size();
		}

		@Override
		public int getColumnCount() {
			return SLOUPCE.length;
		}

		@Override
		public String getColumnName(final int c) {
			return SLOUPCE[c];
		}

		@Override
		public Class<?> getColumnClass(final int c) {
			return c == 0 ? Boolean.class : String.class;
		}

		@Override
		public boolean isCellEditable(final int r, final int c) {
			return c == 0;
		}

		@Override
		public void setValueAt(final Object v, final int r, final int c) {
			final Radek radek = radky.get(r);
			if (radek.polozka == null) {
				model.setTypZapnut(radek.typ, (Boolean) v);
			} else {
				model.setPolozkaZapnuta(radek.polozka, (Boolean) v);
			}
		}

		@Override
		public Object getValueAt(final int r, final int c) {
			final Radek radek = radky.get(r);
			final Polozka p = radek.polozka;
			switch (c) {
			case 0:
				return p != null ? p.nacist : model.isTypZapnut(radek.typ);
			case 1:
				return p != null ? p.nazev : radek.typ.getNazev();
			case 2:
				return velikost(p != null ? p.velikost : soucet(radek.typ, false));
			case 3:
				return p != null ? wp(p.wpBrano, p.wpCelkem) : model.isTypZapnut(radek.typ) ? wp(souctoveBrano(radek.typ), soucet(radek.typ, true)) : "–";
			default:
				return p == null ? souhrnnyStav(radek.typ) : p.stav == Stav.NACITA_SE ? "Načítá se… " + p.postup + " %" : p.stav.getText();
			}
		}
	};

	public JZdrojePopup(final ZdrojeModel model, final Typ zobrazenyTyp) {
		super(new BorderLayout(0, 6));
		this.model = model;
		this.zobrazenyTyp = zobrazenyTyp;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		final JPanel odkazy = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		odkazy.add(odkaz("Vše zapnout", true));
		odkazy.add(new JLabel("|"));
		odkazy.add(odkaz("Vše vypnout", false));
		add(odkazy, BorderLayout.NORTH);

		tabulka = new JTable(tm) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(final java.awt.event.MouseEvent e) {
				final int r = rowAtPoint(e.getPoint());
				if (r < 0 || convertColumnIndexToModel(columnAtPoint(e.getPoint())) != 3) {
					return null;
				}
				final Polozka p = radky.get(r).polozka;
				final int dup = p != null ? (p.nacist ? p.getPocetDuplicit() : 0) : duplicitTypu(radky.get(r).typ);
				return dup > 0 ? textDuplicit(dup) : null;
			}
		};
		tabulka.setRowHeight(22);
		tabulka.setShowGrid(false);
		tabulka.setIntercellSpacing(new Dimension(0, 0));
		tabulka.setFocusable(false);
		tabulka.getTableHeader().setReorderingAllowed(false);
		tabulka.setDefaultRenderer(String.class, new Vykreslovac());
		final int[] sirky = { 56, 200, 120, 150, 220 };
		for (int i = 0; i < sirky.length; i++) {
			tabulka.getColumnModel().getColumn(i).setPreferredWidth(sirky[i]);
		}
		scroll = new JScrollPane(tabulka);
		scroll.setPreferredSize(new Dimension(PLNA_SIRKA - 16, 22 * (zobrazenyTyp == null ? Typ.values().length + model.getPolozky().size() : model.getPolozky(zobrazenyTyp).size() + 1) + 30));
		add(scroll, BorderLayout.CENTER);
		sloupecVelikost = tabulka.getColumnModel().getColumn(2);

		model.addPosluchac(this::obnov);
		obnov();
	}

	private void obnov() {
		radky.clear();
		for (final Typ typ : Typ.values()) {
			if (zobrazenyTyp != null && zobrazenyTyp != typ) {
				continue;
			}
			radky.add(new Radek(typ, null));
			for (final Polozka p : model.getPolozky(typ)) {
				radky.add(new Radek(typ, p));
			}
		}
		tm.fireTableDataChanged();
	}

	/** V úzkém okně se tabulka zúží na šířku okna a odpadne sloupec Velikost. */
	public void nastavDostupnouSirku(final int sirka) {
		final boolean nove = sirka < JPrepinaceZdroju.PRAH_KOMPAKTNI;
		if (nove != uzky) {
			uzky = nove;
			if (nove) {
				tabulka.removeColumn(sloupecVelikost);
			} else {
				tabulka.addColumn(sloupecVelikost);
				tabulka.moveColumn(tabulka.getColumnCount() - 1, 2);
			}
		}
		final int vyska = scroll.getPreferredSize().height;
		scroll.setPreferredSize(new Dimension(Math.min(PLNA_SIRKA, sirka) - 16, vyska));
		revalidate();
	}

	private JButton odkaz(final String text, final boolean zapnout) {
		final JButton b = new JButton("<html><a href=\"#\">" + text + "</a></html>");
		b.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		b.setContentAreaFilled(false);
		b.setFocusable(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(e -> {
			if (zobrazenyTyp == null) {
				model.setVse(zapnout);
			} else {
				model.setTypZapnut(zobrazenyTyp, zapnout);
			}
		});
		return b;
	}

	private String souhrnnyStav(final Typ typ) {
		final Stav stav = model.getStavTypu(typ);
		return stav == Stav.NACITA_SE ? "Načítá se… " + postup(typ) + " %" : stav.getText();
	}

	private int postup(final Typ typ) {
		int soucet = 0;
		int pocet = 0;
		for (final Polozka p : model.getPolozky(typ)) {
			if (p.nacist) {
				soucet += p.stav == Stav.NACITA_SE ? p.postup : 100;
				pocet++;
			}
		}
		return pocet == 0 ? 0 : soucet / pocet;
	}

	private int duplicitTypu(final Typ typ) {
		int s = 0;
		for (final Polozka p : model.getPolozky(typ)) {
			s += p.nacist ? p.getPocetDuplicit() : 0;
		}
		return s;
	}

	private long soucet(final Typ typ, final boolean wp) {
		long s = 0;
		for (final Polozka p : model.getPolozky(typ)) {
			s += wp ? (p.nacist ? p.wpCelkem : 0) : p.velikost;
		}
		return s;
	}

	private long souctoveBrano(final Typ typ) {
		long s = 0;
		for (final Polozka p : model.getPolozky(typ)) {
			s += p.wpBrano;
		}
		return s;
	}

	static String textDuplicit(final int pocet) {
		return String.format(CS, "%,d keší se neukazuje, protože jsou ve více zdrojích.", pocet);
	}

	static String wp(final long brano, final long celkem) {
		return brano == celkem ? String.format(CS, "%,d", brano) : String.format(CS, "%,d z %,d  ≠", brano, celkem);
	}

	static String velikost(final long bajty) {
		if (bajty < 1_000_000L) {
			return String.format(CS, "%,d kB", Math.max(1, bajty / 1000));
		}
		return bajty < 1_000_000_000L ? String.format(CS, "%.1f MB", bajty / 1e6) : String.format(CS, "%.2f GB", bajty / 1e9);
	}

	private class Vykreslovac extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(final JTable t, final Object v, final boolean sel, final boolean fokus, final int r, final int sloupec) {
			super.getTableCellRendererComponent(t, v, false, false, r, sloupec);
			final int c = t.convertColumnIndexToModel(sloupec);
			final Radek radek = radky.get(r);
			final Polozka p = radek.polozka;
			setIcon(null);
			setHorizontalAlignment(c == 2 || c == 3 ? RIGHT : LEFT);
			setFont(t.getFont().deriveFont(p == null ? Font.BOLD : Font.PLAIN));
			setBorder(BorderFactory.createEmptyBorder(0, c == 1 && p != null ? 22 : 6, 0, 6));
			setBackground(p == null ? new Color(0, 0, 0, 20) : t.getBackground());
			setForeground(p != null && !p.nacist ? Color.GRAY : t.getForeground());
			if (c == 4) {
				setIcon(IkonyZdroju.pro(p != null ? p.stav : model.getStavTypu(radek.typ)));
			}
			if (p == null) {
				setForeground(model.isTypZapnut(radek.typ) ? t.getForeground() : Color.GRAY);
			}
			if (c == 3 && (p != null ? p.getPocetDuplicit() > 0 && p.nacist : duplicitTypu(radek.typ) > 0)) {
				setForeground(new Color(0x9A5B00));
			}
			setOpaque(true);
			return this;
		}
	}
}
