package org.apache.hadoop.hive.contrib.serde2.field.parsers;

public interface IFieldParser {
	
	/**
	 * An interface that acts like a delegate,
	 * It helps us parsing the value to its type, when a string is provided
	 * @param o -value to be parsed
	 * @return the value in the correct type
	 * @throws Exception
	 */
	public Object parse(String o) throws Exception;

}
