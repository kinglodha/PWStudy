package com.study.pwjarvis.telegram;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private, no-backup storage for the TDLib database key and application credentials. */
final class PrivateTdStore {
    private static final String KEY_ALIAS = "pwstudy_tdlib_credentials_v1";
    private final AtomicFile envelope;
    private final File storage;

    PrivateTdStore(Context context) {
        storage = new File(context.getNoBackupFilesDir(), "native-telegram");
        envelope = new AtomicFile(new File(storage, "credentials.enc"));
    }

    File databaseDirectory() { return new File(storage, "database"); }
    File filesDirectory() { return new File(storage, "files"); }

    JSONObject read() throws Exception {
        File base = envelope.getBaseFile();
        File backup = new File(base.getPath() + ".bak");
        File pending = new File(base.getPath() + ".new");
        if (!base.exists() && !backup.exists()) {
            // Never manufacture a fresh database key when old TDLib data may still exist.
            // An interrupted first write needs investigation rather than silent deletion.
            if (pending.exists() || hasStoredData(databaseDirectory()) || hasStoredData(filesDirectory()))
                throw new IllegalStateException("Existing Telegram data needs recovery");
            return null;
        }
        SecretKey key = findKey();
        if (key == null) throw new IllegalStateException("Session key unavailable");
        byte[] encrypted;
        // AtomicFile.openRead() restores a legacy .bak before returning the base file.
        try (FileInputStream input = envelope.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 8192) throw new IllegalStateException("Invalid secure store");
                output.write(buffer, 0, count);
            }
            encrypted = output.toByteArray();
        }
        JSONObject stored = new JSONObject(new String(encrypted, StandardCharsets.UTF_8));
        byte[] iv = Base64.decode(stored.getString("iv"), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(stored.getString("ciphertext"), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
    }

    private static boolean hasStoredData(File directory) {
        if (!directory.exists()) return false;
        if (!directory.isDirectory()) return true;
        File[] children = directory.listFiles();
        // An unreadable directory is not evidence that it is safe to create a new key.
        if (children == null) return true;
        for (File child : children) if (hasStoredData(child)) return true;
        return false;
    }

    /** Preserve the existing random database key when application credentials are corrected. */
    JSONObject writeCredentials(int apiId, String apiHash) throws Exception {
        if (!storage.exists() && !storage.mkdirs()) throw new IllegalStateException("Storage unavailable");
        JSONObject previous = read();
        byte[] databaseKey;
        if (previous == null) {
            databaseKey = new byte[32];
            new SecureRandom().nextBytes(databaseKey);
        } else {
            databaseKey = Base64.decode(previous.getString("db_key"), Base64.NO_WRAP);
        }
        JSONObject data = new JSONObject();
        data.put("api_id", apiId);
        data.put("api_hash", apiHash);
        data.put("db_key", Base64.encodeToString(databaseKey, Base64.NO_WRAP));
        SecretKey key = findKey();
        if (key == null) {
            if (previous != null) throw new IllegalStateException("Session key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            key = generator.generateKey();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] ciphertext = cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject outer = new JSONObject();
        outer.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        outer.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        FileOutputStream output = null;
        try {
            output = envelope.startWrite();
            output.write(outer.toString().getBytes(StandardCharsets.UTF_8));
            envelope.finishWrite(output);
        } catch (Exception e) {
            if (output != null) envelope.failWrite(output);
            throw e;
        }
        java.util.Arrays.fill(databaseKey, (byte) 0);
        return data;
    }

    private SecretKey findKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}
