import java.util.Comparator;
import java.util.Hashtable;

public class RecordComparator implements Comparator<Hashtable<String, Object>> {
    private String clusteringKey;

    public RecordComparator(String clusteringKey) {
        this.clusteringKey = clusteringKey;
    }

    @Override
    public int compare(Hashtable<String, Object> record1, Hashtable<String, Object> record2) {
        Object value1 = record1.get(clusteringKey);
        Object value2 = record2.get(clusteringKey);
        
        if (value1 instanceof Integer && value2 instanceof Integer) {
            return Integer.compare((Integer) value1, (Integer) value2);
        } else if (value1 instanceof Double && value2 instanceof Double) {
            return Double.compare((Double) value1, (Double) value2);
        } else if (value1 instanceof String && value2 instanceof String) {
            return ((String) value1).compareTo((String) value2);
        } else if (value1 instanceof Boolean && value2 instanceof Boolean) {
            return Boolean.compare((Boolean) value1, (Boolean) value2);
        } else {
            throw new IllegalArgumentException("Invalid data type for clustering key.");
        }
    }
}