package docxAnonymizer;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class UtilsID {
    private Random rnd_int = new Random(System.currentTimeMillis());
    private HashMap<String, String> idMap = new HashMap<String, String>();
    private String BASE = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

    public String getID(String nominativoPlain){
        List<String> plainSplit = Arrays.asList(nominativoPlain.split("\\s"));
        if (plainSplit.size() < 2)
            throw new IllegalArgumentException("Un nominativo deve avere almeno un nome ed un cognome");
        String uniqueFlat = Persona.getAsUniqueFlatString(plainSplit);
        if(idMap.containsKey(uniqueFlat))
            return idMap.get(uniqueFlat);
        else {
            String newID;
            do {
                int n = rnd_int.nextInt();
                if (n >= 0)
                    newID = encode36(n);
                else
                    newID = encode36(-n);
            } while(idMap.containsValue(newID));
            idMap.put(uniqueFlat, newID);
            return newID;
        }
    }

    private String encode36(int n){
        return new BigInteger(String.valueOf(n)).toString(36).toUpperCase();
    }
}
