CREATE TABLE geocache (key INTEGER PRIMARY KEY, id TEXT, guid TEXT, x TEXT, y TEXT, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT, cachestatus INTEGER DEFAULT 0, dthidden INTEGER DEFAULT 0, dtfound INTEGER DEFAULT 0, country TEXT, state TEXT, gs_ownerid TEXT);
CREATE TABLE geolist (key INTEGER PRIMARY KEY, id TEXT, shortdesc TEXT, hint TEXT);
CREATE TABLE waypoint (key INTEGER PRIMARY KEY, id TEXT, x TEXT, y TEXT, name TEXT, prefixid TEXT, wpttype TEXT);
CREATE TABLE geotag (key INTEGER PRIMARY KEY, id TEXT, ptrkat INTEGER DEFAULT 0, ptrvalue INTEGER DEFAULT 0);
CREATE TABLE geotagcategory (key INTEGER PRIMARY KEY, value TEXT);
CREATE TABLE geotagvalue (key INTEGER PRIMARY KEY, value TEXT);
