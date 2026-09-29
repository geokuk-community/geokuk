package cz.geokuk.plugins.kesoid.importek;

public interface IImportBuilder {

	void init();

	void done();

	void addGpxWpt(GpxWpt gpxwpt);

	void addTrackWpt(GpxWpt wpt);

	void begTrack();

	void begTrackSegment();

	void endTrack();

	void endTrackSegment();

	void setTrackName(String aTrackName);
}
