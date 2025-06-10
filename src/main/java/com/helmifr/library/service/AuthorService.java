package com.helmifr.library.service;

import com.helmifr.library.dao.AuthorDAO;
import com.helmifr.library.entity.Author;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorService {
    @Autowired
    AuthorDAO authorDAO;

    public Author findAuthorById(Long id) {
        return authorDAO.findById(id).orElse(null);
    }

    public List<Author> findAllAuthors() {
        return authorDAO.findAllByIsDeleted(false);
    }

    public List<Author> searchAuthors(Author author) {
        author.setIsDeleted(false);
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreCase()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);
        return authorDAO.findAll(Example.of(author, matcher),
                Sort.by(Sort.Order.desc("createdAt")));
    }

    public Author saveAuthor(Author author) {
        return authorDAO.save(author);
    }

    public void deleteAuthorById(Long id) {
        authorDAO.findById(id).ifPresent(author -> {
            author.setIsDeleted(true);
            authorDAO.save(author);
        });
    }
}
