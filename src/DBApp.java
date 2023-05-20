import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import Exceptions.DBAppException;

public class DBApp implements Serializable {
	private static final String META_FILE = "metadata.csv";
	private static final String CONFIG_FILE = "src/resources/DBApp.config";
	private static final Properties properties = new Properties();

	public DBApp() {

		init();
	}

	public void init() {
		try {
			File metaFile = new File(META_FILE);
			if (!metaFile.exists()) {
				metaFile.createNewFile();
			}
			properties.load(new FileInputStream(CONFIG_FILE));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void createTable(String strTableName, String strClusteringKeyColumn,
			Hashtable<String, String> htblColNameType, Hashtable<String, String> htblColNameMin,
			Hashtable<String, String> htblColNameMax) throws DBAppException, IOException {
		try {
			try {
				checkTables(strTableName, strClusteringKeyColumn, htblColNameType, htblColNameMin, htblColNameMax);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			Table temp = new Table(strTableName, strClusteringKeyColumn, htblColNameType, htblColNameMin,
					htblColNameMax);
			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(META_FILE, true));
				for (Column column : temp.getColumns()) {
					String isClusteringKey = Boolean.toString(column.getName().equals(strClusteringKeyColumn));
					bw.write(strTableName + "," + column.getName() + "," + column.getType() + "," + isClusteringKey
							+ "," + column.getIndexName() + "," + column.getIndexType() + "," + column.getMin() + ","
							+ column.getMax() + "\n");
				}
				bw.close();
				temp.serialize(strTableName);
				serialize();
				temp = null;
			} catch (IOException e) {
				throw new DBAppException("Failed to create table " + strTableName + ": " + e.getMessage());
			}
			String TableFile = strTableName + ".csv";
			File tempFile = new File(TableFile);
			if (!tempFile.exists()) {
				tempFile.createNewFile();
			}
		} catch (DBAppException e) {
			e.printStackTrace();
		}

	}

	public void checkTables(String strTableName, String clusteringKey, Hashtable<String, String> htblColNameType,
			Hashtable<String, String> htblColNameMin, Hashtable<String, String> htblColNameMax)
			throws DBAppException, IOException {
		if (clusteringKey == null || clusteringKey.toLowerCase() == "null") {
			throw new DBAppException("Clustering Key should be defined for Table");
		}
		if (htblColNameType.size() != htblColNameMin.size() && htblColNameMin.size() != htblColNameMax.size())
			throw new DBAppException("Inconsistent Data for Columns");
		if (!htblColNameType.containsKey(clusteringKey)) {
			throw new DBAppException("Clustering Key should be defined correctly for Table");
		}
		for (String type : htblColNameType.values()) {
			if (!(type.toLowerCase().equals("java.lang.integer") || type.toLowerCase().equals("java.lang.string")
					|| type.toLowerCase().equals("java.util.date") || type.toLowerCase().equals("java.lang.double"))) {
				throw new DBAppException("One or more data types are not supported!");
			}
		}
		for (String keys : htblColNameMin.keySet()) {
			if (!(htblColNameType.containsKey(keys) && htblColNameMax.containsKey(keys))) {
				throw new DBAppException("Inconsistent Data for Columns");
			} else if (htblColNameMin.get(keys).compareTo(htblColNameMax.get(keys)) > 0) {
				throw new DBAppException("Minimums and maximums aren't set properly for " + keys);
			}
		}
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(",");
			String tableName = parts[0];
			if (tableName.toLowerCase().equals(strTableName.toLowerCase())) {
				throw new DBAppException("Table with name " + strTableName + " already exists.");
			}
		}
		br.close();
	}

