package vault;
import java.util.ArrayList;
import java.util.List;
import model.Secret;

public class Vault<T extends Secret> {

    private List<T> secrets = new ArrayList<>();

    public void addSecret(T secret) {
        secrets.add(secret);
    }

    public List<T> getSecrets() {
        return secrets;
    }
}