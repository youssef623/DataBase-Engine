import java.util.Vector;

public class SQLTerm {
	String strTableName;
	String strColumnName;
	String strOperator;
	Object objValue;
	Vector<Tuple> resultSet;
	public SQLTerm() {
		
		resultSet=new Vector<Tuple>();
		
	}
}
