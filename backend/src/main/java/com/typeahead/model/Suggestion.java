package com.typeahead.model;

public class Suggestion implements Comparable<Suggestion> {
    private String query;
    private long score;

    public Suggestion() {}

    public Suggestion(String query, long score) {
        this.query = query;
        this.score = score;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    @Override
    public int compareTo(Suggestion other) {
        return Long.compare(this.score, other.score);
    }
}
