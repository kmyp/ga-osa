package org.knime.geo.jsonwriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.LongValue;
import org.knime.core.data.MissingCell;
import org.knime.core.data.StringValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.geoutils.Constants;
import org.knime.geoutils.WriteShapefile;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.vividsolutions.jts.geom.Geometry;

/**
 * This is the model implementation of GeoJsonWriter.
 * 
 *
 * @author 
 */
public class GeoJsonWriterNodeModel extends NodeModel {
	
	  static final String CFG_LOC = "FilePath";
	  public final SettingsModelString jsonFileLoc =new SettingsModelString(CFG_LOC,"");
    
    /**
     * Constructor for the node model.
     */
    protected GeoJsonWriterNodeModel() {
    
        super(1, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {

    	String fname=jsonFileLoc.getStringValue().concat(".geojson");
    	BufferedDataTable inTable = inData[0];
    	
    	DataRow firstRow =  inTable.iterator().next();
    	DataCell firstCell = firstRow.getCell(0);
    	String featureStr = ((StringValue) firstCell).getStringValue();
    	String jsonStr = Constants.GetGeoJsonStr(featureStr);
    	String geomType = getGeomType(jsonStr);
    	
    	if (geomType.compareTo("Polygon") == 0)
    		geomType = "MultiPolygon";
    	if (geomType.compareTo("LineString") == 0)
    		geomType = "MultiLineString";    	
    	
    	int geomIndex = inTable.getSpec().findColumnIndex(Constants.GEOM);
    	int numberOfColumns = inTable.getSpec().getNumColumns();
    	
    	String schema = "the_geom:"+geomType+":srid="+Constants.GetSRID(Constants.GetCRS(featureStr))+",";
    	
    	for ( int col = 0; col < numberOfColumns; col++ ) {	
			if (col != geomIndex ) {
				schema += inTable.getSpec().getColumnSpec(col).getName()+":";
				DataColumnSpec columnSpec = inTable.getSpec().getColumnSpec(col);
				DataType t = columnSpec.getType();
				if (t.isCompatible(IntValue.class) )
					schema += "Integer";
				else if (t.isCompatible(LongValue.class) )
					schema += "Integer";
				else if (t.isCompatible(DoubleValue.class) )
					schema += "Double";
				else if (t.isCompatible(BooleanValue.class) )
					schema += "Boolean";
				else
					schema += "String";
				
				if ( col != numberOfColumns-1)
					schema += ",";
				
			}
		}
    	
    	String fname1 = fname.replace("\\", "/");
    	String typeDef = fname1.substring(fname1.lastIndexOf("/"),fname1.indexOf(".geojson"));
    	
    	SimpleFeatureType schemaDef = DataUtilities.createType(typeDef,schema);
		
    	List<SimpleFeature> feats = new ArrayList<SimpleFeature>();
    	
    	try{

    		for (DataRow row : inTable) {
    			
	    		DataCell geometryCell = row.getCell(geomIndex);
	    		
	    		if (geometryCell instanceof StringValue){
	    			String geoJsonString = ((StringValue) geometryCell).getStringValue();
	    			Geometry geo = Constants.FeatureToGeometry(geoJsonString);
	    			SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schemaDef);
					featureBuilder.add(geo);
					for ( int col = 0; col < numberOfColumns; col++ ) {	
						if (col != geomIndex ) {
							DataCell cell = row.getCell(col);
							if ( cell instanceof IntValue )
								featureBuilder.add(((IntValue) cell).getIntValue());
							else if ( cell instanceof LongValue )
								featureBuilder.add(((LongValue) cell).getLongValue());
							else if ( cell instanceof DoubleValue )
								featureBuilder.add(((DoubleValue) cell).getDoubleValue());
							else if (cell instanceof BooleanValue)
								featureBuilder.add(((BooleanValue) cell).getBooleanValue());
							else if (cell instanceof MissingCell)
								featureBuilder.add(DataType.getMissingCell());
							else
								featureBuilder.add( ((StringValue) cell).getStringValue());
						}
					}
					
					SimpleFeature feature = featureBuilder.buildFeature(null);
					feats.add(feature);
	    			
	    		}
    		}
    		
    		
    		FeatureCollection<SimpleFeatureType, SimpleFeature> features = new ListFeatureCollection(schemaDef, feats);
    		
    		GeometryJSON gjson = new GeometryJSON(Constants.JsonPrecision);
    		FeatureJSON io = new FeatureJSON(gjson);
    		StringWriter s = new StringWriter();
    		io.writeFeatureCollection(features, s);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			JsonParser jp = new JsonParser();
			JsonElement je = jp.parse(s.toString());
			String prettyJsonString = gson.toJson(je);
			FileWriter writer = new FileWriter(fname);
			writer.write(prettyJsonString);
			writer.close();
    	}
		catch (Exception e)
		{
			e.printStackTrace();
			
		}
    	
    	return new BufferedDataTable[]{};
	    	
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // TODO: generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {

    	if (jsonFileLoc.getStringValue() == null) {
			throw new InvalidSettingsException("No GeoJSON file name specified");
		}
    	
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
    	jsonFileLoc.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	jsonFileLoc.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	 jsonFileLoc.validateSettings(settings);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
     
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
    }
    
    private String getGeomType(String jsonStr)
    {
    	String[] type = jsonStr.split(":");
		String[] typeStr = type[1].split(",");
		String geomString = typeStr[0].substring(1, typeStr[0].length()-1);
    	return geomString;
    }

}

