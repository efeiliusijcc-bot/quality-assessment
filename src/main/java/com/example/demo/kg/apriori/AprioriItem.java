package com.example.demo.kg.apriori;

/** A transaction item used by Apriori mining. */
public record AprioriItem(String type, String refId, String name, String state) implements Comparable<AprioriItem> {
    public AprioriItem {
        state = state == null ? "" : state;
    }

    public String key() {
        return type + "|" + (refId == null ? "" : refId) + "|" + name + "|" + state;
    }

    public String displayName() {
        return state == null || state.isBlank() ? name : name + "[" + state + "]";
    }

    @Override
    public int compareTo(AprioriItem other) {
        return key().compareTo(other.key());
    }
}
