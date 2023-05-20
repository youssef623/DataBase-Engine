import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;

public class Tuple implements Serializable {
	private Vector<Object> values;
	private Hashtable<String, Object> colNameValue;

	public Tuple(Vector<Column> TableColumns, Hashtable<String, Object> tablecolNameValue) {
		
		this.values = new Vector<>();
		this.colNameValue = new Hashtable<>();
		for (int i = 0; i < TableColumns.size(); i++) {
			Object value = tablecolNameValue.get(TableColumns.get(i).getName());
			if (value == null) {
				value = "null";
			}
			values.add(value);
			colNameValue.put((String) TableColumns.get(i).getName(), value);
		}
	}


	public Hashtable<String, Object> getColNameValue() {
		return colNameValue;
	}

	public void setColNameValue(Hashtable<String, Object> colNameValue) {
		this.colNameValue = colNameValue;
	}
	
	public boolean equals(Object o) {
		Tuple t=(Tuple)o;
		return this.colNameValue.equals(t.getColNameValue());
	}

}

