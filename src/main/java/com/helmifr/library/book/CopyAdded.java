package com.helmifr.library.book;

/** Published when a copy joins the collection, so waiting holds can be served. */
public record CopyAdded(Book book) {}
