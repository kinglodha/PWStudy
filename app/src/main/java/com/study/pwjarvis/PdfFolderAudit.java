package com.study.pwjarvis;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Permission-scoped, cancellable offline scanner. No source files are written. */
public final class PdfFolderAudit {
    private static final int DIRECTORY_LIMIT = 20000;
    private static final int FILE_LIMIT = 100000;
    private static final String[] COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    public interface Listener {
        void progress(int files, int folders);
    }

    public static final class Entry {
        public final Uri uri;
        public final String parentId;
        public final String path;
        public final String name;
        public final String suggestion;
        public final String subject;
        public final long bytes;
        public final long modified;

        Entry(Uri uri, String parentId, String path, String name, long bytes, long modified) {
            this.uri = uri;
            this.parentId = parentId;
            this.path = path;
            this.name = name;
            this.suggestion = PdfNameRules.suggest(name);
            this.subject = PdfNameRules.subject(name, path);
            this.bytes = bytes;
            this.modified = modified;
        }
    }

    public static final class Result {
        public final List<Entry> entries = new ArrayList<>();
        public final Map<String, List<Entry>> duplicates = new HashMap<>();
        public final Set<String> collisions = new HashSet<>();
        public int folders;
        public int errors;
        public boolean partial;
        public long bytes;
    }

    private static final class Directory {
        final String id;
        final String path;
        Directory(String id, String path) {
            this.id = id;
            this.path = path;
        }
    }

    private final ContentResolver resolver;
    private final AtomicBoolean cancelled;

    public PdfFolderAudit(Context context, AtomicBoolean cancelled) {
        this.resolver = context.getContentResolver();
        this.cancelled = cancelled;
    }

    private boolean stopping() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public Result scan(Uri tree, boolean verifyDuplicates, Listener listener) {
        Result result = new Result();
        Set<String> seen = new HashSet<>();
        Map<Long, List<Entry>> bySize = new HashMap<>();
        Map<String, Integer> targets = new HashMap<>();
        ArrayDeque<Directory> queue = new ArrayDeque<>();
        queue.add(new Directory(DocumentsContract.getTreeDocumentId(tree), ""));
        while (!queue.isEmpty() && !stopping() && seen.size() < DIRECTORY_LIMIT
                && result.entries.size() < FILE_LIMIT) {
            Directory folder = queue.removeFirst();
            if (!seen.add(folder.id)) continue;
            result.folders++;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id);
            try (Cursor cursor = resolver.query(children, COLUMNS, null, null, null)) {
                if (cursor == null) {
                    result.errors++;
                    continue;
                }
                while (!stopping() && cursor.moveToNext()) {
                    if (result.entries.size() >= FILE_LIMIT) break;
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (id == null || name == null) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        queue.add(new Directory(id, folder.path + name + "/"));
                        continue;
                    }
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")
                            && !"application/pdf".equals(mime)) continue;
                    long size = cursor.isNull(3) ? -1 : cursor.getLong(3);
                    long date = cursor.isNull(4) ? 0 : cursor.getLong(4);
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    Entry entry = new Entry(uri, folder.id, folder.path, name, size, date);
                    result.entries.add(entry);
                    if (size >= 0) {
                        result.bytes += size;
                        if (verifyDuplicates) {
                            List<Entry> group = bySize.get(size);
                            if (group == null) {
                                group = new ArrayList<>();
                                bySize.put(size, group);
                            }
                            group.add(entry);
                        }
                    }
                    String key = collisionKey(entry);
                    Integer count = targets.get(key);
                    targets.put(key, count == null ? 1 : count + 1);
                    if (result.entries.size() % 50 == 0) {
                        listener.progress(result.entries.size(), result.folders);
                    }
                }
            } catch (RuntimeException e) {
                result.errors++;
            }
        }
        for (Map.Entry<String, Integer> e : targets.entrySet()) {
            if (e.getValue() > 1) result.collisions.add(e.getKey());
        }
        if (verifyDuplicates && !stopping()) {
            // Only hash equal-sized candidates; a matching size alone is not a duplicate.
            Map<String, List<Entry>> hashes = new HashMap<>();
            for (List<Entry> group : bySize.values()) {
                if (stopping()) break;
                if (group.size() < 2) continue;
                for (Entry entry : group) {
                    if (stopping()) break;
                    try {
                        String digest = digest(entry.uri);
                        if (digest == null) continue;
                        List<Entry> matches = hashes.get(digest);
                        if (matches == null) {
                            matches = new ArrayList<>();
                            hashes.put(digest, matches);
                        }
                        matches.add(entry);
                    } catch (Exception e) {
                        result.errors++;
                    }
                }
            }
            for (Map.Entry<String, List<Entry>> group : hashes.entrySet()) {
                if (group.getValue().size() > 1) {
                    result.duplicates.put(group.getKey(), group.getValue());
                }
            }
        }
        result.partial = stopping() || !queue.isEmpty() || result.errors > 0
                || result.entries.size() >= FILE_LIMIT;
        return result;
    }

    public static String collisionKey(Entry entry) {
        return entry.parentId + "\n" + entry.suggestion.toLowerCase(Locale.ROOT);
    }

    private String digest(Uri uri) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("Unable to open file");
            int count;
            while (!stopping() && (count = input.read(buffer)) != -1) {
                hash.update(buffer, 0, count);
            }
        }
        if (stopping()) return null;
        StringBuilder text = new StringBuilder();
        for (byte b : hash.digest()) {
            int v = b & 255;
            text.append(Character.forDigit(v >>> 4, 16));
            text.append(Character.forDigit(v & 15, 16));
        }
        return text.toString();
    }
}