	public void loadintoTable(String strTableName, Table t) throws DBAppException {

		try {
			File temp = new File(strTableName + ".csv");
			temp.delete();
			temp.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(strTableName + ".csv", true));
			if (t.getPages().size() >= 1 && t.getPages().get(0).getRecords().size() >= 1) {
				Set<String> keys = t.getPages().get(0).getRecords().get(0).getColNameValue().keySet();
				for (int i = 0; i < t.getPages().size(); i++) {
					for (int j = 0; j < t.getPages().get(i).getRecords().size(); j++) {
						for (String k : keys)
							bw.write(t.getPages().get(i).getRecords().get(j).getColNameValue().get(k) + ",");

						bw.newLine();
					}
				}
			}
			bw.close();
			temp = null;
			t = null;
			System.gc();

		} catch (IOException e) {
			throw new DBAppException("Failed to create table " + strTableName + ": " + e.getMessage());
		}

	}

	public void insertIntoTable(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws DBAppException, ClassNotFoundException, IOException, ParseException {
		Table t = (Table) deserialize(strTableName + ".ser");
		Vector<Page> p = t.getPages();
		Tuple temp = new Tuple(t.getColumns(), htblColNameValue);
		Vector<String> indices = new Vector<String>();
		
		if (validate(strTableName, htblColNameValue) && validateNoPrimaryKey(strTableName, htblColNameValue)) {
			if (!noDuplicates(strTableName, htblColNameValue.get(t.getClusteringKey()))) {
				throw new DBAppException("Primary Key is duplicated");
			}
			String indexKey = checkIfPrimaryIndexed(strTableName, t.getClusteringKey());
			if (indexKey != null && !indexKey.equals("null")) {

				Octree index = (Octree) deserialize(strTableName + indexKey + ".ser");
				Vector<Point> points = index.allPoints();
				int x = correctPage(htblColNameValue.get(t.getClusteringKey()), points);		
				Page currP = (Page) deserialize(strTableName + x + ".ser");
				int rec=indexFinder(t.getClusteringKey(),currP,htblColNameValue.get(t.getClusteringKey()));
				currP.getRecords().insertElementAt(temp,rec);
				currP.serialize(strTableName, x);
				index.insert(htblColNameValue, t.getClusteringKey(), x);
				index.serialize(strTableName + indexKey);
				t.getPages().remove(x - 1);
				t.getPages().insertElementAt(currP, x - 1);
				t.serialize(strTableName);
				if (currP.sizeExceeded()) {
					fullPageHandler(t);
				}

			} else {
				BufferedReader br = new BufferedReader(new FileReader(META_FILE));
				String line = null;
				while ((line = br.readLine()) != null) {
					String[] parts = line.split(",");
					String tableName = parts[0];
					if (tableName.equals(strTableName)) {
						if (!parts[4].equals("null") && !indices.contains(parts[4])) {
							indices.add(parts[4]);
						}
					}
				}
				br.close();
				if (p.size() == 0) {
					for (String k : indices) {
						Octree index = (Octree) deserialize(strTableName + k + ".ser");
						index.insert(htblColNameValue, t.getClusteringKey(), 1);
						index.serialize(strTableName + k);
					}
					t.addPage(temp);
					File f = new File(strTableName + ".ser");
					f.delete();
					f = null;
					loadintoTable(strTableName, t);
					t.serialize(strTableName);
				} else if (!noDuplicates(strTableName, htblColNameValue.get(t.getClusteringKey()))) {
					throw new DBAppException("Primary Key is duplicated");
				} else {
					for (int i = 0; i < p.size(); i++) {

						int count = i + 1;
						Page existingPage = (Page) deserialize(strTableName + count + ".ser");
						if (existingPage.isFull()) {
							if (i == 0) {
								Tuple start = existingPage.getRecords().get(0);
								if (insertIntoFirstPage(t.getClusteringKey(), start, temp)) {
									for (String k : indices) {
										Octree index = (Octree) deserialize(strTableName + k + ".ser");
										index.insert(htblColNameValue, t.getClusteringKey(), count);
										index.serialize(strTableName + k);
									}
									existingPage.getRecords().insertElementAt(temp, 0);
									existingPage.serialize(strTableName, count);
									t.getPages().remove(i);
									t.getPages().insertElementAt(existingPage, i);
									t.serialize(strTableName);
									fullPageHandler(t);
									existingPage = null;
									start = null;
									loadintoTable(strTableName, t);
									break;
								}

							}
							Tuple start = existingPage.getRecords().get(0);
							Tuple end = existingPage.getRecords().get(existingPage.getRecords().size() - 1);
							if (checkCorrectPage(t.getClusteringKey(), start, end, temp)) {
								for (String k : indices) {
									Octree index = (Octree) deserialize(strTableName + k + ".ser");
									index.insert(htblColNameValue, t.getClusteringKey(), count);
									index.serialize(strTableName + k);
								}
								int index = indexGetter(t.getClusteringKey(), existingPage, temp);
								existingPage.getRecords().insertElementAt(temp, index);
								existingPage.serialize(strTableName, count);
								t.getPages().remove(i);
								t.getPages().insertElementAt(existingPage, i);
								t.serialize(strTableName);
								existingPage = null;
								start = null;
								end = null;
								loadintoTable(strTableName, t);
								break;

							}

							if (i == p.size() - 1) {
								start = existingPage.getRecords().get(0);
								if (insertNewPage(t.getClusteringKey(), start, temp)) {
									for (String k : indices) {
										Octree index = (Octree) deserialize(strTableName + k + ".ser");
										index.insert(htblColNameValue, t.getClusteringKey(), count);
										index.serialize(strTableName + k);
									}
									existingPage.getRecords().add(temp);
									existingPage.serialize(strTableName, count);
									t.getPages().remove(i);
									t.getPages().insertElementAt(existingPage, i);
									t.serialize(strTableName);
									fullPageHandler(t);
									existingPage = null;
									start = null;
									end = null;
									loadintoTable(strTableName, t);
									break;
								}

							}
							if (i < p.size() - 1) {
								Page nextPage = (Page) deserialize(strTableName + (count + 1) + ".ser");
								start = existingPage.getRecords().get(existingPage.getRecords().size() - 1);
								end = nextPage.getRecords().get(0);
								if (checkCorrectPage(t.getClusteringKey(), start, end, temp)) {
									for (String k : indices) {
										Octree index = (Octree) deserialize(strTableName + k + ".ser");
										index.insert(htblColNameValue, t.getClusteringKey(), count);
										index.serialize(strTableName + k);
									}
									existingPage.getRecords().add(temp);
									existingPage.serialize(strTableName, count);
									nextPage.serialize(strTableName, (count + 1));
									t.getPages().remove(i);
									t.getPages().insertElementAt(existingPage, i);
									t.serialize(strTableName);
									fullPageHandler(t);
									existingPage = null;
									start = null;
									end = null;
									nextPage = null;
									loadintoTable(strTableName, t);
									break;
								}
							}

							existingPage.serialize(strTableName, count);
						} else if (i == p.size() - 1) {
							for (String k : indices) {
								Octree index = (Octree) deserialize(strTableName + k + ".ser");
								index.insert(htblColNameValue, t.getClusteringKey(), count);
								index.serialize(strTableName + k);
							}
							int index = indexGetter(t.getClusteringKey(), existingPage, temp);
							existingPage.getRecords().insertElementAt(temp, index);
							existingPage.serialize(strTableName, count);
							t.getPages().remove(i);
							t.getPages().insertElementAt(existingPage, i);
							t.serialize(strTableName);
							loadintoTable(strTableName, t);
							existingPage = null;
							break;
						} else {
							Page nextPage = (Page) deserialize(strTableName + (count + 1) + ".ser");
							Tuple start = nextPage.getRecords().get(0);
							if (comparator(start.getColNameValue().get(t.getClusteringKey()),
									temp.getColNameValue().get(t.getClusteringKey())) > 0) {
								nextPage.serialize(strTableName, count + 1);
								continue;
							} else {
								for (String k : indices) {
									Octree index = (Octree) deserialize(strTableName + k + ".ser");
									index.insert(htblColNameValue, t.getClusteringKey(), count);
									index.serialize(strTableName + k);
								}
								int index = indexGetter(t.getClusteringKey(), existingPage, temp);
								existingPage.getRecords().insertElementAt(temp, index);
								existingPage.serialize(strTableName, count);
								t.getPages().remove(i);
								t.getPages().insertElementAt(existingPage, i);
								t.serialize(strTableName);
								existingPage = null;
								nextPage = null;
								start = null;
								loadintoTable(strTableName, t);
								break;
							}
						}
						existingPage = null;
					}
				}
				fullPageHandler(t);
			}
		} else {
			t.serialize(strTableName);

			throw new DBAppException("Data not matching Table definition");

		}
		t = null;
		p = null;
		temp = null;

	}

	public static void fullPageHandler(Table t) throws ClassNotFoundException, IOException, DBAppException {
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		Vector<String> indices = new Vector<String>();
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(",");
			String tableName = parts[0];
			if (tableName.equals(t.getName())) {
				if (!parts[4].equals("null") && !indices.contains(parts[4])) {
					indices.add(parts[4]);
				}
			}
		}
		br.close();
		Table tab = (Table) deserialize(t.getName() + ".ser");
		for (int c = 0; c < tab.getPages().size() - 1; c++) {
			tab = (Table) deserialize(t.getName() + ".ser");
			int count = c + 1;
			Page currentPage = (Page) deserialize(tab.getName() + count + ".ser");
			while (currentPage.sizeExceeded()) {
				currentPage = (Page) deserialize(tab.getName() + count + ".ser");
				tab = (Table) deserialize(t.getName() + ".ser");
				int next = count + 1;
				Page nextPage = (Page) deserialize(tab.getName() + next + ".ser");

				for (String k : indices) {
					Octree index = (Octree) deserialize(t.getName() + k + ".ser");
					index.updateRef(currentPage.getRecords().get(currentPage.getRecords().size() - 1).getColNameValue(),
							t.getClusteringKey(), next);
					index.serialize(t.getName() + k);
				}
				nextPage.getRecords().insertElementAt(currentPage.getRecords().get(currentPage.getRecords().size() - 1),
						0);

				currentPage.getRecords().remove(currentPage.getRecords().size() - 1);
				currentPage.serialize(tab.getName(), count);
				nextPage.serialize(tab.getName(), next);
				tab.getPages().remove(c + 1);
				tab.getPages().insertElementAt(nextPage, (c + 1));
				tab.getPages().remove(c);
				tab.getPages().insertElementAt(currentPage, c);
				tab.serialize(t.getName());
				tab = (Table) deserialize(t.getName() + ".ser");
			}
			tab = (Table) deserialize(t.getName() + ".ser");
			tab.getPages().remove(c);
			currentPage = (Page) deserialize(tab.getName() + count + ".ser");

			tab.getPages().insertElementAt(currentPage, c);
			tab.serialize(t.getName());
			currentPage.serialize(tab.getName(), count);
			currentPage = null;
		}
		tab = (Table) deserialize(t.getName() + ".ser");
		int x = tab.getPages().size();
		Page lastPage = (Page) deserialize(tab.getName() + x + ".ser");
		if (lastPage.sizeExceeded()) {
			for (String k : indices) {
				Octree index = (Octree) deserialize(t.getName() + k + ".ser");
				index.updateRef(lastPage.getRecords().get(lastPage.getRecords().size() - 1).getColNameValue(),
						t.getClusteringKey(), (x + 1));
				index.serialize(t.getName() + k);
			}
			tab.addPage(lastPage.getRecords().get(lastPage.getRecords().size() - 1));
			lastPage.getRecords().remove(lastPage.getRecords().size() - 1);
			lastPage.serialize(tab.getName(), x);
			x = tab.getPages().size();
			tab.getPages().remove(x - 2);
			tab.getPages().insertElementAt(lastPage, x - 2);
			tab.serialize(t.getName());
		}
		x = tab.getPages().size();
		while (lastPage.sizeExceeded()) {
			tab = (Table) deserialize(t.getName() + ".ser");
			x = tab.getPages().size();
			Page newPage = (Page) deserialize(tab.getName() + x + ".ser");
			newPage.getRecords().add(lastPage.getRecords().get(lastPage.getRecords().size() - 1));
			for (String k : indices) {
				Octree index = (Octree) deserialize(t.getName() + k + ".ser");
				index.updateRef(lastPage.getRecords().get(lastPage.getRecords().size() - 1).getColNameValue(),
						t.getClusteringKey(), (x + 1));
				index.serialize(t.getName() + k);
			}
			lastPage.getRecords().remove(lastPage.getRecords().size() - 1);
			newPage.serialize(tab.getName(), x);
			tab.getPages().remove(x - 2);
			tab.getPages().insertElementAt(lastPage, x - 2);
			tab.serialize(t.getName());
			tab.getPages().remove(x - 1);
			tab.getPages().insertElementAt(newPage, x - 1);
			tab.serialize(t.getName());
			newPage = null;

		}

		tab.serialize(tab.getName());
		tab = (Table) deserialize(t.getName() + ".ser");
		if (tab.getPages().get(tab.getPages().size() - 1).sizeExceeded()) {
			fullPageHandler(tab);
		}
		tab.serialize(tab.getName());
		tab = null;
		lastPage = null;
		System.gc();
	}

	public int indexGetter(String key, Page p, Tuple t) {
		int low = 0;
		int high = p.getRecords().size() - 1;
		while (low <= high) {
			int mid = low + (high - low) / 2;
			Object toBeinserted = t.getColNameValue().get(key);
			Object toBeComparedWith = p.getRecords().get(mid).getColNameValue().get(key);
			int x = comparator(toBeComparedWith, toBeinserted);
			if (x == 0)
				return mid + 1;
			else if (x > 0)
				low = mid + 1;
			else
				high = mid - 1;
		}
		p = null;
		t = null;
		System.gc();
		return low;
	}

	public int comparator(Object o1, Object o2) {
		Double result = 0.0;
		if (o1 instanceof Integer && o2 instanceof Integer) {
			result = (double) ((int) o2 - (int) o1);
		} else if (o1 instanceof Double && o2 instanceof Double) {
			result = (Double) o2 - (Double) o1;
		} else if (o1 instanceof String && o2 instanceof String) {
			result = (double) (((String) o2).toLowerCase()).compareTo(((String) o1).toLowerCase());
		} else if (o1 instanceof Date && o2 instanceof Date) {
			DateFormat format = new SimpleDateFormat("YYYY/MM/DD");
			String o1converted = format.format(o1);
			String o2converted = format.format(o2);
			result = (double) (((String) o2converted).toLowerCase()).compareTo(((String) o1converted).toLowerCase());

		}
		if (result > 0)
			return 1;
		else if (result < 0)
			return -1;
		else
			return 0;
	}

	public boolean checkCorrectPage(String clusterKey, Tuple s, Tuple e, Tuple i) {
		boolean correct = false;
		Object o1 = s.getColNameValue().get(clusterKey);
		Object o2 = e.getColNameValue().get(clusterKey);
		Object o3 = i.getColNameValue().get(clusterKey);
		if (o3 instanceof Integer && o2 instanceof Integer) {
			if ((int) o3 <= (int) o2 && (int) o3 >= (int) o1)
				correct = true;
		} else if (o3 instanceof Double && o2 instanceof Double) {
			if ((Double) o3 <= (Double) o2 && (Double) o3 >= (Double) o1)
				correct = true;
		} else if (o3 instanceof String && o2 instanceof String) {
			if (((String) o2).toLowerCase().compareTo(((String) o3).toLowerCase()) >= 0
					&& ((String) o1).toLowerCase().compareTo(((String) o3).toLowerCase()) <= 0)
				correct = true;
		} else if (o3 instanceof Date && o2 instanceof Date) {
			DateFormat format = new SimpleDateFormat("YYYY/MM/DD");
			String o3converted = format.format(o3);
			String o1converted = format.format(o1);
			String o2converted = format.format(o2);
			if (((String) o2converted).toLowerCase().compareTo(((String) o3converted).toLowerCase()) >= 0
					&& ((String) o1converted).toLowerCase().compareTo(((String) o3converted).toLowerCase()) <= 0)
				correct = true;
		}
		return correct;
	}

	public boolean insertNewPage(String clusterKey, Tuple s, Tuple i) {
		boolean correct = false;
		Object o1 = s.getColNameValue().get(clusterKey);
		Object o2 = i.getColNameValue().get(clusterKey);
		if (o1 instanceof Integer && o2 instanceof Integer) {
			if ((int) o2 >= (int) o1)
				correct = true;
		} else if (o2 instanceof Double && o1 instanceof Double) {
			if ((Double) o2 >= (Double) o1)
				correct = true;
		} else if (o1 instanceof String && o2 instanceof String) {
			if (((String) o2).toLowerCase().compareTo(((String) o1).toLowerCase()) >= 0)
				correct = true;
		} else if (o1 instanceof Date && o2 instanceof Date) {
			DateFormat format = new SimpleDateFormat("YYYY/MM/DD");
			String o1converted = format.format(o1);
			String o2converted = format.format(o2);
			if (((String) o2converted).toLowerCase().compareTo(((String) o1converted).toLowerCase()) >= 0)
				correct = true;
		}
		return correct;
	}

	public boolean insertIntoFirstPage(String clusterKey, Tuple s, Tuple i) {
		boolean correct = false;
		Object o1 = s.getColNameValue().get(clusterKey);
		Object o2 = i.getColNameValue().get(clusterKey);
		if (o1 instanceof Integer && o2 instanceof Integer) {
			if ((int) o2 <= (int) o1)
				correct = true;
		} else if (o2 instanceof Double && o1 instanceof Double) {
			if ((Double) o2 <= (Double) o1)
				correct = true;
		} else if (o1 instanceof String && o2 instanceof String) {
			if (((String) o2).toLowerCase().compareTo(((String) o1).toLowerCase()) <= 0)
				correct = true;
		} else if (o1 instanceof Date && o2 instanceof Date) {
			DateFormat format = new SimpleDateFormat("YYYY/MM/DD");
			String o1converted = format.format(o1);
			String o2converted = format.format(o2);
			if (((String) o2converted).toLowerCase().compareTo(((String) o1converted).toLowerCase()) <= 0)
				correct = true;
		}
		return correct;
	}

	public boolean noDuplicates(String strTableName, Object value)
			throws ClassNotFoundException, IOException, DBAppException {
		Table t = (Table) deserialize(strTableName + ".ser");
		String k = checkIfPrimaryIndexed(strTableName, t.getClusteringKey());
		if (k != null && !k.equals("null")) {
			System.out.println("Entered");
			Octree index = (Octree) deserialize(strTableName + k + ".ser");
			Hashtable<String, Object> temp = new Hashtable<String, Object>();
			temp.put(t.getClusteringKey(), value);
			if (index.search(temp).size() != 0) {
				System.out.println("Found");
				t = null;
				index = null;
				return false;
			} else {
				System.out.println("Safe");
				t = null;
				index = null;
				return true;
			}
		} else {
			System.out.println("Enteredddd");
			for (int i = 0; i < t.getPages().size(); i++) {
				for (int j = 0; j < t.getPages().get(i).getRecords().size(); j++) {
					if (t.getPages().get(i).getRecords().get(j).getColNameValue().contains(value))
						return false;
				}
			}
			t = null;
			System.gc();
			return true;
		}
	}

	public static boolean validate(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws DBAppException, NumberFormatException, ClassNotFoundException {
		try {

			BufferedReader br = new BufferedReader(new FileReader(META_FILE));
			String line = null;
			boolean found = false;
			boolean valid = true;
			Vector<String> colNames = new Vector<String>();

			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				String tableName = parts[0];
				colNames.add(parts[1]);
				if (tableName.equals(strTableName)) {
					found = true;
					if (parts[3].toUpperCase().equals("TRUE")) {
						if (!htblColNameValue.keySet().contains(parts[1]))
							valid = false;
					}
					if (htblColNameValue.keySet().contains(parts[1])) {
						if (parts[2].toLowerCase().equals("java.util.date")) {
							DateFormat format = new SimpleDateFormat("YYYY-MM-DD");
							try {
								String dateconverted = format.format(htblColNameValue.get(parts[1]));
								if (!(dateconverted.compareTo(parts[6]) >= 0)
										&& ((dateconverted).compareTo(parts[7])) <= 0)
									valid = false;
							} catch (Exception e) {
								valid = false;
							}

						} else {
							switch (htblColNameValue.get(parts[1]).getClass().getName()) {
							case "java.lang.Integer": {
								try {
									if (!(parts[2].toLowerCase().equals("java.lang.integer"))
											|| (!(((Integer) htblColNameValue.get(parts[1]))
													.compareTo(Integer.parseInt(parts[6])) >= 0
													&& ((Integer) htblColNameValue.get(parts[1]))
															.compareTo(Integer.parseInt(parts[7])) <= 0))) {
										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}

								break;
							}
							case "java.lang.String": {

								try {
									if (!(parts[2].toLowerCase().equals("java.lang.string"))
											|| !(((String) htblColNameValue.get(parts[1]))
													.compareTo((String) parts[6]) >= 0
													&& ((String) htblColNameValue.get(parts[1]))
															.compareTo((String) parts[7]) <= 0)) {

										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}
								break;
							}
							case "java.lang.Double": {
								try {
									if (!(parts[2].toLowerCase().equals("java.lang.double"))
											|| !(((Double) htblColNameValue.get(parts[1]))
													.compareTo(Double.parseDouble(parts[6])) >= 0
													&& ((Double) htblColNameValue.get(parts[1]))
															.compareTo(Double.parseDouble(parts[7])) <= 0)) {
										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}
								break;
							}
							default: {
							}
							}
						}
					}
				}
			}
			br.close();
			br = null;
			if (htblColNameValue.size() > colNames.size()) {
				valid = false;
			}
			for (String key : htblColNameValue.keySet()) {
				if (!(colNames.contains(key))) {
					found = false;
				}
			}
			System.gc();
			if (!found)
				return false;
			else
				return valid;

		} catch (IOException e) {
			System.gc();
			throw new DBAppException("Failed to initialize database: " + e.getMessage());

		}

	}

	public static boolean validateNoPrimaryKey(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws DBAppException, NumberFormatException, ClassNotFoundException {
		try {

			BufferedReader br = new BufferedReader(new FileReader(META_FILE));
			String line = null;
			boolean found = false;
			boolean valid = true;
			Vector<String> colNames = new Vector<String>();
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				String tableName = parts[0];
				colNames.add(parts[1]);
				if (tableName.equals(strTableName)) {
					found = true;
					if (htblColNameValue.keySet().contains(parts[1])) {
						if (parts[2].toLowerCase().equals("java.util.date")) {
							DateFormat format = new SimpleDateFormat("YYYY-MM-DD");
							try {
								String dateconverted = format.format(htblColNameValue.get(parts[1]));
								if (!(dateconverted.compareTo(parts[6]) >= 0)
										&& ((dateconverted).compareTo(parts[7])) <= 0)
									valid = false;
							} catch (Exception e) {
								valid = false;
							}

						} else {
							switch (htblColNameValue.get(parts[1]).getClass().getName().toLowerCase()) {
							case "java.lang.integer": {
								try {
									if (!(parts[2].toLowerCase().equals("java.lang.integer"))
											|| (!(((Integer) htblColNameValue.get(parts[1]))
													.compareTo(Integer.parseInt(parts[6])) >= 0
													&& ((Integer) htblColNameValue.get(parts[1]))
															.compareTo(Integer.parseInt(parts[7])) <= 0))) {
										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}

								break;
							}
							case "java.lang.string": {

								try {
									if (!(parts[2].toLowerCase().equals("java.lang.string"))
											|| !(((String) htblColNameValue.get(parts[1]))
													.compareTo((String) parts[6]) >= 0
													&& ((String) htblColNameValue.get(parts[1]))
															.compareTo((String) parts[7]) <= 0)) {

										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}
								break;
							}
							case "java.lang.double": {
								try {
									if (!(parts[2].toLowerCase().equals("java.lang.double"))
											|| !(((Double) htblColNameValue.get(parts[1]))
													.compareTo(Double.parseDouble(parts[6])) >= 0
													&& ((Double) htblColNameValue.get(parts[1]))
															.compareTo(Double.parseDouble(parts[7])) <= 0)) {
										valid = false;
									}
								} catch (NumberFormatException e) {
									valid = false;
								}
								break;
							}
							default: {
							}
							}
						}
					}
				}
			}
			br.close();
			for (String key : htblColNameValue.keySet()) {
				if (!(colNames.contains(key))) {
					found = false;
				}
			}
			if (!found)
				return false;
			else
				return valid;
		} catch (IOException e) {
			throw new DBAppException("Failed to initialize database: " + e.getMessage());
		}
	}

	public Vector<Point> findCommonPoints(Vector<Vector<Point>> pointVectors) {
		Vector<Point> commonPoints = null;

		if (!pointVectors.isEmpty()) {
			commonPoints = new Vector<>(pointVectors.get(0));
		}

		for (int i = 1; i < pointVectors.size(); i++) {
			Vector<Point> currentVector = pointVectors.get(i);
			Vector<Point> newCommonPoints = new Vector<>();

			for (int j = 0; j < currentVector.size(); j++) {
				Point currentPoint = currentVector.get(j);

				for (int k = 0; k < commonPoints.size(); k++) {
					Point commonPoint = commonPoints.get(k);

					if (currentPoint.equals(commonPoint)) {
						newCommonPoints.add(currentPoint);
						break;
					}
				}
			}

			// update the common points to be the new common points
			commonPoints = newCommonPoints;
		}

		return commonPoints;
	}

	public Vector<Point> pointsToBeDeleted(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws FileNotFoundException {
		Vector<String> indices = new Vector<String>();
		Vector<Vector<Point>> allResults = new Vector<Vector<Point>>();
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		Set<String> keys = htblColNameValue.keySet();
		try {
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				String tableName = parts[0];
				if (tableName.equals(strTableName)) {
					if (!parts[4].equals("null") && !indices.contains(parts[4]) && keys.contains(parts[1])) {
						indices.add(parts[4]);
					}
				}
			}
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (indices.size() == 0)
			return null;
		for (String k : indices) {
			try {
				Octree index = (Octree) deserialize(strTableName + k + ".ser");
				allResults.add(index.delete(htblColNameValue));
				index.serialize(strTableName + k);

			} catch (ClassNotFoundException | IOException | DBAppException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return findCommonPoints(allResults);
	}

	public Vector<String> getAllIndices(String strTableName) throws FileNotFoundException {
		Vector<String> indices = new Vector<String>();
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		try {
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				String tableName = parts[0];
				if (tableName.equals(strTableName)) {
					if (!parts[4].equals("null") && !indices.contains(parts[4])) {
						indices.add(parts[4]);
					}
				}
			}
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return indices;
	}

	public void deletFromTable(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws ClassNotFoundException, DBAppException, IOException {
		Vector<String> indices = getAllIndices(strTableName);
		Vector<Point> toDelete = pointsToBeDeleted(strTableName, htblColNameValue);
		Vector<Integer> emptyPages = new Vector<Integer>();
		int pageSize = 0;
		if (toDelete == null) {
			deleteFromTable2(strTableName, htblColNameValue);
		} else {
			Table t = (Table) deserialize(strTableName + ".ser");
			pageSize = t.getPageCount();
			int x;
			for (Point e : toDelete) {
				Page p = (Page) deserialize(strTableName + e.page + ".ser");
				if (e.primaryKey instanceof Vector)
					x = indexFinder(t.getClusteringKey(), p, ((Vector) e.primaryKey).get(0));
				else {
					x = indexFinder(t.getClusteringKey(), p, e.primaryKey);
				}
				p.getRecords().remove(x);
				t.getPages().remove(e.page - 1);
				if (p.getRecords().size() != 0) {
					p.serialize(strTableName, e.page);
					t.getPages().insertElementAt(p, e.page - 1);
				} else {
					emptyPages.add(e.page);
				}

			}
			for (int i : emptyPages) {
				for (String j : indices) {
					Octree index = (Octree) deserialize(strTableName + j + ".ser");
					index.updateReference(i);
					index.serialize(strTableName + j);
				}
				for (int j = i; j <= pageSize; j++) {
					File f = new File(strTableName + j + ".ser");
					Page p = (Page) deserialize(strTableName + j + ".ser");
					p.serialize(strTableName, j - 1);
					f.delete();
					p = null;
				}
			}
			t.serialize(strTableName);

		}
	}

	public void deleteFromTable2(String strTableName, Hashtable<String, Object> htblColNameValue)
			throws DBAppException, ClassNotFoundException, IOException {
		Table t = (Table) deserialize(strTableName + ".ser");
		String cluster = t.getClusteringKey();
		if (!(validateNoPrimaryKey(strTableName, htblColNameValue))) {
			throw new DBAppException("HtblColNameValue doesn't exist");
		}

		if (htblColNameValue.containsKey(cluster)) {
			for (int i = 0; i < t.getPages().size(); i++) {
				int count = i + 1;
				Page currentPage = (Page) deserialize(strTableName + count + ".ser");
				Tuple start = currentPage.getRecords().get(0);
				Tuple end = currentPage.getRecords().get(currentPage.getRecords().size() - 1);
				if (checkCorrectPage(cluster, htblColNameValue.get(cluster), start, end)) {
					int x = indexFinder(cluster, currentPage, htblColNameValue.get(cluster));
					if (matches(currentPage.getRecords().get(x), htblColNameValue)) {
						currentPage.getRecords().remove(x);
						if (currentPage.getRecords().size() != 0) {
							currentPage.serialize(strTableName, count);
							t.getPages().remove(i);
							t.getPages().insertElementAt(currentPage, i);
							t.serialize(strTableName);
							loadintoTable(strTableName, t);
							return;
						} else {
							for (int j = i; j < t.getPages().size(); j++) {
								count = j + 1;
								File f = new File(strTableName + count + ".ser");

								Page p = (Page) deserialize(strTableName + count + ".ser");
								p.serialize(strTableName, j);
								f.delete();
								p = null;
							}
							t.getPages().remove(i);
							t.serialize(strTableName);
							loadintoTable(strTableName, t);
							return;
						}

					}
				}
				currentPage = null;
				start = null;
				end = null;
			}
		} else {
			for (int i = 0; i < t.getPages().size(); i++) {
				int count = i + 1;
				Page currentPage = (Page) deserialize(strTableName + count + ".ser");
				for (int j = currentPage.getRecords().size() - 1; j >= 0; j--) {
					if (matches(currentPage.getRecords().get(j), htblColNameValue)) {
						currentPage.getRecords().remove(j);
						if (currentPage.getRecords().size() != 0) {
							currentPage.serialize(strTableName, count);
							t.getPages().remove(i);
							t.getPages().insertElementAt(currentPage, i);
							t.serialize(strTableName);
							loadintoTable(strTableName, t);
							return;
						} else {
							for (int k = i; k < t.getPages().size(); k++) {
								count = k + 1;
								File f = new File(strTableName + count + ".ser");
								Page p = (Page) deserialize(strTableName + count + ".ser");
								p.serialize(strTableName, k);
								f.delete();
								p = null;
							}
							t.getPages().remove(i);
							t.serialize(strTableName);
							loadintoTable(strTableName, t);
							return;
						}
					}
				}
				currentPage.serialize(strTableName, count);
				t.getPages().remove(i);
				t.getPages().insertElementAt(currentPage, i);
				currentPage = null;
			}
			loadintoTable(strTableName, t);
			t.serialize(strTableName);

		}
		t = null;
		System.gc();
	}

	public boolean matches(Tuple t, Hashtable<String, Object> htblColNameValue) {
		Set<String> keys = htblColNameValue.keySet();
		for (String k : keys) {
			if (!t.getColNameValue().containsKey(k)) {
				return false;
			}
			if (!t.getColNameValue().get(k).equals(htblColNameValue.get(k))) {
				return false;
			}
		}
		return true;
	}

	public boolean checkCorrectPage(String clusterKey, Object value, Tuple s, Tuple e) {
		boolean correct = false;
		Object o1 = s.getColNameValue().get(clusterKey);
		Object o2 = e.getColNameValue().get(clusterKey);
		if (value instanceof Integer && o2 instanceof Integer) {
			if ((int) value <= (int) o2 && (int) value >= (int) o1)
				correct = true;
		} else if (value instanceof Double && o2 instanceof Double) {
			if ((Double) value <= (Double) o2 && (Double) value >= (Double) o1)
				correct = true;
		} else if (value instanceof String && o2 instanceof String) {
			if (((String) o2).toLowerCase().compareTo(((String) value).toLowerCase()) >= 0
					&& ((String) o1).toLowerCase().compareTo(((String) value).toLowerCase()) <= 0)
				correct = true;
		} else if (value instanceof Date && o2 instanceof Date) {
			DateFormat format = new SimpleDateFormat("YYYY/MM/DD");
			String valueconverted = format.format(value);
			String o1converted = format.format(o1);
			String o2converted = format.format(o2);
			if ((((String) o1converted).compareTo((String) valueconverted) >= 0)
					&& ((String) o2converted).compareTo((String) valueconverted) <= 0) {
				correct = true;
			}

		}
		return correct;
	}

	public int indexFinder(String key, Page p, Object t) {
		int low = 0;
		int high = p.getRecords().size() - 1;
		while (low <= high) {
			int mid = low + (high - low) / 2;
			Object toBeinserted = t;
			Object toBeComparedWith = p.getRecords().get(mid).getColNameValue().get(key);
			int x = comparator(toBeComparedWith, toBeinserted);
			if (x == 0)
				return mid;
			else if (x > 0)
				low = mid + 1;
			else
				high = mid - 1;
		}
		return low;
	}

	public boolean validateTuple(String strTableName, Hashtable<String, Object> htblColNameValue) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(",");
			String tableName = parts[0];
			if (tableName.equals(strTableName) && htblColNameValue.containsKey(parts[1])) {
				switch (parts[2].toLowerCase()) {
				case "java.lang.integer": {
					if (!(htblColNameValue.get(parts[1]) instanceof Integer)) {
						return false;
					}
					break;
				}
				case "java.lang.string": {
					if (!(htblColNameValue.get(parts[1]) instanceof String)) {
						return false;
					}
					break;
				}
				case "java.lang.double": {
					if (!(htblColNameValue.get(parts[1]) instanceof Double)) {
						return false;
					}
					break;
				}
				default: {
					if (!(htblColNameValue.get(parts[1]) instanceof Date)) {
						return false;
					}
					break;
				}
				}
			}

		}

		br.close();
		return true;
	}

	public void updateTable(String strTableName, String strClusteringKeyValue,
			Hashtable<String, Object> htblColNameValue) throws DBAppException, ClassNotFoundException, IOException {
		Object clusteringValue = strClusteringKeyValue;
		String str = strClusteringKeyValue;
		String className = "java.lang.Integer";
		String clusterKey = null;
		BufferedReader br = new BufferedReader(new FileReader(META_FILE));
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(",");
			String tableName = parts[0];
			if (tableName.equals(strTableName) && parts[3].toLowerCase().equals("true")) {
				className = parts[2];
				clusterKey = parts[1];
				break;
			}
		}
		br.close();
		if (htblColNameValue.containsKey(clusterKey)) {
			throw new DBAppException("Primary Key can't be updated exclude it from hashtable");
		}
		try {
			Class<?> cls = Class.forName(className);
			clusteringValue = cls.getDeclaredMethod("valueOf", String.class).invoke(null, str);
		} catch (Exception e) {
			throw new DBAppException("Clustering Key doesn't Match Table's Clustering Key Type");
		}

		if (!(validateTuple(strTableName, htblColNameValue))
				|| !(validateNoPrimaryKey(strTableName, htblColNameValue))) {
			throw new DBAppException("HtblColNameValue doesn't match Table definition");
		}
		Table t = (Table) deserialize(strTableName + ".ser");
		String cluster = t.getClusteringKey();

		if (checkIfPrimaryIndexed(strTableName, cluster) != null
				&& !checkIfPrimaryIndexed(strTableName, cluster).equals("null")) {
			Octree index = (Octree) deserialize(strTableName + checkIfPrimaryIndexed(strTableName, cluster) + ".ser");
			Hashtable<String, Object> temp2 = new Hashtable<String, Object>();
			temp2.put(cluster, clusteringValue);
			index.update(temp2, htblColNameValue);
			Vector<Point> results = index.search(temp2);
			if (results.size() != 0) {
				int pageNumber = results.get(0).page;
				Page currentPage = (Page) deserialize(strTableName + pageNumber + ".ser");
				int x = indexFinder(cluster, currentPage, clusteringValue);
				Tuple temp = updateTuple(currentPage.getRecords().get(x), htblColNameValue);
				currentPage.getRecords().remove(x);
				currentPage.getRecords().insertElementAt(temp, x);
				currentPage.serialize(strTableName, pageNumber);
				t.getPages().remove(pageNumber - 1);
				t.getPages().insertElementAt(currentPage, pageNumber - 1);
				index.serialize(strTableName + checkIfPrimaryIndexed(strTableName, cluster));
				t.serialize(strTableName);
				loadintoTable(strTableName, t);
				currentPage = null;
			}
			return;

		} else {
			Vector<String> indices = new Vector<String>();
			br = new BufferedReader(new FileReader(META_FILE));
			line = null;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				String tableName = parts[0];
				if (tableName.equals(strTableName)) {
					if (!parts[4].equals("null") && !indices.contains(parts[4])) {
						indices.add(parts[4]);
					}
				}
			}
			br.close();
			for (int i = 0; i < t.getPages().size(); i++) {
				int count = i + 1;
				Page currentPage = (Page) deserialize(strTableName + count + ".ser");
				Tuple start = currentPage.getRecords().get(0);
				Tuple end = currentPage.getRecords().get(currentPage.getRecords().size() - 1);
				if (checkCorrectPage(cluster, clusteringValue, start, end)
						&& validateNoPrimaryKey(strTableName, htblColNameValue)) {

					int x = indexFinder(cluster, currentPage, clusteringValue);
					Tuple temp = updateTuple(currentPage.getRecords().get(x), htblColNameValue);
					for (String k : indices) {
						Octree index = (Octree) deserialize(t.getName() + k + ".ser");
						index.update(temp.getColNameValue(), cluster, count, clusteringValue);
						index.serialize(t.getName() + k);
					}
					currentPage.getRecords().remove(x);
					currentPage.getRecords().insertElementAt(temp, x);
					currentPage.serialize(strTableName, count);
					t.getPages().remove(i);
					t.getPages().insertElementAt(currentPage, i);
					t.serialize(strTableName);
					loadintoTable(strTableName, t);
					return;
				}
				currentPage = null;
				start = null;
				end = null;

			}
			t = null;
			System.gc();
		}
	}

	public void updateTableWithIndex(String indexName, String strTableName, String clusterKey, Object clusterValue,
			Hashtable<String, Object> htblColNameValue) throws DBAppException, ClassNotFoundException, IOException {

		Octree index = (Octree) deserialize(indexName + ".ser");
		Vector<Point> rowNumbers = index.search(htblColNameValue);
		Table t = (Table) deserialize(strTableName + ".ser");
		Page p = null;
		for (Point tup : rowNumbers) {
			p = (Page) deserialize(strTableName + tup.page + ".ser");
			t.getPages().remove(tup.page);
			int row = indexFinder(clusterKey, p, tup.primaryKey);
			p.getRecords().remove(row);
			p.getRecords().insertElementAt(updateTuple(p.getRecords().get(row), htblColNameValue), row);
			t.getPages().insertElementAt(p, tup.page);
		}

		t.serialize(strTableName);

	}

	public Tuple updateTuple(Tuple t, Hashtable<String, Object> htblColNameValue) throws DBAppException {

		for (String k : htblColNameValue.keySet()) {
			if (t.getColNameValue().containsKey(k)) {
				t.getColNameValue().put(k, htblColNameValue.get(k));
			}
		}
		return t;
	}

	public void serialize() throws DBAppException {
		try {
			FileOutputStream fileOut = new FileOutputStream("db.ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			throw new DBAppException("Failed to serialize database: " + e.getMessage());
		}
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

	private static Object deserialize(String name) throws IOException, ClassNotFoundException, DBAppException {

		try {
			FileInputStream fileIn = new FileInputStream(name);
			ObjectInputStream objectIn = new ObjectInputStream(fileIn);
			Object o = objectIn.readObject();
			objectIn.close();
			fileIn.close();
			return o;
		} catch (IOException e) {
			throw new DBAppException(name + "");
		}
	}

	public Iterator selectFromTable(SQLTerm[] arrSQLTerms, String[] strarrOperators) throws DBAppException {
		Vector<Tuple> results = new Vector<Tuple>();
		if (!(validateSQL(arrSQLTerms, strarrOperators))) {
			throw new DBAppException("Unsupported SQL Term or Operator");
		}
		Vector<Vector<Tuple>> resultSets = new Vector<Vector<Tuple>>();
		String tableName = arrSQLTerms[0].strTableName;
		for (SQLTerm sql : arrSQLTerms) {
			resultSets.add(findMatches(tableName, sql));
		}
		Vector<Tuple> operandOne = new Vector<Tuple>();
		Vector<Tuple> operandTwo = new Vector<Tuple>();
		for (int i = 0; i < strarrOperators.length; i++) {
			if (i == 0) {
				operandOne = resultSets.get(0);
				operandTwo = resultSets.get(1);
			} else {
				operandOne.clear();
				operandOne.addAll(results);
				results.clear();
				operandTwo = resultSets.get(i + 1);
			}
			switch (strarrOperators[i].toUpperCase()) {
			case "AND":
				for (Tuple temp : operandOne) {
					if (operandTwo.contains(temp))
						results.add(temp);
				}

				break;
			case "OR":
				if (operandOne.size() == 0)
					results.addAll(operandTwo);
				else if (operandTwo.size() == 0)
					results.addAll(operandOne);
				else {
					results.addAll(operandOne);
					for (Tuple temp1 : operandTwo) {
						if (!(operandOne.contains(temp1)))
							results.add(temp1);
					}
				}
				break;
			case "XOR":
				for (Tuple temp1 : operandOne) {
					if (!(operandTwo.contains(temp1)))
						results.add(temp1);
				}
				for (Tuple temp2 : operandTwo) {
					if (!(operandOne.contains(temp2)))
						results.add(temp2);
				}
				break;
			}
		}
		return results.iterator();

	}

	public static Vector<Tuple> findMatches(String tableName, SQLTerm term) throws DBAppException {
		Vector<Tuple> result = new Vector<Tuple>();
		String colName = term.strColumnName;
		Object value = term.objValue;
		String operator = term.strOperator;
		try {
			BufferedReader br = new BufferedReader(new FileReader(META_FILE));
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");
				if (parts[0].equals(tableName) && parts[1].equals(colName)) {
					if (parts[2].toLowerCase().equals("java.util.date")) {
						if (!(value instanceof Date)) {
							throw new DBAppException("Column Datatype Doesn't match input ");
						}
					} else if (parts[2].toLowerCase().equals("java.lang.string")) {
						if (!(value instanceof String)) {
							throw new DBAppException("Column Datatype Doesn't match input ");
						}
					} else if (parts[2].toLowerCase().equals("java.lang.integer")) {
						if (!(value instanceof Integer)) {
							throw new DBAppException("Column Datatype Doesn't match input ");
						}
					} else if (parts[2].toLowerCase().equals("java.lang.double")) {
						if (!(value instanceof Double)) {
							throw new DBAppException("Column Datatype Doesn't match input ");
						}
					}
				}
			}
			br.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {

			Table temp = (Table) deserialize(tableName + ".ser");

			for (int j = 1; j <= temp.getPageCount(); j++) {
				try {
					Page p = (Page) deserialize(tableName + j + ".ser");
					for (Tuple row : p.getRecords()) {

						if (row.getColNameValue().get(colName) != null) {
							switch (operator) {
							case "=":
								if (value instanceof Number && row.getColNameValue().get(colName) instanceof Number
										&& value == row.getColNameValue().get(colName))
									result.add(row);
								else if (value.equals(row.getColNameValue().get(colName)))
									result.add(row);
								break;
							case "!=":
								if (value instanceof Number && row.getColNameValue().get(colName) instanceof Number
										&& value != row.getColNameValue().get(colName))
									result.add(row);
								else if (!(value.equals(row.getColNameValue().get(colName))))
									result.add(row);
								break;
							case ">=":
								if (value instanceof Integer && row.getColNameValue().get(colName) instanceof Integer
										&& (int) value <= (int) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof Double && row.getColNameValue().get(colName) instanceof Double
										&& (Double) value <= (Double) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof String && row.getColNameValue().get(colName) instanceof String
										&& (((String) row.getColNameValue().get(colName))
												.compareToIgnoreCase((String) value)) >= 0)
									result.add(row);
								else if (value instanceof Date && row.getColNameValue().get(colName) instanceof Date
										&& ((((Date) row.getColNameValue().get(colName)).after((Date) value))
												|| ((Date) row.getColNameValue().get(colName))
														.compareTo((Date) value) == 0))
									result.add(row);

								break;
							case ">":
								if (value instanceof Integer && row.getColNameValue().get(colName) instanceof Integer
										&& (int) value < (int) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof Double && row.getColNameValue().get(colName) instanceof Double
										&& (Double) value < (Double) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof String && row.getColNameValue().get(colName) instanceof String
										&& (((String) row.getColNameValue().get(colName))
												.compareToIgnoreCase((String) value)) > 0)
									result.add(row);
								else if (value instanceof Date && row.getColNameValue().get(colName) instanceof Date
										&& (((Date) row.getColNameValue().get(colName)).after((Date) value)))
									result.add(row);
								break;
							case "<=":
								if (value instanceof Integer && row.getColNameValue().get(colName) instanceof Integer
										&& (int) value >= (int) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof Double && row.getColNameValue().get(colName) instanceof Double
										&& (Double) value >= (Double) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof String && row.getColNameValue().get(colName) instanceof String
										&& (((String) row.getColNameValue().get(colName))
												.compareToIgnoreCase((String) value)) <= 0)
									result.add(row);
								else if (value instanceof Date && row.getColNameValue().get(colName) instanceof Date
										&& ((((Date) row.getColNameValue().get(colName)).before((Date) value))
												|| ((Date) row.getColNameValue().get(colName))
														.compareTo((Date) value) == 0))
									result.add(row);

								break;
							case "<":
								if (value instanceof Integer && row.getColNameValue().get(colName) instanceof Integer
										&& (int) value > (int) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof Double && row.getColNameValue().get(colName) instanceof Double
										&& (Double) value > (Double) row.getColNameValue().get(colName))
									result.add(row);
								else if (value instanceof String && row.getColNameValue().get(colName) instanceof String
										&& (((String) row.getColNameValue().get(colName))
												.compareToIgnoreCase((String) value)) < 0)
									result.add(row);
								else if (value instanceof Date && row.getColNameValue().get(colName) instanceof Date
										&& (((Date) row.getColNameValue().get(colName)).before((Date) value)))
									result.add(row);
								break;
							}
						}
					}
				} catch (ClassNotFoundException | IOException | DBAppException e) {
					break;
				}

			}
		} catch (ClassNotFoundException | IOException | DBAppException e) {
			throw new DBAppException("Table not found in Database");
		}
		return result;
	}

	public boolean validateSQL(SQLTerm[] arrSQLTerms, String[] strarrOperators) {
		boolean valid = true;
		String strTableName = arrSQLTerms[0].strColumnName;
		for (SQLTerm term : arrSQLTerms) {
			if (!(term.strOperator.equals(">") || term.strOperator.equals("<") || term.strOperator.equals(">=")
					|| term.strOperator.equals("<=") || term.strOperator.equals("!=") || term.strOperator.equals("=")))
				valid = false;
			if (!term.strColumnName.equals(strTableName)) {
				valid = false;
			}
		}

		for (String s : strarrOperators) {
			if (!(s.toUpperCase().equals("OR") || s.toUpperCase().equals("XOR") || s.toUpperCase().equals("AND")))
				valid = false;
		}
		if ((arrSQLTerms.length - 1) != strarrOperators.length) {
			valid = false;
		}

		return valid;
	}

	public void createIndex(String strTableName, String[] strarrColName) throws DBAppException {
		Set<String> uniqueStrings = new HashSet<>(Arrays.asList(strarrColName));
		String[] uniqueStringsArr = uniqueStrings.toArray(new String[0]);
		Arrays.sort(strarrColName);
		Hashtable<String, Object> max = new Hashtable<String, Object>();
		Hashtable<String, Object> min = new Hashtable<String, Object>();
		String clusterKey = null;
		try {
			BufferedReader br = new BufferedReader(new FileReader(META_FILE));
			String line = null;
			boolean found = false;
			boolean match1 = false;
			boolean match2 = false;
			boolean match3 = false;

			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",");

				if (parts[0].equals(strTableName)) {

					found = true;
					if (parts[3].toLowerCase().equals("true"))
						clusterKey = parts[1];
					if (parts[1].toLowerCase().equals(strarrColName[0].toLowerCase())) {
						if (!parts[4].equals("null")) {
							throw new DBAppException("One or more columns are already indexed");
						}
						match1 = true;

						switch (parts[2].toLowerCase()) {
						case "java.lang.integer":
							max.put(strarrColName[0], Integer.parseInt(parts[7]));
							min.put(strarrColName[0], Integer.parseInt(parts[6]));
							break;
						case "java.lang.double":
							max.put(strarrColName[0], Double.parseDouble(parts[7]));
							min.put(strarrColName[0], Double.parseDouble(parts[6]));
							break;
						case "java.lang.string":
							max.put(strarrColName[0], (parts[7]));
							min.put(strarrColName[0], (parts[6]));
							break;
						default:

							String dateStringOne = parts[7];
							String dateStringTwo = parts[6];
							SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
							Date dateMax = dateFormat.parse(dateStringOne);
							Date dateMin = dateFormat.parse(dateStringTwo);
							max.put(strarrColName[0], dateMax);
							min.put(strarrColName[0], dateMin);
						}
					} else if (parts[1].toLowerCase().equals(strarrColName[1].toLowerCase())) {
						if (!parts[4].equals("null")) {
							throw new DBAppException("One or more columns are already indexed");

						}
						switch (parts[2].toLowerCase()) {
						case "java.lang.integer":
							max.put(strarrColName[1], Integer.parseInt(parts[7]));
							min.put(strarrColName[1], Integer.parseInt(parts[6]));
							break;
						case "java.lang.double":
							max.put(strarrColName[1], Double.parseDouble(parts[7]));
							min.put(strarrColName[1], Double.parseDouble(parts[6]));
							break;
						case "java.lang.string":
							max.put(strarrColName[1], (parts[7]));
							min.put(strarrColName[1], (parts[6]));
							break;
						default:
							String dateStringOne = parts[7];
							String dateStringTwo = parts[6];
							SimpleDateFormat dateFormat = new SimpleDateFormat("yyy/MM/dd");
							Date dateMax = dateFormat.parse(dateStringOne);
							Date dateMin = dateFormat.parse(dateStringTwo);
							max.put(strarrColName[1], dateMax);
							min.put(strarrColName[1], dateMin);
						}
						match2 = true;
					} else if (parts[1].toLowerCase().equals(strarrColName[2].toLowerCase())) {
						if (!parts[4].equals("null")) {
							throw new DBAppException("One or more columns are already indexed");
						}
						match3 = true;
						switch (parts[2].toLowerCase()) {
						case "java.lang.integer":
							max.put(strarrColName[2], Integer.parseInt(parts[7]));
							min.put(strarrColName[2], Integer.parseInt(parts[6]));
							break;
						case "java.lang.double":
							max.put(strarrColName[2], Double.parseDouble(parts[7]));
							min.put(strarrColName[2], Double.parseDouble(parts[6]));
							break;
						case "java.lang.string":
							max.put(strarrColName[2], (parts[7]));
							min.put(strarrColName[2], (parts[6]));
							break;
						default:
							String dateStringOne = parts[7];
							String dateStringTwo = parts[6];
							SimpleDateFormat dateFormat = new SimpleDateFormat("yyy/MM/dd");
							Date dateMax = dateFormat.parse(dateStringOne);
							Date dateMin = dateFormat.parse(dateStringTwo);
							max.put(strarrColName[2], dateMax);
							min.put(strarrColName[2], dateMin);
						}
					}
				}
			}
			br.close();
			if (!found) {
				throw new DBAppException("Table with name " + strTableName + " doesn't exist.");
			}
			if (!(match1 && match2 && match3)) {
				throw new DBAppException("One or more columns don't exist in the table");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (uniqueStringsArr.length != 3)
			throw new DBAppException("Index can be created on 3 Distinct Columns Only.");

		try {
			String line;
			BufferedReader reader = new BufferedReader(new FileReader(META_FILE));
			StringBuilder sb = new StringBuilder(); // Create a StringBuilder to store the lines

			while ((line = reader.readLine()) != null) {
				sb.append(line).append(System.lineSeparator()); // Store each line in the StringBuilder
			}

			reader.close();

			String[] lines = sb.toString().split(System.lineSeparator()); // Split the StringBuilder by newlines

			for (int i = 0; i < lines.length; i++) {
				String[] parts = lines[i].split(",");
				if (parts[1].toLowerCase().equals(strarrColName[0].toLowerCase())
						|| parts[1].toLowerCase().equals(strarrColName[1].toLowerCase())
						|| parts[1].toLowerCase().equals(strarrColName[2].toLowerCase())) { // Check if the age matches
					parts[4] = strarrColName[0].toLowerCase() + strarrColName[1].toLowerCase()
							+ strarrColName[2].toLowerCase(); // Update the age
					parts[5] = "Octree";
					lines[i] = String.join(",", parts); // Join the parts back into a string
				}
			}

			BufferedWriter writer = new BufferedWriter(new FileWriter(META_FILE, false));

			for (String row : lines) {
				if (!row.isEmpty()) { // Skip any empty rows
					writer.write(row);
					writer.newLine(); // Add a newline after each row
				}
			}

			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Octree index = new Octree(max, min, clusterKey);
		try {
			Table tempTable = (Table) deserialize(strTableName + ".ser");
			for (int i = 1; i <= tempTable.getPageCount(); i++) {
				Page temp = (Page) deserialize(strTableName + i + ".ser");
				for (int j = 0; j < temp.getRecords().size(); j++) {
					index.insert(temp.getRecords().get(j).getColNameValue(), clusterKey, i);
				}
				temp.serialize(strTableName, i);
			}
		} catch (ClassNotFoundException | IOException | DBAppException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		index.serialize(strTableName + strarrColName[0].toLowerCase() + strarrColName[1].toLowerCase()
				+ strarrColName[2].toLowerCase());
		// index.print();

	}

	public String checkIfPrimaryIndexed(String tableName, String cluster) {
		System.out.println(cluster + "_" + tableName);
		String indexName = null;
		String line;
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(META_FILE));
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(",");
				if (parts[0].equals(tableName) && parts[1].toLowerCase().equals(cluster.toLowerCase())) {
					if (parts[3].toUpperCase().equals("TRUE"))
						indexName = parts[4];
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(indexName);
		return indexName;
	}

	public String checkIfIndexed(String tableName, Hashtable<String, Object> test) {
		String indexName = null;
		String line;
		BufferedReader reader;
		String[] keys = test.keySet().toArray(new String[test.keySet().size()]);

		Arrays.sort(keys);
		boolean col1 = false, col2 = false, col3 = false;
		String name1 = null, name2 = null, name3 = null;
		try {
			reader = new BufferedReader(new FileReader(META_FILE));
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(",");
				if (parts[0].equals(tableName) && keys[0].equals(parts[1])) {
					col1 = true;
					name1 = parts[4];
				}
				if (parts[0].equals(tableName) && keys[1].equals(parts[1])) {
					col2 = true;
					name2 = parts[4];

				}
				if (parts[0].equals(tableName) && keys[2].equals(parts[1])) {
					col3 = true;
					name3 = parts[4];
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if ((col1 & col2 & col3) && (name1.equals(name2) && name2.equals(name3))) {

			indexName = tableName + keys[0] + keys[1] + keys[2];
		}
		return indexName;
	}

	public static int correctPage(Object value, Vector<Point> points) {
		int closest = -1;
		double minDistance = Double.POSITIVE_INFINITY;
		Object pk;
		for (Point point : points) {
			if (point.primaryKey instanceof Vector)
				pk = ((Vector) point.primaryKey).get(0);
			else {
				pk = point.primaryKey;
			}
			double distance;

			if (pk instanceof Integer && value instanceof Integer) {
				distance = Math.abs((Integer) value - (Integer) pk);
			} else if (pk instanceof Double && value instanceof Double) {
				distance = Math.abs((Double) value - (Double) pk);
				System.out.println(distance);
			} else if (pk instanceof String && value instanceof String) {
				distance = Math.abs(((String) value).compareTo((String) pk));

			} else if (pk instanceof Date && value instanceof Date) {
				distance = Math.abs(((Date) value).getTime() - ((Date) pk).getTime());
			} else {
				// Types do not match
				continue;
			}
			if (distance < minDistance) {
				minDistance = distance;
				closest = point.page;
			}

		}

		return closest;
	}

	public static void main(String[] args) throws DBAppException, ClassNotFoundException, IOException {
		String strTableName = "Student5";

		DBApp test = new DBApp();

		Hashtable htblColNameType = new Hashtable();
		Hashtable htblColNameMin = new Hashtable();
		Hashtable htblColNameMax = new Hashtable();
		Hashtable testValues = new Hashtable();
		htblColNameType.put("id", "java.lang.Integer");
		htblColNameType.put("name", "java.lang.String");
		htblColNameType.put("gpa", "java.lang.Double");
		htblColNameType.put("date", "java.util.Date");
		htblColNameMin.put("id", "0");
		htblColNameMax.put("id", "20");
		htblColNameMin.put("name", " ");
		htblColNameMax.put("name", "zzzzzzzzzzzzzz");
		htblColNameMin.put("gpa", "0.0");
		htblColNameMax.put("gpa", "5.0");
		htblColNameMin.put("date", "2000-01-01");
		htblColNameMax.put("date", "2050-01-01");
		test.createTable(strTableName, "id", htblColNameType, htblColNameMin, htblColNameMax);

		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("1"));
			htblColNameValue.put("name", new String("Menna"));
			htblColNameValue.put("gpa", new Double(5.0));
			htblColNameValue.put("date", new Date("2022/10/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("2"));
			htblColNameValue.put("name", new String("Ahmed Noooor"));
			htblColNameValue.put("gpa", new Double(2.5));
			htblColNameValue.put("date", new Date("2024/09/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("4"));
			htblColNameValue.put("name", new String("Sarah"));
			htblColNameValue.put("gpa", new Double(4.8));
			htblColNameValue.put("date", new Date("2025/09/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("9"));
			htblColNameValue.put("gpa", new Double(4.0));
			htblColNameValue.put("name", new String("Sarah"));
			htblColNameValue.put("date", new Date("2022/11/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("10"));
			htblColNameValue.put("gpa", new Double(0.95));
			htblColNameValue.put("name", new String("Sarah"));
			htblColNameValue.put("date", new Date("2022/10/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Hashtable htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("11"));
			htblColNameValue.put("gpa", new Double(3.6));
			htblColNameValue.put("name", new String("Sarah"));
			htblColNameValue.put("date", new Date("2023/10/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	/*	String[] cols = { "gpa", "name", "id" };
		test.createIndex("Student5", cols);
		Table t = (Table) deserialize(strTableName + ".ser");
		SQLTerm[] arrSQLTerms;
		arrSQLTerms = new SQLTerm[3];

		arrSQLTerms[0] = new SQLTerm();
		arrSQLTerms[0].strTableName = strTableName;
		arrSQLTerms[0].strColumnName = "gpa";
		arrSQLTerms[0].strOperator = ">";
		arrSQLTerms[0].objValue = new Double(2.5);

		arrSQLTerms[1] = new SQLTerm();
		arrSQLTerms[1].strTableName = strTableName;
		arrSQLTerms[1].strColumnName = "gpa";
		arrSQLTerms[1].strOperator = "=";
		arrSQLTerms[1].objValue = new Double(0.95);

		arrSQLTerms[2] = new SQLTerm();
		arrSQLTerms[2].strTableName = strTableName;
		arrSQLTerms[2].strColumnName = "gpa";
		arrSQLTerms[2].strOperator = "=";
		arrSQLTerms[2].objValue = new Double(0.85);

		String[] strarrOperators = new String[2];
		strarrOperators[0] = new String();
		strarrOperators[0] = "OR";

		strarrOperators[1] = new String();
		strarrOperators[1] = "AND";

		for (int i = 0; i < t.getPages().size(); i++) {
			System.out.println(t.getPages().get(i).getRecords().size() + "aa");
			for (int j = 0; j < t.getPages().get(i).getRecords().size(); j++)
				System.out.println(t.getPages().get(i).getRecords().get(j).getColNameValue());
		}
		System.out.println("---------------");

		/*
		 * Octree index = (Octree) deserialize("Student5gpaidname.ser"); index.print();
		 

		System.out.println("---------------");

		Hashtable htblColNameValue = new Hashtable();

		try {
			htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("0"));
			htblColNameValue.put("date", new Date("2022/10/22"));
			htblColNameValue.put("gpa", new Double(0.85));
			htblColNameValue.put("name", new String("Menna"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("12"));
			htblColNameValue.put("date", new Date("2022/10/22"));
			htblColNameValue.put("gpa", new Double(0.85));
			htblColNameValue.put("name", new String("Menna"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			htblColNameValue = new Hashtable();
			htblColNameValue.put("id", new Integer("9"));
			htblColNameValue.put("gpa", new Double(4.0));
			htblColNameValue.put("name", new String("Sarah"));
			htblColNameValue.put("date", new Date("2022/11/22"));
			test.insertIntoTable(strTableName, htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			htblColNameValue = new Hashtable();
			htblColNameValue.put("date", new Date("2023/10/22"));
			htblColNameValue.put("gpa", new Double(1.2));
			htblColNameValue.put("name", new String("Sarah"));
			test.updateTable(strTableName, "12", htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			htblColNameValue = new Hashtable();
			htblColNameValue.put("date", new Date("2022/10/22"));
			htblColNameValue.put("gpa", new Double(1.2));
			htblColNameValue.put("name", new String("Sarah"));
			test.updateTable(strTableName, "11", htblColNameValue);
		} catch (ClassNotFoundException | DBAppException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		t = (Table) deserialize(strTableName + ".ser");
		for (int i = 0; i < t.getPages().size(); i++) {
			System.out.println(t.getPages().get(i).getRecords().size() + "aa");
			for (int j = 0; j < t.getPages().get(i).getRecords().size(); j++)
				System.out.println(t.getPages().get(i).getRecords().get(j).getColNameValue());
		}
		System.out.println("---------------");
		*/
		/*
		 * index = (Octree) deserialize("Student5gpaidname.ser"); index.print();
		 */
/*
		htblColNameValue = new Hashtable();
		htblColNameValue.put("gpa", new Double("4.0"));
		test.deletFromTable(strTableName, htblColNameValue);
		htblColNameValue = new Hashtable();
		htblColNameValue.put("gpa", new Double("0.95"));
		test.deletFromTable(strTableName, htblColNameValue);

	*/
/*
		htblColNameValue = new Hashtable();
		htblColNameValue.put("id", new Integer("11"));
		//test.deletFromTable(strTableName, htblColNameValue);

		t = (Table) deserialize(strTableName + ".ser");
		for (int i = 0; i < t.getPages().size(); i++) {
			System.out.println(t.getPages().get(i).getRecords().size() + "aa");
			for (int j = 0; j < t.getPages().get(i).getRecords().size(); j++)
				System.out.println(t.getPages().get(i).getRecords().get(j).getColNameValue());
		}
		System.out.println("---------------");
		Octree index = (Octree) deserialize("Student5gpaidname.ser");
		index.print();
		
		Vector<Point> e = new Vector<Point>();
		Point temp = new Point(1, 5);
		e.add(temp);
		temp = new Point(2, 8);
		e.add(temp);
		temp = new Point(10, 12);
		e.add(temp);
		System.out.println(correctPage(7, e));*/
	}

}