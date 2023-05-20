import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;

public class value implements Serializable{
	 Hashtable<String, Object> data;
	Vector<Integer> reference=new Vector<Integer>();
	Vector<Object> primaryKey=new Vector<Object>();
	Vector<Point> ref=new Vector<Point>();
	
	
	public value(Hashtable<String, Object> data, Object primary, int reference) {
		this.data = data;
		this.reference.add(reference);
		if(primary instanceof Vector<?>) {
			for(Object o: (Vector<Object>)primary) {
				this.primaryKey.add(o);
			}
		}
		else
			this.primaryKey.add(primary);
		
		ref.add(new Point(reference, primary));
		
	}
	
	public value(Hashtable<String, Object> data,  Object primary, Vector<Integer> reference) {
		this.data = data;
		this.reference.addAll(reference);
		if(primary instanceof Vector<?>) {
			for(Object o: (Vector<Object>)primary) {
				this.primaryKey.add(o);
			}
		}
		else
			this.primaryKey.add(primary);
		for(int x:reference) {
			ref.add(new Point(x, primary));
		}
	}
	public value(Hashtable<String, Object> data,  Vector<Point> ref) {
		this.data = data;
		this.ref=ref;
		for(Point p: ref) {
			this.reference.add(p.page);
			this.primaryKey.add(p.primaryKey);
		}
		
	}
	public Vector<Object> getPrimaryKey() {
		return primaryKey;
	}

	public void setPrimaryKey(Vector<Object> primaryKey) {
		this.primaryKey = primaryKey;
	}

	public Hashtable<String, Object> getData() {
		return data;
	}

	public void setData(Hashtable<String, Object> data) {
		this.data = data;
	}

	public Vector<Integer> getReference() {
		return reference;
	}

	public void setReference(Vector<Integer> reference) {
		this.reference = reference;
	}


	/*public void add(int ref, Object primary) {
		if(!this.reference.contains(ref))
		 this.reference.add(ref);
		Point p=
	}
	public void add(Vector<Integer> ref) {
		for(int i=0;i<ref.size();i++) {
			if(!this.reference.contains(ref.get(i)))
				 this.reference.add(ref.get(i));
		}
	}*/
	public boolean equals(Object o) {
		value temp=(value) o;
		if(this.getData().equals(temp.getData()))
			return true;
		return false;
	}
	
	
	
}
