package thredds.wcs.v1_0_0_Plus;

import java.io.File;
import java.text.ParseException;
import java.util.List;

import ucar.unidata.geoloc.EPSG_OGC_CF_Helper;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.LatLonPointImpl;
import ucar.nc2.dt.GridCoordSystem;
import thredds.datatype.DateRange;
import thredds.datatype.DateType;

/**
 * _more_
 *
 * @author edavis
 * @since 4.0
 */
public class GetCoverage extends WcsRequest
{
  private static org.slf4j.Logger log =
          org.slf4j.LoggerFactory.getLogger( GetCoverage.class );


  private WcsCoverage coverage;

  private LatLonRect bboxLatLonRect;
  private DateRange timeRange;
  private List<AxisSubset> axisSubset;     // ??????


  public GetCoverage( Operation operation, String version, WcsDataset dataset,
                      String coverageId, String crs, String responseCRS,
                      String bbox, String time, String rangeSubset, String format )
          throws WcsException
  {
    super( operation, version, dataset);

    // Validate coverage ID parameter.
    if ( coverageId == null )
      throw new WcsException( WcsException.Code.MissingParameterValue, "coverage", "Coverage identifier required." );
    if ( !this.getDataset().isAvailableCoverageName( coverageId ) )
      throw new WcsException( WcsException.Code.InvalidParameterValue, "coverage", "Unknown coverage identifier <" + coverageId + ">." );
    this.coverage = this.getDataset().getAvailableCoverage( coverageId );
    if ( this.coverage == null ) // Double check just in case.
      throw new WcsException( WcsException.Code.InvalidParameterValue, "coverage", "Unknown coverage identifier <" + coverageId + ">." );

    // Assign and validate request and response CRS parameters.

    if ( crs == null )
      throw new WcsException( WcsException.Code.MissingParameterValue, "CRS", "Request CRS required.");
    if ( ! crs.equalsIgnoreCase( this.coverage.getDefaultRequestCrs() ) )
      throw new WcsException( WcsException.Code.InvalidParameterValue, "CRS", "Request CRS <" + crs + "> not allowed <" + this.coverage.getDefaultRequestCrs() + ">." );

    String nativeCRS = EPSG_OGC_CF_Helper.getWcs1_0CrsId( coverage.getCoordinateSystem().getProjection() );
    if ( nativeCRS == null )
      throw new WcsException( WcsException.Code.CoverageNotDefined, "", "Coverage not in recognized CRS. (???)");

    // Response CRS not required if data is in latLon ("OGC:CRS84"). Default is request CRS.
    if ( responseCRS == null )
    {
      if ( ! nativeCRS.equalsIgnoreCase( this.coverage.getDefaultRequestCrs()))
        throw new WcsException( WcsException.Code.MissingParameterValue, "Response_CRS", "Response CRS required." );
    }
    else if ( ! responseCRS.equalsIgnoreCase( nativeCRS))
        throw new WcsException( WcsException.Code.InvalidParameterValue, "response_CRS", "Respnse CRS <" + responseCRS + "> not allowed <" + nativeCRS + ">." );

    // Assign and validate BBOX and TIME parameters.
    if ( bbox == null && time == null )
      throw new WcsException( WcsException.Code.MissingParameterValue, "BBOX", "BBOX and/or TIME required.");
    if ( bbox != null )
      bboxLatLonRect = parseBoundingBox( bbox, coverage.getCoordinateSystem());
    if ( time != null )
      timeRange = parseTime( time);

    // WIDTH, HEIGHT, DEPTH parameters not needed since the only interpolation method is "NONE".
    // RESX, RESY, RESZ parameters not needed since the only interpolation method is "NONE".

    // Assign and validate RangeSubset parameter.
    if ( rangeSubset != null )
            axisSubset = parseRangeSubset( rangeSubset, coverage.getRange());

    // Assign and validate FORMAT parameter.
    if ( format == null )
    {
      log.error( "GetCoverage(): FORMAT parameter required.");
      throw new WcsException( WcsException.Code.InvalidParameterValue, "FORMAT", "FORMAT parameter required.");
    }
    if ( ! format.equalsIgnoreCase( this.coverage.getAllowedCoverageFormat() ))
    {
      throw new WcsException( WcsException.Code.InvalidFormat, "", "Request format <" + format + "> now allowed <" + this.coverage.getAllowedCoverageFormat() + ">");
    }
  }

  public File writeCoverageDataToFile()
          throws WcsException
  {
    return this.coverage.writeCoverageDataToFile( this.bboxLatLonRect,
                                                  this.axisSubset,
                                                  this.timeRange);
  }

