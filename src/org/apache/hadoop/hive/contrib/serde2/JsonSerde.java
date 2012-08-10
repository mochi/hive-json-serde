/**
 * JSON SerDe for Hive
 */
package org.apache.hadoop.hive.contrib.serde2;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.contrib.serde2.field.parsers.FieldParsers;
import org.apache.hadoop.hive.contrib.serde2.field.parsers.IFieldParser;
import org.apache.hadoop.hive.serde.Constants;
import org.apache.hadoop.hive.serde2.SerDe;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSON SerDe for Hive
 * <p>
 * This SerDe can be used to read data in JSON format. For example, if your JSON
 * files had the following contents:
 * 
 * <pre>
 * {"field1":"data1","field2":100,"field3":"more data1"}
 * {"field1":"data2","field2":200,"field3":"more data2"}
 * {"field1":"data3","field2":300,"field3":"more data3"}
 * {"field1":"data4","field2":400,"field3":"more data4"}
 * </pre>
 * 
 * The following steps can be used to read this data:
 * <ol>
 * <li>Build this project using <code>ant build</code></li>
 * <li>Copy <code>hive-json-serde.jar</code> to the Hive server</li>
 * <li>Inside the Hive client, run
 * 
 * <pre>
 * ADD JAR /home/hadoop/hive-json-serde.jar;
 * </pre>
 * 
 * </li>
 * <li>Create a table that uses files where each line is JSON object
 * 
 * <pre>
 * CREATE EXTERNAL TABLE IF NOT EXISTS my_table (
 *    field1 string, field2 int, field3 string
 * )
 * ROW FORMAT SERDE 'org.apache.hadoop.hive.contrib.serde2.JsonSerde'
 * LOCATION '/my_data/my_table/';
 * </pre>
 * 
 * </li>
 * <li>Copy your JSON files to <code>/my_data/my_table/</code>. You can now
 * select data using normal SELECT statements
 * 
 * <pre>
 * SELECT * FROM my_table LIMIT 10;
 * </pre>
 * 
 * 
 * </li>
 * </ol>
 * <p>
 * The table does not have to have the same columns as the JSON files, and
 * vice-versa. If the table has a column that does not exist in the JSON object,
 * it will have a NULL value. If the JSON file contains fields that are not
 * columns in the table, they will be ignored and not visible to the table.
 * 
 * Conduit Additions:
 * <p>
 * The json can be read with all values as string, and the serde will be
 * reposible to return the correct type of the value, using the type of the
 * column
 * </p>
 * 
 * <p>
 * a specail column name: json_vaue... if you add this column name to your
 * table... it will the entire JSON you have
 * </p>
 * 
 * @see <a href="http://code.google.com/p/hive-json-serde/">hive-json-serde on
 *      Google Code</a>
 * @author Peter Sankauskas
 */
public class JsonSerde implements SerDe {
	private static final String NULL = "null";

	private static final String ROW_KEY = "json_value";

	/**
	 * Apache commons logger
	 */
	private static final Log LOG = LogFactory.getLog(JsonSerde.class.getName());

	/**
	 * The number of columns in the table this SerDe is being used with
	 */
	private int numColumns;

	/**
	 * List of column names in the table
	 */
	private List<String> columnNames;

	/**
	 * An ObjectInspector to be used as meta-data about a deserialized row
	 */
	private StructObjectInspector rowOI;

	/**
	 * List of row objects
	 */
	private ArrayList<Object> row;

	/**
	 * List of column type information
	 */
	private List<TypeInfo> columnTypes;

	/**
	 * A map from column names to json names.
	 */
	private HashMap<String, String> renames;

