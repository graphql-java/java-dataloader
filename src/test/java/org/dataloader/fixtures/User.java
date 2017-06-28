package org.dataloader.fixtures;

public class User {
    final Long id;
    final String name;
    final Long invitedByID;

    public User(Long id, Long invitedByID, String name) {
        this.id = id;
        this.name = name;
        this.invitedByID = invitedByID;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getInvitedByID() {
        return invitedByID;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                "invitedByID='" + invitedByID + '\'' +
                '}';
    }
}
