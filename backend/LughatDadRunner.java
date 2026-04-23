import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * LughatDadRunner — thin subprocess entry point.
 *
 * Called per-request by LughatDadServer:
 *   java LughatDadRunner <source_file.txt>
 *
 * Reads the Arabic source from the given file, runs it through the
 * LughatDad interpreter (compiled from LughatDad.jj via JavaCC),
 * and prints the output to stdout.
 *
 * Each invocation is a fresh JVM process, so STATIC=true in the
 * grammar is not a problem — static state is isolated per process.
 */
public class LughatDadRunner {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("خطأ: مسار الملف مطلوب");
            System.exit(1);
        }

        String filePath = args[0];

        try {
            // Reset symbol table for clean run
            LughatDad.symbolTable = new java.util.HashMap<>();

            // Open the temp source file
            InputStream stream = new FileInputStream(filePath);

            // Parse and execute using the JavaCC-generated parser
            LughatDad parser = new LughatDad(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

            LughatDad.BlockStmt program = parser.program();
            program.execute();

            System.exit(0);

        } catch (FileNotFoundException e) {
            System.out.println("خطأ: ملف المصدر غير موجود: " + filePath);
            System.exit(1);

        } catch (ParseException e) {
            System.out.println("خطأ نحوي: " + cleanMessage(e.getMessage()));
            System.exit(1);

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            System.out.println("خطأ: " + cleanMessage(msg));
            System.exit(1);
        }
    }

    /** Remove JavaCC internal details from error messages */
    static String cleanMessage(String msg) {
        if (msg == null) return "خطأ غير معروف";
        // Strip "Encountered..." prefix from ParseException if present
        if (msg.startsWith("Encountered")) {
            int at = msg.indexOf("Was expecting");
            if (at > 0) return msg.substring(at);
        }
        return msg;
    }
}