	/**
	 * Initialize this SerDe with the system properties and table properties
	 * 
	 */
	@Override
	public void initialize(Configuration sysProps, Properties tblProps)
			throws SerDeException {
		try {
			LOG.debug("Initializing JsonSerde");

			// Get the names of the columns for the table this SerDe is being
			// used
			// with
			String columnNameProperty = tblProps
					.getProperty(Constants.LIST_COLUMNS);
			columnNames = Arrays.asList(columnNameProperty.split(","));

			// Convert column types from text to TypeInfo objects
			String columnTypeProperty = tblProps
					.getProperty(Constants.LIST_COLUMN_TYPES);
			columnTypes = TypeInfoUtils
					.getTypeInfosFromTypeString(columnTypeProperty);
			assert columnNames.size() == columnTypes.size();
			numColumns = columnNames.size();

			// Create ObjectInspectors from the type information for each column
			List<ObjectInspector> columnOIs = new ArrayList<ObjectInspector>(
					columnNames.size());
			ObjectInspector oi;
			for (int c = 0; c < numColumns; c++) {
				oi = TypeInfoUtils
						.getStandardJavaObjectInspectorFromTypeInfo(columnTypes
								.get(c));
				columnOIs.add(oi);
			}
			rowOI = ObjectInspectorFactory.getStandardStructObjectInspector(
					columnNames, columnOIs);

			// Create an empty row object to be reused during deserialization
			row = new ArrayList<Object>(numColumns);
			for (int c = 0; c < numColumns; c++) {
				row.add(null);
			}

			// Read rename properties.
			renames = new HashMap<String, String>();
			String renameProperty = tblProps.getProperty("rename_columns");
			if (renameProperty != null) {
				String[] individualRenames = renameProperty.split(",");
				for (String rename : individualRenames) {
					String[] fromTo = rename.split(">");
					renames.put(fromTo[1], fromTo[0]);
				}
			}

			LOG.debug("JsonSerde initialization complete");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Gets the ObjectInspector for a row deserialized by this SerDe
	 */
	@Override
	public ObjectInspector getObjectInspector() throws SerDeException {
		return rowOI;
	}

	private static Object[] convertJsonArrayToJavaArray(JSONArray arr) {
		int length = arr.length();
		Object[] retVal = new Object[length];
		for (int i = 0; i < length; i++) {
			try {
				Object value = arr.get(i);
				if (value instanceof JSONObject){
					value = convertJsonMaptoJavaMap((JSONObject) value);
				}
				retVal[i] = value;
				
				if (arr.isNull(i)) {
					retVal[i] = null;
				}
			} catch (JSONException je) {
				retVal[i] = je.getMessage();
			}
		}

		return retVal;

	}

	/**
	 * Deserialize a JSON Object into a row for the table
	 */
	@Override
	public Object deserialize(Writable blob) throws SerDeException {
		Text rowText = (Text) blob;
		LOG.debug("Deserialize row: " + rowText.toString());

		// Try parsing row into JSON object
		JSONObject jObj;
		try {
			jObj = new JSONObject(rowText.toString()) {
				/**
				 * In Hive column names are case insensitive, so lower-case all
				 * field names
				 * 
				 * @see org.json.JSONObject#put(java.lang.String,
				 *      java.lang.Object)
				 */
				@Override
				public JSONObject put(String key, Object value)
						throws JSONException {
					return super.put(key.toLowerCase(), value);
				}
			};
		} catch (JSONException e) {
			// If row is not a JSON object, make the whole row NULL
			LOG.error("Row is not a valid JSON Object - JSONException: "
					+ e.getMessage());
			return null;
		}

		// Loop over columns in table and set values
		String colName;
		Object value;
		for (int c = 0; c < numColumns; c++) {
			colName = columnNames.get(c);
			if (renames.containsKey(colName)) {
				colName = renames.get(colName);
			}
			TypeInfo ti = columnTypes.get(c);
			try {
				/**
				 * if the col name is ROW_KEY we return the raw string of the
				 * json This way we don't force the user to declare all the
				 * columns ahead
				 */
				if (colName.equalsIgnoreCase(ROW_KEY)) {
					value = rowText.toString();
				}
				// Get type-safe JSON values
				else if (jObj.isNull(colName)) {
					value = null;
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.DOUBLE_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.DOUBLE_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.BIGINT_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.LONG_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.INT_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.INTEGER_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.TINYINT_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.TINYINT_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.FLOAT_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.FLOAT_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.BOOLEAN_TYPE_NAME)) {
					value = getValue(jObj, colName, FieldParsers.BOOLEAN_PARSER);
				} else if (ti.getTypeName().equalsIgnoreCase(
						Constants.STRING_TYPE_NAME)
						&& jObj.get(colName) instanceof java.lang.Number) {
					// convert numbers to strings if need be
					value = jObj.getString(colName);
				} else if (ti.getTypeName()
						.startsWith(Constants.LIST_TYPE_NAME)
						&& jObj.get(colName) instanceof JSONArray) {
					value = convertJsonArrayToJavaArray(jObj
							.getJSONArray(colName));
				} else if (ti.getTypeName().startsWith(Constants.MAP_TYPE_NAME)) {
					
					value = convertJsonMaptoJavaMap(jObj.getJSONObject(colName));

				} else {
					// Fall back, just get an object
					value = jObj.get(colName);
					// incase of JSONArray the json_object eval udf is not
					// supported
					// so we are adding the array to the the JSONOBject
					// containet
					// And than we can query the array using jeval
					
					if (value instanceof org.json.JSONArray) {
						JSONObject container = new JSONObject();
						container.put("array", value);
						value = container.toString();
					}
					
					value = value.toString();

				}
			} catch (JSONException e) {
				// If the column cannot be found, just make it a NULL value and
				// skip over it
				if (LOG.isDebugEnabled()) {
					LOG.debug("Column '" + colName + "' not found in row: "
							+ rowText.toString() + " - JSONException: "
							+ e.getMessage());
				}
				value = null;
			}
			row.set(c, value);
		}

		return row;
	}

	private static Map convertJsonMaptoJavaMap(JSONObject jsonObject)
			throws JSONException {
		Map<Object, Object> result = new HashMap<Object, Object>();
		Iterator<String> keys = jsonObject.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			result.put(key, jsonObject.get(key).toString());

		}
		return result;
	}

	private Object getValue(JSONObject jobj, String colName,
			IFieldParser fparser) {
		Object value = null;
		try {
			value = jobj.get(colName);
			if (value != null && value instanceof String) {
				String stringValue = (String) value;
				if (!NULL.equalsIgnoreCase(stringValue))
					value = fparser.parse(stringValue);
				else
					value = null;
			}
		} catch (Exception e) {
		}
		return value;
	}

	/**
	 * Not sure - something to do with serialization of data
	 */
	@Override
	public Class<? extends Writable> getSerializedClass() {
		return Text.class;
	}

	/**
	 * Serializes a row of data into a JSON object
	 * 
	 * @todo Implement this - sorry!
	 */
	@Override
	public Writable serialize(Object obj, ObjectInspector objInspector)
			throws SerDeException {
		LOG.info("-----------------------------");
		LOG.info("--------- serialize ---------");
		LOG.info("-----------------------------");
		LOG.info(obj.toString());
		LOG.info(objInspector.toString());

		return null;
	}
}
