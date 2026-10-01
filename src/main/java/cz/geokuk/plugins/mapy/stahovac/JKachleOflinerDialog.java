package cz.geokuk.plugins.mapy.stahovac;

import java.awt.Dimension;

import javax.swing.*;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coord.VyrezChangedEvent;
import cz.geokuk.core.program.ZobrazServisniOknoAction;
import cz.geokuk.framework.*;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.gui.JKachle;
import cz.geokuk.plugins.mapy.kachle.gui.Kaputer;
import cz.geokuk.plugins.mapy.kachle.podklady.KaOneReq;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JKachleOflinerDialog extends JMyDialog0 implements AfterEventReceiverRegistrationInit {

	public class KachleOflinerSwingWorker extends MySwingWorker0<Integer, JKachle> {

		private final boolean zafrontovati;

		KachleOflinerSwingWorker(final boolean zafrontovat) {
			zafrontovati = zafrontovat;
		}

		@Override
		protected Integer doInBackground() throws Exception {
			if (zafrontovati) {
				new Thread(() -> {
					postahovatNeboSpocitat(true);
					davka.zarazovaniSkonceno();
				}, "pro-offline").start();
				return 0;
			} else {
				return postahovatNeboSpocitat(false);
			}

		}

		@Override
		protected void donex() throws Exception {
			if (!isCancelled() && !zafrontovati) {
				final Integer pocet = get();
				pocetDlazdic = pocet;
				nastavCudl(pocet);
			}
		}

		private Integer postahovatNeboSpocitat(final boolean zafrontovat) {
			int moumer = moord.getMoumer(); // aktuální měřítko
			int w = moord.getWidth();
			int h = moord.getHeight();
			// nastavit na najvětší měřítko
			while (moumer <= totoSeTaha.maxmoumer) {
				w *= 2;
				h *= 2;
				moumer++;
			}

			// a teď projet směrem nahoru
			int pocetKachli = 0;
			while (moumer > totoSeTaha.minmoumer && !(zafrontovat && davka.jeZastavena())) {
				w /= 2;
				h /= 2;
				moumer--;
				final Coord coco = new Coord(moumer, moord.getMoustred(), new Dimension(w, h), 0.0);

				final Kaputer kaputer = new Kaputer(coco);
				if (log.isTraceEnabled()) {
					log.trace("Vykreslovani kachli od {} pro {} -- {}", kaputer.getKachlePoint(0, 0), kaputer.getKachleMou(0, 0), kaputer);
				}
				for (int yi = 0; yi < kaputer.getPocetKachliY(); yi++) {
					log.trace(" .... řádek", coco);
					for (int xi = 0; xi < kaputer.getPocetKachliX(); xi++) {
						final KaLoc kaloc = kaputer.getKaloc(xi, yi);
						pocetKachli += 1;
						if (zafrontovat) {
							final Ka kaall = new Ka(kaloc, totoSeTaha.katype);
							final DiagnosticsData.Listener diagListener = (diagnosticsData, diagnosticesFazeStr) -> {
								log.trace("Stahování offline kache: {}: {}", diagnosticesFazeStr, diagnosticsData);
							};
							final KaOneReq req = new KaOneReq(kaall, davka.prijemce(), Priority.STAHOVANI);
							// kachleModel.getZiskavac().hloupěČekejAžNebudeZabránoMocZdrojů();
							if (!davka.zarazeno(kachleModel.getZiskavac().ziskejObsah(req, DiagnosticsData.create(null, "Stathování pro offline", diagListener).with("kaAllReq", req)))) {
								return pocetKachli;
							}
						}
					}
				}

			}
			return pocetKachli;
		}

	}

	private class TotoSeTaha {
		private int minmoumer;
		private int maxmoumer;
		private EKaType katype;
	}

	private static final long serialVersionUID = 7180968190465321695L;

	private static final int LIMIT_DLAZDIC = 100000;

	private JTextPane uvod;

	private JButton spustit;

	private Coord moord;

	private TotoSeTaha totoSeTaha;

	private KachleOflinerSwingWorker kosw;

	private KachleModel kachleModel;

	private EKaType katype;

	private int xminmoumer;

	private ZobrazServisniOknoAction zobrazServisniOknoAction;

	private DavkaStahovani davka;

	private int pocetDlazdic;

	public JKachleOflinerDialog() {
		setTitle("Hromadné dotažení mapových dlaždic");
		init();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.AfterEventReceiverRegistrationInit#initAfterEventReceiverRegistration()
	 */
	@Override
	public void initAfterEventReceiverRegistration() {
		totoSeTaha = new TotoSeTaha();
		totoSeTaha.maxmoumer = moord.getMoumer(); // zapamatovat si měřítko, do něj budeme kachlovat
		totoSeTaha.minmoumer = xminmoumer;
		totoSeTaha.katype = katype;
		registerEvents();
		prepocetKachli();
		uvod.setText(pokecani());
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void inject(final ZobrazServisniOknoAction zobrazServisniOknoAction) {
		this.zobrazServisniOknoAction = zobrazServisniOknoAction;
	}

	public void onEvent(final VyrezChangedEvent event) {
		moord = event.getMoord();
		prepocetKachli();
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		katype = event.getKatype();
		xminmoumer = katype.getMinMoumer();
		prepocetKachli();
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "StahovaniMapovychDlazdic";
	}

	@Override
	protected void initComponents() {
		// Napřed registrovat, aby při inicializaci už byl výsledek tady
		final Box box = Box.createVerticalBox();
		add(box);

		uvod = new JTextPane();
		uvod.setContentType("text/html");
		uvod.setText("Tady bude kecání");
		uvod.setPreferredSize(new Dimension(400, 200));
		uvod.setAlignmentX(CENTER_ALIGNMENT);

		spustit = new JButton("Vyberte nějaký výřez");
		spustit.setEnabled(false);
		spustit.setAlignmentX(CENTER_ALIGNMENT);

		box.add(uvod);
		box.add(Box.createVerticalStrut(10));
		box.add(spustit);

	}

	private void nastavCudl(final int pocetDlazdic) {
		if (!totoSeTaha.katype.isHromadneStahovaniPovoleno()) {
			spustit.setEnabled(false);
			spustit.setText("Mapu " + totoSeTaha.katype.getNazev() + " nelze stahovat hromadně");
			return;
		}
		final boolean b = pocetDlazdic > 0 && pocetDlazdic <= LIMIT_DLAZDIC;
		spustit.setEnabled(b);
		if (b) {
			spustit.setText(String.format("Stáhnout %d dlaždic do cache", pocetDlazdic));
		} else {
			if (pocetDlazdic == 0) {
				spustit.setText("Počítáme dlaždice");
			} else {
				spustit.setText("Spočítáno " + pocetDlazdic + ", limit je " + LIMIT_DLAZDIC);
			}

		}
	}

	/**
	 * @return
	 */
	private String pokecani() {
		return String.format("<html>Budou stahovány dlaždice mapových pokladů <b>%s</b> v rozmění měřítek " + " <b>&lt;%d,%d&gt;</b>"
				+ " nyní natavte v hlavním okně výřez mapy který chcete stáhnout. Výřez můžete" + " nastavit v libovolném měřítku a v na libovolném mapovém podkladu. "
				+ " Pak spusťte stahování tlačítkem. Stahování poběží na pozadí, průběh uvidíte v samostatném okně, kde ho lze i zastavit.",
				totoSeTaha.katype, totoSeTaha.minmoumer, totoSeTaha.maxmoumer);
	}

	private void prepocetKachli() {
		if (totoSeTaha != null) {
			if (kosw != null) {
				kosw.cancel(true);
			}
			kosw = new KachleOflinerSwingWorker(false);
			nastavCudl(0);
			kosw.execute();
		}
	}

	private void registerEvents() {

		spustit.addActionListener(e -> {
			if (kosw != null) {
				kosw.cancel(true);
			}
			zobrazServisniOknoAction.actionPerformed(null);
			dispose();
			final JKachleOflinerPocetStazenychDialog jVysledek = factory.init(new JKachleOflinerPocetStazenychDialog());
			davka = new DavkaStahovani(pocetDlazdic, () -> SwingUtilities.invokeLater(jVysledek::obnov));
			jVysledek.setDavka(davka);
			jVysledek.setVisible(true);
			kosw = new KachleOflinerSwingWorker(true);
			kosw.execute();
		});

	}

}
