package argus.core;

public class SharedContext {

    private static String studentName;
    private static String examName;
    private static String session;

    public static void init(String student, String exam) {
        studentName = student;
        examName = exam;
        session = student + "_" + exam;
    }

    public static String student() { return studentName; }
    public static String exam() { return examName; }
    public static String session() { return session; }
}
