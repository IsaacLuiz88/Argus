package argus.core;

public class SharedContext {

    private static String studentName;
    private static String examName;

    public static void init(String student, String exam) {
        studentName = student;
        examName = exam;
    }

    public static String getStudent() {
        return studentName;
    }

    public static String getExam() {
        return examName;
    }

    public static String getPrefix() {
        return studentName + "_" + examName;
    }
}
