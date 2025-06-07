package com.helmifr.library.service;

import com.helmifr.library.dao.AuthorDAO;
import com.helmifr.library.entity.Author;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthorService {
    @Autowired
    AuthorDAO authorDAO;

    public Author findAuthorById(Long id) {
        Optional<Author> author = authorDAO.findById(id);
        return author.orElse(null);
    }

    public List<Author> findAllAuthors() {
        return authorDAO.findAllByIsDeleted(false);
    }

    public List<Author> searchAuthors(Author author) {
        return authorDAO.findAll(Example.of(author,
                ExampleMatcher.matching()
                        .withIgnoreCase()
                        .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
        ), Sort.by(Sort.Order.desc("createdAt")));
    }

    public Author saveAuthor(Author author) {
        return authorDAO.save(author);
    }

    public void deleteAuthorById(Long id) {
        Author author = findAuthorById(id);
        if(author != null) {
            author.setIsDeleted(true);
            authorDAO.save(author);
        }
    }
}
