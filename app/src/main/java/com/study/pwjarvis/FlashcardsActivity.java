package com.study.pwjarvis;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local question/answer decks with due dates, review sessions and reset controls. */
public class FlashcardsActivity extends StudyToolActivity {
    private static final int PAGE_SIZE = 50;
    private long deckId;
    private String deckName = "";
    private int cardOffset;
    private String search = "";
    private final ArrayList<StudyDatabase.Card> queue = new ArrayList<>();
    private int position;
    private int remembered;
    private int again;
    private boolean revealed;
    private boolean reviewing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            deckId = state.getLong("deck", 0);
            search = state.getString("search", "");
            cardOffset = state.getInt("offset", 0);
        }
        // Reviews already rated remain saved; restarting a review is safe after recreation.
        if (deckId > 0) showDeck(); else showDecks();
    }

    private void showDecks() {
        reviewing = false;
        deckId = 0;
        search = "";
        screen("Flashcards", "Create your own NCERT revision cards and review them offline.");
        content.addView(button("+ New deck", () -> editDeck(null)));
        List<StudyDatabase.Deck> decks = store.decks(System.currentTimeMillis());
        if (decks.isEmpty()) {
            content.addView(text("No decks yet. Create Physics, Chemistry or Biology cards.", 15, false));
            return;
        }
        for (StudyDatabase.Deck deck : decks) {
            LinearLayout item = card(content);
            item.addView(text(deck.name, 18, true));
            item.addView(text(deck.subject + " · " + deck.count + " cards · " + deck.due + " due", 14, false));
            addPair(item, "Open", () -> {
                deckId = deck.id;
                search = "";
                cardOffset = 0;
                showDeck();
            }, "Review due", () -> {
                deckId = deck.id;
                deckName = deck.name;
                beginReview();
            });
            addPair(item, "Edit deck", () -> editDeck(deck), "Delete deck", () ->
                    confirm("Delete this deck?", "All flashcards in this deck will be removed.", () -> {
                        store.deleteDeck(deck.id);
                        showDecks();
                    }));
        }
    }

    private void editDeck(StudyDatabase.Deck existing) {
        LinearLayout form = dialogBody();
        EditText name = input("Deck name", existing == null ? "" : existing.name, true);
        Spinner subject = subjects(existing == null ? "Biology" : existing.subject);
        form.addView(name);
        form.addView(subject);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Create deck" : "Edit deck")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                store.saveDeck(existing == null ? 0 : existing.id, name.getText().toString(),
                        subject.getSelectedItem().toString());
                dialog.dismiss();
                showDecks();
            } catch (RuntimeException e) {
                toast(e.getMessage() == null ? "Unable to save deck" : e.getMessage());
            }
        }));
        dialog.show();
    }

    private StudyDatabase.Deck findDeck() {
        for (StudyDatabase.Deck deck : store.decks(System.currentTimeMillis())) {
            if (deck.id == deckId) return deck;
        }
        return null;
    }

    private void showDeck() {
        reviewing = false;
        StudyDatabase.Deck deck = findDeck();
        if (deck == null) {
            showDecks();
            return;
        }
        deckName = deck.name;
        screen(deck.name, deck.subject + " · " + deck.count + " cards · " + deck.due + " due now");
        addPair(content, "+ Add card", () -> editCard(null), "Review due", this::beginReview);
        addPair(content, "Search cards", this::searchCards, "All decks", this::showDecks);
        List<StudyDatabase.Card> all = store.cards(deckId, false, System.currentTimeMillis());
        ArrayList<StudyDatabase.Card> filtered = new ArrayList<>();
        for (StudyDatabase.Card card : all) {
            String combined = (card.front + " " + card.back).toLowerCase(Locale.ROOT);
            if (combined.contains(search.toLowerCase(Locale.ROOT))) filtered.add(card);
        }
        if (filtered.isEmpty()) {
            content.addView(text("No matching cards.", 14, false));
            return;
        }
        if (cardOffset >= filtered.size()) cardOffset = Math.max(0, ((filtered.size() - 1) / PAGE_SIZE) * PAGE_SIZE);
        int end = Math.min(filtered.size(), cardOffset + PAGE_SIZE);
        content.addView(text("Cards " + (cardOffset + 1) + "–" + end + " of " + filtered.size(), 13, false));
        for (int i = cardOffset; i < end; i++) showCard(filtered.get(i));
        LinearLayout pages = row();
        Button previous = button("Previous", () -> {
            cardOffset = Math.max(0, cardOffset - PAGE_SIZE);
            showDeck();
        });
        previous.setEnabled(cardOffset > 0);
        Button next = button("Next", () -> {
            cardOffset += PAGE_SIZE;
            showDeck();
        });
        next.setEnabled(end < filtered.size());
        pages.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        pages.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(pages);
    }

    private void searchCards() {
        EditText input = input("Question or answer", search, true);
        new AlertDialog.Builder(this).setTitle("Find cards").setView(input)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (d,w) -> {
                    search = "";
                    cardOffset = 0;
                    showDeck();
                })
                .setPositiveButton("Search", (d,w) -> {
                    search = input.getText().toString().trim();
                    cardOffset = 0;
                    showDeck();
                }).show();
    }

    private void showCard(StudyDatabase.Card card) {
        LinearLayout item = card(content);
        TextView question = text(card.front, 17, true);
        question.setMaxLines(4);
        item.addView(question);
        String state = card.due == 0 ? "New" :
                card.due <= System.currentTimeMillis() ? "Due now" : "Next: " + StudyDates.date(card.due);
        item.addView(text(state + " · " + card.reviews + " reviews · " + card.lapses + " forgotten", 12, false));
        addPair(item, "Preview answer", () -> new AlertDialog.Builder(this)
                .setTitle(card.front).setMessage(card.back).setPositiveButton("Close", null).show(),
                "Edit", () -> editCard(card));
        addPair(item, "Reset schedule", () -> confirm("Reset card?", "Review history and due date will be reset.", () -> {
            store.resetCard(card.id);
            showDeck();
        }), "Delete", () -> confirm("Delete card?", "This question and answer will be removed.", () -> {
            store.deleteCard(card.id);
            showDeck();
        }));
    }

    private void editCard(StudyDatabase.Card existing) {
        LinearLayout form = dialogBody();
        EditText question = input("Question / term", existing == null ? "" : existing.front, false);
        EditText answer = input("Answer / explanation", existing == null ? "" : existing.back, false);
        form.addView(question);
        form.addView(answer);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New flashcard" : "Edit flashcard")
                .setView(dialogScroll(form)).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                store.saveCard(existing == null ? 0 : existing.id, deckId,
                        question.getText().toString(), answer.getText().toString());
                dialog.dismiss();
                showDeck();
            } catch (RuntimeException e) {
                toast(e.getMessage() == null ? "Unable to save card" : e.getMessage());
            }
        }));
        dialog.show();
    }

    private void beginReview() {
        queue.clear();
        queue.addAll(store.cards(deckId, true, System.currentTimeMillis()));
        position = 0;
        remembered = 0;
        again = 0;
        revealed = false;
        reviewing = true;
        showReview();
    }

    private void showReview() {
        screen(deckName + " review", "Try to recall the answer before revealing it.");
        if (position >= queue.size()) {
            content.addView(text(queue.isEmpty() ? "No cards due right now." : "Review complete", 22, true));
            content.addView(text(remembered + " remembered · " + again + " to review again", 15, false));
            if (again > 0) content.addView(text("Forgotten cards become due again in 10 minutes.", 13, false));
            content.addView(button("Back to deck", this::showDeck));
            reviewing = false;
            return;
        }
        StudyDatabase.Card card = queue.get(position);
        TextView counter = text((position + 1) + " / " + queue.size(), 14, true);
        content.addView(counter);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(queue.size());
        progress.setProgress(position);
        content.addView(progress);
        LinearLayout display = card(content);
        display.addView(text(card.front, 21, true));
        if (revealed) {
            display.addView(text("Answer", 14, true));
            TextView answer = text(card.back, 19, false);
            answer.setTextIsSelectable(true);
            display.addView(answer);
            addPair(content, "Forgot / again", () -> rate(false), "Remembered", () -> rate(true));
        } else {
            content.addView(button("Reveal answer", () -> {
                revealed = true;
                showReview();
            }));
        }
        content.addView(button("End review", this::endReview));
    }

    private void rate(boolean correct) {
        if (!reviewing || !revealed || position >= queue.size()) return;
        try {
            store.rate(queue.get(position), correct, System.currentTimeMillis());
            if (correct) remembered++; else again++;
            position++;
            revealed = false;
            showReview();
        } catch (RuntimeException e) {
            toast("Could not save rating; reopen review.");
            showDeck();
        }
    }

    private void endReview() {
        confirm("End review?", "Ratings already saved will be retained.", this::showDeck);
    }

    @Override public void onBackPressed() {
        if (reviewing) {
            endReview();
        } else if (deckId > 0) {
            showDecks();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("deck", deckId);
        state.putInt("offset", cardOffset);
        state.putString("search", search);
        super.onSaveInstanceState(state);
    }
}
