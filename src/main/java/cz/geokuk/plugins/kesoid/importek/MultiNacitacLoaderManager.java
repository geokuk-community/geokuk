package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.*;

import javax.swing.Timer;

import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

public class MultiNacitacLoaderManager {

	private final MultiNacitac multiNacitac;

	private MultiNacitacSwingWorker klsw;

	private Timer iTimer;

	private final KesoidModel kesoidModel;

	public MultiNacitacLoaderManager(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
		multiNacitac = new MultiNacitac(kesoidModel);
	}

	public void startLoad(final boolean prenacti, final Genom genom) {
		if (iTimer != null) {
			iTimer.stop();
		}
		final KesoidUmisteniSouboru u = kesoidModel.getUmisteniSouboru();
		// Uložená cesta ani ikony nejsou keše; leží-li v datové složce, každé uložení by přenačetlo všechna data.
		final Set<File> vynechane = vynechane(u.getCestyDir(), u.getImageMyDir(), u.getImage3rdPartyDir());
		multiNacitac.setRootDirs(prenacti, u.getKesDir().getEffectiveFileIfActive(), u.getGeogetDataDir().getEffectiveFileIfActive(), u.getGsakDataDir().getEffectiveFileIfActive(), vynechane);
		if (klsw == null || klsw.isDone()) {
			klsw = new MultiNacitacSwingWorker(multiNacitac, genom, kesoidModel);
			klsw.execute();
		}
		startTimer(genom);
	}

	/** Neplatná cesta (třeba se znakem, který systém v názvu nedovolí) žádnou složku neoznačuje, není co vynechat. */
	static Set<File> vynechane(final Filex... slozky) {
		final Set<File> vysledek = new HashSet<>();
		for (final Filex f : slozky) {
			try {
				vysledek.add(f.getEffectiveFile().toPath().toAbsolutePath().normalize().toFile());
			} catch (final InvalidPathException e) {
				// nic
			}
		}
		return vysledek;
	}

	public boolean jeZamcena(final File databaze) {
		return multiNacitac.jeZamcena(databaze);
	}

	private void startTimer(final Genom genom) {
		iTimer = new Timer(10000, e -> startLoad(false, genom));
		iTimer.start();

	}

}
