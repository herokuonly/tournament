import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        TableServer tableServer = new TableServer(Integer.parseInt(args[0]));
        tableServer.start();
    }
}
//dubr0vindmitr