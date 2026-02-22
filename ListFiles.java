import java.io.File;

public class ListFiles {
    public static void main(String[] args) {
        File dir = new File("/opt/translation");
        File[] files = dir.listFiles();
        
        if (files != null) {
            int count = 0;
            for (File file : files) {
                if (count >= 5) break;
                if (file.isFile()) {
                    System.out.println(file.getName());
                    count++;
                }
            }
        }
    }
}
