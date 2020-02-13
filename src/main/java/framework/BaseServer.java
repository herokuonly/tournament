package framework;

import com.google.gson.Gson;
import framework.annotations.ApiMethod;
import framework.annotations.GetParameter;
import framework.annotations.RequiredRights;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class BaseServer extends Server {
    public BaseServer(int port) throws IOException {
        super(port);
    }

    public BaseServer(int port, String dbName) throws IOException {
        super(port, dbName);
    }

    Random random = new Random();


    @ApiMethod(path = "/create_secret")
    @RequiredRights(level = 4)
    public String createSecret(@GetParameter(name = "level") int level) {
        String secret = "";
        for (int i = 0; i < 20; i++) {
            int id = random.nextInt(36);
            if (id < 26) {
                secret += (char) ('a' + id);
            } else {
                secret += (char) ('0' + id - 26);
            }
        }
        secret += "-";
        for (int i = 0; i < 10; i++) {
            int id = random.nextInt(36);
            if (id < 26) {
                secret += (char) ('a' + id);
            } else {
                secret += (char) ('0' + id - 26);
            }
        }
        secrets.put(secret, level);
        return secret;
    }


    @ApiMethod(path = "/all_secrets")
    @RequiredRights(level = 4)
    public String allSecrets(@GetParameter(name = "level", isRequired = false) Integer level) {
        if (level == null) {
            return new Gson().toJson(secrets);
        }
        ArrayList<String> res = new ArrayList<>();
        for (String keys : secrets.keySet()) {
            if (secrets.get(keys).equals(level)) {
                res.add(keys);
            }
        }
        return res.toString();
    }

    @ApiMethod(path = "/remove_secret")
    @RequiredRights(level = 4)
    public String removeSecret(@GetParameter(name = "value") String secret) {
        secrets.remove(secret);
        return "OK";
    }

    private static String RENDER_START = "<script for=\"render\">";
    private static String RENDER_END = "end()</script>";

    public File renderHtml(File file, String jsonData) throws FileNotFoundException {
        Scanner in = new Scanner(file);
        String str = "";
        while (in.hasNext()) {
            str += in.nextLine() + "\n";
        }
        ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("js");
        try {
            scriptEngine.eval("" +
                    "        var page = {\n" +
                    "            innerHTML:\"\"\n" +
                    "        };");

            scriptEngine.put("parameter", jsonData);
            scriptEngine.eval("parameter = JSON.parse(parameter);");
            while (str.contains(RENDER_START)) {
                scriptEngine.eval("page.innerHTML=\'\';");
                int ind1 = str.indexOf(RENDER_START);
                int ind2 = str.indexOf(RENDER_END);
                String pref = str.substring(0, ind1);
                String suff = str.substring(ind2 + RENDER_END.length());
                String code = str.substring(ind1 + RENDER_START.length(), ind2);
                scriptEngine.eval(code);
                str = pref + scriptEngine.eval("page.innerHTML") + suff;
            }
            PrintWriter out = new PrintWriter("render/tmp");
            out.print(str);
            out.flush();
            out.close();
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        return new File("render/tmp");
    }


}
