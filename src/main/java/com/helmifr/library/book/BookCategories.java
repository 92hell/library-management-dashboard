package com.helmifr.library.book;

import java.util.List;

/** The fixed list of categories a book can be tagged with. */
public final class BookCategories {
    private BookCategories() {}

    public static final List<String> ALL = List.of(
            "Fiction", "Non-Fiction", "Mystery", "Thriller", "Fantasy", "Science Fiction", "Romance",
            "Historical Fiction", "Horror", "Biography & Autobiography", "History", "Self-Help", "Cookbooks",
            "Travel", "Art & Photography", "Science & Technology", "Business & Economics", "Health & Fitness",
            "Religion & Spirituality", "Philosophy", "Poetry", "Comics & Graphic Novels", "Young Adult",
            "Children's Books", "Education", "True Crime", "Memoir", "Classics", "Dystopian");
}