  private LatLonRect parseBoundingBox( String bbox, GridCoordSystem gcs)
          throws WcsException
  {
    if ( bbox == null || bbox.equals( "") )
      return null;

    String[] bboxSplit = bbox.split( ",");
    if ( bboxSplit.length != 4)
    {
      log.error( "parseBoundingBox(): BBOX <" + bbox + "> not limited to X and Y." );
      throw new WcsException( WcsException.Code.InvalidParameterValue, "BBOX", "BBOX <"+bbox+"> has more values <" + bboxSplit.length + "> than expected <4>.");
    }
    double minx = Double.parseDouble( bboxSplit[0] );
    double miny = Double.parseDouble( bboxSplit[1] );
    double maxx = Double.parseDouble( bboxSplit[2] );
    double maxy = Double.parseDouble( bboxSplit[3] );

    boolean includesNorthPole = false;
    int[] resultNP = new int[2];
    resultNP = gcs.findXYindexFromLatLon( 90.0, 0, null );
    if ( resultNP[0] == -1 || resultNP[1] == -1 ) includesNorthPole = true;
    boolean includesSouthPole = false;
    int[] resultSP = new int[2];
    resultSP = gcs.findXYindexFromLatLon( -90.0, 0, null );
    if ( resultSP[0] == -1 || resultSP[1] == -1 ) includesSouthPole = true;


    LatLonPointImpl minll = new LatLonPointImpl( miny, minx );
    LatLonPointImpl maxll = new LatLonPointImpl( maxy, maxx );

    LatLonRect bboxLatLonRect = new LatLonRect( minll, maxll );

//    if ( ! bboxLatLonRect.containedIn( covLatLonRect))
//    {
//      log.error( "parseBoundingBox(): BBOX <" + bbox + "> not contained in coverage BBOX <"+ covLatLonRect.toString2()+">.");
//      throw new WcsException( WcsException.Code.InvalidParameterValue, "BBOX", "BBOX <" + bbox + "> not contained in coverage.");
//    }

    return bboxLatLonRect;
  }

  private DateRange parseTime( String time )
          throws WcsException
  {
    if ( time == null || time.equals( ""))
      return null;

    DateRange dateRange;

    try
    {
      if ( time.indexOf( ",") != -1 )
      {
        log.error( "parseTime(): Unsupported time parameter (list) <" + time + ">.");
        throw new WcsException( WcsException.Code.InvalidParameterValue, "TIME",
                                "Not currently supporting time list." );
        //String[] timeList = time.split( "," );
        //dateRange = new DateRange( date, date, null, null );
      }
      else if ( time.indexOf( "/") != -1 )
      {
        String[] timeRange = time.split( "/" );
        if ( timeRange.length != 2)
        {
          log.error( "parseTime(): Unsupported time parameter (time range with resolution) <" + time + ">.");
          throw new WcsException( WcsException.Code.InvalidParameterValue, "TIME", "Not currently supporting time range with resolution.");
        }
        dateRange = new DateRange( new DateType( timeRange[0], null, null ),
                                   new DateType( timeRange[1], null, null ), null, null );
      }
      else
      {
        DateType date = new DateType( time, null, null );
        dateRange = new DateRange( date, date, null, null );
      }
    }
    catch ( ParseException e )
    {
      log.error( "parseTime(): Failed to parse time parameter <" + time + ">: " + e.getMessage() );

      throw new WcsException( WcsException.Code.InvalidParameterValue, "TIME",
                              "Invalid time format <" + time + ">." );
    }

    return dateRange;
  }

  private List<AxisSubset> parseRangeSubset( String rangeSubset, List<WcsRangeField> range)
          throws WcsException
  {
    if ( rangeSubset == null || rangeSubset.equals( "" ) )
      return null;

    AxisSubset range;

    if ( rangeSubset.indexOf( "," ) != -1 )
    {
      log.error( "parseRangeSubset(): Vertical value list not supported <" + rangeSubset + ">." );
      throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Not currently supporting list of Vertical values (just range, i.e., \"min/max\")." );
    }
    else if ( rangeSubset.indexOf( "/" ) != -1 )
    {
      String[] rangeSplit = rangeSubset.split( "/" );
      if ( rangeSplit.length != 2 )
      {
        log.error( "parseRangeSubset(): Unsupported Vertical value (range with resolution) <" + rangeSubset + ">." );
        throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Not currently supporting vertical range with resolution." );
      }
      double minValue = 0;
      double maxValue = 0;
      try
      {
        minValue = Double.parseDouble( rangeSplit[0] );
        maxValue = Double.parseDouble( rangeSplit[1] );
      }
      catch ( NumberFormatException e )
      {
        log.error( "parseRangeSubset(): Failed to parse Vertical range min or max <" + rangeSubset + ">: " + e.getMessage() );
        throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Failed to parse Vertical range min or max." );
      }
      if ( minValue > maxValue)
      {
        log.error( "parseRangeSubset(): Vertical range must be \"min/max\" <" + rangeSubset + ">." );
        throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Vertical range must be \"min/max\"." );
      }
      range = new AxisSubset( minValue, maxValue, 1);
    }
    else
    {
      if ( ! coverage.getRangeField().getAxis().getValues().contains( rangeSubset))
      {
        log.error( "parseRangeSubset(): Unrecognized RangeSet Axis value <" + rangeSubset + ">." );
        throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(),
                                "Unrecognized RangeSet Axis value <" + rangeSubset + ">." );
      }
      else
      {
        double value = 0;
        try
        {
          value = Double.parseDouble( rangeSubset );
        }
        catch ( NumberFormatException e )
        {
          log.error( "parseRangeSubset(): Failed to parse Vertical value <" + rangeSubset + ">: " + e.getMessage() );
          throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Failed to parse Vertical value." );
        }
        range = new AxisSubset( value, value, 1 );
      }
    }

    if ( range == null)
    {
      log.error( "parseRangeSubset(): Invalid Vertical range requested <" + rangeSubset + ">." );
      throw new WcsException( WcsException.Code.InvalidParameterValue, coverage.getRangeField().getAxis().getName(), "Invalid Vertical range requested." );
    }

    return range;
  }

}
