/*
 * Copyright 1997-2008 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.dt2.point;

import ucar.nc2.dt2.*;
import ucar.unidata.geoloc.LatLonRect;

import java.util.List;
import java.io.IOException;

/**
 * Abstract superclass for StationProfileFeatureCollection
 * Subclasses must implement getNestedPointFeatureCollection
 * @author caron
 * @since Mar 20, 2008
 */
public abstract class StationProfileFeatureCollectionImpl extends NestedPointFeatureCollectionImpl implements StationProfileFeatureCollection {

  protected StationHelper stationHelper;

  public StationProfileFeatureCollectionImpl() {
    super(true, StationProfileFeature.class);
    stationHelper = new StationHelper();
  }

  protected void setStationHelper(StationHelper stationHelper) {
    this.stationHelper = stationHelper;
  }

  public List<Station> getStations() {
    return stationHelper.getStations();
  }

  public List<Station> getStations(LatLonRect boundingBox) throws IOException {
    return stationHelper.getStations(boundingBox);
  }

  public Station getStation(String name) {
    return stationHelper.getStation(name);
  }

  public LatLonRect getBoundingBox() {
    return stationHelper.getBoundingBox();
  }

  public StationProfileFeatureCollectionImpl subset(List<Station> stations) throws IOException {
    if (stations == null) return this;
    return new StationProfileFeatureCollectionSubset(this, stations);
  }

  public StationProfileFeature getStationProfileFeature(Station s) throws IOException {
    return (StationProfileFeature) s;  // LOOK subclass must override if not true
  }

  public PointFeatureCollectionIterator getPointFeatureCollectionIterator(int bufferSize) throws IOException {
    throw new UnsupportedOperationException("StationProfileFeatureCollection does not implement getPointFeatureCollectionIterator()");
  }

    // LOOK subset by filtering on the stations, but it would be easier if we could get the StationFeature from the Station
  private class StationProfileFeatureCollectionSubset extends StationProfileFeatureCollectionImpl {
    StationProfileFeatureCollectionImpl from;

    StationProfileFeatureCollectionSubset(StationProfileFeatureCollectionImpl from, List<Station> stations) {
      this.from = from;
      stationHelper = new StationHelper();
      stationHelper.setStations(stations);
    }

    // use this only if it is multiply nested
    public NestedPointFeatureCollectionIterator getNestedPointFeatureCollectionIterator(int bufferSize) throws IOException {
      return new NestedPointFeatureCollectionIteratorFiltered( from.getNestedPointFeatureCollectionIterator(bufferSize), new Filter());
    }

    private class Filter implements NestedPointFeatureCollectionIterator.Filter {

      public boolean filter(NestedPointFeatureCollection pointFeatureCollection) {
        StationProfileFeature stationFeature = (StationProfileFeature) pointFeatureCollection;
        return stationHelper.getStation(stationFeature.getName()) != null;
      }
    }
  }

}
