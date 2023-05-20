import java.io.Serializable;
import java.util.Vector;

public class Point implements Serializable{
	Vector<Integer>pageNumber=new Vector<Integer>();
	Vector<Object>primaryKeys=new Vector<Object>();
	int page;
	Object primaryKey;
	public Point(Vector<Integer> pageNumber, Vector<Object> primaryKeys) {
		this.pageNumber = pageNumber;
		this.primaryKeys = primaryKeys;
	}
	public Point(int pageNumber, Object primaryKeys) {
		this.page = pageNumber;
		this.primaryKey = primaryKeys;
	}
	

}
