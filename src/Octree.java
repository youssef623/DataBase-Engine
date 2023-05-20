import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

import Exceptions.DBAppException;

public class Octree implements Serializable{

	private OctreeNode root;
	
	public Octree(Hashtable<String,Object> max, Hashtable<String,Object> min, String cluster) {
		this.root=new OctreeNode(min,max, cluster);
	}
	public void insert(Hashtable<String,Object> value, String cluster, int ref) {
		this.root.insert(value, cluster, ref);
	}
	public void print() {
		this.root.print();
	}
	public void update(Hashtable<String, Object> find, Hashtable<String, Object> newValue) {
		value v=this.root.searchUpdate(find,  newValue);
		if(v!=null) {
		for(String k: newValue.keySet()) {
			v.data.remove(k);
			v.data.put(k, newValue.get(k));
		}
		this.root.insert(v.getData(), v.primaryKey, v.reference);
		}
		
	}
	public Vector<Point> delete(Hashtable<String, Object> searchFor) {
		return this.root.delete(searchFor);
	}
	
	public void updateRef(Hashtable<String, Object> newValue, String cluster, int newPage) {
		value v=this.root.searchUpdate(newValue, newValue);
		if(v.getData()!=null);
			this.root.insert(newValue, cluster, newPage);
	}
	public void update(Hashtable<String, Object> find, String cluster, int page, Object value) {
		this.root.update(find, page, value);
		this.root.insert(find, cluster, page);
		
	}
	//decrement pages
	public void updateReference(int page) {
		this.root.updateReference(page);
	}
	public Vector<Point> search(Hashtable<String,Object> searchFor){
		return this.root.search(searchFor);
	}

	public Vector<Point> allPoints(){
		return this.root.allPoints();
	}
	public static void main(String[] args) {
		Hashtable<String,Object> max=new Hashtable<String,Object>();
		 max.put("age1", 100);
		 max.put("age2", 100.0);
		 max.put("age3", "menn");
		 Hashtable<String,Object> min=new Hashtable<String,Object>();
		 min.put("age1", 0);
		 min.put("age2", 0.0);
		 min.put("age3", "aaaa");
		

	}
	public void serialize(String name) throws DBAppException {
		try {
			FileOutputStream fileOut = new FileOutputStream(name + ".ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
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
}
