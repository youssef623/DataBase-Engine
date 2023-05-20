import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class OctreeNode implements Serializable {
	Vector<value> values = new Vector<value>();
	OctreeNode[] children = new OctreeNode[8];
	Hashtable<String, Object> max;
	Hashtable<String, Object> min;
	Hashtable<String, Object> bounds;
	Vector<value> duplicates = new Vector<value>();
	String x;
	String y;
	String z;
	String primaryKey;
	private static final String CONFIG_FILE = "src/resources/DBApp.config";
	private static final Properties properties = new Properties();
	private static int treshold;

	public OctreeNode(Hashtable<String, Object> min, Hashtable<String, Object> max, Hashtable<String, Object> value,
			int reference, String clusteringKey) {
		String[] keys = max.keySet().toArray(new String[max.keySet().size()]);
		Arrays.sort(keys);
		x = keys[0];
		y = keys[1];
		z = keys[2];
		Hashtable<String, Object> insert = new Hashtable<String, Object>();
		insert.put(x, value.get(x));
		insert.put(y, value.get(y));
		insert.put(z, value.get(z));
		this.primaryKey = clusteringKey;
		value temp = new value(insert, value.get(clusteringKey), reference);
		this.values.add(temp);
		this.max = max;
		this.min = min;
		this.bounds = middleValue(max, min);

		try {
			properties.load(new FileInputStream(CONFIG_FILE));
			treshold = Integer.parseInt(properties.getProperty("MaximumEntriesinOctreeNode"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public OctreeNode(Hashtable<String, Object> min, Hashtable<String, Object> max, Hashtable<String, Object> value,
			Vector<Point> reference) {
		String[] keys = max.keySet().toArray(new String[max.keySet().size()]);
		Arrays.sort(keys);
		x = keys[0];
		y = keys[1];
		z = keys[2];

		Hashtable<String, Object> insert = new Hashtable<String, Object>();
		insert.put(x, value.get(x));
		insert.put(y, value.get(y));
		insert.put(z, value.get(z));
		value temp = new value(insert, reference);
		this.values.add(temp);
		this.max = max;
		this.min = min;
		this.bounds = middleValue(max, min);

		try {
			properties.load(new FileInputStream(CONFIG_FILE));
			treshold = Integer.parseInt(properties.getProperty("MaximumEntriesinOctreeNode"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public OctreeNode(Hashtable<String, Object> min, Hashtable<String, Object> max, Hashtable<String, Object> value,
			Vector<Integer> reference, Vector<Object> primaryKeys) {
		String[] keys = max.keySet().toArray(new String[max.keySet().size()]);
		Arrays.sort(keys);
		x = keys[0];
		y = keys[1];
		z = keys[2];

		Hashtable<String, Object> insert = new Hashtable<String, Object>();
		insert.put(x, value.get(x));
		insert.put(y, value.get(y));
		insert.put(z, value.get(z));
		value temp = new value(insert, primaryKeys, reference);
		this.values.add(temp);
		this.max = max;
		this.min = min;
		this.bounds = middleValue(max, min);

		try {
			properties.load(new FileInputStream(CONFIG_FILE));
			treshold = Integer.parseInt(properties.getProperty("MaximumEntriesinOctreeNode"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public OctreeNode(Hashtable<String, Object> min, Hashtable<String, Object> max, String clusteringKey) {
		this.max = max;
		this.min = min;
		this.bounds = middleValue(max, min);
		String[] keys = max.keySet().toArray(new String[max.keySet().size()]);
		Arrays.sort(keys);
		x = keys[0];
		y = keys[1];
		z = keys[2];
		this.primaryKey = clusteringKey;
		try {
			properties.load(new FileInputStream(CONFIG_FILE));
			treshold = Integer.parseInt(properties.getProperty("MaximumEntriesinOctreeNode"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void insert(Hashtable<String, Object> value, Vector<Object> cluster, Vector<Integer> reference) {
		Hashtable<String, Object> insert = new Hashtable<String, Object>();
		insert.put(this.x, value.get(x));
		insert.put(this.y, value.get(y));
		insert.put(this.z, value.get(z));

		if (this.duplicates(insert, reference, cluster)) {
			return;
		}
		if (this.isLeafNode() && values.size() < treshold) {
			value temp = new value(insert, cluster, reference);
			this.values.add(temp);
		} else if (this.isLeafNode() && values.size() >= treshold) {
			value temp = new value(insert, cluster, reference);
			this.values.add(temp);
			this.subdivide(this.bounds, value);
		} else {
			int x = hashComparater(this.bounds, value);
			if (this.children[x] == null) {
				if (x / 4 == 0)
					this.children[x] = new OctreeNode(this.min, this.bounds, value, reference, cluster);
				else
					this.children[x] = new OctreeNode(this.bounds, this.max, value, reference, cluster);
			} else
				this.children[x].insert(value, cluster, reference);
		}

	}

	public void insert(Hashtable<String, Object> value, String cluster, int reference) {
		Hashtable<String, Object> insert = new Hashtable<String, Object>();
		if (value.containsKey(x))
			insert.put(this.x, value.get(x));
		if (value.containsKey(y))
			insert.put(this.y, value.get(y));
		if (value.containsKey(z))
			insert.put(this.z, value.get(z));
		boolean dups = this.duplicates(insert, reference, value.get(cluster));
		if (dups) {
			return;
		}

		if (this.isLeafNode() && values.size() < treshold) {
			value temp = new value(insert, value.get(cluster), reference);
			this.values.add(temp);
		} else if (this.isLeafNode() && values.size() >= treshold) {
			value temp = new value(insert, value.get(cluster), reference);
			this.values.add(temp);
			this.subdivide(this.bounds, value);
		} else {
			int x = hashComparater(this.bounds, insert);
			if (this.children[x] == null) {
				if (x / 4 == 0) {
					this.children[x] = new OctreeNode(this.min, this.bounds, value, reference, cluster);
				} else {
					this.children[x] = new OctreeNode(this.bounds, this.max, value, reference, cluster);
				}
			} else {
				this.children[x].insert(value, cluster, reference);
			}
		}

	}

	public void print() {
		for (int i = 0; i < this.values.size(); i++) {
			System.out.println(this.values.get(i).getData() + " ref: " + this.values.get(i).getReference() + "prim: "
					+ this.values.get(i).getPrimaryKey());
		}
		for (int i = 0; i < 8; i++) {
			if (this.children[i] != null) {
				System.out.println("Child" + i + "---");
				this.children[i].print();

			}
		}
	}

	public void updateReference(int page) {
		if (this.isLeafNode()) {
			for (int i = 0; i < this.values.size(); i++) {
				for (int j = 0; j < this.values.get(i).reference.size(); j++) {
					if (this.values.get(i).reference.get(j) >= page) {
						this.values.get(i).reference.add(this.values.get(i).reference.get(j) - 1);
						this.values.get(i).reference.remove(j);
					}
				}
				for (int j = 0; j < this.values.get(i).ref.size(); j++) {
					Object primary;
					if (this.values.get(i).ref.get(j).page >= page) {
						if (this.values.get(i).ref.get(j).primaryKey instanceof Vector) {
							primary = ((Vector) this.values.get(i).ref.get(j).primaryKey).get(0);
						} else {
							primary = this.values.get(i).ref.get(j).primaryKey;
						}
						this.values.get(i).ref.add(new Point(this.values.get(i).ref.get(j).page - 1, primary));
						this.values.get(i).ref.remove(j);
					}
				}
			}
		} else {
			if (this.children[0] != null) {
				this.children[0].updateReference(page);
			}
			if (this.children[1] != null) {
				this.children[1].updateReference(page);
			}
			if (this.children[2] != null) {
				this.children[2].updateReference(page);
			}
			if (this.children[3] != null) {
				this.children[3].updateReference(page);
			}
			if (this.children[4] != null) {
				this.children[4].updateReference(page);
			}
			if (this.children[5] != null) {
				this.children[5].updateReference(page);
			}
			if (this.children[6] != null) {
				this.children[6].updateReference(page);
			}
			if (this.children[7] != null) {
				this.children[7].updateReference(page);
			}
		}
	}

	public void subdivide(Hashtable<String, Object> bounds, Hashtable<String, Object> value) {
		for (value v : this.values) {
			int x = hashComparater(this.bounds, v.getData());
			if (this.children[x] == null) {
				if (x / 4 == 0)
					this.children[x] = new OctreeNode(this.min, this.bounds, v.getData(), v.ref);
				else
					this.children[x] = new OctreeNode(this.bounds, this.max, v.getData(), v.ref);
			} else
				this.children[x].insert(v.getData(), v.getPrimaryKey(), v.getReference());
		}
		this.values.clear();
	}

	public Vector<Integer> findReferences(Hashtable<String, Object> bound, Hashtable<String, Object> toBeComparedWith) {
		int x, y, z;
		Vector<Integer> results = new Vector<Integer>();
		if (toBeComparedWith.containsKey(this.x) && toBeComparedWith.containsKey(this.y)
				&& toBeComparedWith.containsKey(this.z))
			results.add(hashComparater(bound, toBeComparedWith));
		else if (toBeComparedWith.containsKey(this.x) && toBeComparedWith.containsKey(this.y)) {
			x = comparator(bound.get(this.x), toBeComparedWith.get(this.x));
			y = comparator(bound.get(this.y), toBeComparedWith.get(this.y));
			if (x < 0 && y < 0) {
				results.add(0);
				results.add(1);
			} else if (x < 0 && y >= 0) {
				results.add(2);
				results.add(3);
			} else if (x >= 0 && y < 0) {
				results.add(4);
				results.add(5);
			} else {
				results.add(6);
				results.add(7);
			}
		} else if (toBeComparedWith.containsKey(this.x) && toBeComparedWith.containsKey(this.z)) {
			x = comparator(bound.get(this.x), toBeComparedWith.get(this.x));
			z = comparator(bound.get(this.z), toBeComparedWith.get(this.z));
			if (x < 0 && z < 0) {
				results.add(0);
				results.add(2);
			} else if (x < 0 && z >= 0) {
				results.add(1);
				results.add(3);
			} else if (x >= 0 && z < 0) {
				results.add(4);
				results.add(6);
			} else {
				results.add(5);
				results.add(7);
			}
		} else if (toBeComparedWith.containsKey(this.y) && toBeComparedWith.containsKey(this.z)) {
			y = comparator(bound.get(this.y), toBeComparedWith.get(this.y));
			z = comparator(bound.get(this.z), toBeComparedWith.get(this.z));
			if (y < 0 && z < 0) {
				results.add(0);
				results.add(4);
			} else if (y < 0 && z >= 0) {
				results.add(1);
				results.add(5);
			} else if (y >= 0 && z < 0) {
				results.add(2);
				results.add(6);
			} else {
				results.add(3);
				results.add(7);
			}
		} else if (toBeComparedWith.containsKey(this.x)) {
			x = comparator(bound.get(this.x), toBeComparedWith.get(this.x));
			if (x < 0) {
				results.add(0);
				results.add(1);
				results.add(2);
				results.add(3);
			} else {
				results.add(4);
				results.add(5);
				results.add(6);
				results.add(7);
			}
		} else if (toBeComparedWith.containsKey(this.y)) {
			y = comparator(bound.get(this.y), toBeComparedWith.get(this.y));
			if (y < 0) {
				results.add(0);
				results.add(1);
				results.add(4);
				results.add(5);
			} else {
				results.add(2);
				results.add(3);
				results.add(6);
				results.add(7);
			}
		} else if (toBeComparedWith.containsKey(this.z)) {
			z = comparator(bound.get(this.z), toBeComparedWith.get(this.z));
			if (z < 0) {
				results.add(0);
				results.add(2);
				results.add(4);
				results.add(6);
			} else {
				results.add(1);
				results.add(3);
				results.add(5);
				results.add(7);
			}
		}
		return results;

	}

	public int hashComparater(Hashtable<String, Object> bound, Hashtable<String, Object> toBeComparedWith) {
		int x, y, z;
		if (toBeComparedWith.containsKey(this.x) && toBeComparedWith.get(this.x) != null
				&& !toBeComparedWith.get(this.x).equals("null")) {
			x = comparator(bound.get(this.x), toBeComparedWith.get(this.x));
		} else
			x = 0;
		if (toBeComparedWith.containsKey(this.y) && toBeComparedWith.get(this.y) != null
				&& !toBeComparedWith.get(this.y).equals("null")) {
			y = comparator(bound.get(this.y), toBeComparedWith.get(this.y));
		} else
			y = 0;
		if (toBeComparedWith.containsKey(this.z) && toBeComparedWith.get(this.z) != null
				&& !toBeComparedWith.get(this.z).equals("null")) {
			z = comparator(bound.get(this.z), toBeComparedWith.get(this.z));
		} else
			z = 0;

		if (x < 0 && y < 0 && z < 0)
			return 0;
		else if (x < 0 && y < 0 && z >= 0)
			return 1;
		else if (x < 0 && y >= 0 && z < 0)
			return 2;
		else if (x < 0 && y >= 0 && z >= 0)
			return 3;
		else if (x >= 0 && y < 0 && z < 0)
			return 4;
		else if (x >= 0 && y < 0 && z >= 0)
			return 5;
		else if (x >= 0 && y >= 0 && z < 0)
			return 6;
		else if (x >= 0 && y >= 0 && z >= 0)
			return 7;
		return -1;

	}

	public static int comparator(Object o1, Object o2) {
		if (o1 instanceof Integer && o2 instanceof Integer) {
			return (int) o2 - (int) o1;
		} else if (o1 instanceof Double && o2 instanceof Double) {
			return (((Double) o2 - (Double) o1 < 0) ? -1 : 0);
		} else if (o1 instanceof String && o2 instanceof String) {
			return (int) ((((String) o2).toLowerCase()).compareTo(((String) o1).toLowerCase()));
		}
		return -2;
	}

	public void update(Hashtable<String, Object> find, Hashtable<String, Object> newValue) {
		this.searchUpdate(find, newValue);

	}

	public static Hashtable<String, Object> middleValue(Hashtable<String, Object> bound,
			Hashtable<String, Object> toBeComparedWith) {

		String[] keys = bound.keySet().toArray(new String[bound.keySet().size()]);
		Arrays.sort(keys);
		Object x = midValue(bound.get(keys[0]), toBeComparedWith.get(keys[0]));
		Object y = midValue(bound.get(keys[1]), toBeComparedWith.get(keys[1]));
		Object z = midValue(bound.get(keys[2]), toBeComparedWith.get(keys[2]));
		Hashtable<String, Object> middlevalues = new Hashtable<String, Object>();
		middlevalues.put(keys[0], x);
		middlevalues.put(keys[1], y);
		middlevalues.put(keys[2], z);
		return middlevalues;
	}

	public static Object midValue(Object secondValue, Object firstValue) {

		if (firstValue instanceof Integer && secondValue instanceof Integer) {
			return (int) Math.ceil(((int) firstValue + (int) secondValue) / 2.0);
		} else if (firstValue instanceof Double && secondValue instanceof Double) {
			return (Double) (Math.ceil((Double) firstValue + (Double) secondValue) / 2);
		} else if (firstValue instanceof String && secondValue instanceof String) {
			firstValue = ((String) firstValue).toLowerCase();
			secondValue = ((String) secondValue).toLowerCase();
			int length = Math.min(((String) firstValue).length(), ((String) secondValue).length());
			StringBuilder middleString = new StringBuilder(length);

			for (int i = 0; i < length; i++) {
				char sChar = ((String) firstValue).charAt(i);
				char tChar = ((String) secondValue).charAt(i);

				int asciiS = sChar;
				int asciiT = tChar;
				int asciiMiddle = (asciiS + asciiT) / 2;

				char middleChar = (char) asciiMiddle;
				middleString.append(middleChar);
			}
			return (String) middleString.toString();
		} else if (firstValue instanceof Date && secondValue instanceof Date) {
			LocalDate localDate1 = ((Date) firstValue).toInstant().atZone(java.time.ZoneId.systemDefault())
					.toLocalDate();
			LocalDate localDate2 = ((Date) secondValue).toInstant().atZone(java.time.ZoneId.systemDefault())
					.toLocalDate();

			long daysBetween = ChronoUnit.DAYS.between(localDate1, localDate2);

			LocalDate middleDate = localDate1.plusDays(daysBetween / 2);
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
			String formattedDate = middleDate.format(formatter); // Format the LocalDate object
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd"); // specify the format of the string
			Date date;
			try {
				date = dateFormat.parse(formattedDate);
				return (Date) date;
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return -2;
	}

	public boolean isLeafNode() {
		return this.children == null || this.children.length == 0
				|| (this.children[0] == null && this.children[1] == null && this.children[2] == null
						&& this.children[3] == null && this.children[4] == null && this.children[5] == null
						&& this.children[6] == null && this.children[7] == null);
	}

	public Vector<Point> search(Hashtable<String, Object> searchFor) {
		Vector<Point> result = new Vector<Point>();
		Hashtable<String, Object> toBeSearched = new Hashtable<String, Object>();
		boolean check = true;

		if (searchFor.containsKey(x))
			toBeSearched.put(x, searchFor.get(x));
		if (searchFor.containsKey(y))
			toBeSearched.put(y, searchFor.get(y));
		if (searchFor.containsKey(z))
			toBeSearched.put(z, searchFor.get(z));
		if (!this.isLeafNode()) {
			Vector<Integer> child = findReferences(this.bounds, toBeSearched);
			for (int x : child) {
				if (this.children[x] != null) {
					result.addAll(this.children[x].search(toBeSearched));
				}
			}
		} else {
			for (value v : this.values) {
				for (String k : toBeSearched.keySet()) {
					if (!v.getData().get(k).equals(toBeSearched.get(k)))
						check = false;
				}
				if (check) {
					result.addAll(v.ref);

				}
				check = true;
			}
		}

		return result;
	}

	public value searchUpdate(Hashtable<String, Object> searchFor, Hashtable<String, Object> toBeInserted) {
		Hashtable<String, Object> toBeSearched = new Hashtable<String, Object>();
		boolean check = true;
		if (searchFor.containsKey(x))
			toBeSearched.put(x, searchFor.get(x));
		if (searchFor.containsKey(y))
			toBeSearched.put(y, searchFor.get(y));
		if (searchFor.containsKey(z))
			toBeSearched.put(z, searchFor.get(z));
		if (!this.isLeafNode()) {
			Vector<Integer> children = findReferences(this.bounds, searchFor);
			for (int i = 0; i < children.size(); i++) {
				if (this.children[children.get(i)] != null) {
					value result = this.children[children.get(i)].searchUpdate(searchFor, toBeInserted);
					if (result != null) {
						return result;
					}
				}
			}
		} else {
			for (value v : this.values) {
				if (v == null || v.getData() == null)
					break;
				for (String k : toBeSearched.keySet()) {
					if (!v.getData().get(k).equals(toBeSearched.get(k))) {
						check = false;
					}
				}
				if (check) {
					value temp = v;
					this.values.remove(v);
					return temp;
				}
				check = true;
			}
		}
		return null;
	}

	public Vector<Point> delete(Hashtable<String, Object> searchFor) {
		Vector<Point> res = new Vector<Point>();
		Hashtable<String, Object> toBeSearched = new Hashtable<String, Object>();
		boolean check = true;
		String[] keysToSearch = { this.x, this.y, this.z };
		for (String key : keysToSearch) {
			if (searchFor.containsKey(key)) {
				toBeSearched.put(key, searchFor.get(key));
			}
		}

		if (!this.isLeafNode()) {
			Vector<Integer> children = findReferences(this.bounds, searchFor);
			for (int i = 0; i < children.size(); i++) {
				if (this.children[children.get(i)] != null) {
					res.addAll(this.children[children.get(i)].delete(searchFor));
				}
			}
		} else {
			Vector<value> toBeRemoved = new Vector<value>();
			for (int i = 0; i < this.values.size(); i++) {
				value v = this.values.get(i);
				for (String k : toBeSearched.keySet()) {
					if (!v.getData().get(k).equals(toBeSearched.get(k))) {
						check = false;
						break;
					}
				}
				if (check) {
					res.addAll(v.ref);

					toBeRemoved.add(v);
				}
				check = true;
			}
			this.values.removeAll(toBeRemoved);
		}
		return res;
	}

	public void update(Hashtable<String, Object> find, int ref, Object value) {
		if (!isLeafNode()) {
			if (this.children[0] != null) {
				this.children[0].update(find, ref, value);
			}
			if (this.children[1] != null) {
				this.children[1].update(find, ref, value);
			}
			if (this.children[2] != null) {
				this.children[2].update(find, ref, value);
			}
			if (this.children[3] != null) {
				this.children[3].update(find, ref, value);
			}
			if (this.children[4] != null) {
				this.children[4].update(find, ref, value);
			}
			if (this.children[5] != null) {
				this.children[5].update(find, ref, value);
			}
			if (this.children[6] != null) {
				this.children[6].update(find, ref, value);
			}
			if (this.children[7] != null) {
				this.children[7].update(find, ref, value);
			}
		} else {
			boolean stillRef = false;
			Iterator<value> iter = this.values.iterator();
			while (iter.hasNext()) {
				value v = iter.next();
				Iterator<Point> pointIter = v.ref.iterator();
				while (pointIter.hasNext()) {
					Point e = pointIter.next();
					if (e.primaryKey.equals(value)) {
						pointIter.remove();
						continue;
					}
					if (e.page == ref) {
						stillRef = true;
					}
				}
				if (v.ref.size() == 0) {
					iter.remove();
					continue;
				}
				Iterator<Object> keyIter = v.primaryKey.iterator();
				while (keyIter.hasNext()) {
					Object k = keyIter.next();
					if (k.equals(value)) {
						keyIter.remove();
						if (v.primaryKey.size() == 0) {
							iter.remove();
							continue;
						}
						if (v.reference.contains(ref) && !stillRef) {
							v.reference.remove((Object) ref);
						}
					}
				}
				stillRef = false;
			}
		}
	}

	public boolean duplicates(Hashtable<String, Object> searchFor, int ref, Object clusterVal) {
		for (value v : this.values) {
			if (v.getData().equals(searchFor)) {
				if (!v.primaryKey.contains(clusterVal)) {
					v.primaryKey.add(clusterVal);
					if (!v.reference.contains(ref))
						v.reference.add(ref);
					v.ref.add(new Point(ref, clusterVal));
					return true;
				}

			}
		}
		return false;
	}

	public boolean duplicates(Hashtable<String, Object> searchFor, Vector<Integer> ref, Object clusterVal) {
		for (value v : this.values) {
			if (v.getData().equals(searchFor)) {
				if (!v.primaryKey.contains(clusterVal)) {
					v.primaryKey.add(clusterVal);
					if (!v.reference.containsAll(ref))
						v.reference.addAll(ref);
					for (int x : ref) {
						v.ref.add(new Point(x, clusterVal));
					}
					return true;
				}

			}
		}
		return false;
	}

	public Vector<Point> allPoints() {
		Vector<Point> result = new Vector<Point>();
		if (!isLeafNode()) {
			for (int i = 0; i < 8; i++) {
				if (this.children[i] != null) {
					for (value v : this.children[i].values) {
						for (Point e : v.ref) {
							if (!result.contains(e))
								result.add(e);
						}
					}
				}
			}
		} else {
			for (value v : this.values) {
				for (Point e : v.ref) {
					if (!result.contains(e))
						result.add(e);
				}
			}
		}
		return result;
	}

}
