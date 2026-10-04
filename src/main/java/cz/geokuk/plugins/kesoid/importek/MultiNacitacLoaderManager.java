package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
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
		final Set<File> vynechane = new HashSet<>();
		for (final Filex f : Arrays.asList(u.getCestyDir(), u.getImageMyDir(), u.getImage3rdPartyDir())) {
			vynechane.add(f.getEffectiveFile().toPath().toAbsolutePath().normalize().toFile());
		}
		multiNacitac.setRootDirs(prenacti, u.getKesDir().getEffectiveFileIfActive(), u.getGeogetDataDir().getEffectiveFileIfActive(), u.getGsakDataDir().getEffectiveFileIfActive(), vynechane);
		if (prenacti && klsw != null && !klsw.isDone()) {
			// Rozběhnuté načítání by doběhlo se starým nastavením; začne se znovu s novým.
			klsw.cancel(false);
		}
		if (klsw == null || klsw.isDone()) {
			klsw = new MultiNacitacSwingWorker(multiNacitac, genom, kesoidModel);
			klsw.execute();
		}
		startTimer(genom);
	}

	public boolean jeZamcena(final File databaze) {
		return multiNacitac.jeZamcena(databaze);
	}

	private void startTimer(final Genom genom) {
		iTimer = new Timer(10000, e -> startLoad(false, genom));
		iTimer.start();

	}

}
