package cz.geokuk.plugins.kesoid.mvc;

import java.awt.Component;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import cz.geokuk.framework.AfterEventReceiverRegistrationInit;
import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.plugins.kesoid.KesBag;

public class JInformaceOZdrojichDialog extends JMyDialog0 implements AfterEventReceiverRegistrationInit {

	/** První sloupec: odsazení podle hloubky a ikona rozbalení. */
	private class ZdrojRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			final StromZdrojuModel.Radek radek = model.getRadek(row);
			setIcon(radek.maDeti() ? (model.jeRozbaleny(row) ? rozbaleno : sbaleno) : null);
			setBorder(BorderFactory.createEmptyBorder(0, odsazeni(radek) + (radek.maDeti() ? 0 : sirkaIkony()), 0, 0));
			return this;
		}
	}

	private static final long serialVersionUID = 5215923043342722378L;

	// private final InformaceOZdrojich iInformaceOZdrojich;

	private JTable jTable;

	private StromZdrojuModel model;

	private final Icon rozbaleno = UIManager.getIcon("Tree.expandedIcon");

	private final Icon sbaleno = UIManager.getIcon("Tree.collapsedIcon");

	private KesoidModel kesoidModel;

	private KesBag vsechny;

	public JInformaceOZdrojichDialog() {
		setTitle("Přehled zdrojů kešoidů");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.AfterEventReceiverRegistrationInit#initAfterEventReceiverRegistration()
	 */
	@Override
	public void initAfterEventReceiverRegistration() {
		init();
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void nastavVlastnostiSloupcu() {
		TableColumn column;

		column = jTable.getColumnModel().getColumn(0);
		column.setMinWidth(200);
		column.setPreferredWidth(200);
		column.setResizable(true);

		column = jTable.getColumnModel().getColumn(1);
		// column.setMaxWidth(200);
		column.setMinWidth(20);
		column.setPreferredWidth(50);
		column.setResizable(true);
		column.setMaxWidth(100);

		column = jTable.getColumnModel().getColumn(2);
		column.setMaxWidth(100);
		column.setMinWidth(50);
		column.setPreferredWidth(100);
		column.setResizable(true);

		column = jTable.getColumnModel().getColumn(3);
		column.setMaxWidth(100);
		column.setMinWidth(50);
		column.setPreferredWidth(100);
		column.setResizable(true);

	}

	public void onEvent(final KeskyNactenyEvent event) {
		vsechny = event.getVsechny();
		if (model != null) {
			model.setKoren(vsechny.getInformaceOZdrojich().getRoot());
		}
		invalidate();
		pack();
	}

	public void onEvent(final KesoidUmisteniSouboruChangedEvent event) {
		// Není to pravda, když jich míme více
		// setTitle("Přehled zdrojů kešoidů: \""+event.getUmisteniSouboru().getKesDir().getEffectiveFile()+ "\"");
	}

	private void pridejKlavesu(final int klavesa, final String jmeno, final boolean rozbalit) {
		jTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(klavesa, 0), jmeno);
		jTable.getActionMap().put(jmeno, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				final int radek = jTable.getSelectedRow();
				if (radek >= 0) {
					model.setRozbaleny(radek, rozbalit);
					jTable.setRowSelectionInterval(radek, radek);
				}
			}
		});
	}

	private int sirkaIkony() {
		return Math.max(sbaleno == null ? 0 : sbaleno.getIconWidth(), 12) + 4;
	}

	private int odsazeni(final StromZdrojuModel.Radek radek) {
		return 2 + radek.hloubka * sirkaIkony();
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "ZdrojeKesoidu";
	}

	@Override
	protected void initComponents() {
		final Box box = Box.createVerticalBox();

		model = new StromZdrojuModel(kesoidModel::maSeNacist, kesoidModel::jeZamcena, kesoidModel::setNacitatSoubor);
		if (vsechny != null) {
			model.setKoren(vsechny.getInformaceOZdrojich().getRoot());
		}
		jTable = new JTable(model);
		jTable.setFillsViewportHeight(true);
		jTable.setRowSelectionAllowed(true);
		jTable.getColumnModel().getColumn(0).setCellRenderer(new ZdrojRenderer());
		jTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				final int radek = jTable.rowAtPoint(e.getPoint());
				if (radek < 0 || jTable.columnAtPoint(e.getPoint()) != 0) {
					return;
				}
				final int x = e.getX() - jTable.getCellRect(radek, 0, false).x - odsazeni(model.getRadek(radek));
				if (e.getClickCount() == 2 || x >= 0 && x < sirkaIkony()) {
					model.setRozbaleny(radek, !model.jeRozbaleny(radek));
					jTable.setRowSelectionInterval(radek, radek);
				}
			}
		});
		pridejKlavesu(KeyEvent.VK_RIGHT, "rozbalit", true);
		pridejKlavesu(KeyEvent.VK_LEFT, "sbalit", false);

		// Create the scroll pane and add the table to it.
		final JScrollPane scrollPane = new JScrollPane(jTable);

		nastavVlastnostiSloupcu();
		box.add(scrollPane);
		add(box);
	}

}
