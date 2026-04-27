package storage;
import java.io.*;

public class FileManager {

    public static void save(Object obj, String filename) throws Exception {
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename));
        out.writeObject(obj);
        out.close(); }
   

    public static Object load(String filename) throws Exception {
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename));
        Object obj = in.readObject();
        in.close();
        return obj; }
  
        
}