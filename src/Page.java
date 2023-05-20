import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;
import java.util.Vector;

import Exceptions.DBAppException;

public class Page implements Serializable {
	private Vector<Tuple> records;
	private int size;
	public Vector<Tuple> getRecords() {
		return records;
	}
	public void setRecords(Vector<Tuple> records) {
		this.records = records;
	}
	private static final String CONFIG_FILE = "src/resources/DBApp.config";
	private static final Properties properties = new Properties();
	static {
		try {
			properties.load(new FileInputStream(CONFIG_FILE));
		} catch (IOException e) {
			
		}
	}
	public Page() {	
		this.records=new Vector<Tuple>();
		this.size = 0;
	}
	public void addToPage(Tuple rec) {
		this.records.add(rec);
		this.size=this.size+1;
	}
	public boolean isEmpty() {
		return this.getRecords().size()==0;
	}
	public boolean isFull() {
		return this.getRecords().size()==Integer.parseInt(getMaximumRowsCountinTablePage());
	}
	public boolean sizeExceeded() {
		return this.getRecords().size()>Integer.parseInt(getMaximumRowsCountinTablePage());
	}
	public static String getMaximumRowsCountinTablePage() {
		return properties.getProperty("MaximumRowsCountinTablePage");
	}

	public static int getMaximumEntriesinOctreeNode() {
		return Integer.parseInt(properties.getProperty("MaximumEntriesinOctreeNode"));
	}
	public void serialize(String name, int x) throws DBAppException {
		try {
			FileOutputStream fileOut = new FileOutputStream(name+x+".ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
			//System.out.println("Serialized data is saved in " + name + ".ser");
		} catch (IOException e) {
			throw new DBAppException("Failed to serialize database: " + e.getMessage());
		}
	}
}
