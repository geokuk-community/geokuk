package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cz.geokuk.framework.ProgressModel;

public abstract class NacitacInputStream0 extends Nacitac0 {

	@Override
	protected final void nacti(final File file, final IImportBuilder builder, final Future<?> future, final ProgressModel progressModel) throws IOException {
		if (!umiNacist(file)) {
			throw new IllegalArgumentException("Cannot load file " + file);
		}
		try (InputStream istm = wrapByProgressor(new FileInputStream(file), file.toString(), progressModel)) {
			nacti(istm, file.toString(), builder, future);
		}
	}

	protected abstract void nacti(InputStream aIstm, String name, IImportBuilder builder, Future<?> future) throws IOException;

	@Override
	protected final void nacti(final ZipFile zipFile, final ZipEntry zipEntry, final IImportBuilder builder, final Future<?> future, final ProgressModel progressModel) throws IOException {
		if (!umiNacist(zipEntry)) {
			throw new IllegalArgumentException("Cannot load zipped entry " + zipEntry + " from file " + zipFile);
		}
		final String name = zipFile.getName() + "/" + zipEntry.getName();
		try (InputStream istm = wrapByProgressor(zipFile.getInputStream(zipEntry), name, progressModel)) {
			nacti(istm, name, builder, future);
		}
	}

}
