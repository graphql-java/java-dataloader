package org.dataloader.fixtures;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserManager {

    public static final User ILÚVATAR = new User(-1L, -1L, "Ilúvatar");
    public static final User AULË = new User(10001L, -1L, "Aulë");
    public static final User OROMË = new User(10002L, -1L, "Oromë");
    public static final User YAVANNA = new User(10003L, -1L, "Yavanna");
    public static final User MANWË = new User(10004L, -1L, "Manwë");
    public static final User MORGOTH = new User(10005L, -1L, "Morgoth");
    public static final User CURUNIR = new User(2L, 10001L, "Curunir");
    public static final User ALATAR = new User(3L, 10002L, "Alatar");
    public static final User AIWENDIL = new User(4L, 10003L, "Aiwendil");
    public static final User OLÓRIN = new User(1L, 10004L, "Olórin");
    public static final User SAURON = new User(5L, 10005L, "Sauron");

    final Map<Long, User> users = new LinkedHashMap<>();

    {
        add(ILÚVATAR);

        add(AULË);
        add(OROMË);
        add(YAVANNA);
        add(MANWË);
        add(MORGOTH);

        add(CURUNIR);
        add(ALATAR);
        add(AIWENDIL);
        add(OLÓRIN);
        add(SAURON);
    }

    private void add(User user) {
        users.put(user.getId(), user);
    }

    public User loadUsersById(Long userId) {
        return users.get(userId);
    }
}
