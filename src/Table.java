import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;

import Exceptions.DBAppException;

public class Table implements Serializable {
	private Vector<Page> pages;
	private String name;
	private String clusteringKey;
	private Vector<Tuple> records;
	private Vector<Column> columns;
	private int pageCount;
	
	public int getPageCount() {
		return pages.size();
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}

	public void serialize(String name) throws DBAppException {
		try {
			FileOutputStream fileOut = new FileOutputStream(name + ".ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			throw new DBAppException("Failed to serialize database: " + e.getMessage());
		}
	}
	
	public Object deserialize(String name) throws IOException, ClassNotFoundException, DBAppException {

		try {
			FileInputStream fileIn = new FileInputStream(name);
			ObjectInputStream objectIn = new ObjectInputStream(fileIn);
			Object o = objectIn.readObject();
			objectIn.close();
			fileIn.close();
			return o;
		} catch (IOException e) {
			throw new DBAppException("Table not found");
		}
	}
	public Table(String strTableName, String strClusteringKeyColumn, Hashtable<String, String> htblColNameType,
			Hashtable<String, String> htblColNameMin, Hashtable<String, String> htblColNameMax) throws DBAppException {
		this.pages = new Vector<Page>();
		this.records = new Vector<Tuple>();
		this.columns = new Vector<Column>();
		this.name = strTableName;
		this.pageCount=0;
		this.clusteringKey = strClusteringKeyColumn;

		for (String col : htblColNameType.keySet()) {
			String colType = htblColNameType.get(col);
			String colMin = htblColNameMin.get(col);
			String colMax = htblColNameMax.get(col);
			this.columns.add(new Column(col, colType, colMin, colMax));
		}
		boolean flag = false;
		for (Column i : this.columns) {
			if (i.getName() == strClusteringKeyColumn)
				flag = true;
		}
		if (!flag) {
			throw new DBAppException("Table doesn't have clustering key");
		}
	}
	 public Table(String name) {
	        this.name = name;
	        this.clusteringKey = null;
	        this.columns = new Vector<Column>();
	        records = new Vector<>();
	        pageCount=0;
	    }
	public Vector<Page> getPages() {
		return pages;
	}
	
	public void addPage(Tuple t) throws DBAppException {
		Page temp=new Page();
		pageCount++;
		temp.addToPage(t);
		this.pages.add(temp);
		temp.serialize(this.getName(), pageCount);
	}
	

	public void setPages(Vector<Page> pages) {
		this.pages = pages;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getClusteringKey() {
		return clusteringKey;
	}

	public void setClusteringKey(String clusteringKey) {
		this.clusteringKey = clusteringKey;
	}

	public Vector<Tuple> getRecords() {
		return records;
	}

	public void setRecords(Vector<Tuple> records) {
		this.records = records;
	}

	public Vector<Column> getColumns() {
		return columns;
	}

	public void setColumns(Vector<Column> columns) {
		this.columns = columns;
	}
	public void addColumns(Column col) {
		this.columns.add(col);
	}

}