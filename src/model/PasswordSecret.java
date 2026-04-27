package model;

public class PasswordSecret extends Secret {
    private String password;

    public PasswordSecret(String name, String password) {
        super(name);
        this.password = password;
    }
    @Override
    public String getValue() {
        return password;
    }
}