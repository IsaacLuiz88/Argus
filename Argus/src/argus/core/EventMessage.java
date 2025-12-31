package argus.core;

public class EventMessage {

	private String student;
    private String exam;
    private String type;
    private String code;
    private long timestamp;

	public String getStudent() {return student;}
	public void setStudent(String student) {this.student = student;}

	public String getExam() {return exam;}
	public void setExam(String exam) {this.exam = exam;}

	public String getType() {return type;}
	public void setType(String type) {this.type = type;}

	public String getCode() {return code;}
	public void setCode(String code) {this.code = code;}

	public long getTimestamp() {return timestamp;}
	public void setTimestamp(long timestamp) {this.timestamp = timestamp;}
}
