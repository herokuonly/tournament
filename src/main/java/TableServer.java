import com.google.gson.Gson;
import framework.BaseServer;
import framework.annotations.*;
import sun.font.Script;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class TableServer extends BaseServer {
    public TableServer(int port) throws IOException {
        super(port);
    }

    @DBField
    public Map<String, Tournament> tournaments = new TreeMap<>();

    @ApiMethod(path = "/admin")
    public String adminRedirect() {
        return redirect("/admin/enter/index.html");
    }

    @DirectoryProvider(pathToDir = "admin/enter/", path = "/admin/enter")
    public File adminEnterProvider(File file) {
        return file;
    }

    @DirectoryProvider(pathToDir = "admin/level1/", path = "/admin/1")
    @RequiredRights(level = 1)
    public File adminProvider1(File file) {
        return file;
    }

    @DirectoryProvider(pathToDir = "admin/res/", path = "/admin/res")
    @RequiredRights(level = 1)
    public File adminResProvider(File file) {
        return file;
    }

    @DirectoryProvider(pathToDir = "admin/level2/", path = "/admin/2")
    @RequiredRights(level = 2)
    public File adminProvider2(File file) {
        return file;
    }

    @DirectoryProvider(pathToDir = "admin/level3/", path = "/admin/3")
    @RequiredRights(level = 3)
    public File adminProvider3(File file) {
        return file;
    }

    @DirectoryProvider(pathToDir = "admin/level4/", path = "/admin/4")
    @RequiredRights(level = 4)
    public File adminProvider4(File file) {
        return file;
    }

    @ApiMethod(path = "/save_cookies")
    @RequiredRights(level = 1)
    public String saveCookies() {
        return redirect("/admin/1/index.html");
    }


    @DirectoryProvider(pathToDir = "view/", path = "/view")
    public File baseProvider(File file,
                             @GetParameter(name = "tournament", isRequired = false) String tournamentName) throws FileNotFoundException {
        if (!file.getName().endsWith(".html")) {
            return file;
        }
        if (tournamentName != null) {
            tournaments.get(tournamentName).current = true;
        }
        File res = renderHtml(file, new Gson().toJson(tournaments));
        if (tournamentName != null) {
            tournaments.get(tournamentName).current = false;
        }
        return res;
    }

    @ApiMethod(path = "/")
    public String base() {
        return redirect("/view/main.html");
    }

    public static String redirect(String to) {
        return String.format(
                "<!DOCTYPE html><html lang=\"en\"><head><meta http-equiv=\"refresh\" content=\"0;%s\"></head><body></body></html>",
                to
        );
    }

    public static Random random = new Random(1297112973);

    @ApiMethod(path = "/create_tournament")
    @RequiredRights(level = 1)
    public void addTournament(
            @GetParameter(name = "name") String name) {
        tournaments.put(name, new Tournament());
    }

    @ApiMethod(path = "/all_tournaments")
    @RequiredRights(level = 1)
    public String allTournaments() {
        return new Gson().toJson(tournaments);
    }


    @ApiMethod(path = "/xor_tournament")
    @RequiredRights(level = 1)
    public void xorTournament(
            @GetParameter(name = "name") String name) {
        tournaments.get(name).open ^= true;
    }

    @ApiMethod(path = "/xor_tournament_ans")
    @RequiredRights(level = 1)
    public void xorTournamentAns(
            @GetParameter(name = "name") String name) {
        tournaments.get(name).canAns ^= true;
    }

    @ApiMethod(path = "/get_tournament")
    @RequiredRights(level = 1)
    public String getTournament(
            @GetParameter(name = "name") String name) {
        return new Gson().toJson(tournaments.get(name));
    }

    @ApiMethod(path = "/add_user")
    @RequiredRights(level = 1)
    public String addUser(
            @GetParameter(name = "name") String name,
            @GetParameter(name = "deck") String deck,
            @GetParameter(name = "tournament") String tournament) {
        System.out.println(name + " " + deck + " " + tournament);
        tournaments.get(tournament).addUser(name, deck);
        return "";
    }

    @ApiMethod(path = "/remove_user")
    @RequiredRights(level = 1)
    public String removeUser(
            @GetParameter(name = "id") String id,
            @GetParameter(name = "tournament") String tournament) {
        tournaments.get(tournament).removeUser(id);
        return "";
    }

    @ApiMethod(path = "/win_match")
    @RequiredRights(level = 1)
    public String winMatch(
            @GetParameter(name = "id1") String id1,
            @GetParameter(name = "id2") String id2,
            @GetParameter(name = "tournament") String tournament) {
        tournaments.get(tournament).win(id1, id2);
        return redirect("/admin/1/tournament.html?name=" + tournament);
    }

    @ApiMethod(path = "/ans_form")
    public String ansForm(
            @GetParameter(name = "code") String code,
            @GetParameter(name = "ans") String ans,
            @GetParameter(name = "tournament") String tournament) {
        if (!tournaments.containsKey(tournament)) {
            return redirect("/view/error.html");
        }
        Tournament tour = tournaments.get(tournament);
        if (!tour.canAns) {
            return redirect("/view/error.html");
        }
        if (!tour.users.containsKey(code)) {
            return redirect("/view/error.html");
        }
        User user = tour.users.get(code);
        if (!user.canAns) {
            return redirect("/view/error.html");
        }
        user.canAns = false;
        System.out.println(ans+" "+code+" "+tournament);
        String[] ids = ans.split(";");
        int score = ids.length - 1;
        for (String id : ids) {
            if (id.equals(code)) {
                continue;
            }
            tour.users.get(id).score += score--;
        }
        return redirect("/view/table.html?tournament=" + tournament);
    }

}

class Tournament implements Serializable {
    boolean open = false;
    boolean current = false;
    boolean canAns = false;


    Map<String, String> tokens = new TreeMap<>();
    TreeMap<String, User> users = new TreeMap();

    public String addUser(String name, String deck) {
        User user = new User(name, deck);
        users.put(user.id, user);
        return user.id;
    }

    public void removeUser(String id) {
        users.remove(id);
    }

    public String createToken(String id) {
        String code = "";
        for (int i = 0; i < 20; i++) {
            code += (char) (TableServer.random.nextInt(26) + 'a');
        }
        tokens.put(code, id);
        System.out.println(tokens + " " + id);
        return code;
    }

    public void win(String id1, String id2) {
        users.get(id1).score++;
        users.get(id1).wins++;
        users.get(id2).loose++;
        users.get(id2).score--;
    }
}

class User implements Serializable {
    int score;
    String name;
    String deckName;
    String id;
    int wins;
    int loose;
    boolean canAns = true;


    public User(String name, String deckName) {
        this.score = 0;
        this.name = name;
        this.deckName = deckName;
        this.id = "";
        for (int i = 0; i < 20; i++) {
            this.id += (char) (TableServer.random.nextInt(26) + 'a');
        }
    }
}
