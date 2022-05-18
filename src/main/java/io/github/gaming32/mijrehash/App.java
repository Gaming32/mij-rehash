package io.github.gaming32.mijrehash;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class App {
    public static void main(String[] args) throws FileNotFoundException, IOException, NoSuchAlgorithmException {
        if (args.length < 1) {
            System.err.println("java -jar mij-rehash-1.0-SNAPSHOT.jar <input-file> [output-file]");
            return;
        }
        String inputFile = args[0];
        String outputFile = args.length > 1 ? args[1] : inputFile;
        JsonObject json;
        try (Reader reader = new FileReader(inputFile)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (System.getProperty("http.agent") == null) {
            System.setProperty("http.agent", "Mozilla/5.0");
        }

        MultiMessageDigest digest = new MultiMessageDigest(
            MessageDigest.getInstance("SHA-1"),
            MessageDigest.getInstance("SHA-512")
        );
        for (JsonElement file : json.getAsJsonArray("files")) {
            JsonObject fileObj = file.getAsJsonObject();
            System.out.print("Processing " + fileObj.get("path").getAsString());
            digest.reset();
            URL downloadUrl = new URL(fileObj.getAsJsonArray("downloads").get(0).getAsString());
            System.out.println(" (" + downloadUrl + ")");
            long downloadSize;
            try (InputStream is = new DigestInputStream(downloadUrl.openStream(), digest)) {
                downloadSize = readThrough(is);
            }
            String sha1 = toHexString(digest.getDigests()[0].digest());
            String sha512 = toHexString(digest.getDigests()[1].digest());
            System.out.println("   sha1:     " + sha1);
            System.out.println("   sha512:   " + sha512);
            System.out.println("   fileSize: " + downloadSize);
            JsonObject hashes = getOrNewObject(fileObj, "hashes");
            hashes.addProperty("sha1", sha1);
            hashes.addProperty("sha512", sha512);
            fileObj.addProperty("fileSize", downloadSize);
        }

        try (Writer writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(json, writer);
        }
    }

    private static long readThrough(InputStream is) throws IOException {
        long size = 0;
        byte[] buffer = new byte[8192];
        int n;
        while ((n = is.read(buffer)) != -1) {
            size += n;
        }
        return size;
    }

    private static JsonObject getOrNewObject(JsonObject parent, String key) {
        JsonObject child = parent.getAsJsonObject(key);
        if (child == null) {
            child = new JsonObject();
            parent.add(key, child);
        }
        return child;
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static String toHexString(byte[] arr) {
        StringBuilder result = new StringBuilder(arr.length << 1);
        for (byte v : arr) {
            result.append(HEX_CHARS[(v & 0xff) >> 4]);
            result.append(HEX_CHARS[v & 0xf]);
        }
        return result.toString();
    }
}
