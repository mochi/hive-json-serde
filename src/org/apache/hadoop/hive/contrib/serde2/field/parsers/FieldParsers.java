package org.apache.hadoop.hive.contrib.serde2.field.parsers;
/**
 * All the parsers that we needed in this project are located here
 * The parsers a are static.
 * @author guy
 *
 */
public class FieldParsers {
	
	public class IntegerFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Integer.valueOf(o);
		}		
	}
	public class BooleanFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Boolean.valueOf(o);
		}		
	}
	public class DoubleFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Double.valueOf(o);
		}		
	}
	public class FloatFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Float.valueOf(o);
		}		
	}
	public class TinyintFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Byte.valueOf(o);
		}		
	}
	public class LongFieldParser implements IFieldParser{
		@Override
		public Object parse(String o) throws Exception {
			return Long.valueOf(o);
		}		
	}	
	
	private static final FieldParsers instance = new FieldParsers();
	public static final IFieldParser INTEGER_PARSER = instance.new IntegerFieldParser();
	public static final IFieldParser DOUBLE_PARSER = instance.new DoubleFieldParser();
	public static final IFieldParser FLOAT_PARSER = instance.new FloatFieldParser();
	public static final IFieldParser TINYINT_PARSER = instance.new TinyintFieldParser();
	public static final IFieldParser BOOLEAN_PARSER = instance.new BooleanFieldParser();
	public static final IFieldParser LONG_PARSER = instance.new LongFieldParser();	
	
}
