import java.io.Serializable;
import java.util.Date;

public class Column implements Serializable {
	private String name;
	private String type;
	private String min;
	private String max;

	public Column(String name, String type, String min, String max) {
		this.name = name;
		this.type = type;
		this.min = min;
		this.max = max;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getMin() {
		return min;
	}

	public void setMin(String min) {
		this.min = min;
	}

	public String getMax() {
		return max;
	}

	public void setMax(String max) {
		this.max = max;
	}
	public String getIndexName() {
		return null;
	}
	public Object getIndexType() {
		return null;
	}


}
