package framework;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import framework.annotations.*;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static Map<String, List<String>> getQueryParams(String url) {
        try {
            Map<String, List<String>> params = new HashMap<String, List<String>>();
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1) {
                String query = urlParts[1];
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1) {
                        try {
                            value = URLDecoder.decode(pair[1], "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }

                    List<String> values = params.get(key);
                    if (values == null) {
                        values = new ArrayList<String>();
                        params.put(key, values);
                    }
                    values.add(value);
                }
            }

            return params;
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
    }

    public Object tmp;
    private Server thisServer = this;
    HttpServer httpServer;
    String dbName;

    @DBField
    public HashMap<String, Integer> secrets = initSecrets();

    private HashMap<String, Integer> initSecrets() {
        HashMap<String, Integer> str = new HashMap<>();
        str.put("admin", 4);
        return str;
    }

    private Map<String, Serializable> db = new HashMap<>();

    public Server(int port, String dbName) throws IOException {
        this.dbName = dbName;
        httpServer = HttpServer.create(new InetSocketAddress(port), 4);
        Class cl = getClass();
        System.out.println(cl);
        Method[] m = cl.getMethods();
        for (Method method : m) {
            if (method.isAnnotationPresent(ApiMethod.class)) {
                ApiMethod apiMethod = method.getAnnotation(ApiMethod.class);
                String path = apiMethod.path().replace("__auto__", "/" + method.getName());
                HttpHandler hh = methodHandler(method, path);
                if (method.isAnnotationPresent(RequiredRights.class)) {
                    int lev = method.getAnnotation(RequiredRights.class).level();
                    hh = incapsulator(hh, lev);
                }
                httpServer.createContext(path, hh);
            }
            if (method.isAnnotationPresent(DirectoryProvider.class)) {
                DirectoryProvider directoryProvider = method.getAnnotation(DirectoryProvider.class);
                String path = directoryProvider.path().replace("__auto__", "/" + method.getName());
                HttpHandler hh = directoryProvider(method, path);
                if (method.isAnnotationPresent(RequiredRights.class)) {
                    int lev = method.getAnnotation(RequiredRights.class).level();
                    hh = incapsulator(hh, lev);
                }
                httpServer.createContext(path, hh);
            }
        }
        tmp = this;
    }

    public Server(int port) throws IOException {
        this(port, "temp.db");
    }


    private static Object objectFromString(String a, Class<?> cl) {
        if (cl.getName().equals("int")) {
            return Integer.parseInt(a);
        }
        if (cl.getName().equals("long")) {
            return Long.parseLong(a);
        }
        if (cl.getName().equals("double")) {
            return Double.parseDouble(a);
        }
        if (cl.getName().equals("short")) {
            return Short.parseShort(a);
        }
        if (cl.getName().equals("byte")) {
            return Byte.parseByte(a);
        }
        if (cl.getName().equals("boolean")) {
            return Boolean.parseBoolean(a);
        }
        try {
            return cl.getDeclaredConstructor(String.class).newInstance(a);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    private HttpHandler methodHandler(Method method, String path) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                if (!httpExchange.getRequestURI().getPath().equals(path)) {
                    int code = 404;
                    String result = "<h1>404 Not found</h1>Cannot found context";
                    byte[] bytes = result.getBytes();
                    httpExchange.sendResponseHeaders(code, bytes.length);

                    OutputStream os = httpExchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }
                int code = 500;
                String result = "<h1>500 Server error</h1>%s";

                try {
                    Map<String, List<String>> lists = getQueryParams(httpExchange.getRequestURI().toString());
                    Parameter[] parameters = method.getParameters();
                    Object[] objects = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        if (!parameters[i].isAnnotationPresent(GetParameter.class)) {
                            continue;
                        }
                        GetParameter getParameter = parameters[i].getAnnotation(GetParameter.class);
                        String name = getParameter.name();
                        boolean isReq = getParameter.isRequired();
                        if (!lists.containsKey(name)) {
                            if (isReq) {
                                throw new Exception("Get parameter " + name + " is required");
                            } else {
                                objects[i] = null;
                            }
                        } else {
                            objects[i] = objectFromString(
                                    lists.get(name).get(0),
                                    parameters[i].getType());
                        }
                    }
                    result = String.valueOf(method.invoke(thisServer, objects));
                    code = 200;
                } catch (Exception e) {
                    String res = e.toString();
                    for (int i = 0; i < e.getStackTrace().length; i++) {
                        res += "\n" + e.getStackTrace()[i];
                    }
                    result = String.format(result, res);
                    e.printStackTrace();
                }

                byte[] bytes = result.getBytes();
                httpExchange.sendResponseHeaders(code, bytes.length);

                OutputStream os = httpExchange.getResponseBody();
                os.write(bytes);
                os.close();

            }
        };
    }

    private HttpHandler directoryProvider(Method method, String path) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                if (!httpExchange.getRequestURI().getPath().startsWith(path + "/")) {
                    int code = 404;
                    String result = "<h1>404 Not found</h1>Cannot found context";
                    byte[] bytes = result.getBytes();
                    httpExchange.sendResponseHeaders(code, bytes.length);

                    OutputStream os = httpExchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }
                int code = 500;
                String result = "<h1>500 Server error</h1>%s";

                try {
                    Map<String, List<String>> lists = getQueryParams(httpExchange.getRequestURI().toString());
                    Parameter[] parameters = method.getParameters();
                    Object[] objects = new Object[parameters.length];
                    for (int i = 1; i < parameters.length; i++) {
                        if (!parameters[i].isAnnotationPresent(GetParameter.class)) {
                            continue;
                        }
                        GetParameter getParameter = parameters[i].getAnnotation(GetParameter.class);
                        String name = getParameter.name();
                        boolean isReq = getParameter.isRequired();
                        if (!lists.containsKey(name)) {
                            if (isReq) {
                                throw new Exception("Get parameter " + name + " is required");
                            } else {
                                objects[i] = null;
                            }
                        } else {
                            objects[i] = objectFromString(
                                    lists.get(name).get(0),
                                    parameters[i].getType());
                        }
                    }
                    DirectoryProvider directoryProvider = method.getAnnotation(DirectoryProvider.class);
                    objects[0] = new File(directoryProvider.pathToDir() + httpExchange.getRequestURI().getPath().substring(path.length() + 1));
                    code = 200;
                    File file = (File) method.invoke(thisServer, objects);
                    httpExchange.sendResponseHeaders(200, file.length());
                    Files.copy(file.toPath(), httpExchange.getResponseBody());
                    httpExchange.getResponseBody().close();
                    return;
                } catch (Exception e) {
                    result = String.format(result, e.toString());
                    e.printStackTrace();
                }

                byte[] bytes = result.getBytes();
                httpExchange.sendResponseHeaders(code, bytes.length);

                OutputStream os = httpExchange.getResponseBody();
                os.write(bytes);
                os.close();

            }
        };
    }

    private HttpHandler incapsulator(HttpHandler httpHandler, int level) {
        return httpExchange -> {
            Map<String, List<String>> lists = getQueryParams(httpExchange.getRequestURI().toString());
            int cur_lev = 0;
            if (lists.containsKey("secret")) {
                cur_lev = secrets.get(lists.get("secret").get(0));
                httpExchange.getResponseHeaders().add("Set-Cookie", "secret=" + lists.get("secret").get(0));
            }
            if (httpExchange.getRequestHeaders().containsKey("Cookie")) {
                String cook = httpExchange.getRequestHeaders().getFirst("Cookie").split(";")[0];
                String sec = cook.split("=")[1];
                if (secrets.containsKey(sec)) {
                    cur_lev = secrets.get(sec);
                }
            }
            if (cur_lev < level) {
                int code = 403;
                String result = "<h1>403 Access denied</h1>Your permission level";
                byte[] bytes = result.getBytes();
                httpExchange.sendResponseHeaders(code, bytes.length);

                OutputStream os = httpExchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }
            httpHandler.handle(httpExchange);
        };
    }

    public void start() throws IOException {
        httpServer.start();
        Class cl = getClass();
        if (new File(dbName).exists()) {
            FileInputStream fis = new FileInputStream(dbName);
            ObjectInputStream oin = new ObjectInputStream(fis);
            try {
                db = (Map<String, Serializable>) oin.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            for (Field field : cl.getFields()) {
                if (field.isAnnotationPresent(DBField.class)) {
                    String name = field.getName();
                    System.out.println(name);
                    if (!db.containsKey(name)) {
                        try {
                            db.put(name, (Serializable) field.get(thisServer));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            field.set(this, db.get(name));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        Object This = this;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    for (String key : db.keySet()) {
                        try {
                            db.put(key, (Serializable) cl.getField(key).get(This));
                        } catch (IllegalAccessException | NoSuchFieldException e) {
                            e.printStackTrace();
                        }
                    }

                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(dbName);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    ObjectOutputStream oos = null;
                    try {
                        oos = new ObjectOutputStream(fos);
                        oos.writeObject(db);
                        oos.flush();
                        oos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

    }

    public void stop(int i) {
        httpServer.stop(i);
    }
}
