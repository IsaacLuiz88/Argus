package argus.core;

public final class SharedContext {

    private static volatile String studentName;
    private static volatile String examName;
    private static volatile String sessionId;

    private SharedContext(){}

    public static synchronized void init(String student,String exam){
        studentName = student;
        examName = exam;}

    public static synchronized void setSession(String session){
        sessionId = session;}

    public static String student() {return studentName;}
    public static String exam() {return examName;}
    public static String session() {return sessionId;}
}